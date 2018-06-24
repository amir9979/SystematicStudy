package hk.polyu.comp.fixja.monitor.snapshot;

import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import org.eclipse.jdt.core.dom.*;

/**
 * Created by Max PEI.
 */
public class StateSnapshotBinaryExpression extends StateSnapshotExpression{

    public static StateSnapshotBinaryExpression getBinaryExpression(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand, BinaryOperator operator){
        InfixExpression.Operator infixOperator;
        Expression left, right;

        switch (operator){
            case EQUAL:
                infixOperator = InfixExpression.Operator.EQUALS;
                break;
            case NOT_EQUAL:
                infixOperator = InfixExpression.Operator.NOT_EQUALS;
                break;
            case GREATER_THAN:
                infixOperator = InfixExpression.Operator.GREATER;
                break;
            case GREATER_THAN_OR_EQUAL:
                infixOperator = InfixExpression.Operator.GREATER_EQUALS;
                break;
            case LESS_THAN:
                infixOperator = InfixExpression.Operator.LESS;
                break;
            case LESS_THAN_OR_EQUAL:
                infixOperator = InfixExpression.Operator.LESS_EQUALS;
                break;
            case CONDITIONAL_AND:
                infixOperator = InfixExpression.Operator.CONDITIONAL_AND;
                break;
            case CONDITIONAL_OR:
                infixOperator = InfixExpression.Operator.CONDITIONAL_OR;
                break;
            default:
                throw new IllegalStateException();
        }
        AST ast = leftOperand.getExpressionAST().getAST();
        InfixExpression infixExpression = ast.newInfixExpression();
        infixExpression.setOperator(infixOperator);

        if(OperatorPrecedence.hasOperatorGreaterEqualPrecedence(infixOperator, leftOperand.getExpressionAST())){
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, leftOperand.getExpressionAST()));
            left = parenthesizedExpression;
        }
        else{
            left = (Expression) ASTNode.copySubtree(ast, leftOperand.getExpressionAST());
        }

        if(OperatorPrecedence.hasOperatorGreaterEqualPrecedence(infixOperator, rightOperand.getExpressionAST())){
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, rightOperand.getExpressionAST()));
            right = parenthesizedExpression;
        }
        else{
            right = (Expression) ASTNode.copySubtree(ast, rightOperand.getExpressionAST());
        }

        infixExpression.setLeftOperand(left);
        infixExpression.setRightOperand(right);
        StateSnapshotBinaryExpression result = new StateSnapshotBinaryExpression(leftOperand, rightOperand, operator, infixExpression,
                leftOperand.getExpressionAST().getAST().resolveWellKnownType("boolean"));

        return result;
    }

    public ExpressionToMonitor getLeftOperand() {
        return leftOperand;
    }

    public ExpressionToMonitor getRightOperand() {
        return rightOperand;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    @Override
    public DebuggerEvaluationResult evaluate(ProgramState state) {
        DebuggerEvaluationResult leftResult = state.getValue(getLeftOperand());
        DebuggerEvaluationResult rightResult = state.getValue(getRightOperand());

        if(leftResult == null || leftResult.hasSyntaxError() || rightResult == null || rightResult.hasSyntaxError())
            return DebuggerEvaluationResult.getDebuggerEvaluationResultSyntaxError();

        if(leftResult.hasSemanticError() || rightResult.hasSemanticError())
            return DebuggerEvaluationResult.getDebuggerEvaluationResultSemanticError();

        DebuggerEvaluationResult.IntegerDebuggerEvaluationResult leftIntegerResult, rightIntegerResult;
        DebuggerEvaluationResult.LongDebuggerEvaluationResult leftLongResult, rightLongResult;
        DebuggerEvaluationResult.DoubleDebuggerEvaluationResult  leftDoubleResult, rightDoubleResult;
        DebuggerEvaluationResult.BooleanDebuggerEvaluationResult leftBooleanResult, rightBooleanResult;
        switch (getOperator()){
            case EQUAL:
            case NOT_EQUAL:
                return DebuggerEvaluationResult.getBooleanDebugValue(getOperator() == BinaryOperator.EQUAL ?
                        leftResult.equals(rightResult) : !leftResult.equals(rightResult));

            case GREATER_THAN:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isGreater(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isGreater(rightLongResult));

                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isGreater(rightDoubleResult));
                }

            case GREATER_THAN_OR_EQUAL:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isGreaterEqual(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isGreaterEqual(rightLongResult));

                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isGreaterEqual(rightDoubleResult));
                }

            case LESS_THAN:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isLess(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isLess(rightLongResult));

                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isLess(rightDoubleResult));
                }

            case LESS_THAN_OR_EQUAL:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isLessEqual(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isLessEqual(rightLongResult));

                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isLessEqual(rightDoubleResult));
                }

            case CONDITIONAL_AND:
                leftBooleanResult  = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) leftResult;
                rightBooleanResult = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) rightResult;
                return DebuggerEvaluationResult.getBooleanDebugValue(leftBooleanResult.getValue() && rightBooleanResult.getValue());

            case CONDITIONAL_OR:
                leftBooleanResult  = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) leftResult;
                rightBooleanResult = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) rightResult;
                return DebuggerEvaluationResult.getBooleanDebugValue(leftBooleanResult.getValue() || rightBooleanResult.getValue());

            default:
                throw new IllegalStateException();
        }
    }

    private StateSnapshotBinaryExpression(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand,
                                          BinaryOperator operator, Expression wholeExpression, ITypeBinding typeBinding){
        super(wholeExpression, typeBinding);

        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.operator = operator;

        this.getOperands().add(leftOperand);
        this.getOperands().add(rightOperand);
    }

    public enum BinaryOperator {
        EQUAL("=="), NOT_EQUAL("!="),
        GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">="),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="),
        CONDITIONAL_OR("||"), CONDITIONAL_AND("&&");

        private String symbol;

        BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        public String toString() {
            return symbol;
        }
    }

    private final ExpressionToMonitor leftOperand;
    private final ExpressionToMonitor rightOperand;
    private final BinaryOperator operator;


}
