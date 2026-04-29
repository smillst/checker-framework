// Test case for Issue 906
// https://github.com/typetools/checker-framework/issues/906
/**
 * @author Michael Grafl
 */
public class Issue906 {
  @SuppressWarnings("unchecked")
  public <B, A extends B> void start(A a, Class<B> cb) {
    // :: error: [dereference.of.nullable]
    Class<? extends A> c = (Class<? extends A>) a.getClass();
    x(a, c);
  }

  private <X, Y extends X> void x(Y a, Class<? extends Y> c) {}
}
