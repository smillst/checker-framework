import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.MaybeGrow;
import org.checkerframework.checker.modifiability.qual.MaybeReplace;
import org.checkerframework.checker.modifiability.qual.MaybeShrink;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.Replaceable;
import org.checkerframework.checker.modifiability.qual.Shrinkable;

/**
 * Tests structural capability removal for Set, Queue, Map.Entry, and Iterator.
 *
 * <p>Convenience aliases on Set / Queue lose the Replace capability (set Replace to @MaybeReplace).
 * Convenience aliases on Map.Entry lose Grow and Shrink capabilities. Convenience aliases on
 * Iterator lose Grow and Replace capabilities. Explicit capability annotations are preserved.
 */
class SetTest {

  // ==========================================================
  // Set: explicit Replace capability is preserved.
  // ==========================================================

  void testSetReplaceRemoved(
      @Replaceable Set<String> replaceable,
      @Growable @Replaceable Set<String> growReplace,
      @Shrinkable @Replaceable Set<String> shrinkReplace,
      @Modifiable Set<String> modifiable) {

    @Growable Set<String> g1 = growReplace; // OK: Grow still intact
    @Replaceable Set<String> r1 = growReplace;

    @Growable @Shrinkable Set<String> gs1 = modifiable; // OK
    // :: error: [assignment]
    @Replaceable Set<String> r2 = modifiable; // maybeReplace cannot assign to replaceable.
  }

  void testSetGrowShrinkPreserved(
      @MaybeGrow @MaybeShrink @MaybeReplace Set<String> unknown,
      @Growable Set<String> growable,
      @Shrinkable Set<String> shrinkable,
      @Growable @Shrinkable Set<String> gs) {

    // Top in all hierarchies: accepts everything
    @MaybeGrow @MaybeShrink @MaybeReplace Set<String> u1 = growable;
    @MaybeGrow @MaybeShrink @MaybeReplace Set<String> u2 = shrinkable;
    @MaybeGrow @MaybeShrink @MaybeReplace Set<String> u3 = gs;

    // @Growable Set: Grow=G, Shrink=Maybe, Replace=Maybe
    @Growable Set<String> g1 = gs; // OK: G+S <: G
    // :: error: [assignment]
    @Growable Set<String> g2 = shrinkable; // Error: S only, no G

    // @Shrinkable Set: Grow=Maybe, Shrink=S, Replace=Maybe
    @Shrinkable Set<String> s1 = gs; // OK: G+S <: S
    // :: error: [assignment]
    @Shrinkable Set<String> s2 = growable; // Error: G only, no S
  }

  void testQueueLikeSet(
      @Modifiable Queue<String> modifiable,
      @Growable @Shrinkable @Replaceable Queue<String> explicit) {
    // @Modifiable Queue loses only its Replace capability.
    @Growable @Shrinkable Queue<String> q1 = modifiable;
    // :: error: [assignment]
    @Growable @Shrinkable @Replaceable Queue<String> q2 = modifiable;
    @Growable @Shrinkable @Replaceable Queue<String> q3 = explicit;
  }

  // ==========================================================
  // Map.Entry: Grow and Shrink capabilities are both removed.
  // Effective types only retain the Replace hierarchy annotation.
  // ==========================================================

