import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// TODO: WIP
// TODO: Cleanup & test
public final class DraftPipe<T> implements Iterable<T>, Transmutable<DraftPipe<T>> {
    private final Iterable<Object> source;
    private IntermediatePipe<Object,T> intermediate;

    @SuppressWarnings("unchecked")
    private DraftPipe(Iterable<T> source) {
        this.source = (Iterable<Object>) source;
        this.intermediate = (IntermediatePipe.ValueProducerOf0Or1<Object,T>) (value, downstream) -> downstream.accept((T) value); // Basically no-op
    }



    public final DraftPipe<T> peek(Consumer<? super T> sideEffectPeeker) {
        this.intermediate = this.intermediate.connect((IntermediatePipe.ValueProducerOf0Or1<T,T>) (previousValue, downstream) -> {
            sideEffectPeeker.accept(previousValue);
            downstream.accept(previousValue);
        });
        return this;
    }

    // Null reject
    public final DraftPipe<T> keepIf(Predicate<? super T> predicate) {
        this.intermediate = this.intermediate.connect((IntermediatePipe.ValueProducerOf0Or1<T,T>) (previousValue, downstream) -> {
            if (previousValue instanceof final T value && predicate.test(value))
                downstream.accept(value);
        });
        return this;
    }

    // Null reject
    public final DraftPipe<T> keepWhile(Predicate<? super T> predicate) {
        this.intermediate = this.intermediate.connect(new IntermediatePipe.ValueProducerOf0Or1<T,T>() {
            private boolean accepting = true;

            @Override public final void operate(T previousValue, Consumer<? super T> downstream) {
                if (this.accepting && previousValue instanceof final T value && predicate.test(value))
                    downstream.accept(value);
                else this.accepting = false;
            }
        });
        return this;
    }

    // Null allow
    public final DraftPipe<T> removeIf(Predicate<? super T> predicate) {
        this.intermediate = this.intermediate.connect((IntermediatePipe.ValueProducerOf0Or1<T,T>) (previousValue, downstream) -> {
            if (previousValue instanceof final T value && !predicate.test(value))
                return;
            downstream.accept(previousValue);
        });
        return this;
    }

    // Null allow
    public final DraftPipe<T> removeWhile(Predicate<? super T> predicate) {
        this.intermediate = this.intermediate.connect(new IntermediatePipe.ValueProducerOf0Or1<T,T>() {
            private boolean rejecting = true;

            @Override public final void operate(T previousValue, Consumer<? super T> downstream) {
                if (this.rejecting && (previousValue == null || (previousValue instanceof final T value && !predicate.test(value))))
                    this.rejecting = false;
                if (!this.rejecting)
                    downstream.accept(previousValue);
            }
        });
        return this;
    }

    public final DraftPipe<T> limit(long count) {
        this.intermediate = this.intermediate.connect(new IntermediatePipe.ValueProducerOf0Or1<T,T>() {
            private long currentCount = 0;

            @Override public final void operate(T previousValue, Consumer<? super T> downstream) {
                if (this.currentCount < count) {
                    ++this.currentCount;
                    downstream.accept(previousValue);
                }
            }
        });
        return this;
    }

    public final DraftPipe<T> skip(long count) {
        this.intermediate = this.intermediate.connect(new IntermediatePipe.ValueProducerOf0Or1<T,T>() {
            private long currentCount = 0;

            @Override public final void operate(T previousValue, Consumer<? super T> downstream) {
                if (this.currentCount < count)
                    ++this.currentCount;
                else
                    downstream.accept(previousValue);
            }
        });
        return this;
    }

// Pipe.source<Map<K,V>>(map)
    //     .map(Function<T,R>)
            //     .mapNull(Function<T,R>) -> Pipe<Nilable<R>>
    //     .mapCatch(Class<E>,FailableFunction<T,R,E>) -> Pipeline<Faulty<R,E>>
    //     .flatMap(Function<T,Pipeline<R>>) .mapMulti -> replaced by .flatMap(Nilable::toPipeline)
//     .peek(Consumer<T>)
    //     .zip(Pipeline<R>2) -> Pipeline<TupleOf2<T,R>> short-circuit on shortest
//     .keepIf(Predicate<T>)
//     .keepWhile(Predicate<T>)
//     .removeIf(Predicate<T>)
//     .removeWhile(Predicate<T>)
//     .limit(long)
//     .skip(long)
    //     .distinct(Function<T,KeyExtractor>)
    //     .sorted(Comparator<T>)
            //     .append()
    //     .sink(e -> e.takeFirst())

