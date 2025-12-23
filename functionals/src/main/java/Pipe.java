import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;



public final class Pipe<T> implements Iterable<T>, Transmutable<Pipe<T>> {
    private Iterator<T> source;

    private Pipe(Iterator<T> source) {
        this.source = source;
    }



    // ---------------------------------------- Instance Methods ----------------------------------------
    public final <R> Pipe<R> map(Function<? super T,? extends R> mapper) {
        final PipeMapper.MapperOf0Or1<T,R> pipeMapper = (value, downstream) -> downstream.accept(mapper.apply(value));
        return new Pipe<>(pipeMapper.performMap(this.source));
    }

    public final <R> Pipe<R> flatMap(Function<? super T,Pipe<? extends R>> mapper) {
        final PipeMapper<T,R> pipeMapper = source -> new Iterator<R>() {
            private Iterator<? extends R> buffer;

            @Override public boolean hasNext() {
                if (this.buffer != null && this.buffer.hasNext())
                    return true;
                if (source.hasNext())
                    this.buffer = mapper.apply(source.next()).source;
                return this.buffer != null && this.buffer.hasNext();
            }

            @Override public R next() {
                if ((this.buffer != null && this.buffer.hasNext()) || this.hasNext())
                    return this.buffer.next();
                throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
            }
        };
        return new Pipe<>(pipeMapper.performMap(this.source));
    }

    public final <$Right> Pipe<FunctionalDatas.TupleOf2<T,$Right>> zip(Pipe<$Right> otherPipe) {
        final PipeMapper<T,FunctionalDatas.TupleOf2<T,$Right>> pipeMapper = source -> new Iterator<FunctionalDatas.TupleOf2<T,$Right>>() {
            @Override public boolean hasNext() {
                return source.hasNext() && otherPipe.source.hasNext();
            }

            @Override public FunctionalDatas.TupleOf2<T,$Right> next() {
                return new FunctionalDatas.TupleOf2<>(source.next(), otherPipe.source.next());
            }
        };
        return new Pipe<>(pipeMapper.performMap(this.source));
    }

