package hk.polyu.comp.fixja.monitor.state;

import com.sun.jdi.*;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrimitiveType;

import static hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult.NULL_REFERENCE_ID;

/**
 * Created by Max PEI.
 */
public abstract class DebuggerEvaluationResult {

    public final static BooleanDebuggerEvaluationResult BOOLEAN_DEBUGGER_EVALUATION_RESULT_TRUE = new BooleanDebuggerEvaluationResult(true);
    public final static BooleanDebuggerEvaluationResult BOOLEAN_DEBUGGER_EVALUATION_RESULT_FALSE = new BooleanDebuggerEvaluationResult(false);
    public final static ReferenceDebuggerEvaluationResult REFERENCE_DEBUGGER_EVALUATION_RESULT_NULL = new ReferenceDebuggerEvaluationResult(NULL_REFERENCE_ID);
    public final static SemanticErrorDebuggerEvaluationResult DEBUGGER_EVALUATION_RESULT_SEMANTIC_ERROR = new SemanticErrorDebuggerEvaluationResult();
    public final static SyntaxErrorDebuggerEvaluationResult DEBUGGER_EVALUATION_RESULT_SYNTAX_ERROR = new SyntaxErrorDebuggerEvaluationResult();
    public final static InvokeMtfDebuggerEvaluationResult INVOKE_MTF_DEBUGGER_EVALUATION_RESULT = new InvokeMtfDebuggerEvaluationResult();

    public boolean hasSemanticError(){ return false; }
    public boolean hasSyntaxError(){ return false; }
    public boolean isInvokeMTF(){ return false; }
    public boolean isNull(){ return false; }

    public static DebuggerEvaluationResult fromValue(ITypeBinding binding, Value value) {
        if (binding.isPrimitive() && PrimitiveType.toCode(binding.getName()) == PrimitiveType.INT) {
            if (value != null && value instanceof IntegerValue) {
                return new IntegerDebuggerEvaluationResult(((IntegerValue) value).value());
            } else {
                return getDebuggerEvaluationResultSemanticError();
            }
        } else if (binding.isPrimitive() && PrimitiveType.toCode(binding.getName()) == PrimitiveType.DOUBLE) {
            if (value != null && value instanceof DoubleValue) {
                return new DoubleDebuggerEvaluationResult(((DoubleValue) value).value());
            } else {
                return getDebuggerEvaluationResultSemanticError();
            }
        } else if (binding.isPrimitive() && PrimitiveType.toCode(binding.getName()) == PrimitiveType.LONG) {
            if (value != null && value instanceof LongValue) {
                return new LongDebuggerEvaluationResult(((LongValue) value).value());
            } else {
                return getDebuggerEvaluationResultSemanticError();
            }
        } else if (binding.isPrimitive() && PrimitiveType.toCode(binding.getName()) == PrimitiveType.BOOLEAN) {
            if (value != null && value instanceof BooleanValue) {
                return getBooleanDebugValue(((BooleanValue) value).value());
            } else {
                return getDebuggerEvaluationResultSemanticError();
            }
        } else if (!binding.isPrimitive()) {
            if (value == null) {
                return getReferenceDebuggerEvaluationResultNull();
            } else if (value instanceof ObjectReference) {
                return new ReferenceDebuggerEvaluationResult(((ObjectReference) value).uniqueID());
            } else {
                return getDebuggerEvaluationResultSemanticError();
            }
        } else
            throw new IllegalStateException("Unexpected value type.");
    }

    public static SemanticErrorDebuggerEvaluationResult getDebuggerEvaluationResultSemanticError() {
        return DEBUGGER_EVALUATION_RESULT_SEMANTIC_ERROR;
    }

    public static SyntaxErrorDebuggerEvaluationResult getDebuggerEvaluationResultSyntaxError() {
        return DEBUGGER_EVALUATION_RESULT_SYNTAX_ERROR;
    }

    public static IntegerDebuggerEvaluationResult getIntegerDebugValue(int value) {
        return new IntegerDebuggerEvaluationResult(value);
    }

