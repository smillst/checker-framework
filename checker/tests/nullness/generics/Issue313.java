import org.checkerframework.checker.nullness.qual.*;

public class Issue313 {
  class A<T> {}

  <X> void m() {
    new A<X>();
  }
}