    public final Pipe<T> peek(Consumer<? super T> sideEffectPeeker) {
        final PipeMapper.MapperOf0Or1<T,T> mapper = (value, downstream) -> {
            sideEffectPeeker.accept(value);
            downstream.accept(value);
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    // Null reject
    public final Pipe<T> keepIf(Predicate<? super T> predicate) {
        final PipeMapper.MapperOf0Or1<T,T> mapper = (previousValue, downstream) -> {
            if (previousValue instanceof final T value && predicate.test(value))
                downstream.accept(value);
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    // Null reject
    public final Pipe<T> keepWhile(Predicate<? super T> predicate) {
        final PipeMapper<T,T> mapper = source -> new Iterator<T>() {
            private boolean accepting = true;
            private T buffer;
            private boolean isValidBuffer = false;

            @Override public boolean hasNext() {
                if (this.isValidBuffer)
                    return true;
                if (!this.accepting)
                    return false;
                while (!this.isValidBuffer && source.hasNext()) {
                    this.buffer        = source.next();
                    this.accepting     = this.buffer instanceof final T value && predicate.test(value);
                    this.isValidBuffer = this.accepting;
                }
                return this.isValidBuffer;
            }

            @Override public T next() {
                if (this.isValidBuffer || this.hasNext()) {
                    this.isValidBuffer = false;
                    return this.buffer;
                }
                throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
            }
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    // Null allow
    public final Pipe<T> removeIf(Predicate<? super T> predicate) {
        final PipeMapper.MapperOf0Or1<T,T> mapper = (previousValue, downstream) -> {
            if (previousValue instanceof final T value && !predicate.test(value))
                return;
            downstream.accept(previousValue);
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    // Null allow
    public final Pipe<T> removeWhile(Predicate<? super T> predicate) {
        final PipeMapper<T,T> mapper = source -> new Iterator<T>() {
            private boolean rejecting = true;
            private T buffer;
            private boolean isValidBuffer = false;

            @Override public boolean hasNext() {
                if (this.isValidBuffer)
                    return true;
                while (!this.isValidBuffer && source.hasNext()) {
                    this.buffer        = source.next();
                    this.rejecting     = this.rejecting && this.buffer instanceof final T value && !predicate.test(value);
                    this.isValidBuffer = !this.rejecting;
                }
                return this.isValidBuffer;
            }

            @Override public T next() {
                if (this.isValidBuffer || this.hasNext()) {
                    this.isValidBuffer = false;
                    return this.buffer;
                }
                throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
            }
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    public final Pipe<T> limit(long count) {
        final PipeMapper<T,T> mapper = source -> new Iterator<T>() {
            private long currentCount = 0;

            @Override public boolean hasNext() {
                return this.currentCount < count && source.hasNext();
            }

            @Override public T next() {
                ++this.currentCount;
                return source.next();
            }
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    public final Pipe<T> skip(long count) {
        final PipeMapper<T,T> mapper = source -> new Iterator<T>() {
            private long currentCount = 0;

            @Override public boolean hasNext() {
                while (this.currentCount < count && source.hasNext()) {
                    ++this.currentCount;
                    source.next();
                }
                return source.hasNext();
            }

            @Override public T next() {
                if (this.hasNext())
                    return source.next();
                throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
            }
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    public final <$Key> Pipe<T> distinct(Function<? super T,? extends $Key> keyExtractor) {
        final PipeMapper<T,T> mapper = source -> new Iterator<T>() {
            private final HashSet<$Key> observedKeys = new HashSet<>();
            private T buffer;
            private boolean isValidBuffer = false;

            @Override public boolean hasNext() {
                if (this.isValidBuffer)
                    return true;
                while (!this.isValidBuffer && source.hasNext()) {
                    this.buffer    = source.next();
                    final $Key key = keyExtractor.apply(this.buffer);
                    if (!this.observedKeys.contains(key)) {
                        this.observedKeys.add(key);
                        this.isValidBuffer = true;
                    }
                }
                return this.isValidBuffer;
            }

            @Override public T next() {
                if (this.isValidBuffer || this.hasNext())
                    return this.buffer;
                throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
            }
        };
        this.source = mapper.performMap(this.source);
        return this;
    }

    public final Pipe<T> sorted(Comparator<T> comparator) {
        final PipeMapper<T,T> mapper = source -> {
            final var intermediateQueue = new PriorityQueue<T>(comparator);
            while (source.hasNext())
                intermediateQueue.add(source.next());
            return intermediateQueue.iterator();
        };
        this.source = mapper.performMap(this.source);
        return this;
    }



    // ---------------------------------------- Terminal Methods ----------------------------------------
    /** Consume this {@link Pipe}. */
    @Override
    public final Iterator<T> iterator() {
        return this.source;
    }

    public final boolean endByMatchAll(Predicate<? super T> predicate) {
        for (final T value: this)
            if (!predicate.test(value))
                return false;
        return true;
    }

    public final boolean endByMatchNone(Predicate<? super T> predicate) {
        for (final T value: this)
            if (!predicate.test(value))
                return false;
        return true;
    }

    public final boolean endByMatchAny(Predicate<? super T> predicate) {
        for (final T value: this)
            if (predicate.test(value))
                return true;
        return false;
    }

    public final Nilable<T> endByTakeFirst() {
        for (final T value: this)
            return Nilable.of(value);
        return Nilable.empty();
    }

    public final List<T> endByToList() {
        final List<T> accumulator = new ArrayList<>();
        for (final T value: this)
            accumulator.add(value);
        return accumulator;
    }

    public final <K,V> Map<K,V> endByToMap(Function<? super T,? extends K> keyExtractor, Function<? super T,? extends V> valueExtractor) {
        final var accumulator = new HashMap<K,V>();
        for (final T value: this)
            accumulator.put(keyExtractor.apply(value), valueExtractor.apply(value));
        return accumulator;
    }

    public final <R> R end(Terminal<T,? extends R> reducer) {
        return reducer.performMutableReduction((Iterable<T>) () -> this.iterator());
    }

    public final <R> R end(Supplier<? extends Terminal<T,? extends R>> reducer) {
        return this.end(reducer.get());
    }






    // ---------------------------------------- Static Functions ----------------------------------------
    @SafeVarargs // Read-only on values
    public static <T> Pipe<T> of(T... values) {
        return new Pipe<>(new Iterator<T>() {
            private int idx = 0;

            @Override public boolean hasNext() {
                return idx < values.length;
            }

            @Override public T next() {
                return values[idx++];
            }
        });
    }

    public static <T> Pipe<T> from(Iterable<T> source) {
        return new Pipe<>(source.iterator());
    }

    public static <T> Pipe<T> from(Iterator<T> source) {
        return new Pipe<>(source);
    }



    // ---------------------------------------- Fold/Reduce Operator ----------------------------------------
    @FunctionalInterface
    public interface Terminal<T,R> {
        R performMutableReduction(Iterable<T> oneTimeProducer);

        default <$NextR> Terminal<T,$NextR> andThen(Function<? super R,? extends $NextR> mapper) {
            return source -> mapper.apply(this.performMutableReduction(source));
        }

        public enum Utils { ;
            public static <T> Terminal<T,Void> forEach(Consumer<? super T> consumer) {
                return producer -> {
                    for (final T value: producer)
                        consumer.accept(value);
                    return null;
                };
            }

            public static <T> Terminal<T,Nilable<T>> fold(BinaryOperator<T> reducer) {
                return producer -> {
                    final Iterator<T> iterator = producer.iterator();
                    if (!iterator.hasNext())
                        return Nilable.empty();
                    T accumulator = iterator.next();
                    while (iterator.hasNext())
                        accumulator = reducer.apply(accumulator, iterator.next());
                    return Nilable.of(accumulator);
                };
            }

            public static <T> Terminal<T,T> fold(T initial, BinaryOperator<T> reducer) {
                return producer -> {
                    T accumulator = initial;
                    for (final T value: producer)
                        accumulator = reducer.apply(accumulator, value);
                    return accumulator;
                };
            }

            public static <T,$Accumulator,R> Terminal<T,R> fold(Collector<? super T,$Accumulator,? extends R> reducer) {
                return producer -> {
                    final $Accumulator accumulator = reducer.supplier().get();
                    for (final T value: producer)
                        reducer.accumulator().accept(accumulator, value);
                    return reducer.finisher().apply(accumulator);
                };
            }

            public static <T,K,V> Terminal<T,Map<K,List<V>>> groupingBy(Function<? super T,? extends K> keyExtractor, Function<? super T,? extends V> valueExtractor) {
                return producer -> {
                    final var accumulator = new HashMap<K,List<V>>();
                    for (final T value: producer) {
                        final K key            = keyExtractor.apply(value);
                        final V extractedValue = valueExtractor.apply(value);
                        if (accumulator.containsKey(key))
                            accumulator.get(key).add(extractedValue);
                        else {
                            final var groupAccumulator = new ArrayList<V>();
                            groupAccumulator.add(extractedValue);
                            accumulator.put(key, groupAccumulator);
                        }
                    }
                    return accumulator;
                };
            }
        }
    }



    // ---------------------------------------- Internal ----------------------------------------
    @FunctionalInterface
    private interface PipeMapper<T,R> {
        Iterator<R> performMap(Iterator<T> source);

        /** Contract: {@link Consumer} downstream must be called <b>at most once</b>. */
        @FunctionalInterface
        interface MapperOf0Or1<T,R> extends PipeMapper<T,R> {
            void connect(T upstreamValue, Consumer<? super R> downstream);

            /** WARNING: This variant will forcefully consume upstream. */
            @Override
            default Iterator<R> performMap(Iterator<T> source) {
                // Because of [0, 1], source.hasNext() does not necessarily implies this.hasNext()
                return new Iterator<R>() {
                    private R buffer;
                    private boolean isValidBuffer = false;

                    private void performStoreBuffer(R value) {
                        this.buffer        = value;
                        this.isValidBuffer = true;
                    }

                    @Override public boolean hasNext() {
                        while (!this.isValidBuffer && source.hasNext())
                            MapperOf0Or1.this.connect(source.next(), this::performStoreBuffer);
                        return this.isValidBuffer;
                    }

                    @Override public R next() {
                        if (this.hasNext()) {
                            this.isValidBuffer = false;
                            return this.buffer;
                        }
                        throw new BuggyCodeException("Trying to invoke Iterator.next() when there's no element");
                    }
                };
            }
        }
    }
}