  void testEntryAssignment(
      // No Replace bit: effective Replace = @MaybeReplace after G+S removal
      Map.@MaybeGrow @MaybeShrink @MaybeReplace Entry<String, String> unknown,
      Map.@Growable Entry<String, String> growable,
      Map.@Shrinkable Entry<String, String> shrinkable,
      Map.@Growable @Shrinkable Entry<String, String> gs,
      // Has Replace bit: retains @Replaceable after G+S removal
      Map.@Replaceable Entry<String, String> replaceable,
      Map.@Growable @Replaceable Entry<String, String> growReplace,
      Map.@Shrinkable @Replaceable Entry<String, String> shrinkReplace,
      Map.@Modifiable Entry<String, String> modifiable,
      Map.@Growable @Shrinkable @Replaceable Entry<String, String> explicit) {

    // Group 1: entries without Replace bit become @MaybeGrow @MaybeShrink @MaybeReplace
    // (G and S are removed → both become Maybe; R was already Maybe)
    Map.@MaybeGrow @MaybeShrink @MaybeReplace Entry<String, String> u1 = growable;
    Map.@MaybeGrow @MaybeShrink @MaybeReplace Entry<String, String> u2 = shrinkable;
    Map.@MaybeGrow @MaybeShrink @MaybeReplace Entry<String, String> u3 = gs;
    // Explicit Grow/Shrink annotations are preserved.
    // :: error: [assignment]
    Map.@Growable Entry<String, String> g1 = unknown;
    // :: error: [assignment]
    Map.@Shrinkable Entry<String, String> s1 = unknown;
    Map.@Growable Entry<String, String> g2 = gs; // OK

    Map.@MaybeGrow @MaybeShrink @Replaceable Entry<String, String> r1 = growReplace;
    Map.@MaybeGrow @MaybeShrink @Replaceable Entry<String, String> r2 = shrinkReplace;
    Map.@MaybeGrow @MaybeShrink @Replaceable Entry<String, String> r3 = modifiable;
    // Explicit Grow/Shrink annotations are preserved on Map.Entry.
    // :: error: [assignment]
    Map.@Growable @Replaceable Entry<String, String> gr1 = replaceable; // OK: same effective type
    // :: error: [assignment]
    Map.@Shrinkable @Replaceable Entry<String, String> sr1 = replaceable;
    Map.@Modifiable Entry<String, String> m1 = replaceable;
    // :: error: [assignment]
    Map.@Shrinkable @Replaceable Entry<String, String> sr2 = growReplace;
    Map.@Replaceable Entry<String, String> gr2 = modifiable;
    Map.@Modifiable Entry<String, String> m2 = explicit;

    // Cross-group: Replace !<: MaybeReplace (wrong direction) is NOT an error —
    // but MaybeReplace !<: Replaceable IS an error:
    // :: error: [assignment]
    Map.@Replaceable Entry<String, String> bad1 = growable; // Error: R=Maybe !<: Replaceable
  }

  // ==========================================================
  // Iterator: Grow and Replace capabilities are both removed.
  // Effective types only retain the Shrink hierarchy annotation.
  // ==========================================================

  void testIteratorAssignment(
      // No Shrink bit: effective after G+R removal → @MaybeGrow @MaybeShrink @MaybeReplace
      @MaybeGrow @MaybeShrink @MaybeReplace Iterator<String> unknown,
      @Growable Iterator<String> growable,
      @Replaceable Iterator<String> replaceable,
      @Growable @Replaceable Iterator<String> growReplace,
      // Has Shrink bit: effective after G+R removal → @MaybeGrow @Shrinkable @MaybeReplace
      @Shrinkable Iterator<String> shrinkable,
      @Growable @Shrinkable Iterator<String> growShrink,
      @Shrinkable @Replaceable Iterator<String> shrinkReplace,
      @Modifiable Iterator<String> modifiable,
      @Growable @Shrinkable @Replaceable Iterator<String> explicit) {

    // Group 1: No Shrink bit → effective @MaybeGrow @MaybeShrink @MaybeReplace
    @MaybeGrow @MaybeShrink @MaybeReplace Iterator<String> u1 = growable;
    @MaybeGrow @MaybeShrink @MaybeReplace Iterator<String> u2 = replaceable;
    @MaybeGrow @MaybeShrink @MaybeReplace Iterator<String> u3 = growReplace;
    // Explicit Grow/Replace annotations are preserved.
    // :: error: [assignment]
    @Growable Iterator<String> g1 = unknown;
    @Replaceable Iterator<String> r1 = growReplace;
    // :: error: [assignment]
    @Growable @Replaceable Iterator<String> gr1 = replaceable;

    // Group 2: Has Shrink bit → effective @MaybeGrow @Shrinkable @MaybeReplace
    @MaybeGrow @Shrinkable @MaybeReplace Iterator<String> s1 = shrinkable;
    @MaybeGrow @Shrinkable @MaybeReplace Iterator<String> s2 = growShrink;
    @MaybeGrow @Shrinkable @MaybeReplace Iterator<String> s3 = shrinkReplace;
    @MaybeGrow @Shrinkable @MaybeReplace Iterator<String> s4 = modifiable;
    // Cross-assignments within group 2:
    @Shrinkable Iterator<String> sh1 = modifiable;
    // :: error: [assignment]
    @Growable @Shrinkable Iterator<String> gs1 = modifiable;
    // :: error: [assignment]
    @Shrinkable @Replaceable Iterator<String> sr1 = modifiable;
    @Modifiable Iterator<String> m1 = explicit;
    // :: error: [assignment]
    @Shrinkable Iterator<String> bad1 = growable; // Error: Shrink=Maybe !<: Shrinkable
  }
}
