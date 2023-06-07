// Test case for issue #2995:
// https://github.com/typetools/checker-framework/issues/2995

class Issue2995 {
  interface Set<E> {}

  class Map<K> {
    Set<K> keySet = new KeySet();

    class KeySet implements Set<K> {}
  }
}
