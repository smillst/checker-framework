import java.util.Optional;

// The type of a method invocation can contain several captures of the receiver's wildcards, and
// the receiver's enclosing type can have wildcards.
public class ReceiverCapture {
  static class Box<T> {
    <U> Pair<T, U> pair(U u) {
      throw new Error();
    }

    void put(T t) {}
  }

  static class Pair<A, B> {}

  void lambdaCapture(Optional<?> o) {
    Optional<?> r = o.map(v -> o);
  }

  void argumentCapture(Box<?> b) {
    Pair<?, ?> p = b.pair(b);
  }

  @SuppressWarnings("interning:argument") // Interning defaults lower bounds to @InternedDistinct.
  void argumentCaptureSuper(Box<? super String> b, boolean c) {
    b.put(c ? "a" : "b");
    Pair<?, ?> p = b.pair(b);
  }

  static class Outer<T> {
    class Inner<S> {
      T getT() {
        throw new Error();
      }

      void setS(S s) {}
    }
  }

  void enclosingWildcard(Outer<?>.Inner<?> x, Outer<? extends Number>.Inner<? super Integer> y) {
    Object o = x.getT();
    Number n = y.getT();
    y.setS(1);
  }
}
