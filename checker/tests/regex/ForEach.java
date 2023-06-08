public class ForEach {
  <T> T iterate(T[] constants) {
    for (T constant : constants) {
      return constant;
    }
    return null;
  }
}