    public final <R> R end(Terminal<T,? extends R> reducer) {
        return reducer.performMutableReduction((Iterable<T>) () -> this.iterator());
    }

    public final <R> R end(Supplier<? extends Terminal<T,? extends R>> reducer) {
        return this.end(reducer.get());
    }

    // TODO: Basically consuming this as well. in fact, the only alternative terminal operation 
    @Override
    public final Iterator<T> iterator() {
        // The actual mutable, null-unsafe engine
        return new Iterator<T>() {
            // Representing which value that we can pick. Not sealed type, to avoid wrapper
            enum ValidBufferState {
                NONE,
                VALUE_PRODUCER_0_OR_1,
                MULTI_VALUE_PRODUCER;
            }
            final Iterator<Object> sourceIterator               = DraftPipe.this.source.iterator();
            final IntermediatePipe<Object,T> sourceIntermediate = DraftPipe.this.intermediate;
            ValidBufferState state = ValidBufferState.NONE;
            T resultBuffer;
            IntermediatePipe.MultiValueProducer<Object,T> multiResultBuffer;

            { /** Instance initializer: Advance enough value from origin Iterable<Object> and get the 1st element (or consume the entire Iterable<Object> source if we can't find any) */
                while (this.state == ValidBufferState.NONE && sourceIterator.hasNext())
                    this.performSinkConsume(sourceIterator.next(), this.sourceIntermediate);
            }

            /** Primarily used for converting mutable fn(T, FnMut<R>) -> () into fn(T) -> R */
            private final void performSinkConsume(Object sourceYield, IntermediatePipe<Object,T> pipe) {
                switch (pipe) {
                    case IntermediatePipe.ValueProducerOf0Or1<Object,T> valueProducer     -> {
                        valueProducer.operate(sourceYield, produced -> {
                            this.resultBuffer = produced;
                            this.state        = ValidBufferState.VALUE_PRODUCER_0_OR_1;
                        });
                    }
                    case IntermediatePipe.MultiValueProducer<Object,T> multiValueProducer -> {
                        multiValueProducer.feed(sourceYield);
                        this.multiResultBuffer = multiValueProducer;
                        this.state             = this.multiResultBuffer.hasNext() ? ValidBufferState.MULTI_VALUE_PRODUCER : ValidBufferState.NONE;
                    }
                }
            }

            @Override public final boolean hasNext() {
                return switch (this.state) {
                    case ValidBufferState.VALUE_PRODUCER_0_OR_1,
                         ValidBufferState.MULTI_VALUE_PRODUCER  -> true;
                    case ValidBufferState.NONE                  -> {
                        if (!this.sourceIterator.hasNext())
                            yield false;
                        do this.performSinkConsume(this.sourceIterator.next(), this.sourceIntermediate);
                        while (this.state == ValidBufferState.NONE && this.sourceIterator.hasNext());
                        yield this.state != ValidBufferState.NONE;
                    }
                };
            }

            @Override public final T next() {
                return switch (this.state) {
                    case ValidBufferState.VALUE_PRODUCER_0_OR_1 -> {
                        this.state = ValidBufferState.NONE;
                        yield this.resultBuffer;
                    }
                    case ValidBufferState.MULTI_VALUE_PRODUCER  -> {
                        final T value = this.multiResultBuffer.next();
                        this.state    = this.multiResultBuffer.hasNext() ? ValidBufferState.MULTI_VALUE_PRODUCER : ValidBufferState.NONE;
                        yield value;
                    }
                    case ValidBufferState.NONE                  -> {
                        throw new BuggyCodeException("Trying to invoke Pipe's Iterable<T>.next() when there's no element");
                    }
                };
            }
        };
    }

    // only Terminal deserve special API
    @FunctionalInterface
    public interface Terminal<T,R> {
        // fn() -> R { for (T value: producer) return fun(value); }
        R performMutableReduction(Iterable<T> oneTimeProducer);

