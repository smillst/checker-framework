package open.liam;

import java.util.Optional;

public class Issue7699 {
  <T> Optional<T> run(Optional<Object> optional, T t) {
    return optional.flatMap(o -> true ? Optional.of(t) : Optional.empty());
  }
}
