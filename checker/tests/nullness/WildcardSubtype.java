import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WildcardSubtype {
  class MyClass {}

  class Visitor<T> {
    String visit(T p) {
      return "";
    }
  }

  class MyClassVisitor extends Visitor<@Nullable MyClass> {}

  class NonNullMyClassVisitor extends Visitor<@NonNull MyClass> {}

  void test(MyClassVisitor myClassVisitor, NonNullMyClassVisitor nonNullMyClassVisitor) {
    // :: error: (argument)
    take(new Visitor<@Nullable Object>());
    // :: error: (argument)
    take(new Visitor<@Nullable Object>());

    Visitor<?> visitor1 = myClassVisitor;
    Visitor<?> visitor2 = nonNullMyClassVisitor;

    // :: error: (assignment)
    Visitor<@NonNull ?> visitor3 = myClassVisitor;
    Visitor<@NonNull ?> visitor4 = nonNullMyClassVisitor;

    // :: error: (assignment)
    Visitor<@NonNull ?> visitor5 = new MyClassVisitor();
    // :: error: (assignment)
    Visitor<@NonNull ?> visitor6 = new MyClassVisitor();
    // :: error: (argument)
    take(new MyClassVisitor());
    // :: error: (argument)
    take(new MyClassVisitor());
  }

  void take(Visitor<@NonNull ?> v) {}

  void bar() {
    // :: error: (argument)
    take(new Visitor<@Nullable Object>());
    // :: error: (argument)
    take(new MyClassVisitor());
  }

  void baz() {
    // :: error: (argument)
    take(new MyClassVisitor());
    take(new NonNullMyClassVisitor());
  }
}
