package hk.polyu.comp.fixja.monitor.snapshot;

import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;

import java.util.*;

/**
 * Created by Max PEI.
 */
public abstract class StateSnapshotExpression extends ExpressionToMonitor{

    private static long nextID = 0;

    private static long getNextID(){
        return nextID++;
    }

    public StateSnapshotExpression(Expression expressionAST, ITypeBinding type) {
        super(expressionAST, type);
        ID = getNextID();
    }

    public abstract DebuggerEvaluationResult evaluate(ProgramState state);

    public List<ExpressionToMonitor> getOperands(){
        if(operands == null)
            operands = new LinkedList<>();

        return operands;
    }

//    public long getID() {
//        return ID;
//    }

    public boolean isValidAtLocation(LineLocation location, Map<LineLocation, SortedSet<ExpressionToMonitor>> locationExpressionMap){
        List<ExpressionToMonitor> operands = getOperands();
        SortedSet<ExpressionToMonitor> validOperands = locationExpressionMap.get(location);
        return operands.stream().allMatch(x -> validOperands.contains(x) || x.isLiteral());
    }

    private List<ExpressionToMonitor> operands;

    private long ID;
}
