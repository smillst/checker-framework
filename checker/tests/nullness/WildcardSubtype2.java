import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WildcardSubtype2 {
  class MyClass {}

  class Visitor<T, S> {
    String visit(T p) {
      return "";
    }
  }

  class MyClassVisitor extends Visitor<@Nullable MyClass, @Nullable MyClass> {}

  class NonNullMyClassVisitor extends Visitor<@NonNull MyClass, @NonNull MyClass> {}

  void test(MyClassVisitor myClassVisitor, NonNullMyClassVisitor nonNullMyClassVisitor) {
    // :: error: (argument)
    take(new Visitor<@Nullable Object, @Nullable Object>());
    // :: error: (argument)
    take(new Visitor<@Nullable Object, @Nullable Object>());
    Visitor<?, ?> visitor1 = myClassVisitor;
    Visitor<?, ?> visitor2 = nonNullMyClassVisitor;

    // :: error: (assignment)
    Visitor<@NonNull ?, @NonNull ?> visitor3 = myClassVisitor;
    Visitor<@NonNull ?, @NonNull ?> visitor4 = nonNullMyClassVisitor;

    Visitor<@NonNull ?, @NonNull ?> visitor5 =
        // :: error: (assignment)
        new MyClassVisitor();
    Visitor<@NonNull ?, @NonNull ?> visitor6 =
        // :: error: (assignment)
        new MyClassVisitor();
    // :: error: (argument)
    take(new MyClassVisitor());
    // :: error: (argument)
    take(new MyClassVisitor());
  }

  void take(Visitor<@NonNull ?, @NonNull ?> v) {}

  void bar() {
    // :: error: (argument)
    take(new Visitor<@Nullable Object, @Nullable Object>());
    // :: error: (argument)
    take(new MyClassVisitor());
  }

  void baz() {
    // :: error: (argument)
    take(new MyClassVisitor());
    take(new NonNullMyClassVisitor());
  }
}
