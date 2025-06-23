import java.net.Socket;
import java.util.Collection;
import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.collectionownership.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class LoopIfElseMergeTest {

  /*
   * The loop is supposed to remove the obligation of the @OwningCollection parameter.
   *
   * The problem is that in the cfg, the else branch goes back directly to the loop condition.
   * The two branches are not merged and the nullness analysis doesn’t work its magic on
   * determining that the type of r is @CalledMethods(“close”). Which means that we currently
   * cannot certify this loop to call “close()” on the elements of collection. If there are
   * more statements after the if-statement, it works as expected, because then the two branches
   * are merged before going to the next statement. The easiest solution seems to insert an
   * additional merge cfg block to merge the two branches.
   */
  void nullableElementWithCheckFailing(@OwningCollection Collection<Socket> resources) {
    for (Socket r : resources) {
      if (r != null) {
        try {
          r.close();
        } catch (Exception e) {
        }
      }
    }
  }

  /*
   * This test shows that adding anything after the if merges the stores and the loop
   * analysis works.
   */
  void nullableElementWithCheck(@OwningCollection Collection<Socket> resources) {
    for (Socket r : resources) {
      if (r != null) {
        try {
          r.close();
        } catch (Exception e) {
        }
      }
      System.out.println("");
    }
  }
}