    public static InvokeMtfDebuggerEvaluationResult getInvokeMtfDebuggerEvaluationResult() {
        return INVOKE_MTF_DEBUGGER_EVALUATION_RESULT;
    }

    public static BooleanDebuggerEvaluationResult getBooleanDebugValue(boolean value) {
        if (value)
            return BOOLEAN_DEBUGGER_EVALUATION_RESULT_TRUE;
        else
            return BOOLEAN_DEBUGGER_EVALUATION_RESULT_FALSE;
    }

    public static ReferenceDebuggerEvaluationResult getReferenceDebuggerEvaluationResultNull() {
        return REFERENCE_DEBUGGER_EVALUATION_RESULT_NULL;
    }

    public static class DoubleDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final double value;

        private DoubleDebuggerEvaluationResult(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public boolean isGreater(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(DoubleDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "DoubleDebuggerEvaluationResult{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DoubleDebuggerEvaluationResult that = (DoubleDebuggerEvaluationResult) o;
//            return getValue() == that.getValue();
            return new Double(getValue()).equals(that.getValue());
        }

        @Override
        public int hashCode() {
            long temp = Double.doubleToLongBits(getValue());
            return (int) (temp ^ (temp >>> 32));
        }
    }

    public static class LongDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final long value;

        private LongDebuggerEvaluationResult(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        public boolean isGreater(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(LongDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "LongDebuggerEvaluationResult{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LongDebuggerEvaluationResult that = (LongDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            long temp = getValue();
            return (int) (temp ^ (temp >>> 32));
        }
    }

    public static class IntegerDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final int value;

        private IntegerDebuggerEvaluationResult(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isGreater(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() > other.getValue();
        }

        public boolean isGreaterEqual(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() >= other.getValue();
        }

        public boolean isLess(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() < other.getValue();
        }

        public boolean isLessEqual(IntegerDebuggerEvaluationResult other) {
            if (hasSyntaxError() || hasSemanticError() || other.hasSyntaxError() || other.hasSemanticError())
                throw new IllegalStateException();

            return getValue() <= other.getValue();
        }

        @Override
        public String toString() {
            return "IntegerDebuggerEvaluationResult{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IntegerDebuggerEvaluationResult that = (IntegerDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            return getValue();
        }
    }

    public static class BooleanDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final boolean value;

        private BooleanDebuggerEvaluationResult(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "BooleanDebuggerEvaluationResult{" +
                    "value=" + value +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BooleanDebuggerEvaluationResult that = (BooleanDebuggerEvaluationResult) o;

            return getValue() == that.getValue();
        }

        @Override
        public int hashCode() {
            return (getValue() ? 1 : 0);
        }
    }

    public static class ReferenceDebuggerEvaluationResult extends DebuggerEvaluationResult {
        private final long objectID;

        private ReferenceDebuggerEvaluationResult(long objectID) {
            this.objectID = objectID;
        }

        public long getObjectID() {
            return objectID;
        }

        @Override
        public boolean isNull() {
            return getObjectID() == 0;
        }

        @Override
        public String toString() {
            return "ReferenceDebuggerEvaluationResult{" +
                    "objectID=" + objectID +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ReferenceDebuggerEvaluationResult that = (ReferenceDebuggerEvaluationResult) o;

            return getObjectID() == that.getObjectID();
        }

        @Override
        public int hashCode() {
            return (int) (getObjectID() ^ (getObjectID() >>> 32));
        }

        public static final int NULL_REFERENCE_ID = 0;
    }

    public static class SemanticErrorDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private SemanticErrorDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "SemanticErrorDebuggerEvaluationResult{}";
        }

        @Override
        public boolean hasSemanticError() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    public static class SyntaxErrorDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private SyntaxErrorDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "SyntaxErrorDebuggerEvaluationResult{}";
        }

        @Override
        public boolean hasSyntaxError() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    public static class InvokeMtfDebuggerEvaluationResult extends DebuggerEvaluationResult {

        private InvokeMtfDebuggerEvaluationResult() {
        }

        @Override
        public String toString() {
            return "InvokeMtfDebuggerEvaluationResult{}";
        }

        @Override
        public boolean isInvokeMTF() {
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

}
