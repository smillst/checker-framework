// Test case for Issue #8048:
// https://github.com/typetools/checker-framework/issues/8048

// Invoking a generic method whose return type is an array of a parameterized type with an
// `? extends` wildcard, in an assignment context whose target is that same array type, crashed
// inference with a FalseBoundException.  Reduced from Apache Beam's MoreFutures.allAsList.

import java.util.Collection;
import java.util.List;

@SuppressWarnings("all") // Only the crash matters, not the errors issued by any given checker.
public class Issue8048 {

  static <T> List<? extends T>[] noArgs() {
    throw new RuntimeException();
  }

  static <T> List<? extends T>[] fromCollection(Collection<? extends T> c) {
    throw new RuntimeException();
  }

  static <T> List<? extends T>[][] twoDimensional() {
    throw new RuntimeException();
  }

  static <T> void typeVariableTarget(Collection<? extends T> c) {
    List<? extends T>[] a = noArgs();
    List<? extends T>[] b = fromCollection(c);
    List<? extends T>[][] d = twoDimensional();
  }

  static void concreteTarget() {
    List<? extends String>[] a = noArgs();
  }

  // These do not use `? extends`, so they never crashed; they guard against a regression in the
  // other direction.

  static <T> List<? super T>[] superWildcard() {
    throw new RuntimeException();
  }

  static <T> List<?>[] unboundedWildcard() {
    throw new RuntimeException();
  }

  static <T> void otherWildcards() {
    List<? super T>[] a = superWildcard();
    List<?>[] b = unboundedWildcard();
  }
}
