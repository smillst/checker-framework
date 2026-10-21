// @below-java17-jdk-skip-test
// Test case for https://github.com/typetools/checker-framework/issues/6872

import java.util.Comparator;
import java.util.List;
import org.checkerframework.checker.tainting.qual.Tainted;
import org.checkerframework.checker.tainting.qual.Untainted;

public class SwitchCaptureLub {
  <T> void switchArms(
      int i,
      Comparator<List<T>> c1,
      Comparator<Object> c2,
      List<? super Comparator<? super List<T>>> sink) {
    // The Tainting Checker defaults lower bounds to @Untainted, so sink accepts only an
    // @Untainted Comparator.
    // :: error: (argument)
    sink.add(c1);
    sink.add(
        switch (i) {
          // :: error: (argument)
          case 2 -> c1;
          // :: error: (argument)
          default -> c2;
        });
  }

  <T> void conditionalArms(
      boolean b,
      Comparator<List<T>> c1,
      Comparator<Object> c2,
      List<? super Comparator<? super List<T>>> sink) {
    // :: error: (argument)
    sink.add(b ? c1 : c2);
  }

  // The arms differ from the lower bound of sink's capture only in a nested type argument.

  void nestedSwitchArms(
      int i,
      @Untainted Comparator<List<@Tainted String>> bad,
      @Untainted Comparator<List<@Untainted String>> good,
      List<? super @Untainted Comparator<? super List<@Untainted String>>> sink) {
    // :: error: (argument)
    sink.add(bad);
    sink.add(good);
    sink.add(
        switch (i) {
          // :: error: (argument)
          case 2 -> bad;
          default -> good;
        });
    sink.add(
        switch (i) {
          case 2 -> good;
          default -> good;
        });
  }

  void nestedConditionalArms(
      boolean b,
      @Untainted Comparator<List<@Tainted String>> bad,
      @Untainted Comparator<List<@Untainted String>> good,
      List<? super @Untainted Comparator<? super List<@Untainted String>>> sink) {
    // :: error: (argument)
    sink.add(b ? bad : good);
    // :: error: (argument)
    sink.add(b ? good : (bad));
    sink.add(b ? good : good);
  }
}
