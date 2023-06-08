import java.lang.annotation.ElementType;
import org.checkerframework.checker.nullness.qual.NonNull;

class MyEnumSet<E extends Enum<E>> {}

public class Enums {
  public enum VarFlags {
    IS_PARAM,
    NO_DUPS
  };

  public MyEnumSet<VarFlags> var_flags = new MyEnumSet<>();

  VarFlags f1 = VarFlags.IS_PARAM;

  void foo1(MyEnumSet<VarFlags> p) {}

  void foo2(MyEnumSet<ElementType> p) {}

  <E extends Enum<E>> void mtv(Class<E> p) {}

  <T> T checkNotNull(T ref) {
    return ref;
  }

  <T extends @NonNull Object, S extends @NonNull Object> T checkNotNull2(T ref, S ref2) {
    return ref;
  }

  class Test<T extends Enum<T>> {
    void m(Class<T> p) {
      checkNotNull(p);
    }

    public <SSS extends @NonNull Object> SSS firstNonNull(SSS first, SSS second) {
      @SuppressWarnings("nullness:nulltest.redundant")
      SSS res = first != null ? first : checkNotNull(second);
      return res;
    }
  }

  class Unbound<X extends @NonNull Object> {}

  class Test2<T extends Unbound<S>, S extends Unbound<T>> {
    void m(Class<T> p, Class<S> q) {
      checkNotNull2(p, q);
    }
  }
}
