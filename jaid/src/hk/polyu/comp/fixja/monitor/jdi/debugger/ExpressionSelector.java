package hk.polyu.comp.fixja.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.tools.example.debug.expr.ExpressionParser;
import hk.polyu.comp.fixja.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.fixja.fixer.config.FailureHandling;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.monitor.*;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.tester.TestRequest;
import hk.polyu.comp.fixja.tester.Tester;
import hk.polyu.comp.fixja.tester.TesterConfig;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;

public class ExpressionSelector extends AbstractDebuggerLauncher {

    private Map<Integer, LineLocation> validLocations;
    private MethodDeclarationInfoCenter infoCenter;
    private Set<ExpressionToMonitor> expressionsToCheck;

    private List<BreakpointRequest> breakpointRequestList;
    private TestRequest passingTest, failingTest;
    private boolean hasFoundExpressionWithSideEffect;

    public ExpressionSelector(JavaProject project, LogLevel logLevel, long timeoutPerTest, FailureHandling failureHandling,
            Set<LineLocation> validLocations, List<TestExecutionResult> lastTestResults) throws Exception {
        super(project, logLevel, timeoutPerTest, failureHandling);

        this.validLocations = validLocations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));
        this.infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
        this.expressionsToCheck = this.infoCenter.getExpressionsToMonitorWithinMethod().stream().filter(ExpressionToMonitor::hasMethodInvocation).collect(Collectors.toSet());

        selectTests(lastTestResults);
    }

    // ======================================== Operations

    public void doSelection() throws Exception {
        setFoundExpressionWithSideEffect(false);
        launch();
    }

    // ======================================== Getters and Setters

    public boolean hasFoundExpressionWithSideEffect() {
        return hasFoundExpressionWithSideEffect;
    }

    public void setFoundExpressionWithSideEffect(boolean hasFoundExpressionWithSideEffect) {
        this.hasFoundExpressionWithSideEffect = hasFoundExpressionWithSideEffect;
    }

    public Map<Integer, LineLocation> getValidLocations() {
        return validLocations;
    }

    public TestRequest getPassingTest() {
        return passingTest;
    }

    public TestRequest getFailingTest() {
        return failingTest;
    }

    // ======================================== Override

    @Override
    protected String testsToRunInString() throws Exception {
        return getProject().commandLineArgumentForTestsToRun(Arrays.asList(passingTest, failingTest));
    }

    @Override
    protected String argumentsForTester() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(TesterConfig.ACTIVE_IS_MONITOR_MODE).append(" ").append(Boolean.TRUE).append(" ")
                .append(super.argumentsForTester());
        return sb.toString();
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getPre4ExpTestResultsLogFilePath();
    }

    @Override
    protected void debuggerFinished() {
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException, ClassNotLoadedException {
        if (!referenceType.name().equals(Tester.class.getName()))
            breakpointRequestList = addBreakPointToLocations(referenceType, validLocations.values(), shouldEnable);
    }

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        if(breakpointRequestList != null && !breakpointRequestList.isEmpty()) {
            breakpointRequestList.forEach(x -> x.enable());
        }
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if(breakpointRequestList != null && !breakpointRequestList.isEmpty()) {
            breakpointRequestList.forEach(x -> x.disable());
        }
    }

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 0, null);

        if (validLocations.containsKey(breakpointEvent.location().lineNumber())) {
            for (ExpressionToMonitor exp : expressionsToCheck) {
                if (!exp.hasChangedState()) {
                    if (hasSurelySideEffect(getVirtualMachine(), getFrame, exp)) {
                        exp.setChangedState(true);
                        setFoundExpressionWithSideEffect(true);

                        if(shouldLogDebug()) {
                            LoggingService.debugAll("Expression with side effect: " + exp.getText());
                        }
                    }
                }
            }
        }
    }

    // ======================================== Implementation details

    private static boolean hasSurelySideEffect(VirtualMachine vm, ExpressionParser.GetFrame getFrame, ExpressionToMonitor expToCheck){
        DebuggerEvaluationResult debuggerEvaluationResult;

        Map<ExpressionToMonitor, DebuggerEvaluationResult> preState = new HashMap<>();
        // record only successful evaluations of the guard expressions
        for(ExpressionToMonitor exp: expToCheck.getGuardExpressions()){
            debuggerEvaluationResult = evaluate(vm, getFrame, exp);
            if(!debuggerEvaluationResult.hasSyntaxError()
                    && !debuggerEvaluationResult.hasSemanticError()) {
                preState.put(exp, debuggerEvaluationResult);
            }
        }

        debuggerEvaluationResult = evaluate(vm, getFrame, expToCheck);
        if(debuggerEvaluationResult.isInvokeMTF()){
            expToCheck.setInvokeMTF(true);
            return false;
        }
        if(debuggerEvaluationResult.hasSyntaxError())
            return false;


        // compare
        for(ExpressionToMonitor exp: preState.keySet()){
            DebuggerEvaluationResult previousValue = preState.get(exp);
            debuggerEvaluationResult = evaluate(vm, getFrame, exp);
            if(!previousValue.equals(debuggerEvaluationResult)) {
                return true;
            }
        }
        return false;
    }

    private void selectTests(List<TestExecutionResult> lastTestResults) {
        if (lastTestResults != null) {
            List<TestExecutionResult> passings = lastTestResults.stream().filter(result -> result.wasSuccessful()).collect(Collectors.toList());
            List<TestExecutionResult> failings = lastTestResults.stream().filter(result -> !result.wasSuccessful()).collect(Collectors.toList());
            for (TestExecutionResult f : failings) {
                for (TestRequest ttr : getProject().getTestsToRun()) {
                    if(ttr != null && f.isForRequest(ttr)) {
                        failingTest = ttr;
                        break;
                    }
                }
            }
            for (TestExecutionResult p : passings) {
                for (TestRequest ttr : getProject().getTestsToRun()) {
                    if (ttr != null && p.isForRequest(ttr)){
                        passingTest = ttr;
                        break;
                    }
                }
            }
            if (failingTest == null)
                throw new IllegalStateException("No failing test.");
        }
    }


}
