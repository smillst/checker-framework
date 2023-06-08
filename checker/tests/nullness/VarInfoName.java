import org.checkerframework.checker.nullness.qual.NonNull;

public abstract class VarInfoName {

  public abstract <T extends @NonNull Object> T accept(Visitor<T> v);

  public abstract static class Visitor<T extends @NonNull Object> {}

  public abstract static class BooleanAndVisitor extends Visitor<Boolean> {
    private boolean result;

    public BooleanAndVisitor(VarInfoName name) {
      // :: error: (argument) :: warning: (nulltest.redundant)
      result = (name.accept(this) != null);
    }
  }
}
