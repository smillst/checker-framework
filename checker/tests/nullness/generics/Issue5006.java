import org.checkerframework.checker.nullness.qual.NonNull;

public class Issue5006 {

  static class C<T> {
    T get() {
      throw new RuntimeException("");
    }
  }

  interface X {
    C<@NonNull ?> get();
  }

  interface Y extends X {
    @Override
    // :: error: (super.wildcard)
    C<? super Object> get();
  }
}
