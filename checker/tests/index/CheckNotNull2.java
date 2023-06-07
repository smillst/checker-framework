public class CheckNotNull2<T extends @NonNull Object> {
  T checkNotNull(T ref) {
    return ref;
  }

  void test(T ref) {
    checkNotNull(ref);
  }
}
