package hk.polyu.comp.fixja.fixaction;

import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.jdi.debugger.TestExecutionResult;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshot;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Ls CHEN.
 */
public class FixAction {
    public static int CURRENT_SEQUENCE_NO = 0;

    private int fixId;
    private String fix;
    private Schemas.Schema schema;

    private List<TestExecutionResult> testExecutionResults;
    private List<TestExecutionResult> successfulTestExecutionResults;
    private int exitLineNo;
    private int entryLineNo;
    private double desirability;

    private double evaluated = Integer.MIN_VALUE;
    private StateSnapshot stateSnapshot;
    private String seed;
    private boolean isWellformed;
    private boolean isValid;
    private boolean wasValidated;
    private double snippet_simi;
    private String statementTextToBeReplaced;


    public FixAction(String fix, StateSnapshot stateSnapshot, Schemas.Schema fixSchema, String seed, double snippet_simi) {
        this.fix = fix;
        this.fixId = CURRENT_SEQUENCE_NO++;
        this.stateSnapshot = stateSnapshot;
        this.schema = fixSchema;
        this.seed = seed + ";; Schema-" + fixSchema;
        this.isWellformed = true;
        this.isValid = false;
        this.snippet_simi = snippet_simi;
    }

    public String getStatementTextToBeReplaced() {
        return statementTextToBeReplaced;
    }

    public int getExitLineNo() {
        return exitLineNo;
    }

    public double getDesirability() {
        return desirability;
    }

    public void setDesirability(double desirability) {
        this.desirability = desirability;
    }

    public void setExitLineNo(int exitLineNo) {
        this.exitLineNo = exitLineNo;
    }

    public int getEntryLineNo() {
        return entryLineNo;
    }

    public void setEntryLineNo(int entryLineNo) {
        this.entryLineNo = entryLineNo;
    }

    public void setStatementTextToBeReplaced(String statementTextToBeReplaced) {
        this.statementTextToBeReplaced = statementTextToBeReplaced;
    }

    public boolean isWellformed() {
        return isWellformed;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public void setWellformed(boolean wellformed) {
        isWellformed = wellformed;
    }

    public boolean wasValidated() {
        return wasValidated;
    }

    public void setValidated(boolean wasValidated) {
        this.wasValidated = wasValidated;
    }

    public String getFix() {
        return fix;
    }

    public void setFix(String fix) {
        this.fix = fix;
    }

    public boolean needsValidation() {
        // fixme: when will getFix() be null or be empty?
        return getFix() != null && getFix().length() > 0 && isWellformed();
    }

    public StateSnapshot getStateSnapshot() {
        return stateSnapshot;
    }

    public LineLocation getLocation() {
        return getStateSnapshot().getLocation();
    }

    public List<TestExecutionResult> getTestExecutionResults() {
        return testExecutionResults;
    }

    public boolean wasValidationSuccessful() {
        return getSuccessfulTestExecutionResults().size() == 0;
    }

    public List<TestExecutionResult> getSuccessfulTestExecutionResults() {
        if (successfulTestExecutionResults == null) {
            successfulTestExecutionResults = getTestExecutionResults().stream().filter(x -> x != null && x.wasSuccessful()).collect(Collectors.toList());
        }
        return successfulTestExecutionResults;
    }

    public void setTestExecutionResults(List<TestExecutionResult> testExecutionResults) {
        if (this.testExecutionResults != null)
            throw new IllegalStateException("testExecutionResults is immutable.");

        this.testExecutionResults = testExecutionResults;
    }

    public void clearDebugTestResults() {
        testExecutionResults = null;
        successfulTestExecutionResults = null;
    }

    public int getFixId() {
        return fixId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FixAction{")
                .append("fixId=").append(fixId);
        sb.append(", seed=").append(seed);
        sb.append(", simi=").append(snippet_simi);
        sb.append(", score=").append(evaluated);
        sb.append(", fix=[\n").append(fix).append("]");
        if (getLocation() != null)
            sb.append("\n, location=").append(getLocation().getLineNo()).append("::").append(getLocation().getStatement());
        sb.append("}\n");
        return sb.toString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FixAction fixAction = (FixAction) o;

        if (!fix.trim().equals(fixAction.fix.trim())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        result = fix.trim().hashCode();
        return result;
    }
}
