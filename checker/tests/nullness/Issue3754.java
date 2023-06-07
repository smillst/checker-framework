// @above-java17-jdk-skip-test TODO: reinstate, false positives may be due to issue #979

import org.checkerframework.checker.nullness.qual.NonNull;

class Issue3754 {
  interface Supplier<T, U extends T> {
    U get();
  }

  Object x(Supplier<@NonNull ?, ?> bar) {
    return bar.get();
  }

  interface Supplier2<U extends T, T> {
    U get();
  }

  Object x(Supplier2<?, @NonNull ?> bar) {
    return bar.get();
  }
}
