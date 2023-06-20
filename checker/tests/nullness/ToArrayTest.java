import java.util.Collection;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class ToArrayTest {

  public static void isReverse1(@NonNull Collection<@NonNull ?> seq1) {
    // :: error: (assignment)
    Object[] seq1_array_TMP2 = seq1.toArray();
    // :: error: (assignment)
    Object[] seq1_array = seq1.toArray(new Object[] {});
  }

  public static void isReverse1b(@NonNull Collection<? extends @NonNull Object> seq1) {
    Object[] seq1_array_TMP2 = seq1.toArray();
    Object[] seq1_array = seq1.toArray(new Object[] {});
  }

  public static void isReverse2(@NonNull Collection<@NonNull ?> seq1) {
    @NonNull Object @NonNull [] seq1_array_TMP = new Object[] {};
    // :: error: (assignment)
    Object[] seq1_array = seq1.toArray(new Object[] {});
  }

  public static void isReverse3(@NonNull Collection<@NonNull ?> seq1) {
    @NonNull Object @NonNull [] seq1_array_TMP = new Object[] {};
    Object[] seq1_array = seq1.toArray(new Object[] {});
  }
}
