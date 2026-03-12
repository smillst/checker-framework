package org.checkerframework.javacutil;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.Tree;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.lang.model.SourceVersion;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.signature.qual.ClassGetName;

/**
 * This class contains utility methods for reflectively accessing Tree classes and methods that were
 * added after Java 11.
 */
@SuppressWarnings("UnusedMethod") // These methods will be used again to support Java 25 trees.
public class TreeUtilsAfterJava11 {

  /** Don't use. */
  private TreeUtilsAfterJava11() {
    throw new AssertionError("Cannot be instantiated.");
  }

  /** The latest source version supported by this compiler. */
  private static final int sourceVersionNumber =
      Integer.parseInt(SourceVersion.latest().toString().substring("RELEASE_".length()));

  /** Utility methods for accessing {@code CaseTree} methods. */
  public static class CaseUtils {

    /** Don't use. */
    private CaseUtils() {
      throw new AssertionError("Cannot be instantiated.");
    }

    /**
     * Returns true if this is the default case for a switch statement or expression. (Also, returns
     * true if {@code caseTree} is {@code case null, default:}.)
     *
     * @param caseTree a case tree
     * @return true if {@code caseTree} is the default case for a switch statement or expression
     * @deprecated Use {@link TreeUtils#isDefaultCaseTree(CaseTree)}
     */
    @Deprecated(forRemoval = true, since = "2026-03-12")
    public static boolean isDefaultCaseTree(CaseTree caseTree) {
      return TreeUtils.isDefaultCaseTree(caseTree);
    }

    /**
     * Returns the list of labels from a case expression. For {@code default}, this is empty. For
     * {@code case null, default}, the list contains {@code null}. Otherwise, in JDK 11 and earlier,
     * this is a list of a single expression tree. In JDK 12+, the list may have multiple expression
     * trees. In JDK 21+, the list might contain a single pattern tree.
     *
     * @param caseTree the case expression to get the labels from
     * @return the list of case labels in the case
     * @deprecated Use {@link TreeUtils#getLabels(CaseTree)}
     */
    @Deprecated(forRemoval = true, since = "2026-03-12")
    public static List<? extends Tree> getLabels(CaseTree caseTree) {
      return TreeUtils.getLabels(caseTree);
    }
  }

  /**
   * Asserts that the latest source version is at least {@code version}.
   *
   * @param version version to check
   * @throws BugInCF if the latest version is smaller than {@code version}
   */
  private static void assertVersionAtLeast(int version) {
    if (sourceVersionNumber < version) {
      throw new BugInCF(
          "Method call requires at least Java version %s, but the current version is %s",
          version, sourceVersionNumber);
    }
  }

  /**
   * Reflectively invokes {@code method} with {@code receiver}; rethrowing any exceptions as {@code
   * BugInCF} exceptions. If the results is {@code null} a {@code BugInCF} is thrown.
   *
   * @param method a method
   * @param receiver the receiver for the method
   * @return the result of invoking {@code method} on {@code receiver}
   */
  private static Object invokeNonNullResult(Method method, Tree receiver) {
    Object result = invoke(method, receiver);
    if (result != null) {
      return result;
    }
    throw new BugInCF(
        "Expected nonnull result for method invocation: %s for tree: %s",
        method.getName(), receiver);
  }

  /**
   * Reflectively invokes {@code method} with {@code receiver}; rethrowing any exceptions as {@code
   * BugInCF} exceptions.
   *
   * @param method a method
   * @param receiver the receiver for the method
   * @return the result of invoking {@code method} on {@code receiver}
   */
  private static @Nullable Object invoke(Method method, Tree receiver) {
    try {
      return method.invoke(receiver);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new BugInCF(
          e, "Reflection failed for method: %s for tree: %s", method.getName(), receiver);
    }
  }

  /**
   * Returns the {@link Method} object for the method with name {@code name} in class {@code clazz}.
   * Rethrowing any exceptions as {@code BugInCF} exceptions.
   *
   * @param clazz a class
   * @param name a method name
   * @return the {@link Method} object for the method with name {@code name} in class {@code clazz}
   */
  private static Method getMethod(Class<?> clazz, String name) {
    try {
      return clazz.getMethod(name);
    } catch (NoSuchMethodException e) {
      throw new BugInCF("Method %s not found in class %s", name, clazz);
    }
  }

  /**
   * Returns the class named {@code name}. Rethrows any exceptions as {@code BugInCF} exceptions.
   *
   * @param name a class name
   * @return the class named {@code name}
   */
  private static Class<?> classForName(@ClassGetName String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      throw new BugInCF("Class not found " + name);
    }
  }
}
