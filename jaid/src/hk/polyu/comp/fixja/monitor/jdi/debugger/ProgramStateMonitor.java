package hk.polyu.comp.fixja.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.tools.example.debug.expr.ExpressionParser;
import hk.polyu.comp.fixja.fixer.config.FailureHandling;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import hk.polyu.comp.fixja.tester.Tester;
import org.eclipse.jdt.core.dom.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.util.CommonUtils.isSubExp;

public class ProgramStateMonitor extends AbstractDebuggerLauncher {

    private Map<Integer, LineLocation> validLocations;

    private List<BreakpointRequest> breakpointRequestsForMonitoring;
    private int nbrStackFramesAtMethodEntry;

    public List<BreakpointRequest> getBreakpointRequestsForMonitoring() {
        if (breakpointRequestsForMonitoring == null) {
            breakpointRequestsForMonitoring = new ArrayList<>();
        }
        return breakpointRequestsForMonitoring;
    }

    public ProgramStateMonitor(JavaProject project, LogLevel logLevel, long timeoutPerTest,
                               FailureHandling failureHandling, Set<LineLocation> validLocations) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        this.validLocations = validLocations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));
    }

    // ============================================== Override

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        nbrStackFramesAtMethodEntry = -1;

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.enable());
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.disable());
    }

    @Override
    protected String testsToRunInString() throws Exception {
        return getProject().commandLineArgumentForTestsToRun();
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getMonitoredTestResultsLogFilePath();
    }

    @Override
    protected void debuggerFinished() {
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException {
        if (!referenceType.name().equals(Tester.class.getName())) {
            Method methodToMonitor = getMethodToMonitorFromType(referenceType);
            getBreakpointRequestsForMonitoring().addAll(addBreakPointToAllLocationsInMethod(methodToMonitor, shouldEnable));
        } else {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }

    }

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        Location location = breakpointEvent.location();
        int lineNo = location.lineNumber();

        if (getMtfEntryLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);
        } else if (getMtfExitLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == safeGetNbrStackFrames(breakpointEvent)) {
            nbrStackFramesAtMethodEntry = -1;
            monitorExitState(breakpointEvent);

        } else {
            if (validLocations.containsKey(lineNo)) {
                ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 0, null);

                LineLocation lineLocation = validLocations.get(lineNo);
                Set<ExpressionToMonitor> expressionToMonitorSet = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter()
                        .getLocationExpressionMap().get(lineLocation);

                ProgramState state = monitoring(getFrame, expressionToMonitorSet, lineLocation);
                getCurrentTestResult().getObservedStates().add(state);
            }
            getCurrentTestResult().getObservedLocations().add(lineNo);
        }
    }


    private ProgramState monitoring(ExpressionParser.GetFrame getFrame, Set<ExpressionToMonitor> expressionToMonitorSet, LineLocation location) {
        ProgramState state = new ProgramState(location);

        Set<ExpressionToMonitor> invalidExpressions = new HashSet<>();
        for (ExpressionToMonitor etm : expressionToMonitorSet) {
            if (isSpecialCase(location, etm)) continue;
            if (etm.isInvokeMTF()) continue;

            DebuggerEvaluationResult debuggerEvaluationResult = evaluate(getVirtualMachine(), getFrame, etm);

            if (debuggerEvaluationResult.hasSyntaxError())
                invalidExpressions.add(etm);

            if (!debuggerEvaluationResult.hasSemanticError() && !debuggerEvaluationResult.hasSyntaxError())
                state.extend(etm, debuggerEvaluationResult);
        }
        expressionToMonitorSet.removeAll(invalidExpressions);

        return state;
    }

    // fixme: is this necessary?
    public boolean isSpecialCase(LineLocation location, ExpressionToMonitor exp) {
        Map<LineLocation, Statement> locationStatementMap = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter().getRelevantLocationStatementMap();
        if (locationStatementMap.containsKey(location)) {
            Statement oldStmt = locationStatementMap.get(location);
            if (oldStmt instanceof ForStatement) {
                ForStatement forStmt = (ForStatement) oldStmt;
                for (Object init : forStmt.initializers()) {
                    if (init instanceof VariableDeclarationExpression) {
                        VariableDeclarationExpression initExp = (VariableDeclarationExpression) init;
                        for (Object o : initExp.fragments()) {
                            VariableDeclarationFragment oexp = (VariableDeclarationFragment) o;
                            if (isSubExp(exp, oexp.getName()))
                                return true;
                        }
                    }
                }
            } else if (oldStmt instanceof EnhancedForStatement) {
                EnhancedForStatement forStmt = (EnhancedForStatement) oldStmt;
                SingleVariableDeclaration initExp = (SingleVariableDeclaration) forStmt.getParameter();
                if (isSubExp(exp, initExp.getName()))
                    return true;
            }
            return false;
        }
        return false;
    }
}
