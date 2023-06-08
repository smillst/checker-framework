public class CheckNotNull2<T> {
  T checkNotNull(T ref) {
    return ref;
  }

  void test(T ref) {
    checkNotNull(ref);
  }
}
