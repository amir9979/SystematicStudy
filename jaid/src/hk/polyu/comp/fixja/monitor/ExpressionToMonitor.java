package hk.polyu.comp.fixja.monitor;

import hk.polyu.comp.fixja.ast.ExpressionCollector;
import hk.polyu.comp.fixja.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.fixja.fixer.Session;
import hk.polyu.comp.fixja.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Created by Max PEI.
 */
public class ExpressionToMonitor {

    private static MethodDeclarationInfoCenter infoCenter = Session.getSession().getConfig().getJavaProject().getMethodToMonitor().getMethodDeclarationInfoCenter();

    private static String calculateOrderText(String text, String typeName) {
        return String.format("%5d", text.length()) + "#" + text + "#" + typeName;
    }

    public ExpressionToMonitor(Expression expressionAST, ITypeBinding type) {
        if (expressionAST == null)
            throw new IllegalArgumentException();

        this.expressionAST = expressionAST;
        this.textCache = expressionAST.toString().replace("\n", "").replace("\r", "");

        ITypeBinding localType;
        if(type == null) {
            localType = infoCenter.getTypeByExpressionText(this.getText());
        }
        else{
            localType = type;
        }
        if(localType == null)
            throw new IllegalStateException();
        this.type = localType;
        infoCenter.registerExpressionToMonitor(this);

        this.hasMethodInvocation = MethodInvocationDetector.methodInvocationFound(this.expressionAST);
        this.isLiteral = isIntegerType() && isSimpleInteger(getText());

        this.orderTextCache = calculateOrderText(this.textCache, this.getType().getName());
    }

    private ITypeBinding getTypeByText(String text){
        return infoCenter.getTypeByExpressionText(text);
    }

    // ================================== Operation

    public static double similarityBetween(Set<ExpressionToMonitor> set1, Set<ExpressionToMonitor> set2) {
        Set<ExpressionToMonitor> union = new HashSet<>();
        union.addAll(set1);
        union.addAll(set2);
        int nbrCommonExpressions = set1.size() + set2.size() - union.size();
        double similarity = ((double) nbrCommonExpressions) / union.size();
        return similarity;
    }

    private static boolean isSimpleInteger(String s) {
        // Treat only literals like 124 and -/+324 as simple integer.
        return (s.startsWith("-") || s.startsWith("+")) && s.substring(1, s.length()).matches("\\d+")
                || s.matches("\\d+");
    }

    private static int getSimpleInteger(String s) {
        if (!isSimpleInteger(s))
            throw new IllegalStateException();

        int value;
        if (s.startsWith("-") || s.startsWith("+"))
            value = Integer.parseInt(s.substring(1, s.length()));
        else
            value = Integer.parseInt(s);
        return s.startsWith("-") ? -value : value;
    }

    public Expression getExpressionAST() {
        return expressionAST;
    }

    public String getText() {
        return textCache;
    }

    public ITypeBinding getType() {
        return type;
    }

    public boolean isBooleanType() {
        return getType() != null && getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.BOOLEAN;
    }

    public boolean isIntegerType() {
        return getType() != null && getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.INT;
    }

    public boolean isNumericType() {
        return getType() != null
                && (getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.INT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.DOUBLE
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.CHAR
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.LONG
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.FLOAT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.SHORT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.BYTE);
    }

    public boolean isReferenceType() {
        return getType() != null && !getType().isPrimitive();
    }

    public boolean isArrayType() {
        return getType() != null && getType().isArray();
    }

    public boolean isSideEffectFree() {
        return !hasMethodInvocation() || !hasChangedState();
    }

    public boolean hasMethodInvocation() {
        return hasMethodInvocation;
    }
    public boolean isInvokeMTF() {
        return isInvokeMTF;
    }

    public void setInvokeMTF(boolean invokeMTF) {
        isInvokeMTF = invokeMTF;
    }

    public boolean hasChangedState() {
        return hasChangedState;
    }

    public boolean isLiteral() {
        return isLiteral;
    }

    public String getOrderText() {
        return orderTextCache;
    }

    public Integer getLiteralIntegerValue() {
        if (!isLiteral())
            throw new IllegalStateException();

        return getSimpleInteger(getText());
    }

