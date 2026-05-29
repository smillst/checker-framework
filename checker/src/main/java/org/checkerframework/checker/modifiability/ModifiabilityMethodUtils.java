package org.checkerframework.checker.modifiability;

import com.sun.source.tree.MethodInvocationTree;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.checkerframework.javacutil.TreeUtils;

/** Utility methods for Modifiability checker method-specific behavior. */
public final class ModifiabilityMethodUtils {

  /** The fully-qualified class name of plume-util's collections utilities. */
  private static final String COLLECTIONS_PLUME = "org.plumelib.util.CollectionsPlume";

  /** The method name in CollectionsPlume for removing duplicates from a collection. */
  private static final String WITHOUT_DUPLICATES = "withoutDuplicates";

  /** This is a utility class. */
  private ModifiabilityMethodUtils() {
    throw new Error("Do not instantiate");
  }

  /**
   * Returns true if {@code tree} is a call to {@code
   * org.plumelib.util.CollectionsPlume.withoutDuplicates(Collection)}.
   *
   * @param tree a method invocation tree
   * @return true iff {@code tree} invokes {@code CollectionsPlume.withoutDuplicates(Collection)}
   */
  public static boolean isCollectionsPlumeWithoutDuplicates(MethodInvocationTree tree) {
    ExecutableElement invokedMethod = TreeUtils.elementFromUse(tree);
    if (invokedMethod != null
        && invokedMethod.getSimpleName().contentEquals(WITHOUT_DUPLICATES)
        && invokedMethod.getParameters().size() == 1) {
      Element enclosingElement = invokedMethod.getEnclosingElement();
      if (enclosingElement instanceof TypeElement enclosingType) {
        return enclosingType.getQualifiedName().contentEquals(COLLECTIONS_PLUME);
      }
    }
    return false;
  }
}
