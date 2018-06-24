package hk.polyu.comp.fixja.fixaction.strategy.preliminary;

import hk.polyu.comp.fixja.fixaction.Schemas;
import hk.polyu.comp.fixja.fixaction.Snippet;
import hk.polyu.comp.fixja.fixaction.strategy.Strategy;
import hk.polyu.comp.fixja.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.fixja.monitor.MethodToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.util.HashSet;
import java.util.Set;

import static hk.polyu.comp.fixja.ast.ASTUtils4SelectInvocation.getValidVars;

/**
 * Created by Ls CHEN.
 */
public class Strategy4ControlFlow extends Strategy {
    private Set<Snippet> snippetSet;
    private static final String VOID = "void";

    @Override
    public Set<Snippet> process() {
        this.snippetSet = new HashSet<>();
        ast = getStateSnapshot().getLocation().getStatement().getAST();
        templateBuildReturn();
        templateBuildContinue();
        return snippetSet;
    }

    private void templateBuildContinue() {
        ASTNode oldStmtParent = getStateSnapshot().getLocation().getStatement().getParent().getParent();
        if (oldStmtParent instanceof ForStatement ||
                oldStmtParent instanceof WhileStatement) {
            snippetSet.add(new Snippet(ast.newContinueStatement(), StrategyUtils.fitSchemaB, getStrategyName("continue;"), getStateSnapshot().getID()));
        }
    }

    private void templateBuildReturn() {
        MethodToMonitor methodToMonitor = getStateSnapshot().getLocation().getContextMethod();
        MethodDeclaration method = methodToMonitor.getMethodAST();
        if (method != null) {
            ITypeBinding type = method.getReturnType2().resolveBinding();
            if (type.getQualifiedName().equals(VOID)) {
                ReturnStatement returnStatement = ast.newReturnStatement();
                snippetSet.add(new Snippet(returnStatement, StrategyUtils.fitSchemaB, getStrategyName("return;"), getStateSnapshot().getID()));
            } else {
                Set<ASTNode> validVars = getValidVars(type, getStateSnapshot().getLocation());
                for (ASTNode var : validVars) {
                    Assignment assignment = ast.newAssignment();
                    assignment.setLeftHandSide((Expression) ASTNode.copySubtree(ast, methodToMonitor.getMethodDeclarationInfoCenter().getResultExpressionToMonitor().getExpressionAST()));
                    assignment.setRightHandSide((Expression) ASTNode.copySubtree(ast, var));

                    ReturnStatement returnStatement = ast.newReturnStatement();
                    returnStatement.setExpression(assignment);
                    snippetSet.add(new Snippet(returnStatement, StrategyUtils.fitSchemaB, getStrategyName("return valid_exp;"), getStateSnapshot().getID()));
                }
                // TODO:This part can add some extra condition since it is only used in LANG51 currently.
                //===============start========
                if (type.getName().equals("boolean")) {
                    buildBooleanLiteralReturn(methodToMonitor);
                }
                //===============end========
            }
        }
    }

    private void buildBooleanLiteralReturn(MethodToMonitor methodToMonitor) {
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) ASTNode.copySubtree(ast, methodToMonitor.getMethodDeclarationInfoCenter().getResultExpressionToMonitor().getExpressionAST()));
        assignment.setRightHandSide(ast.newBooleanLiteral(true));

        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(assignment);

        snippetSet.add(new Snippet(returnStatement, getBooleanReturnSchema(), getStrategyName("return T;"), getStateSnapshot().getID()));
        ReturnStatement returnStatement1 = ast.newReturnStatement();
        returnStatement1.setExpression(ast.newBooleanLiteral(false));
        snippetSet.add(new Snippet(returnStatement1, getBooleanReturnSchema(), getStrategyName("return F;"), getStateSnapshot().getID()));
    }

    private Set<Schemas.Schema> getBooleanReturnSchema() {
        if (getStateSnapshot().getLocation().getStatement() instanceof ReturnStatement) {
            return StrategyUtils.fitSchemaDE;
        } else {
            return StrategyUtils.fitSchemaB;
        }
    }

//    /**
//     * 计算 snippet & oldStmt 的相似度
//     */
//    private double calSnippetAndOldStmtSimi(String snippet) {
//        ASTParser ifCondParser = ASTParser.newParser(AST.JLS8);
//        ifCondParser.setSource(snippet.toCharArray());
//        ifCondParser.setKind(ASTParser.K_STATEMENTS);
//        Block snippetAst = (Block) ifCondParser.createAST(null);
//
//        SubExpCollector subExpCollector = new SubExpCollector(snippetAst);
//        Set<ASTNode> snippetSubSet = subExpCollector.find();
//        final int[] counter = {0};
//        getStateSnapshot().getLocation().getOldStmtSubExp().stream().forEach(ifSub -> {
//            snippetSubSet.stream().forEach(ssSub -> {
//                if (ifSub.toString().equals(ssSub.toString()))
//                    counter[0]++;
//            });
//        });
//        return (double) counter[0] / snippetSubSet.size();
//    }


}
