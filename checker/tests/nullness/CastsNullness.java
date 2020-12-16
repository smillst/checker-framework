import org.checkerframework.checker.nullness.qual.*;

public class CastsNullness {

    void test(String nonNullParam) {
        Object lc1 = (Object) nonNullParam;
        lc1.toString();

        String nullable = null;
        Object lc2 = (Object) nullable;
        // :: error: (dereference.of.nullable)
        lc2.toString(); // error
    }

    void testBoxing() {
        Integer b = null;
        // :: error: (unboxing.of.nullable)
        int i = b;
        // no error, because there was already a nullpointer exception
        Object o = (int) b;
    }

    void testUnsafeCast(@Nullable Object x) {
        // :: warning: (cast.unsafe)
        @NonNull Object y = (@NonNull Object) x;
        y.toString();
    }

    void testSuppression(@Nullable Object x) {
        // :: error: (assignment.type.incompatible)
        @NonNull String s1 = (String) x;
        @SuppressWarnings("nullness")
        @NonNull String s2 = (String) x;
    }

    class Generics<T extends Object> {
        T t;
        @Nullable T nt;

        Generics(T t) {
            this.t = t;
            this.nt = t;
        }

        void m() {
            // :: error: (assignment.type.incompatible)
            t = (@Nullable T) null;
            nt = (@Nullable T) null;
            // :: warning: (cast.unsafe)
            t = (T) null;
            // :: warning: (cast.unsafe)
            nt = (T) null;
        }
    }

    void testSafeCasts() {
        // :: error: (nullness.on.primitive)
        Integer x = (@Nullable int) 1;
    }
}
