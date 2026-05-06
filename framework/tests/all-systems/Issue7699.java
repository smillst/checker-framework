import java.util.Optional;

public class Issue7699 {
  <T> Optional<T> run(Optional<Object> optional, T t) {
    // :: error: [argument]
    return optional.flatMap(o -> true ? Optional.of(t) : Optional.empty());
  }
}
