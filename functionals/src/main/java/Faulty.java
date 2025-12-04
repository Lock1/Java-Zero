import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Disjoint container, contain either 1 element {@code T} or 1 element {@code E}.<br/>
  * While there's no type parameter constraints, it's recommended to use type parameter {@code E} as "error type", in similar fashion with Haskell's {@code Either<L,R>} and Rust's {@code Result<T,E>}.<br/><br/>
  * 
  * Definition for "error type" is up to the library user, concretely could be {@link java.lang.Enum}, could be {@code sealed} types, etc, as long it denote "computation error".<br/>
  * However, some extra utility functions only works when {@code E} is subtype of {@link Exception} (ex: {@link #ifAnyErrorThrow(Faulty...)}).<br/><br/>
  *
  * Unlike {@link Nilable}, this type intended to represent "possible error need to be taken care of" and does not provide throwing-unwrap instance method.<br/>
  * It's encouraged to use {@code instanceof} pattern match &amp; {@code switch} expression to handle this type.<br/><br/>
  * 
  * Warning: 0 check on raw {@code null}, do not use this parametric type with it.
  *
  * @param <T> Any type
  * @param <E> Any type but preferrably "error type"
  * @see <a href="https://doc.rust-lang.org/std/result/index.html">Rust counterpart: {@code Result<T,E>}</a>
  * @see <a href="https://hackage.haskell.org/package/base-4.21.0.0/docs/Data-Either.html">Haskell counterpart: {@code Either<L,R>}</a> */
public sealed interface Faulty<T,E> extends Transmutable<Faulty<T,E>> { // Post-Valhalla: public sealed abstract value class Faulty<T,E>
    /** Type representing "successful computation" {@link Faulty}.
      * @param <T> Any type
      * @param <E> Any type but preferrably "error type". Used as a phantom type
      * @param value Value to be wrapped */
    public /** value */ record Ok<T,E>(T value) implements Faulty<T,E> {}
    /** Type representing "failed computation" {@link Faulty}.
      * @param <T> Any type, used as a phantom type here
      * @param <E> Any type but preferrably "error type"
      * @param error Error value to be wrapped */
    public /** value */ record Error<T,E>(E error) implements Faulty<T,E> {}



    // ------------------------- Static functions: Factories -------------------------
    /** Primary factory method {@link Faulty.Ok}: Equivalent to {@link Faulty.Ok#Ok(Object)}.
      * @param <T> Any type
      * @param <E> Any type but preferrably "error type". Used as a phantom type
      * @param value Value to be wrapped
      * @return {@link Faulty} of appropriate type */
    public static <T,E> Faulty<T,E> of(T value) {
        return new Faulty.Ok<>(value);
    }

    /** Primary factory method {@link Faulty.Error}: Equivalent to {@link Faulty.Error#Error(Object)}.
      * @param <T> Any type, used as a phantom type here
      * @param <E> Any type but preferrably "error type"
      * @param error Error to be wrapped
      * @return {@link Faulty} of appropriate type */
    public static <T,E> Faulty<T,E> ofError(E error) {
        return new Faulty.Error<>(error);
    }

    /** Factory: {@code try}-statement replacement for checked exception, turning it into typed {@link Faulty}.<br/>
      * Provides expression alternative for {@code try}-statement + checked exception.
      *
      * <b>WARNING: Avoid using this function to catch unchecked exception, even though it can.</b>
      *             If this function catches unrecognized exception type, this function will throw {@link BuggyCodeException}.
      *
      * @param <T> Any type
      * @param <E> Subtype of {@link Exception}
      * @param expectedThrowType Class literal of {@code E}, expected exception to be thrown from supplier
      * @param supplier Side-effect producer lambda that might throws {@code E}
      * @return Well-typed {@link Faulty} */
    public static <T,E extends Exception> Faulty<T,E> fromCheckedCatch(Class<E> expectedThrowType, FunctionalDatas.ThrowingSupplier<T,E> supplier) {
        try {
            return Faulty.of(supplier.get());
        } catch (Exception e) {
            if (expectedThrowType.isInstance(e))
                return Faulty.ofError(expectedThrowType.cast(e));
            throw new BuggyCodeException("Faulty.fromCheckedCatch() catches wrong exception type (possibly being used to catch unchecked exception which violate Faulty.fromCheckedCatch() contract)", e);
        }
    }

    /** Factory: Fallback to {@link Exception} variant of {@link #fromCheckedCatch(Class, FunctionalDatas.ThrowingSupplier)}.
      * @param <T> Any type
      * @param supplier Side-effect product lambda that might throws {@link Exception}
      * @return {@link Faulty} parametrized with {@link Exception} */
    public static <T> Faulty<T,Exception> fromCheckedCatch(FunctionalDatas.ThrowingSupplier<T,?> supplier) {
        try {
            return Faulty.of(supplier.get());
        } catch (Exception e) {
            return Faulty.ofError(e);
        }
    }

    /** Factory: {@code try}-statement for all kind of unchecked exception, turning it into typed {@link Faulty}.<br/>
      * @param <T> Any type
      * @param supplier Side-effect producer lambda that might throws unchecked {@code RuntimeException}
      * @return Non-specific typed {@link Faulty} */
    public static <T> Faulty<T,RuntimeException> fromCatch(FunctionalDatas.ThrowingSupplier<T,RuntimeException> supplier) {
        try {
            return Faulty.of(supplier.get());
        } catch (RuntimeException e) {
            return Faulty.ofError(e);
        }
    }

    /** Transpose-{@link Nilable}: Bijective map {@code Nilable<Faulty<T,E>> -> Faulty<Nilable<T>,E>}.
      * <table>
      *     <caption>Table of all possible 3 mapping, identical to Rust's Option::transpose()</caption>
      *     <thead><tr><th>Source</th><th>Destination</th></tr></thead>
      *     <tbody>
      *         <tr><td>{@code Nilable.Has(Faulty.Ok(value))}</td>   <td>{@code Faulty.Ok(Nilable.Has(value))}</td></tr>
      *         <tr><td>{@code Nilable.Has(Faulty.Error(error))}</td><td>{@code Faulty.Error(error)}</td></tr>
      *         <tr><td>{@code Nilable.Empty}</td>                   <td>{@code Faulty.Ok(Nilable.Empty)}</td></tr>
      *     </tbody>
      * </table>
      * @param <T> Any type
      * @param <E> Any type but preferrably "error type"
      * @param nilable Source to be transposed
      * @return Transpose result {@link Faulty}
      * @see Nilable#ofTransposed(Faulty) Inverse map: {@code Nilable::ofTransposed(Faulty)}
      * @see <a href="https://doc.rust-lang.org/std/option/enum.Option.html#method.transpose">Rust counterpart: {@code Option::transpose()} (note: member function, unlike this {@code static} function)</a> */
    public static <T,E> Faulty<Nilable<T>,E> ofTransposed(Nilable<Faulty<T,E>> nilable) {
        return switch (nilable) {
            case Nilable.Has(Faulty<T,E> faulty) -> switch (faulty) {
                case Faulty.Ok(T value)    -> new Faulty.Ok<>(Nilable.of(value));
                case Faulty.Error(E error) -> new Faulty.Error<>(error);
            };
            case Nilable.Empty<Faulty<T,E>> __   -> new Faulty.Ok<>(Nilable.empty());
        };
    }



    // ------------------------- Static functions: Utilities -------------------------
    /** Specialized variant of {@link Faulty}: {@code E} is subtype of {@link Exception}.<br/> 
      * @param <E> Any subtype of {@link Exception}
      * @param faulties Faulties to check
      * @return {@link Iterable}, can be used with {@code for} statement &amp; {@code throw} */
    @SafeVarargs // Read-only & reference copying to new Iterable<T>
    public static <E extends Exception> Iterable<E> iterateException(Faulty<?,E> ...faulties) {
        return () -> Stream.of(faulties)
            .mapMulti(Faulty.Streams.MapMulti.takeError())
            .iterator();
    }

    /** Specialized variant of {@link Faulty}: {@code E} is subtype of {@link Exception}.<br/> 
      * @param <E> Any subtype of {@link Exception}
      * @param faulties Faulties to check
      * @throws E Fail-fast: If any of the {@link Faulty} contains {@link Faulty.Error}, throw contained {@code E} */
    @SafeVarargs // See Faulty#iterateException(Faulty)
    public static <E extends Exception> void ifAnyErrorThrow(Faulty<?,E> ...faulties) throws E {
        for (E exception: Faulty.iterateException(faulties))
            throw exception;
    }

    /** {@link Faulty} sub-namespace related to {@link Stream}. */
    public enum Streams { ;
        /** {@link Faulty} sub-namespace dedicated for {@link Collector}. */
        public enum Collect { ;
            public static <T,E> Collector<Faulty<T,E>,?,Stream<T>> takeOk() {
                return Collector.<Faulty<T,E>,Stream.Builder<T>,Stream<T>>of(
                    Stream::<T>builder,
                    (acc, element) -> { element.peek(acc::add); },
                    (left, right) -> { right.build().forEach(left::add); return left; },
                    stream -> stream.build()
                );
            }

            public static <T,E> Collector<Faulty<T,E>,?,Stream<E>> takeError() {
                return Collector.<Faulty<T,E>,Stream.Builder<E>,Stream<E>>of(
                    Stream::<E>builder,
                    (acc, element) -> { element.peekError(acc::add); },
                    (left, right) -> { right.build().forEach(left::add); return left; },
                    stream -> stream.build()
                );
            }

            /** TODO: Docs. Fail-fast counterpart better served by MapMulti.filterX */
            public static <T,E> Collector<Faulty<T,E>,?,Faulty<Stream<T>,Stream<E>>> failDeferredCollect() {
                return Collectors.teeing(
                    Collect.<T,E>takeOk(),
                    Collect.<T,E>takeError(),
                    (oks, errors) -> {
                        final List<E> errorList = errors.toList();
                        return errorList.isEmpty() ? Faulty.of(oks) : Faulty.ofError(errorList.stream());
                    }
                );
            }
        }

        /** {@link Faulty}-{@link Stream} sub-namespace dedicated for {@link Stream#mapMulti(BiConsumer)}.
          * @since JDK 21 */
        public enum MapMulti { ;
            /** Fusion operator: {@link Stream#filter(Predicate)} remove all non-{@link Faulty.Ok} variant and {@link Stream#map(Function)} unwrap {@code T} element.<br/> 
              * @param <T> Any type
              * @param <E> Any type but preferrably "error type"
              * @return {@link BiConsumer} that applies side-effect to its arguments
              * @see Nilable.Streams.MapMulti#filterUnwrap() */
            public static <T,E> BiConsumer<Faulty<T,?>,Consumer<T>> takeOk() {
                return (faulty, downstreamPipeline) -> {
                    if (faulty instanceof Faulty.Ok(T value))
                        downstreamPipeline.accept(value);
                };
            }

            /** Fusion operator: {@link Stream#filter(Predicate)} remove all non-{@link Faulty.Error} variant and {@link Stream#map(Function)} unwrap {@code E} element.<br/> 
              * @param <T> Any type
              * @param <E> Any type but preferrably "error type"
              * @return {@link BiConsumer} that applies side-effect to its arguments
              * @see Nilable.Streams.MapMulti#filterUnwrap() */
            public static <T,E> BiConsumer<Faulty<?,E>,Consumer<E>> takeError() {
                return (faulty, downstreamPipeline) -> {
                    if (faulty instanceof Faulty.Error(E error))
                        downstreamPipeline.accept(error);
                };
            }
        }
    }



    // ------------------------- Functor instance methods -------------------------
    @SuppressWarnings("unchecked") // Cast warning: (Faulty<R,E>) Faulty.Error<T,E>. T is just a phantom type 
    public default <T2> Faulty<T2,E> map(Function<? super T,? extends T2> mapper) {
        return this instanceof Faulty.Ok(T value) ? new Faulty.Ok<>(mapper.apply(value)) : (Faulty<T2,E>) this;
    }

    @SuppressWarnings("unchecked") // Cast warning: See Faulty#map(), E is just a phantom type in Faulty.Ok<T,E>
    public default <E2> Faulty<T,E2> mapError(Function<? super E,? extends E2> errorMapper) {
        return this instanceof Faulty.Error(E error) ? new Faulty.Error<>(errorMapper.apply(error)) : (Faulty<T,E2>) this;
    }

    @SuppressWarnings("unchecked") // Cast warning: See Faulty#map(), T is just a phantom type in Faulty.Error<T,E>
    public default <T2> Faulty<T2,E> flatMap(Function<? super T,Faulty<T2,E>> faultyMapper) {
        return this instanceof Faulty.Ok(T value) ? faultyMapper.apply(value) : (Faulty<T2,E>) this;
    }

    /** TODO: Docs. Behave similar to filter() */
    public default Faulty<T,E> testOrError(Predicate<? super T> predicate, Supplier<? extends E> errorSupplier) {
        return !(this instanceof Faulty.Ok(T value)) ? this : // If this is an error, then short-circuit & keep the value
               predicate.test(value)                 ? this : new Faulty.Error<>(errorSupplier.get());
    }



    // ------------------------- Transmutation methods -------------------------
    /** Outbound-transmutation method: Apply lossy injection {@link Faulty.Ok} {@code ->} {@link Nilable.Has} &amp; {@link Faulty.Error} {@code ->} {@link Nilable.Empty}.
      * @return Conversion result {@link Nilable}
      *
      * @see <a href="https://doc.rust-lang.org/std/result/enum.Result.html#method.ok">Rust counterpart: {@code Result::ok()}</a> */
    public default Nilable<T> toNilableOk() {
        return this instanceof Faulty.Ok(T value) ? Nilable.of(value) : Nilable.empty();
    } 

    /** T-split variant of {@link Faulty#toNilableOk()}: Commonly used for logging &amp; deliberately "don't care" with specific of type {@code E}.
      * @param errorSideEffectLambda Side-effect lambda consuming {@code E} type
      * @return Conversion result {@link Nilable} */
    public default Nilable<T> toNilableTeeSplit(Consumer<? super E> errorSideEffectLambda) {
        if (this instanceof Faulty.Error(E error))
            errorSideEffectLambda.accept(error);
        return this instanceof Faulty.Ok(T value) ? Nilable.of(value) : Nilable.empty();
    } 

    /** Outbound-transmutation method: Dual of {@link #toNilableOk()}, {@link Faulty.Error} {@code ->} {@link Nilable.Has}.
      * @return Conversion result {@link Nilable}
      *
      * @see <a href="https://doc.rust-lang.org/std/result/enum.Result.html#method.err">Rust counterpart: {@code Result::err()}</a> */
    public default Nilable<E> toNilableError() {
        return this instanceof Faulty.Error(E error) ? Nilable.of(error) : Nilable.empty();
    }



    // ------------------------- Query methods -------------------------
    public default boolean isOk() {
        return this instanceof Faulty.Ok<T,E>;
    }

    public default boolean isOk(Predicate<? super T> predicate) {
        return this instanceof Faulty.Ok(T value) && predicate.test(value);
    }

    public default boolean isError() {
        return this instanceof Faulty.Error<T,E>;
    }

    public default boolean isError(Predicate<? super E> predicate) {
        return this instanceof Faulty.Error(E error) && predicate.test(error);
    }



    // ------------------------- Side-effect methods -------------------------
    public default Faulty<T,E> peek(Consumer<? super T> sideEffectLambda) {
        if (this instanceof Faulty.Ok(T value))
            sideEffectLambda.accept(value);
        return this;
    }

    public default Faulty<T,E> peekError(Consumer<? super E> sideEffectLambda) {
        if (this instanceof Faulty.Error(E error))
            sideEffectLambda.accept(error);
        return this;
    }



    // ------------------------- Boolean short-circuiting operator -------------------------
    public default Faulty<T,E> or(Faulty<T,E> other) {
        return this instanceof Faulty.Ok<T,E> ? this : other;
    }

    @SuppressWarnings("unchecked") // Cast warning: Faulty<? extends T,? extends E> is compatible to Faulty<T,E>
    public default Faulty<T,E> or(Supplier<Faulty<? extends T,? extends E>> other) {
        return this instanceof Faulty.Ok<T,E> ? this : (Faulty<T,E>) other.get();
    }

    @SuppressWarnings("unchecked") // Cast warning: Faulty.Error<T,E> is compatible to Faulty<R,E> (phantom type)
    public default <R> Faulty<R,E> and(Faulty<R,E> other) {
        return this instanceof Faulty.Error<T,E> ? (Faulty<R,E>) this : other;
    }

    @SuppressWarnings("unchecked") // Cast warning: See phantom type #and(Faulty), this one also includes Faulty<T,E> is compatible to Faulty<? extends R,? extends E>
    public default <R> Faulty<R,E> and(Supplier<Faulty<? extends R,? extends E>> other) {
        return (Faulty<R,E>) (this instanceof Faulty.Error<T,E> ? this : other.get());
    }



    // ------------------------- Unwrapping methods -------------------------
    public default T unwrapOkOrElse(T other) {
        return this instanceof Faulty.Ok(T value) ? value : other;
    }

    public default T unwrapOkOrElse(Supplier<? extends T> other) {
        return this instanceof Faulty.Ok(T value) ? value : other.get();
    }

    public default E unwrapErrorOrElse(E other) {
        return this instanceof Faulty.Error(E error) ? error : other;
    }

    public default E unwrapErrorOrElse(Supplier<? extends E> other) {
        return this instanceof Faulty.Error(E error) ? error : other.get();
    }
}

