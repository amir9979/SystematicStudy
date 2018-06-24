package hk.polyu.comp.fixja.monitor.jdi.debugger;

import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import hk.polyu.comp.fixja.tester.TestRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class TestExecutionResult {
    private String testClass;
    private String testMethod;
    private long runTime;
    private boolean wasSuccessful;
    private boolean wasTestKilled;

    private List<ProgramState> observedStates;
    private List<ProgramState> derivedStates;

    private List<ProgramState> exitStates;
    private List<Integer> observedLocations;

    public TestExecutionResult(String testClass, String testMethod) {
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.runTime = 0;
        this.wasSuccessful = false;
        this.wasTestKilled = false;
    }

    public boolean isForRequest(TestRequest request) {
        return getTestClass().equals(request.getTestClass()) && getTestMethod().equals(request.getTestMethod());
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public String getTestClassAndMethod() {
        return testClass + "." + testMethod;
    }

    public static final double MINIMAL_SIMILARITY = 0.1;

    public long getRunTime() {
        return runTime;
    }

    private static Set<Integer> getObservedLocations(TestExecutionResult testExecutionResult) {
        Set<Integer> observedLocations;
        if (testExecutionResult.getObservedLocations().size() > 0)
            observedLocations = new HashSet<>(testExecutionResult.getObservedLocations());
        else
            observedLocations = testExecutionResult.getObservedStates().stream().mapToInt(x -> x.getLocation().getLineNo()).boxed().collect(Collectors.toSet());
        return observedLocations;
    }

    public List<ProgramState> getObservedStates() {
        if (observedStates == null)
            observedStates = new LinkedList<>();

        return observedStates;
    }

    public void deriveStatesUsingStateSnapshotExpressions(Set<StateSnapshotExpression> stateSnapshotExpressions) {
        derivedStates = new LinkedList<>();
        getObservedStates().forEach(x -> getDerivedStates().add(x.getExtendedState(stateSnapshotExpressions)));
    }

    public List<ProgramState> getDerivedStates() {
        if (derivedStates == null)
            throw new IllegalStateException();

        return derivedStates;
    }

    public void clearDerivedStates() {
        derivedStates.clear();
        derivedStates = null;
    }

    public boolean wasSuccessful() {
        return !wasTestKilled && wasSuccessful;
    }

    public void setRunTime(long runTime) {
        this.runTime = runTime;
    }

    public void setWasSuccessful(boolean wasSuccessful) {
        this.wasSuccessful = wasSuccessful;
    }

    public boolean wasTestKilled() {
        return wasTestKilled;
    }

    public void setTestKilled(boolean wasTestKilled) {
        this.wasTestKilled = wasTestKilled;
    }

    public List<Integer> getObservedLocations() {
        if (observedLocations == null)
            observedLocations = new LinkedList<>();
        return observedLocations;
    }

    public List<ProgramState> getExitStates() {
        if (exitStates == null)
            exitStates = new LinkedList<>();

        return exitStates;
    }

    public void addExitState(ProgramState exitState) {
        this.getExitStates().add(exitState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestExecutionResult that = (TestExecutionResult) o;

        if (getRunTime() != that.getRunTime()) return false;
        if (wasSuccessful != that.wasSuccessful) return false;
        if (!getTestClass().equals(that.getTestClass())) return false;
        return getTestMethod().equals(that.getTestMethod());
    }

    @Override
    public int hashCode() {
        int result = getTestClass().hashCode();
        result = 31 * result + getTestMethod().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TestExecutionResult{" +
                "testClass='" + testClass + '\'' +
                ", testMethod='" + testMethod + '\'' +
                ", runTime=" + runTime +
                ", wasSuccessful=" + wasSuccessful +
                '}';
    }
}
