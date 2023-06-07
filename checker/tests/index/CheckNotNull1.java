public class CheckNotNull1 {
  <T extends @NonNull Object> T checkNotNull(T ref) {
    return ref;
  }

  <S extends @NonNull Object> void test(S ref) {
    checkNotNull(ref);
  }
}
