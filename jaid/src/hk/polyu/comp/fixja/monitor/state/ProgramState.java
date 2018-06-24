package hk.polyu.comp.fixja.monitor.state;

import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshotExpression;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ProgramState {

    private final LineLocation location;
    private Map<ExpressionToMonitor, DebuggerEvaluationResult> expressionToValueMap;

    public ProgramState(LineLocation location) {
        this.location = location;
        this.expressionToValueMap = new HashMap<>();
    }

    public LineLocation getLocation() {
        return location;
    }

    public Map<ExpressionToMonitor, DebuggerEvaluationResult> getExpressionToValueMap() {
        return expressionToValueMap;
    }

    public void extend(ExpressionToMonitor expressionToMonitor, DebuggerEvaluationResult result){
        if(getExpressionToValueMap().containsKey(expressionToMonitor)){
            throw new IllegalStateException();
        }

        getExpressionToValueMap().put(expressionToMonitor, result);
    }

    public DebuggerEvaluationResult getValue(ExpressionToMonitor expressionToMonitor){
        if(expressionToMonitor.isLiteral())
            return DebuggerEvaluationResult.getIntegerDebugValue(expressionToMonitor.getLiteralIntegerValue());

        if(getExpressionToValueMap().containsKey(expressionToMonitor))
            return getExpressionToValueMap().get(expressionToMonitor);

        return null;
    }

    public ProgramState getExtendedState(Set<StateSnapshotExpression> extendedExpressions){
        ProgramState newState = new ProgramState(this.getLocation());
        for(StateSnapshotExpression expression: extendedExpressions){
            DebuggerEvaluationResult evaluationResult = expression.evaluate(this);
            //newState contains expressions that has error DebuggerEvaluationResult
            newState.extend(expression, evaluationResult);
        }
        return newState;
    }
    public ProgramState getExtendedStateWithoutErrorResult(Set<StateSnapshotExpression> extendedExpressions){
        ProgramState newState = new ProgramState(this.getLocation());
        for(StateSnapshotExpression expression: extendedExpressions){
            DebuggerEvaluationResult evaluationResult = expression.evaluate(this);
            //newState dose not contains expressions that has error DebuggerEvaluationResult
            if(!(evaluationResult.hasSyntaxError()||evaluationResult.hasSemanticError()))
                newState.extend(expression, evaluationResult);
        }
        return newState;
    }

    public double computeSimilarity(ProgramState other){
        Set<ExpressionToMonitor> commonExpressions = expressionToValueMap.keySet();
        commonExpressions.retainAll(other.expressionToValueMap.keySet());

        int commonValues = 0;
        for(ExpressionToMonitor exp: commonExpressions){
            if(expressionToValueMap.get(exp).equals(other.expressionToValueMap.get(exp)))
                commonValues++;
        }

        return ((double)commonValues) / (expressionToValueMap.size() + other.expressionToValueMap.size() - commonValues);
    }
}
