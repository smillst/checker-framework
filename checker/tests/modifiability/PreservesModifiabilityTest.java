import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PreservesModifiability;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.Shrinkable;

class PreservesModifiabilityTest {

  @PreservesModifiability
  static <T> List<T> annotated(Collection<T> values) {
    return new ArrayList<>(values);
  }

  static <T> List<T> unannotated(Collection<T> values) {
    return new ArrayList<>(values);
  }

  @PreservesModifiability
  static <T> void annotatedVoid(Collection<T> values) {}

  void preservesCapabilities(
      @Growable List<String> growable,
      @Shrinkable List<String> shrinkable,
      @Replaceable List<String> replaceable,
      @IteratorPolyMod List<String> iteratorPoly,
      @Modifiable List<String> modifiable) {
    @Growable List<String> g = annotated(growable);
    @Shrinkable List<String> s = annotated(shrinkable);
    @Replaceable List<String> r = annotated(replaceable);
    @IteratorPolyMod List<String> i = annotated(iteratorPoly);
    @Modifiable List<String> m = annotated(modifiable);
  }

  void unannotatedDoesNotPreserve(
      @Growable List<String> growable,
      @Shrinkable List<String> shrinkable,
      @Replaceable List<String> replaceable,
      @IteratorPolyMod List<String> iteratorPoly,
      @Modifiable List<String> modifiable) {
    // :: error: [assignment]
    @Growable List<String> g = unannotated(growable);
    // :: error: [assignment]
    @Shrinkable List<String> s = unannotated(shrinkable);
    // :: error: [assignment]
    @Replaceable List<String> r = unannotated(replaceable);
    // :: error: [assignment]
    @IteratorPolyMod List<String> i = unannotated(iteratorPoly);
    // :: error: [assignment]
    @Modifiable List<String> m = unannotated(modifiable);
  }

  void voidAnnotatedMethodHasNoEffect(@Growable List<String> growable) {
    annotatedVoid(growable);
  }
}
