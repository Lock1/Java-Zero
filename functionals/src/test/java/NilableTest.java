import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class NilableTest {
    @Test
    void static_Factory_Of() {
        Assertions.assertTrue(Nilable.of(null) instanceof Nilable.Empty, "Instantiating empty Nilable");
        Assertions.assertTrue(Nilable.of(new Object()) instanceof Nilable.Has, "Instantiating container Nilable.Has");
    }

    @Test
    void super_Object_Equals() {
        Assertions.assertFalse(Nilable.empty().equals(Nilable.of(new Object())));
        record Sample(int n) {}
        Assertions.assertTrue(Nilable.of(new Sample(10)).equals(Nilable.of(new Sample(10))));
    }

    @Test
    void method_To() {
        final Faulty<Nilable<String>,Integer> transposedNilable = Nilable.of(Faulty.<String,Integer>ofError(100))
            .to(Faulty::ofTransposed);
        Assertions.assertTrue(transposedNilable.equals(Faulty.ofError(100)), "Nilable::to(Faulty::ofTransposed()) nice behavior");

        final Function<Nilable<? extends Exception>,String> functionalInterface = ex -> ex.map(Exception::getMessage).orElse("10");
        final String mapOrElseResult = Nilable.of(new RuntimeException())
            .to(functionalInterface);
        Assertions.assertTrue(mapOrElseResult.equals(Nilable.of(new Exception()).to(NilableTest::fun)), "Functional interface vs static function produces equals()");
    }

    private static <T extends Exception> String fun(Nilable<T> e) {
        return e.map(Exception::getMessage).orElse("10");
    }
}
