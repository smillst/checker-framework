import java.util.Iterator;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;

public class IteratorConflictingAnnosUse {
  private final IteratorConflictingAnnosTypeTuple inputTypes =
      new IteratorConflictingAnnosTypeTuple();

  @Unmodifiable Iterator<String> reproduce() {
    return inputTypes.iterator();
  }
}
