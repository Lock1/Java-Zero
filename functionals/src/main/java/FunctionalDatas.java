import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Namespace for functional programming common data structures. */
public enum FunctionalDatas { ;
    public record TupleOf2<T1,T2>(T1 t1, T2 t2) implements Map.Entry<T1,T2> {
        @Override public T1 getKey() { return this.t1; }
        @Override public T2 getValue() { return this.t2; }
        @Override public T2 setValue(T2 arg0) { throw new BuggyCodeException("TupleOf2 does not support Map.setValue()"); }
    }
    public record TupleOf3<T1,T2,T3>(T1 t1, T2 t2, T3 t3) {}
    public record TupleOf4<T1,T2,T3,T4>(T1 t1, T2 t2, T3 t3, T4 t4) {}
    public record TupleOf5<T1,T2,T3,T4,T5>(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {}

    @FunctionalInterface public interface FunctionOf1<T,R> extends Function<T,R> {}
    @FunctionalInterface public interface FunctionOf2<T1,T2,R> extends BiFunction<T1,T2,R> {
        @Override R apply(T1 t1, T2 t2);
        default R apply(TupleOf2<? extends T1,? extends T2> tuple) { return this.apply(tuple.t1, tuple.t2); }
    }
    @FunctionalInterface public interface FunctionOf3<T1,T2,T3,R> {
        R apply(T1 t1, T2 t2, T3 t3);
        default R apply(TupleOf3<? extends T1,? extends T2,? extends T3> tuple) { return this.apply(tuple.t1, tuple.t2, tuple.t3); }
    }
    @FunctionalInterface public interface FunctionOf4<T1,T2,T3,T4,R> {
        R apply(T1 t1, T2 t2, T3 t3, T4 t4);
        default R apply(TupleOf4<? extends T1,? extends T2,? extends T3,? extends T4> tuple) { return this.apply(tuple.t1, tuple.t2, tuple.t3, tuple.t4); }
    }
    @FunctionalInterface public interface FunctionOf5<T1,T2,T3,T4,T5,R> {
        R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
        default R apply(TupleOf5<? extends T1,? extends T2,? extends T3,? extends T4,? extends T5> tuple) { return this.apply(tuple.t1, tuple.t2, tuple.t3, tuple.t4, tuple.t5); }
    }
}
