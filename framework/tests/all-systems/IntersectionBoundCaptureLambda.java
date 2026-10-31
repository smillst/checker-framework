import java.util.function.Consumer;

// The type of a lambda parameter and the function type's parameter type are different captures of
// the same wildcard, whose bound is an intersection that mentions a type variable.
public class IntersectionBoundCaptureLambda {
  interface Box<E> {}

  static class Pair<A extends Object, B extends Box<A> & Comparable<A>> {
    void forSecond(Consumer<B> c) {}
  }

  static void take(Object o) {}

  static void lambda(Pair<?, ?> p) {
    p.forSecond(s -> {});
  }

  static void methodReference(Pair<?, ?> p) {
    p.forSecond(IntersectionBoundCaptureLambda::take);
  }
}
