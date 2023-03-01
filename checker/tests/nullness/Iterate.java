// @above-java17-jdk-skip-test TODO: reinstate, false positives may be due to issue #979

package wildcards;

import org.checkerframework.checker.nullness.qual.NonNull;

public class Iterate {
  void method(Iterable<? extends @NonNull Object> files) {
    for (Object file : files) {
      file.getClass();
    }
  }
}