    public void setChangedState(boolean hasChangedState) {
        this.hasChangedState = hasChangedState;
    }

    public Set<ExpressionToMonitor> getSubExpressions() {
        if (subExpressions == null) {
            subExpressions = new HashSet<>();

            ExpressionCollector collector = new ExpressionCollector(false);
            collector.collect(getExpressionAST());
            Set<Expression> expressions = collector.getSubExpressionSet();
            expressions.stream().filter(x -> infoCenter.hasExpressionTextRegistered(x.toString()))
                    .forEach(x -> subExpressions.add(new ExpressionToMonitor(x, x.resolveTypeBinding())));
        }
        return subExpressions;
    }

    public boolean isSuperExpressionOf(ExpressionToMonitor expr) {
        return getSubExpressions().contains(expr);
    }

    public boolean isProperSuperExpressionOf(ExpressionToMonitor expr) {
        return !this.equals(expr) && getSubExpressions().contains(expr);
    }

    public boolean isSubExpressionOf(ExpressionToMonitor expr) {
        return expr.getSubExpressions().contains(this);
    }

    public boolean isProperSubExpressionOf(ExpressionToMonitor expr) {
        return !this.equals(expr) && expr.getSubExpressions().contains(this);
    }

    public Set<ExpressionToMonitor> getGuardExpressions() {
        //fixme: why not add other valid expressions as guards?
        if (guardExpressions == null) {
            guardExpressions = new HashSet<>();
            if (hasMethodInvocation()) {
                // Expressions with no method invocation are side-effect free, therefore they do NOT need guard expressions

                // Use all side-effect free sub-expressions as guard
                getSubExpressions().stream().filter(x -> !x.hasMethodInvocation() && !x.equals(this)).forEach(guardExpressions::add);

                // also include field accesses based on sub-expressions of reference types
                Set<ExpressionToMonitor> fieldAccessGuardExpressions = new HashSet<>();
                for (ExpressionToMonitor subExpr : guardExpressions) {
                    fieldAccessGuardExpressions.addAll(subExpr.getFieldsToMonitor().values());
                }
                guardExpressions.addAll(fieldAccessGuardExpressions);
            }
        }
        return guardExpressions;
    }

    public Map<IVariableBinding, ExpressionToMonitor> getFieldsToMonitor() {
        if (fieldsAccessExpressions == null) {
            fieldsAccessExpressions = new HashMap<>();

            if (isReferenceType()) {
                AST ast = getExpressionAST().getAST();
                Expression left;
                if (!CommonUtils.isParenthesizeNeeded(getExpressionAST())) {
                    left = (Expression) ASTNode.copySubtree(ast, getExpressionAST());
                } else {
                    ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                    parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, getExpressionAST()));
                    left = parenthesizedExpression;
                }
                Set<IVariableBinding> fields = collectFieldsFromType(getType());
                ExpressionToMonitor fieldAccessToMonitor;
                for (IVariableBinding binding : fields) {
                    // fixme: distinguish between static and member fields
                    SimpleName simpleName = ast.newSimpleName(binding.getName());
                    FieldAccess access = ast.newFieldAccess();
                    access.setExpression((Expression) ASTNode.copySubtree(ast, left));
                    access.setName(simpleName);
                    fieldAccessToMonitor = new ExpressionToMonitor(access, binding.getType());
                    fieldsAccessExpressions.put(binding, fieldAccessToMonitor);
                }
            }
        }
        return fieldsAccessExpressions;
    }

    public static Comparator<ExpressionToMonitor> getByLengthComparator() {
        if (byLengthComparator == null)
            byLengthComparator = new Comparator<ExpressionToMonitor>() {
                @Override
                public int compare(ExpressionToMonitor o1, ExpressionToMonitor o2) {
                    return o1.getOrderText().compareTo(o2.getOrderText());
                }
            };

        return byLengthComparator;
    }

    // ========================= Storage

    private final Expression expressionAST;
    private final String textCache;
    private final ITypeBinding type;
    private final String orderTextCache;

    private final boolean hasMethodInvocation;
    private final boolean isLiteral;

    private boolean hasChangedState;
    private boolean isInvokeMTF;

    private Set<ExpressionToMonitor> subExpressions;
    private Set<ExpressionToMonitor> guardExpressions;
    private Map<IVariableBinding, ExpressionToMonitor> fieldsAccessExpressions;

    private static Comparator<ExpressionToMonitor> byLengthComparator;

    // ========================== Override

    @Override
    public String toString() {
        return getText() + "[" + type.getName().toString() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExpressionToMonitor that = (ExpressionToMonitor) o;

        return textCache.equals(that.textCache);
    }

    @Override
    public int hashCode() {
        return textCache.hashCode();
    }

