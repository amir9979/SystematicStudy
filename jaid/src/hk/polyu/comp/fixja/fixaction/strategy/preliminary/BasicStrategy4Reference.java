package hk.polyu.comp.fixja.fixaction.strategy.preliminary;

import hk.polyu.comp.fixja.ast.ASTUtils4SelectInvocation;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static hk.polyu.comp.fixja.ast.ASTUtils4SelectInvocation.isThrow;
import static hk.polyu.comp.fixja.util.CommonUtils.checkStmt;


/**
 * Created by Ls CHEN
 */
public class BasicStrategy4Reference extends AbsBasicStrategy {

    private boolean appendParamInvocation = false;

    public void setAppendParamInvocation(boolean appendParamInvocation) {
        this.appendParamInvocation = appendParamInvocation;
    }

    @Override
    boolean isDesiredType() {
        return getStateSnapshot().getSnapshotExpression().getOperands().get(0).isReferenceType();
    }

    void building(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand) {
        templateAssignR2L(leftOperand, rightOperand);
        templateAssignR2L(rightOperand, leftOperand);
    }

    void building(ExpressionToMonitor operand) {
        templateAssignNull(operand);
        templateAppendInvocation(operand);
    }

    private void templateAssignR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        if (!left.getText().equals("this")
                && left.getType().getQualifiedName().equals(right.getType().getQualifiedName())) {
            String strategyName = getStrategyName("VrefL=VrefR");
            checkIfLeftVariableAndConstructSnippet(strategyName, left, right.getExpressionAST());
        }
    }

    private void templateAssignNull(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o=null");
        if (operand.getText().equals("this")) return;
        NullLiteral nullLiteral = ast.newNullLiteral();
        constructAndCreate(operand, nullLiteral, strategyName);
    }

    /**
     * append method invocation to the reference type expression
     */
    private void templateAppendInvocation(ExpressionToMonitor operand) {
        if (!operand.isMethodInvoke() && !operand.isValidVariable()) return;
        if (operand.isMethodInvoke() && getType().getQualifiedName().equals("java.lang.String")) return;
        IMethodBinding[] methodBindings = getType().getDeclaredMethods();
        List<IMethodBinding> imbs = ASTUtils4SelectInvocation.selectChangeStateMethods(methodBindings, getStateSnapshot().getLocation());
        for (IMethodBinding imb : imbs) {
            MethodInvocation invocation;
            if (imb.getParameterTypes().length == 0) {
                invocation = CommonUtils.appendInvoking(operand.getExpressionAST(), imb.getName(), null);
                checkInvocationAndCreateSnippet(invocation, imb);
            } else if (appendParamInvocation) {
                //get parameters' name
                Set<List<ASTNode>> paramsList = ASTUtils4SelectInvocation.getCombinedParametersName(imb, getStateSnapshot().getLocation());
                for (List<ASTNode> params : paramsList) {
                    invocation = CommonUtils.appendInvoking(operand.getExpressionAST(), imb.getName(), params);
                    checkInvocationAndCreateSnippet(invocation, imb);
                }
            }
        }
    }


    /**
     * If the fix candidate is a SideEffectFree Method invocation, it is not a desired snippet
     */
    private boolean isDesiredInvocation(Expression action) {
        for (ExpressionToMonitor etm : getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getSideEffectFreeExpressionsToMonitorWithinMethod()) {
            if (etm.getText().equals(action.toString()))
                return false;
        }
        return true;
    }

    private void checkInvocationAndCreateSnippet(MethodInvocation invocation, IMethodBinding imb) {
        Statement action;
        if (isDesiredInvocation(invocation)) {
            if (isThrow(imb))
                action = CommonUtils.appendThrowableInvoking(invocation);
            else
                action = checkStmt(invocation);
            createSnippet(action, getStrategyName("o.invoke"));
        }
    }

}
