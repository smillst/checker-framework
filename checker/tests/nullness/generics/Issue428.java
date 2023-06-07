// Test case for issue #428:
// https://github.com/typetools/checker-framework/issues/428

import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

public interface Issue428<T extends Number> {}

class Test428 {
  void m(List<Issue428<@NonNull ?>> is) {
    Issue428<?> i = is.get(0);
  }
}