// ========================== Implementation

    private Set<IVariableBinding> collectFieldsFromType(ITypeBinding baseType) {
        Set<IVariableBinding> allFields = new HashSet<>();

        Queue<ITypeBinding> contextTypes = new LinkedList<>();
        contextTypes.add(baseType);
        while (!contextTypes.isEmpty()) {
            ITypeBinding typeBinding = contextTypes.poll();
            // fixme: add static/non-static/final fields only
            allFields.addAll(Arrays.asList(typeBinding.getDeclaredFields()));

            if (typeBinding.getSuperclass() != null && !typeBinding.getSuperclass().getName().equals(Object.class.getName())) {
                contextTypes.offer(typeBinding.getSuperclass());
            }
        }

        return allFields;
    }

    /**
     * Object to collect bindings to local variables from an "Expression" AST.
     */
    private static class LocalVariableCollector extends ASTVisitor {
        private Set<IVariableBinding> localVariables;

        public void collect(Expression expression) {
            localVariables = new HashSet<IVariableBinding>();
            expression.accept(this);
        }

        public Set<IVariableBinding> getLocalVariables() {
            return localVariables;
        }

        public boolean visit(SimpleName node) {
            if (node.resolveBinding() instanceof IVariableBinding) {
                IVariableBinding binding = (IVariableBinding) node.resolveBinding();
                if (!binding.isField() && !binding.isEnumConstant()) {
                    localVariables.add(binding);
                }
            }
            return false;
        }
    }

    private static class MethodInvocationDetector extends ASTVisitor {
        private static MethodInvocationDetector detector = new MethodInvocationDetector();
        private boolean hasMethodInvocation;

        public static boolean methodInvocationFound(Expression exp) {
            detector.hasMethodInvocation = false;
            exp.accept(detector);
            return detector.hasMethodInvocation;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            hasMethodInvocation = true;
            return super.visit(node);
        }

    }

    /**
     * 检查expression是不是合适的变量（与 方法调用、其他表达式、不可改变的变量 区别开来）
     */
    public boolean isValidVariable() {
        String opString = getText();
        Expression opExp = getExpressionAST();

        if (opString.length() > 0
                && !(opExp instanceof InfixExpression)
                && !(opExp instanceof PrefixExpression)
                && !(opExp instanceof MethodInvocation)
                && !(opExp instanceof NumberLiteral)
                && !(opExp instanceof QualifiedName && opExp.toString().contains("length"))
                ) {
            if (getExpressionAST() instanceof SimpleName) {
                SimpleName node = (SimpleName) getExpressionAST();
                if (node.resolveBinding() instanceof IVariableBinding) {
                    IVariableBinding binding = (IVariableBinding) node.resolveBinding();
                    if(binding==null)
                        return false;
                    else
                        return !Modifier.isFinal(binding.getVariableDeclaration().getModifiers());
                }
            } else if (getExpressionAST() instanceof FieldAccess) {
                FieldAccess node = (FieldAccess) getExpressionAST();
                IVariableBinding binding =  node.resolveFieldBinding();
                if(binding==null)
                    return false;
                else
                    return !Modifier.isFinal(binding.getVariableDeclaration().getModifiers());
            } else {
                return true;
            }
        }
        return false;
    }


    public boolean isMethodInvoke() {
        return getExpressionAST() instanceof MethodInvocation;
    }

    public boolean isQualified() {
        String opString = getText();
        if (opString.length() > 0 && opString.contains(".")
                && !(getExpressionAST() instanceof NumberLiteral)
                && (getExpressionAST() instanceof QualifiedName)) {
            return true;
        }
        return false;
    }
}
