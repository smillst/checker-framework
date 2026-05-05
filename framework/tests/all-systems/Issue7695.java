package open.liam;

import java.util.Optional;

public class Issue7695 {
  void test(Optional<Object> optional) {
    Optional<String> x = optional.flatMap((Object unused) -> Optional.of(""));
  }
}
