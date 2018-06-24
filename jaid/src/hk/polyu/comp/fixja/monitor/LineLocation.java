package hk.polyu.comp.fixja.monitor;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;

import java.util.*;


/**
 * Created by Max PEI.
 */
public class LineLocation implements Comparable<LineLocation> {

    private final MethodToMonitor contextMethod;
    private final int lineNo;
    private List<ExpressionToMonitor> expressionsToMonitor;

    private static int maximumDistanceToFailure;

    public static int getMaximumDistanceToFailure() {
        return maximumDistanceToFailure;
    }

    public static void setMaximumDistanceToFailure(int value){
        maximumDistanceToFailure = value;
    }

    public LineLocation(MethodToMonitor contextMethod, int lineNo) {
        this.contextMethod = contextMethod;
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return lineNo;
    }

    public boolean isBefore(LineLocation location){
        return getLineNo() < location.getLineNo();
    }

    public boolean isBeforeOrEqual(LineLocation location){
        return isBefore(location) || equals(location);
    }

    public MethodDeclaration getMethodDeclaration() {
        return contextMethod.getMethodAST();
    }

    public MethodToMonitor getContextMethod() {
        return contextMethod;
    }

    public Statement getStatement(){
        return getContextMethod().getMethodDeclarationInfoCenter().getAllLocationStatementMap().getOrDefault(this, null);
    }

    public Set<ExpressionToMonitor> getExpressionsToMonitor() {
        return getContextMethod().getMethodDeclarationInfoCenter().getLocationExpressionMap().getOrDefault(this, new TreeSet<>(ExpressionToMonitor.getByLengthComparator()));
    }

    public Set<ExpressionToMonitor> getExpressionsAppearedAtLocation(){
        return getContextMethod().getMethodDeclarationInfoCenter().getExpressionsAppearAtLocationMap().getOrDefault(this, new HashSet<>());
    }

    public void registerExpressionsToMonitor(List<ExpressionToMonitor> expressionToRegister){
        getExpressionsToMonitor().addAll(expressionToRegister);
    }

    public void removeExpressionsToMonitor(Collection<ExpressionToMonitor> expressionsToRemove){
        getExpressionsToMonitor().removeAll(expressionsToRemove);
    }

    public void removeExpressionToMonitor(ExpressionToMonitor expressionToRemove){
        getExpressionsToMonitor().remove(expressionToRemove);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LineLocation that = (LineLocation) o;

        if (getMethodDeclaration() != that.getMethodDeclaration() || getLineNo() != that.getLineNo())
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getMethodDeclaration() != null ? getMethodDeclaration().hashCode() : 0;
        result = 31 * result + getLineNo();
        return result;
    }

    @Override
    public String toString() {
        return getContextMethod().getSignature() + "[" + lineNo + "]";
    }

    @Override
    public int compareTo(LineLocation o) {
        return this.getLineNo() - o.getLineNo();
    }
}
