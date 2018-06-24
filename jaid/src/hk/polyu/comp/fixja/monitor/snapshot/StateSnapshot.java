package hk.polyu.comp.fixja.monitor.snapshot;

import hk.polyu.comp.fixja.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;

import static hk.polyu.comp.fixja.util.CommonUtils.checkParenthesizeNeeded;

/**
 * Created by Max PEI.
 */
public class StateSnapshot {
    private static long nextID = 0;
    private long ID;

    private static long getNextID(){
        return nextID++;
    }

    private final LineLocation location;
    private final StateSnapshotExpression snapshotExpression;
    private final DebuggerEvaluationResult value;

    private int occurrenceInPassing;
    private int occurrenceInFailing;
    private double distanceToFailure;

    private double suspiciousness;

    public StateSnapshot(LineLocation location, StateSnapshotExpression snapshotExpression, DebuggerEvaluationResult value) {
        this.location = location;
        this.snapshotExpression = snapshotExpression;
        this.value = value;
        ID = getNextID();
    }

    public long getID() {
        return ID;
    }

    public LineLocation getLocation() {
        return location;
    }

    public StateSnapshotExpression getSnapshotExpression() {
        return snapshotExpression;
    }

    private boolean instantiateSchemaC = true;

    public boolean shouldInstantiateSchemaC() {
        return instantiateSchemaC;
    }

    public void disableInstantiateSchemaC() {
        this.instantiateSchemaC = false;
    }

    private Expression failingStateExpression;
    public Expression getFailingStateExpression() {
        if (failingStateExpression==null){
            AST ast=getSnapshotExpression().getExpressionAST().getAST();
            InfixExpression infixExpression=ast.newInfixExpression();
            Expression snapshotExp=checkParenthesizeNeeded((Expression)ASTNode.copySubtree(ast, getSnapshotExpression().getExpressionAST()));
            infixExpression.setLeftOperand(snapshotExp);
            infixExpression.setRightOperand(ast.newBooleanLiteral(((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult)value).getValue()));
            infixExpression.setOperator(InfixExpression.Operator.EQUALS);
            failingStateExpression=infixExpression;
        }
        return failingStateExpression;
    }
    public Expression getFailingStateExpressionNegation() {
        AST ast=getSnapshotExpression().getExpressionAST().getAST();
        InfixExpression infixExpression=ast.newInfixExpression();
        Expression snapshotExp=checkParenthesizeNeeded((Expression)ASTNode.copySubtree(ast, getSnapshotExpression().getExpressionAST()));
        infixExpression.setLeftOperand(snapshotExp);
        infixExpression.setRightOperand(ast.newBooleanLiteral(((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult)value).getValue()));
        infixExpression.setOperator(InfixExpression.Operator.NOT_EQUALS);
        return infixExpression;
    }

    public DebuggerEvaluationResult getValue() {
        return value;
    }

    public double getSuspiciousness() {
        return suspiciousness;
    }

    public int getOccurrenceInPassing() {
        return occurrenceInPassing;
    }

    public int getOccurrenceInFailing() {
        return occurrenceInFailing;
    }

    public void increaseOccurrenceInPassing() {
        this.occurrenceInPassing++;
    }

    public void increaseOccurrenceInFailing() {
        this.occurrenceInFailing++;
    }

    public void setDistanceToFailure(double distanceToFailure) {
        // Ignore distance greater than MAXIMUM_DISTANCE_TO_FAILURE.
        this.distanceToFailure = Math.min(distanceToFailure, LineLocation.getMaximumDistanceToFailure());
    }

    public void computeSuspiciousness(MethodDeclarationInfoCenter infoCenter){
        double frequencyContribution = GAMMA + ALPHA / (1 - ALPHA) * (1 - BETA + BETA * Math.pow(ALPHA, occurrenceInPassing) - Math.pow(ALPHA, occurrenceInFailing));
        double distanceContribution = 1 - this.distanceToFailure / (LineLocation.getMaximumDistanceToFailure() + 1);
        double similarityContribution = MINIMUM_SIMILARITY + ExpressionToMonitor.similarityBetween(getSnapshotExpression().getSubExpressions(), infoCenter.getExpressionsAppearAtLocationMap().get(getLocation()));

        // suspiciousness = 3.0 / (1 / frequencyContribution + 1 / distanceContribution + 1 / similarityContribution);
        // suspiciousness = frequencyContribution;
        suspiciousness = 2.0 / (1 / frequencyContribution + 1 / similarityContribution);
    }

    @Override
    public String toString() {
        return "StateSnapshot{" +
                "ID=" + ID +
                ", location=" + location +
                ", snapshotExpression=" + snapshotExpression +
                ", value=" + value +
                ", suspiciousness=" + suspiciousness +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StateSnapshot that = (StateSnapshot) o;

        if (!getLocation().equals(that.getLocation())) return false;
        if (!getSnapshotExpression().equals(that.getSnapshotExpression())) return false;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        int result = getLocation().hashCode();
        result = 31 * result + getSnapshotExpression().hashCode();
        result = 31 * result + getValue().hashCode();
        return result;
    }

    public static final double MAXIMUM_DISTANCE_TO_FAILURE = 50.0;
    public static final double MINIMUM_SIMILARITY = 1;
    public static final double ALPHA = 1.0 / 3;
    public static final double BETA = 2.0 / 3;
    public static final double GAMMA = 1.0;
}
