import org.checkerframework.checker.nullness.qual.*;

class APair<S, T> {
  static <U, V> APair<U, V> of(U p1, V p2) {
    return new APair<U, V>();
  }

  static <U, V> APair<U, V> of2(U p1, V p2) {
    return new APair<>();
  }
}

class PairSub<SS, TS> extends APair<SS, TS> {
  static <US, VS> PairSub<US, VS> of(US p1, VS p2) {
    return new PairSub<US, VS>();
  }
}

class PairSubSwitching<SS, TS> extends APair<TS, SS> {
  static <US, VS> PairSubSwitching<US, VS> ofPSS(US p1, VS p2) {
    return new PairSubSwitching<US, VS>();
  }
}

class Test1<X> {
  APair<@Nullable X, @Nullable X> test1(@Nullable X p) {
    return APair.<@Nullable X, @Nullable X>of(p, (X) null);
  }
}

class Test2<X> {
  APair<@Nullable X, @Nullable X> test1(@Nullable X p) {
    return APair.of(p, (@Nullable X) null);
  }
  /*
  APair<@Nullable X, @Nullable X> test2(@Nullable X p) {
      // TODO cast: should this X mean the same as above??
      return APair.of(p, (X) null);
  }
  */
}

class Test3<X> {
  APair<@NonNull X, @NonNull X> test1(@Nullable X p) {
    // :: error: (return)
    return APair.of(p, (X) null);
  }
}

class Test4 {
  APair<@Nullable String, Integer> psi = PairSub.of("Hi", 42);
  APair<@Nullable String, Integer> psi2 = PairSub.of(null, 42);
  // :: error: (assignment)
  APair<String, Integer> psi3 = PairSub.of(null, 42);

  APair<@Nullable String, Integer> psisw = PairSubSwitching.ofPSS(42, null);
  // :: error: (assignment)
  APair<String, Integer> psisw2 = PairSubSwitching.ofPSS(42, null);
}
