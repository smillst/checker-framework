import java.util.List;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.MaybeModifiable;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.Shrinkable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;

/**
 * Tests @PolyModifiable, which expands to @PolyGrow @PolyShrink @PolyReplace and thus preserves all
 * three capabilities independently.
 */
public class PolyModifiableTest {

  /** A simple polymorphic identity method that preserves all three capabilities. */
  @PolyModifiable List<String> identity(@PolyModifiable List<String> x) {
    return x;
  }

  void testPoly(
      @Modifiable List<String> mod,
      @Growable @Shrinkable List<String> gs, // Grow+Shrink; Replace = MaybeReplace (default)
      @Growable @Replaceable List<String> gr, // Grow+Replace; Shrink = MaybeShrink
      @Shrinkable @Replaceable List<String> sr, // Shrink+Replace; Grow = MaybeGrow
      @Growable List<String> g,
      @Shrinkable List<String> s,
      @Replaceable List<String> r,
      @MaybeModifiable List<String> unknown,
      @Unmodifiable List<String> unmod) {

    // ============================================================
    // Identity on @Modifiable (G+S+R)
    // ============================================================
    @Modifiable List<String> m1 = identity(mod); // OK: poly resolves to G+S+R
    @MaybeModifiable List<String> m2 = identity(mod); // OK: G+S+R <: top in all

    // ============================================================
    // Identity on @Growable @Shrinkable (G+S, R=MaybeReplace)
    // ============================================================
    @Growable @Shrinkable List<String> gs1 = identity(gs); // OK
    // :: error: [assignment]
    @Modifiable List<String> gs2 = identity(gs); // Error: R=Maybe !<: Replaceable
    @Growable List<String> gs3 = identity(gs); // OK: G+S+MaybeR <: G
    @Shrinkable List<String> gs4 = identity(gs); // OK: G+S+MaybeR <: S
    // :: error: [assignment]
    @Replaceable List<String> gs5 = identity(gs); // Error: MaybeR !<: Replaceable

    // ============================================================
    // Identity on @Growable @Replaceable (G+R, S=MaybeShrink)
    // ============================================================
    @Growable @Replaceable List<String> gr1 = identity(gr); // OK
    // :: error: [assignment]
    @Modifiable List<String> gr2 = identity(gr); // Error: S=Maybe !<: Shrinkable
    @Growable List<String> gr3 = identity(gr); // OK
    // :: error: [assignment]
    @Shrinkable List<String> gr4 = identity(gr); // Error: S=Maybe
    @Replaceable List<String> gr5 = identity(gr); // OK

    // ============================================================
    // Identity on @Shrinkable @Replaceable (S+R, G=MaybeGrow)
    // ============================================================
    @Shrinkable @Replaceable List<String> sr1 = identity(sr); // OK
    // :: error: [assignment]
    @Modifiable List<String> sr2 = identity(sr); // Error: G=Maybe !<: Growable
    // :: error: [assignment]
    @Growable List<String> sr3 = identity(sr); // Error: G=Maybe
    @Shrinkable List<String> sr4 = identity(sr); // OK
    @Replaceable List<String> sr5 = identity(sr); // OK

    // ============================================================
    // Identity on @Growable (G only; S=Maybe, R=Maybe)
    // ============================================================
    @Growable List<String> g1 = identity(g); // OK
    // :: error: [assignment]
    @Growable @Shrinkable List<String> g2 = identity(g); // Error: S=Maybe
    // :: error: [assignment]
    @Modifiable List<String> g3 = identity(g); // Error: S=Maybe, R=Maybe

    // ============================================================
    // Identity on @Shrinkable (S only; G=Maybe, R=Maybe)
    // ============================================================
    @Shrinkable List<String> s1 = identity(s); // OK
    // :: error: [assignment]
    @Growable @Shrinkable List<String> s2 = identity(s); // Error: G=Maybe
    // :: error: [assignment]
    @Modifiable List<String> s3 = identity(s); // Error

    // ============================================================
    // Identity on @Replaceable (R only; G=Maybe, S=Maybe)
    // ============================================================
    @Replaceable List<String> r1 = identity(r); // OK
    // :: error: [assignment]
    @Growable @Replaceable List<String> r2 = identity(r); // Error: G=Maybe
    // :: error: [assignment]
    @Modifiable List<String> r3 = identity(r); // Error

    // ============================================================
    // Identity on @MaybeModifiable (all tops)
    // ============================================================
    @MaybeModifiable List<String> u1 = identity(unknown); // OK
    // :: error: [assignment]
    @Modifiable List<String> u2 = identity(unknown); // Error
    // :: error: [assignment]
    @Growable List<String> u3 = identity(unknown); // Error

    // ============================================================
    // Identity on @Unmodifiable (Ungrowable+Unshrinkable+Unreplaceable)
    // ============================================================
    @Unmodifiable List<String> unmod1 = identity(unmod); // OK
    @MaybeModifiable List<String> unmod2 = identity(unmod); // OK
    // :: error: [assignment]
    @Modifiable List<String> unmod3 = identity(unmod); // Error
  }
}
