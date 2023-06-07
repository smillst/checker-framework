import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;

public class EisopIssue270 {
  // In annotated jdk, the package-info of java.util defines KeyForBottom as the
  // default qualifier for lower bound.
  void foo(Set<Object> so, Set<@NonNull ?> seo) {
    // No errors if package-info is loaded correctly.
    so.retainAll(seo);
  }
}
