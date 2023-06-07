/*
 * @test
 * @summary Test for Issue #1055
 * @library .
 * @compile NonN.java
 * @compile -XDrawDiagnostics -processor org.checkerframework.checker.nullness.NullnessChecker -Astubs=NonN.astub Wildcards.java  -Werror
 */

public class Wildcards {
  NonN<?> f = new NonN<Object>();

  class LocalNonN<T extends @NonNull Object> {}

  LocalNonN<?> g = new LocalNonN<Object>();
}
