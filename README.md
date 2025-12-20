# Lock1's 0-Dependency Java Library
Assortment of personal 0-external-dependency Java library designed to keep me sane.
Note, API designs are based on whatever suits my type-driven Jaskell-style. 

Designed to be easily source-copied or in JAR library form.



## Project: Functionals
Provides central functional programming building blocks & utilities.
- Helper: `Transmutable<T>`
    - Basically just function application, but reversed: `arg.to(function)` instead of `function(arg)`
    - Allow left-to-right fluent reading & chaining
    - Also allows some fun interaction with Java type system
        - `Faulty<Nilable<T>,Err>.to(Nilable::ofTransposed) -> Nilable<Faulty<T,Err>>`
- Sum-type `sealed Nilable<T>`
    - Haskell/Rust/Scala `Maybe<T>` incarnation, 2 variant: `Nilable.Has<T>` and `Nilable.Empty<T>`
    - `record Nilable.Has<T>` compatible with modern exhaustive pattern matching & record destructuring
    - No API yield raw `null`
        - Some escape hatches is still possible, but all of them deliberately designed to be verbose
        - In case of bare `null` is needed, just go with `instanceof` or `switch`
- Sum-type `sealed Faulty<T,E>`
    - Haskell/Rust/Scala `Either<L,R>` incarnation, 2 variant: `Faulty.Ok<T,E>` and `Faulty.Error<T,E>`
        - Rather than shy away and mislead non-FP reader, this type deliberately named to represent "faulty code"
        - However, there's no type constraint on both sides, it's possible to use both type parameter as an exclusive data container
        - Although there is some "specialization" in static functions that only works for certain type parameters
    - Both variant is a `record`, so both of them can be used in exhaustive pattern matching & destructuring
        - Primarily designed for exhaustive `switch`, but also provide common API (`mapValue`, `mapError`, ...)
    - Provide some special static functions that only works on specific type parameter
