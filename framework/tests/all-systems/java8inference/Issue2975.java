import java.io.Closeable;
import java.util.function.Consumer;

public class Issue2975<T extends AutoCloseable> {
    static class Child extends Issue2975<Closeable> {
        // true postives.
        @SuppressWarnings({
            "nullness:assignment.type.incompatible",
            "nullness:constructor.invocation.invalid"
        })
        Wrapper y = new Wrapper(Child::takesCloseable);

        private static void takesCloseable(Closeable rhs) {}
    }

    protected class Wrapper {
        protected Wrapper() {}

        protected Wrapper(Consumer<T> makeExpression) {}

        protected Wrapper method(Consumer<T> makeExpression) {
            throw new RuntimeException();
        }
    }
}
