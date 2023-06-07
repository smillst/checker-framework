// @above-java17-jdk-skip-test TODO: reinstate, false positives may be due to issue #979

import org.checkerframework.checker.nullness.qual.NonNull;

public class Iterate {
  void method(Iterable<@NonNull ?> files) {
    for (Object file : files) {
      file.getClass();
    }
  }
}
