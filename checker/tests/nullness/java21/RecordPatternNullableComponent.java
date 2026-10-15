import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

// @below-java21-jdk-skip-test

// None of the WPI formats supports the new Java 21 languages features, so skip inference until they
// do.
// @infer-jaifs-skip-test
// @infer-ajava-skip-test
// @infer-stubs-skip-test
public class RecordPatternNullableComponent {

  record Box<T>(T t) implements Supplier<T> {
    public T get() {
      return t;
    }
  }

  record ObjectBox(@Nullable Object o) {}

  record Pair<A, B>(A a, B b) {}

  void varPattern(Box<@Nullable String> box) {
    if (box instanceof Box(var s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  void varPatternAssignNull(Box<@Nullable String> box) {
    if (box instanceof Box(var s)) {
      s = null;
    }
  }

  void varPatternNonNull(Box<String> box) {
    if (box instanceof Box(var s)) {
      s.length();
    }
  }

  // `String s` is unconditional for the component type, so it matches null.
  void unconditionalTypePattern(Box<@Nullable String> box) {
    if (box instanceof Box(String s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  void unconditionalTypePatternSupertype(Box<@Nullable String> box) {
    if (box instanceof Box(CharSequence s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  // `String s` is not unconditional for Object, so it does not match null.
  void conditionalTypePattern(ObjectBox box) {
    if (box instanceof ObjectBox(String s)) {
      s.length();
    }
  }

  void switchVarPattern(Box<@Nullable String> box) {
    switch (box) {
      // :: error: (dereference.of.nullable)
      case Box(var s) -> s.length();
    }
  }

  void switchTypePattern(Box<@Nullable String> box) {
    switch (box) {
      // :: error: (dereference.of.nullable)
      case Box(String s) -> s.length();
    }
  }

  void nested(Box<Pair<@Nullable String, String>> box) {
    if (box instanceof Box(Pair(var a, String b))) {
      // :: error: (dereference.of.nullable)
      a.length();
      b.length();
    }
  }

  void supertype(Supplier<@Nullable String> supplier) {
    if (supplier instanceof Box(var s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  void wildcard(Supplier<? extends @Nullable String> supplier) {
    if (supplier instanceof Box(String s)) {
      // :: error: (dereference.of.nullable)
      s.length();
    }
  }

  <T extends @Nullable Object> void typeVariable(Box<T> box) {
    if (box instanceof Box(var t)) {
      // :: error: (dereference.of.nullable)
      t.toString();
    }
    if (box instanceof Box(Object o)) {
      // :: error: (dereference.of.nullable)
      o.toString();
    }
  }
}
