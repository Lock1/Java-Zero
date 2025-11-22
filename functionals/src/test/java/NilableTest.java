import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class NilableTest {
    @Test
    void staticFactory_Of() {
        Assertions.assertTrue(Nilable.of(null) instanceof Nilable.Empty, "Instantiating empty Nilable");
        Assertions.assertTrue(Nilable.of(new Object()) instanceof Nilable.Has, "Instantiating container Nilable.Has");
    }
}
