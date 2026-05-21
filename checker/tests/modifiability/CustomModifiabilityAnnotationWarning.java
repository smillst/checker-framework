import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;

public class CustomModifiabilityAnnotationWarning {

  // :: warning: [modifiability.annotation.unverified]
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

  // :: warning: [modifiability.annotation.unverified]
  abstract static class DirectCollection implements Collection<String> {}

  // :: warning: [modifiability.annotation.unverified]
  abstract static class DirectMap implements Map<String, String> {}

  // :: warning: [modifiability.annotation.unverified]
  abstract static class DirectIterator implements Iterator<String> {}

  // :: warning: [modifiability.annotation.unverified]
  abstract static class DirectListIterator implements ListIterator<String> {}

  static class NonModifiabilityType {}

  @SuppressWarnings("modifiability:annotation.unverified")
  static class SuppressedList extends AbstractList<String> {
    @Override
    public String get(int index) {
      return "value";
    }

    @Override
    public int size() {
      return 1;
    }
  }

  @SuppressWarnings("modifiability:annotation.unverified")
  abstract static class SuppressedMap implements Map<String, String> {}

  @SuppressWarnings("modifiability:annotation.unverified")
  abstract static class SuppressedIterator implements Iterator<String> {}
}
