public class ForEach {
  <T extends @NonNull Object> T iterate(T[] constants) {
    for (T constant : constants) {
      return constant;
    }
    return null;
  }
}
