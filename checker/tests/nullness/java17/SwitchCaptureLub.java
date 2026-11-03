// @below-java17-jdk-skip-test
// Test case for https://github.com/typetools/checker-framework/issues/6872

import java.util.Comparator;
import java.util.List;

public class SwitchCaptureLub {
  <T> void switchArms(
      int i,
      Comparator<List<T>> c1,
      Comparator<Object> c2,
      List<? super Comparator<? super List<T>>> sink) {
    sink.add(
        switch (i) {
          case 2 -> c1;
          default -> c2;
        });
  }

  <T> void conditionalArms(
      boolean b,
      Comparator<List<T>> c1,
      Comparator<Object> c2,
      List<? super Comparator<? super List<T>>> sink) {
    sink.add(b ? c1 : c2);
  }

  static class Holder<U> {
    List<? super Comparator<? super List<U>>> sink;

    Holder(List<? super Comparator<? super List<U>>> sink) {
      this.sink = sink;
    }
  }

  <T> void fieldReceiver(
      int i, boolean b, Holder<T> h, Comparator<List<T>> c1, Comparator<Object> c2) {
    h.sink.add(
        switch (i) {
          case 2 -> c1;
          default -> c2;
        });
    h.sink.add(b ? c1 : c2);
  }

  static <U> U id(U u) {
    return u;
  }

  <T> void inferredArgument(
      Comparator<List<T>> c1, List<? super Comparator<? super List<T>>> sink) {
    sink.add(id(c1));
  }
}
