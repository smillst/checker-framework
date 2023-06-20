// Test for Issue 258
// https://github.com/typetools/checker-framework/issues/258

public class GenericsBounds2 {
  @SuppressWarnings("nullness:dereference.of.nullable")
  <I extends Object, C extends I> void method(C arg) {
    arg.toString();
  }
}
