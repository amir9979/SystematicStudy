package hk.polyu.comp.fixja.monitor.snapshot;

import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import org.eclipse.jdt.core.dom.*;

/**
 * Created by Max PEI.
 */
public class StateSnapshotUnaryExpression extends StateSnapshotExpression{

    public static StateSnapshotUnaryExpression getUnaryExpression(ExpressionToMonitor operand, UnaryOperator operator){

        AST ast = operand.getExpressionAST().getAST();
        switch (operator){
            case No_OP:
                return new StateSnapshotUnaryExpression(operand, operator, operand.getExpressionAST(), operand.getType());

            case NEGATION:
                if(!operand.isBooleanType())
                    throw new IllegalStateException();

                PrefixExpression prefixExpression = operand.getExpressionAST().getAST().newPrefixExpression();
                prefixExpression.setOperator(PrefixExpression.Operator.NOT);
                if (OperatorPrecedence.hasOperatorGreaterPrecedence(PrefixExpression.Operator.COMPLEMENT, operand.getExpressionAST())) {
                    ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                    parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
                    prefixExpression.setOperand(parenthesizedExpression);
                } else {
                    prefixExpression.setOperand((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
                }
                return new StateSnapshotUnaryExpression(operand, operator, prefixExpression, operand.getType());

            case IS_NULL:
            case IS_NOT_NULL:
                if(!operand.isReferenceType())
                    throw new IllegalStateException();

                Expression left = ast.newNullLiteral();
                Expression right;
                if(OperatorPrecedence.hasOperatorGreaterEqualPrecedence(InfixExpression.Operator.EQUALS, operand.getExpressionAST())){
                    ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                    parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
                    right = parenthesizedExpression;
                }
                else{
                    right = (Expression) ASTNode.copySubtree(ast, operand.getExpressionAST());
                }

                InfixExpression infixExpression = operand.getExpressionAST().getAST().newInfixExpression();
                infixExpression.setOperator(operator == UnaryOperator.IS_NULL ? InfixExpression.Operator.EQUALS : InfixExpression.Operator.NOT_EQUALS);
                infixExpression.setLeftOperand(left);
                infixExpression.setRightOperand(right);

                return new StateSnapshotUnaryExpression(operand, operator, infixExpression, operand.getExpressionAST().getAST().resolveWellKnownType("boolean"));

            default:
                throw new IllegalStateException();
        }
    }

    public static InfixExpression negation(InfixExpression expression){
        Expression left = expression.getLeftOperand();
        Expression right = expression.getRightOperand();
        InfixExpression.Operator operator = expression.getOperator();
        InfixExpression.Operator newOperator;
        if(operator.equals(InfixExpression.Operator.EQUALS)){
            newOperator = InfixExpression.Operator.NOT_EQUALS;
        }
        else if(operator.equals(InfixExpression.Operator.NOT_EQUALS)){
            newOperator = InfixExpression.Operator.EQUALS;
        }
        else if(operator.equals(InfixExpression.Operator.GREATER)){
            newOperator = InfixExpression.Operator.LESS_EQUALS;
        }
        else if(operator.equals(InfixExpression.Operator.GREATER_EQUALS)){
            newOperator = InfixExpression.Operator.LESS;
        }
        else if(operator.equals(InfixExpression.Operator.LESS)){
            newOperator = InfixExpression.Operator.GREATER_EQUALS;
        }
        else if(operator.equals(InfixExpression.Operator.LESS_EQUALS)){
            newOperator = InfixExpression.Operator.GREATER;
        }
        else{
            return null;
        }

        AST ast = expression.getAST();
        InfixExpression result = ast.newInfixExpression();
        result.setOperator(newOperator);
        result.setLeftOperand((Expression)ASTNode.copySubtree(ast, expression.getLeftOperand()));
        result.setRightOperand((Expression)ASTNode.copySubtree(ast, expression.getRightOperand()));
        return result;
    }

    public ExpressionToMonitor getOperand() {
        return operand;
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    private final ExpressionToMonitor operand;
    private final UnaryOperator operator;

    private StateSnapshotUnaryExpression(ExpressionToMonitor operand, UnaryOperator operator, Expression wholeExpression, ITypeBinding type) {
        super(wholeExpression, type);

        this.operand = operand;
        this.operator = operator;

        this.getOperands().add(operand);
    }

    public enum UnaryOperator {
        No_OP(""), NEGATION("!"),
        IS_NULL("null == "), IS_NOT_NULL("null != ");

        private String symbol;


        UnaryOperator(String symbol) {
            this.symbol = symbol;
        }

        public String toString() {
            return symbol;
        }
    }

    @Override
    public DebuggerEvaluationResult evaluate(ProgramState state) {
        DebuggerEvaluationResult result = state.getValue(getOperand());
        if(result == null)
            return DebuggerEvaluationResult.getDebuggerEvaluationResultSyntaxError();

        if(result.hasSyntaxError() || result.hasSemanticError())
            return result;

        switch (getOperator()){
            case No_OP:
                return result;

            case NEGATION:
                if(!(result instanceof DebuggerEvaluationResult.BooleanDebuggerEvaluationResult))
                    throw new IllegalStateException();

                DebuggerEvaluationResult.BooleanDebuggerEvaluationResult booleanResult = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) result;
                return DebuggerEvaluationResult.getBooleanDebugValue(!booleanResult.getValue());

            case IS_NULL:
            case IS_NOT_NULL:
                if(!(result instanceof DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult))
                    throw new IllegalStateException();

                DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult referenceResult = (DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult) result;
                return DebuggerEvaluationResult.getBooleanDebugValue(getOperator() == UnaryOperator.IS_NULL ? referenceResult.isNull() : !referenceResult.isNull());

            default:
                throw new IllegalStateException();
        }
    }
}
