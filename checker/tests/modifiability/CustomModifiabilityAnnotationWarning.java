import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeGrowable;
import org.checkerframework.checker.modifiability.qual.MaybeIteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.MaybeSeqGrowable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.Shrinkable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;

public class CustomModifiabilityAnnotationWarning {

  static class ExtendsAbstractList extends AbstractList<String> {
    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  abstract static class DirectCollection implements Collection<String> {}

  abstract static class DirectMap implements Map<String, String> {}

  abstract static class DirectIterator implements Iterator<String> {}

  abstract static class DirectListIterator implements ListIterator<String> {}

  static class NonModifiabilityType {}

  // :: warning: [modifiability.annotation.unverified] :: error: [super.invocation]
  static @Modifiable class ClassLevelModifiableList extends AbstractList<String> {
    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  // The implicit super() call is fine because ArrayList's constructor already
  // returns @Modifiable, but the subclass annotation is still an unverified modifiability claim.
  // :: warning: [modifiability.annotation.unverified]
  static @Modifiable class ModifiableSubclassWithoutSuperError extends ArrayList<String> {}

  // :: warning: [modifiability.annotation.unverified] :: error: [super.invocation]
  abstract static @Growable class ClassLevelGrowableCollection implements Collection<String> {}

  // :: warning: [modifiability.annotation.unverified] :: error: [super.invocation]
  abstract static @Unmodifiable class ClassLevelUnmodifiableMap implements Map<String, String> {}

  // :: warning: [modifiability.annotation.unverified]
  abstract static @IteratorPolyMod class ClassLevelIteratorPolyModIterator
      implements Iterator<String> {}

  // :: warning: [modifiability.annotation.unverified]
  static class ConstructorLevelShrinkableList extends AbstractList<String> {
    // :: error: [super.invocation]
    @Shrinkable ConstructorLevelShrinkableList() {}

    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  static @MaybeModifiable class ClassLevelMaybeModifiableList extends AbstractList<String> {
    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  abstract static @MaybeGrowable class ClassLevelMaybeGrowableCollection
      implements Collection<String> {}

  abstract static @MaybeIteratorPolyMod class ClassLevelMaybeIteratorPolyModIterator
      implements Iterator<String> {}

  static class ConstructorLevelMaybeSeqGrowableList extends AbstractList<String> {
    @MaybeSeqGrowable
    ConstructorLevelMaybeSeqGrowableList() {}

    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  static class MemberAnnotationDoesNotCount extends AbstractList<String> {
    @Modifiable AbstractList<String> field;

    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  // :: warning: [modifiability.annotation.unverified] :: error: [super.invocation]
  static @Modifiable class SuppressedList extends AbstractList<String> {
    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    // no [override.receiver] error here because @Modifiable is an upper bound.
    public boolean add(@IteratorPolyMod SuppressedList this, String e) {
      return super.add(e);
    }
  }

  // :: warning: [modifiability.annotation.unverified] :: error: [super.invocation]
  abstract static @Unmodifiable class SuppressedMap implements Map<String, String> {}
}
