import java.util.function.Function;

/** F-bounded parametric type: Provides "forward function composition".<br/>
  * @param <T> Any type, but in concrete implementation "self"-type. Example: {@code interface Nilable<T> extends Transmutable<Nilable<T>>} */
public interface Transmutable<T extends Transmutable<T>> {
    /** General outbound-transmutation method: Transform this {@link Transmutable} into any type.<br/>
      * This method can be seen as a fusion of {@code map(Function)} &amp; {@code orElse(Object)}.<br/>
      * This method also can be seen as forward function composition or "pipeline" operator, allowing fluent chaining.<br/><br/>
      *
      * This method has a neat (and bit quirky) interaction with static functions, due to how Java method reference &amp; parametrized type works.<br/>
      * For example, {@code nilableFaulty.to(Faulty::ofTransposed)}.<br/>
      *
      * <h4>Dealing with subtyping</h4>
      * With static functions, it's possible to emulate other languages compile-time type checking on parametrized types like what happen here.<br/> 
      * However, this mechanic has some limitation when used alongside subtyping, so additional care is required when defining function accepting parametrized type like {@link Nilable}.<br/><br/>
      *
      * Functional interfaces need to be explicitly declared as covariant in order to work with this method.<br/>
      * Example: <pre>{@code
      * Function<Nilable<? extends Exception>,String> f = ex -> { ... }; // Covariance: ? extends Exception
      * Nilable.of(new RuntimeException())                               // RuntimeException is subtype of / extends Exception
      *     .to(f);
      * }</pre>
      * For static functions, declare any function working with {@link Nilable} to be generic static function with appropriate type constraint / bound or do the similar thing with functional interface.<br/>
      * Example <pre>{@code
      * static <T extends Exception> String f(Nilable<T> ex) { ... } // Declare type `T` in f(Nilable<T>) to be covariant
      * // static String f(Nilable<? extends Exception> ex) { ... }  // Acceptable alternative, similar to functional interfaces
      *
      * Nilable.of(new RuntimeException())                           // Exception :> RuntimeException
      *     .to(Main::f);
      * }</pre>
      *
      * <h4>Flexibility &amp; extension functions</h4>
      * It's not restricted to pre-made function inside this library, it's up to user code to define convenience function that suits their needs.<br/>
      * It also can be thought like Kotlin's extension functions, but without terrible namespace pollution it brings (opt-in explicit invocation of {@link #to(Function)}).
      *
      * @param <R> Any type
      * @param transmutator {@link Nilable} type transformer. Use explicit type witnesses if needed, ex: {@code Function<? super Nilable<? extends T>,? extends R>}
      * @return Transmutation result */
    @SuppressWarnings("unchecked") // Cast warning site: (T) Transmutable<T>. But T is subtype of Transmutable<T> due to type parameter bound, so this cast should be safe
    public default <R> R to(Function<? super T,? extends R> transmutator) { // This implicitly forces implementor type T to be invariant (ex: Nilable<U>, not Nilable<? extends U>) and it's an intended behavior 
        return transmutator.apply((T) this);
    }
}
