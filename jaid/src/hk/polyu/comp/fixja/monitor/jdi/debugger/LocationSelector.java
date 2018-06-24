package hk.polyu.comp.fixja.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.WatchpointEvent;
import com.sun.jdi.request.*;
import hk.polyu.comp.fixja.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.fixja.fixer.config.FailureHandling;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.MethodToMonitor;
import hk.polyu.comp.fixja.tester.Tester;

import java.nio.file.Path;
import java.util.*;


public class LocationSelector extends AbstractDebuggerLauncher {

    private Set<LineLocation> locationsCoveredByTest;
    private Map<TestExecutionResult, Set<LineLocation>> allLocationsCovered;
    private Set<LineLocation> relevantLocations;
    private Set<Field> visitedFields;

    private List<BreakpointRequest> breakpointRequestsForMonitoring;
    private int nbrStackFramesAtMethodEntry;
    private List<WatchpointRequest> watchpointRequests;

    public List<BreakpointRequest> getBreakpointRequestsForMonitoring() {
        if (breakpointRequestsForMonitoring == null) {
            breakpointRequestsForMonitoring = new ArrayList<>();
        }
        return breakpointRequestsForMonitoring;
    }
    public LocationSelector(JavaProject project, LogLevel logLevel, long timeoutPerTest, FailureHandling failureHandling) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        allLocationsCovered = new HashMap<>();
        visitedFields = new HashSet<>();
        watchpointRequests = new LinkedList<>();
    }

    // Getters and Setters

    public Set<LineLocation> getRelevantLocations() {
        return relevantLocations;
    }

    public Map<TestExecutionResult, Set<LineLocation>> getAllLocationsCovered() {
        return allLocationsCovered;
    }

    public int getNbrStackFramesAtMethodEntry() {
        return nbrStackFramesAtMethodEntry;
    }

    public void setNbrStackFramesAtMethodEntry(int nbrStackFramesAtMethodEntry) {
        this.nbrStackFramesAtMethodEntry = nbrStackFramesAtMethodEntry;
    }

    // Operations

    public void pruneIrrelevantLocationsAndTests(){
        relevantLocations = new HashSet<>();
        for(TestExecutionResult result: getAllLocationsCovered().keySet()){
            if(!result.wasSuccessful()){
                relevantLocations.addAll(getAllLocationsCovered().get(result));
            }
        }

        // prune irrelevant tests that cover none of the relevant locations
        // fixme: is the definition of the irrelevant tests correct?
        Set<TestExecutionResult> testsToRemove = new HashSet<>();
        for(TestExecutionResult result: getAllLocationsCovered().keySet()){
            Set<LineLocation> commonLocations = new HashSet<>(relevantLocations);
            commonLocations.retainAll(getAllLocationsCovered().get(result));
            if(commonLocations.isEmpty()){
                testsToRemove.add(result);
            }
            // prune timeout tests
            if (result.getRunTime()==-1){
                LoggingService.infoAll("Test removed due to timeout: "+result.getTestClassAndMethod());
                testsToRemove.add(result);
            }
        }
        testsToRemove.forEach(x -> getAllLocationsCovered().remove(x));
    }

    public List<TestExecutionResult> getRelevantTestResults(){
        return new ArrayList<>(getAllLocationsCovered().keySet());
    }

    // Override

    @Override
    protected String testsToRunInString() throws Exception {
        return getProject().commandLineArgumentForTestsToRun();
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getPre4LocationTestResultsLogFilePath();
    }

    @Override
    protected void debuggerFinished() {
        getProject().getMethodToMonitor().setAccessedFields(getVisitedFieldsString());
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException, ClassNotLoadedException {
        if (!referenceType.name().equals(Tester.class.getName())) {

            Method methodToMonitor = getMethodToMonitorFromType(referenceType);
            MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();

            getBreakpointRequestsForMonitoring().addAll( registerAllBreakpoint(getBreakpointLocations(methodToMonitor, infoCenter.getRelevantLocationStatementMap().keySet()), shouldEnable));
            // Prepare extra breakpoint requests for evaluate field visit (R/W) and method exit.
            prepareFieldVisitEventRequests(referenceType);
        }else{
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }
    }

    @Override
    protected void handleOtherEventType(EventSet eventSet, Event event) {
        if (event instanceof WatchpointEvent) {
            WatchpointEvent wEvent = (WatchpointEvent) event;
            Field field = wEvent.field();
            visitedFields.add(field);
        }
        eventSet.resume();
    }

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        locationsCoveredByTest = new HashSet<>();
        getAllLocationsCovered().put(getCurrentTestResult(), locationsCoveredByTest);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.enable());
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent){
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.disable());
    }

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        if (breakpointEvent.location().equals(getMtfEntryLocationBreakpoint())) {
            setNbrStackFramesAtMethodEntry(safeGetNbrStackFrames(breakpointEvent));
            watchpointRequests.forEach(x -> x.enable());
        }
        else if(breakpointEvent.location().equals(getMtfExitLocationBreakpoint())){
            if(getNbrStackFramesAtMethodEntry() == safeGetNbrStackFrames(breakpointEvent))
                watchpointRequests.forEach(x -> x.disable());
        }
        else{
            MethodToMonitor methodToMonitor = getProject().getMethodToMonitor();
            LineLocation location = new LineLocation(methodToMonitor, breakpointEvent.location().lineNumber());
            locationsCoveredByTest.add(location);
        }
    }

    // Implementation details

    private void prepareFieldVisitEventRequests(ReferenceType referenceType) {
        List<Field> fields = referenceType.allFields();
        watchpointRequests = new LinkedList<>();
        for (Field field : fields) {
            // fixme: it's more precise to also use instance filter, when available.
            // fixme: For that purpose, we need to distinguish instance methods from static ones.
            AccessWatchpointRequest awRequest = getEventRequestManager().createAccessWatchpointRequest(field);
            awRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            watchpointRequests.add(awRequest);

            ModificationWatchpointRequest mwRequest = getEventRequestManager().createModificationWatchpointRequest(field);
            mwRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            watchpointRequests.add(mwRequest);
        }
    }

    private Set<String> getVisitedFieldsString() {
        Set<String> visitedFieldsStr = new HashSet<>();
        visitedFields.stream().forEach(field -> visitedFieldsStr.add(field.toString()));
        return visitedFieldsStr;
    }


}
