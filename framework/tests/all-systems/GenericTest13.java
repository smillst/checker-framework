// Test case for Issue 142
// https://github.com/typetools/checker-framework/issues/142

import org.checkerframework.checker.nullness.qual.NonNull;

public class GenericTest13 {
  interface Entry<K extends @NonNull Object, V extends @NonNull Object> {
    V getValue();
  }

  interface Iterator<E extends @NonNull Object> {
    E next();
  }

  <S extends @NonNull Object> S call(Iterator<? extends Entry<?, S>> entryIterator) {
    return entryIterator.next().getValue();
  }
}
