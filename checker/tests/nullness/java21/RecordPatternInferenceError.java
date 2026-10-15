import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @below-java21-jdk-skip-test

// None of the WPI formats supports the new Java 21 languages features, so skip inference until they
// do.
// @infer-jaifs-skip-test
// @infer-ajava-skip-test
// @infer-stubs-skip-test
public class RecordPatternInferenceError {

  interface I<A> {}

  interface J<A> {}

  record Both<T>(T t) implements I<T>, J<T> {}

  record NonNullSupplier<T extends @NonNull Object>(T t) implements Supplier<T> {
    public T get() {
      return t;
    }
  }

  // The two elements of the intersection give alpha = @Nullable String and alpha = @NonNull String.
  // When inference fails, the type arguments of the pattern are top.
  <X extends I<@Nullable String> & J<String>> void conflictingBounds(X x) {
    // :: error: (type.arguments.not.inferred)
    if (x instanceof Both(var s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  <X extends I<@Nullable String> & J<String>> void conflictingBoundsSwitch(X x) {
    switch (x) {
      // :: error: (type.arguments.not.inferred)
      case Both(var s) -> {}
      default -> {}
    }
  }

  // The scrutinee gives alpha = @Nullable String, which violates alpha's declared bound.
  void scrutineeViolatesBound(Supplier<@Nullable String> s) {
    // :: error: (type.arguments.not.inferred)
    if (s instanceof NonNullSupplier(var t)) {
      // :: error: (dereference.of.nullable)
      t.length();
    }
  }

  void scrutineeViolatesBoundSwitch(Supplier<@Nullable String> s) {
    switch (s) {
      // :: error: (type.arguments.not.inferred)
      case NonNullSupplier(var t) -> {}
      default -> {}
    }
  }

  void scrutineeSatisfiesBound(Supplier<String> s) {
    if (s instanceof NonNullSupplier(var t)) {
      t.length();
    }
  }
}
