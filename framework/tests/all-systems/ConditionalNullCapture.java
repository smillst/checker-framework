import java.util.List;

public class ConditionalNullCapture {
  <T> void nullArm(boolean b, List<? super List<T>> l) {
    var x = b ? null : l.get(0);
  }

  <T> void noNullArm(boolean b, List<? super List<T>> l) {
    var x = b ? l.get(0) : l.get(0);
  }

  static class Holder<U> {
    List<? super List<U>> l;

    Holder(List<? super List<U>> l) {
      this.l = l;
    }

    List<? super List<U>> getL() {
      return l;
    }
  }

  static class Pair<A, B> {
    B getSecond() {
      throw new Error();
    }
  }

  <T> void fieldReceiver(boolean b, Holder<T> h) {
    var x = b ? null : h.l.get(0);
  }

  <T> void callReceiver(boolean b, Holder<T> h) {
    var x = b ? null : h.getL().get(0);
  }

  <T> void arrayReceiver(boolean b, List<? super List<T>>[] a) {
    var x = b ? null : a[0].get(0);
  }

  @SuppressWarnings("unchecked")
  <T> void castReceiver(boolean b, Object o) {
    var x = b ? null : ((List<? super List<T>>) o).get(0);
  }

  // The method's type mentions only the capture of the second wildcard.
  <T> void partlyCapturedReceiver(boolean b, Pair<?, ? super List<T>> p) {
    var x = b ? null : p.getSecond();
  }
}