        public enum Selector { INSTANCE;
            // public forEach(Consumer<T>)
            // public fold(T, BinaryOperator<T>) -> T
            // public fold(BinaryOperator<T>) -> Nilable<T>
            // public fold(Collector)
            // public toMap()
            // public toList()
            // public toSet()
            // public toArray()
            // public matchAll()
            // public matchAny()
            // public matchNone()
            public static <T> Terminal<T,Nilable<T>> takeFirst() {
                // Collector.of(null, null, null);
                return producer -> {
                    for (final T value: producer)
                        return Nilable.of(value);
                    return Nilable.empty();
                };
            }
            public static <T> Terminal<T,Void> forEach(Consumer<? super T> consumer) {
                return producer -> {
                    for (final T value: producer)
                        consumer.accept(value);
                    return null;
                };
            }
        }
    }

    // public static of()
    // public static of()
    // public static empty()

    public static <T> DraftPipe<T> source(Collection<T> source) {
        return new DraftPipe<>(source);
    }

    public enum Util { ;
        // public static generate()
    }



    // ----- Internals -----
    private sealed interface IntermediatePipe<T,R> {
        <$NextR> IntermediatePipe<T,$NextR> connect(IntermediatePipe<? super R,? extends $NextR> nextPipe);

        /** Mutation-variant of value-producing fn(T) -> R.<br/>
          * Unlike value-producing function, downstream might be invoked {@code [0,inf]} times.
          * @param <T> Source type
          * @param <R> Result type */
        @FunctionalInterface
        non-sealed interface ValueProducerOf0Or1<T,R> extends IntermediatePipe<T,R> {
            void operate(T value, Consumer<? super R> downstream);

            /** Connect end of this pipe to start of the {@code nextPipe}. */
            default <$NextR> ValueProducerOf0Or1<T,$NextR> connect(IntermediatePipe<? super R,? extends $NextR> nextPipe) {
                return (source, downstream) -> {
                    switch (nextPipe) {
                        case IntermediatePipe.ValueProducerOf0Or1<? super R,? extends $NextR> nextValueProducer -> {
                            this.operate(source, produced -> nextValueProducer.operate(produced, downstream));
                        }
                        case IntermediatePipe.MultiValueProducer<? super R,? extends $NextR> nextMultiValueProducer -> {
                            this.operate(source, produced -> {
                                nextMultiValueProducer.feed(produced);
                                while (nextMultiValueProducer.hasNext())
                                    downstream.accept(nextMultiValueProducer.next());
                            });
                        }
                    }
                };
            }
        }

        /** Practically just fused {@link Function}-{@link Iterator}, buffer multi-element return from a function. */
        static final class MultiValueProducer<T,R> implements IntermediatePipe<T,R> {
            private final Function<T,Iterable<R>> flatMapper;
            Iterator<R> bufferedResult;

            MultiValueProducer(Function<? super T,Iterable<R>> flatMapper) {
                this.flatMapper = flatMapper::apply;
            }

            /** Only callable internally, skipping feed operation and directly construct usable instance. */
            private MultiValueProducer(Iterator<R> bufferedResult) {
                this.flatMapper = null;
                this.bufferedResult = bufferedResult;
            }

            final void feed(T source) {
                this.bufferedResult = this.flatMapper.apply(source).iterator();
            }

            final boolean hasNext() {
                return this.bufferedResult.hasNext();
            }

            final R next() {
                return this.bufferedResult.next();
            }

            /** Only callable on {@link #feed(Object)}-ed {@link MultiValueProducer} */
            public final <$NextR> MultiValueProducer<T,$NextR> connect(IntermediatePipe<? super R,? extends $NextR> nextPipe) {
                return switch (nextPipe) {
                    case IntermediatePipe.ValueProducerOf0Or1<? super R,? extends $NextR> nextValueProducer -> {
                        final var postMapBuffer = new ArrayList<$NextR>(4);
                        while (this.hasNext())
                            nextValueProducer.operate(this.next(), postMapBuffer::add);
                        yield new MultiValueProducer<>(postMapBuffer.iterator());
                    }
                    case IntermediatePipe.MultiValueProducer<? super R,? extends $NextR> nextMultiValueProducer -> {
                        final var postMapBuffer = new ArrayList<$NextR>(8);
                        while (this.hasNext()) {
                            nextMultiValueProducer.feed(this.next());
                            while (nextMultiValueProducer.hasNext())
                                postMapBuffer.add(nextMultiValueProducer.next());
                        }
                        yield new MultiValueProducer<>(postMapBuffer.iterator());
                    }
                };
            }
        }
    }
}

