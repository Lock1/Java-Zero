import java.util.Optional;
import java.util.function.Function;

/** 
  * Container of [0..1] element.<br/>
  *
  * Sum-type-ified {@link Optional}, provides exhaustible pattern match when used with modern {@code switch} expression.
  *
  * @see <a href="https://doc.rust-lang.org/std/option/">Rust {@code Option<T>}</a>
  * @see <a href="https://hackage.haskell.org/package/base-4.21.0.0/docs/Data-Maybe.html">Haskell {@code Maybe<T>}</a>
  */
public sealed interface Nilable<T> {
    public record Has<T>(T value) implements Nilable<T> {}
    public final class Empty<T> implements Nilable<T> {
        private static final Empty<?> INSTANCE = new Empty<>();
    }



    public static <T> Nilable<T> of(T nullableValue) {
        return nullableValue != null ? new Nilable.Has<>(nullableValue) : Nilable.empty();
    }

    @SuppressWarnings("unchecked") // Empty container can be cast into any Nilable<T>
    public static <T> Nilable<T> empty() {
        return (Nilable<T>) Nilable.Empty.INSTANCE;
    }

    public static <T> Nilable<T> from(Optional<T> optional) {
        return optional.isPresent() ? new Nilable.Has<>(optional.get()) : Nilable.empty();
    }



    public default <R> Nilable<R> map(Function<? super T,? extends R> mapper) {
        return this instanceof Nilable.Has(T value) ? Nilable.of(mapper.apply(value)) : Nilable.empty();
    }

    @SuppressWarnings("unchecked") // Nilable<? extends R> -> Nilable<R>, just ignore Nilable<>, (? extends R) is assignable to R
    public default <R> Nilable<R> flatMap(Function<? super T,Nilable<? extends R>> mapper) {
        return this instanceof Nilable.Has(T value) ? (Nilable<R>) mapper.apply(value) : Nilable.empty();
    }
}
