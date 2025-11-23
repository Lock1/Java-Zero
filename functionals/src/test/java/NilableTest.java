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
}
