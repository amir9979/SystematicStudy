package hk.polyu.comp.fixja.util;

import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by liushanchen on 16/8/8.
 */
public class CommonUtils {
    private static final String STMT_ENDING = ";";
    static final String EXP = "${exp}";
    static String tryCatchBlock = "try{" + EXP + "}catch (Exception e ){e.printStackTrace();}\n";


    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(Hashtable<K, V> map) {
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();

        st.sorted(Map.Entry.comparingByValue())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public static <K extends Comparable<? super K>, V> LinkedHashMap<K, V> sortByKey(Map<K, V> map) {
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();

        st.sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public static String checkStmt(String stmt) {
        stmt.replaceAll(System.lineSeparator(), "");
        if (stmt.endsWith(STMT_ENDING) || stmt.endsWith(STMT_ENDING + "\n")) {
            return stmt;
        } else if (stmt.startsWith("{") && (stmt.endsWith("}") || stmt.endsWith("}\n"))) {
            return stmt;
        } else {
            return stmt + STMT_ENDING;
        }
    }

    public static Statement checkStmt(ASTNode stmt) {
        if (stmt instanceof Expression)
            return stmt.getRoot().getAST().newExpressionStatement((Expression) stmt);
        else if (stmt instanceof Statement)
            return (Statement) stmt;
        else {
            LoggingService.warn("stmt:" + stmt.toString() + " neither expression nor statement.");
            return null;
        }
    }

    public static String appendInvokingByString(String expVar, String invokingName) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(expVar)
                .append(")")
                .append(".")
                .append(invokingName)
                .append("()");
        return sb.toString();
        //========  replace string operation by AST operation   =======//
//        MethodInvocation nestedMI = ast.newMethodInvocation();
//        nestedMI.setExpression(ast.newSimpleName("tmpStringBuffer"));
//        nestedMI.setName(ast.newSimpleName("append"));
//        sl = ast.newStringLiteral();
//        sl.setLiteralValue("Content: " );
//        nestedMI.arguments().add(s1);
//
//        MethodInvocation mi = ast.newMethodInvocation();
//        mi.setExpression(nestedMI);
//        mi.setName(ast.newSimpleName("append"));
//        mi.arguments().add(ast.newSimpleName("gateId"));
//
//        bufferBlock.statements().add(ast.newExpressionStatement(mi));
    }

    public static String appendInvokingWithParmsByString(String expVar, String invokingName, String[] prams) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(expVar)
                .append(")")
                .append(".")
                .append(invokingName)
                .append("(");
        for (String p : prams) {
            sb.append(p).append(",");
        }
        sb.replace(sb.lastIndexOf(","), sb.lastIndexOf(",") + 1, " ");
        sb.append(")");
        return sb.toString();
    }

    public static MethodInvocation appendInvoking(Expression exp, String invokingName, List<ASTNode> prams) {
        AST ast = exp.getRoot().getAST();
        MethodInvocation mi = ast.newMethodInvocation();
        if (exp instanceof ThisExpression) {

        } else if (!isParenthesizeNeeded(exp)) {
            Expression expression = (Expression) ASTNode.copySubtree(ast, exp);
            mi.setExpression(expression);
        } else {
            Expression expression = (Expression) ASTNode.copySubtree(ast, exp);
            ParenthesizedExpression expr = ast.newParenthesizedExpression();
            expr.setExpression(expression);
            mi.setExpression(expr);
        }
        mi.setName(ast.newSimpleName(invokingName));
        if (prams != null)
            for (ASTNode pa : prams)
                mi.arguments().add(ASTNode.copySubtree(ast, pa));
        return mi;
    }

    public static boolean isParenthesizeNeeded(Expression exp) {
        if (exp instanceof SimpleName || exp instanceof QualifiedName
                || exp instanceof NumberLiteral || exp instanceof NullLiteral
                || exp instanceof CharacterLiteral || exp instanceof StringLiteral
                || exp instanceof MethodInvocation || exp instanceof ArrayAccess
                || exp instanceof ThisExpression || exp instanceof SuperFieldAccess
                || exp instanceof FieldAccess)
            return false;
        else
            return true;
    }

    public static Expression checkParenthesizeNeeded(Expression exp) {
        AST ast = exp.getRoot().getAST();
        if (isParenthesizeNeeded(exp)) {
            ParenthesizedExpression pe = ast.newParenthesizedExpression();
            pe.setExpression((Expression) ASTNode.copySubtree(ast, exp));
            return pe;
        } else
            return (Expression) ASTNode.copySubtree(ast, exp);
    }

    public static String appendThrowableInvokingByString(Expression exp, String invokingName, List<ASTNode> prams) {
        return tryCatchBlock.replace(EXP, checkStmt(appendInvoking(exp, invokingName, prams).toString()));
    }

    public static TryStatement appendThrowableInvoking(ASTNode exp) {
        AST ast = exp.getRoot().getAST();
        TryStatement tryStatement = ast.newTryStatement();
        Block b = ast.newBlock();
        b.statements().add(checkStmt(exp));
        tryStatement.setBody(b);
        CatchClause catchClause = ast.newCatchClause();
        SingleVariableDeclaration newSingleVariableDeclaration = ast.newSingleVariableDeclaration();
        newSingleVariableDeclaration.setType(ast.newSimpleType(ast.newName("Throwable")));
        newSingleVariableDeclaration.setName(ast.newSimpleName("e"));
        catchClause.setException(newSingleVariableDeclaration);
        tryStatement.catchClauses().add(catchClause);
        return tryStatement;
    }

    public static String replaceLast(String original, String tobeReplace, String replacement) {
        int idx = original.lastIndexOf(tobeReplace);
        if (idx < 0) return original;
        String head = original.substring(0, idx);
        String tail = original.substring(idx).replace(tobeReplace, replacement);
        return head + tail;
    }

    public static boolean isSubExp(ExpressionToMonitor bigger, ASTNode smaller) {
        if (equalExpString(bigger.getExpressionAST(), smaller)) return true;
        for (ASTNode astNode : bigger.getSubExpressions().stream().map(x -> x.getExpressionAST()).collect(Collectors.toList())) {
            if (equalExpString(astNode, smaller)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllConstant(Set<ASTNode> astNodeSet) {
        if (astNodeSet.size() == 0) return false;
        for (ASTNode astNode : astNodeSet) {
            if (!(astNode instanceof NullLiteral ||
                    astNode instanceof NumberLiteral ||
                    astNode instanceof BooleanLiteral ||
                    astNode instanceof CharacterLiteral ||
                    astNode instanceof StringLiteral))
                return false;
        }
        return true;
    }

    public static boolean equalExpString(ASTNode node1, ASTNode node2) {
        String node1s = node1.toString();
        node1s = node1s.replace("(", "").replace(")", "").trim();
        String node2s = node2.toString();
        node2s = node2s.replace("(", "").replace(")", "").trim();
        return node1s.equals(node2s);
    }

    public static String quotExp(String expVar) {
        StringBuilder sb = new StringBuilder("(");
        sb.append(expVar);
        sb.append(")");
        return sb.toString();
    }
}
