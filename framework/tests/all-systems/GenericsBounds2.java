// Test for Issue 258
// https://github.com/typetools/checker-framework/issues/258
public class GenericsBounds2 {
  <I extends @NonNull Object, C extends I> void method(C arg) {
    arg.toString();
  }
}
