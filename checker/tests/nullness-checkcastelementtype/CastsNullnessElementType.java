import org.checkerframework.checker.nullness.qual.*;

public class CastsNullnessElementType {

    void testUnsafeCastArray1(@Nullable Object[] x) {
        // :: warning: (cast.unsafe)
        @NonNull Object[] y = (@NonNull Object[]) x;
        y[0].toString();
    }

    void testUnsafeCastArray2(@NonNull Object x) {
        // :: warning: (cast.unsafe)
        @NonNull Object[] y = (@NonNull Object[]) x;
        y[0].toString();
    }

    void testUnsafeCastList1(java.util.ArrayList<@Nullable Object> x) {
        // :: warning: (cast.unsafe)
        java.util.List<@NonNull Object> y = (java.util.List<@NonNull Object>) x;
        y.get(0).toString();
        // :: warning: (cast.unsafe)
        java.util.List<@NonNull Object> y2 = (java.util.ArrayList<@NonNull Object>) x;
        java.util.List<@Nullable Object> y3 = (java.util.List<@Nullable Object>) x;
    }

    void testUnsafeCastList2(java.util.List<@Nullable Object> x) {
        java.util.List<@Nullable Object> y = (java.util.ArrayList<@Nullable Object>) x;
        // TODO :: warning: (cast.unsafe)
        java.util.List<@NonNull Object> y2 = (java.util.ArrayList<@NonNull Object>) x;
    }

    void testUnsafeCastList3(@NonNull Object x) {
        // :: warning: (cast.unsafe)
        // :: warning: [unchecked] unchecked cast
        java.util.List<@Nullable Object> y = (java.util.List<@Nullable Object>) x;
        // :: warning: (cast.unsafe)
        // :: warning: [unchecked] unchecked cast
        java.util.List<@NonNull Object> y2 = (java.util.ArrayList<@NonNull Object>) x;
    }
}
