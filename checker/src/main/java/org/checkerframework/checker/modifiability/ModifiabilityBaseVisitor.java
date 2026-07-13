package org.checkerframework.checker.modifiability;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 * Base visitor for the modifiability sub-checkers (Grow, SeqGrow, Shrink, Replace).
 *
 * <p>This class contains logic shared across all four sub-checkers:
 *
 * <ul>
 *   <li>Suppressing the "constructor result must be TOP" check, since collection constructors may
 *       legitimately produce {@code @Modifiable}.
 * </ul>
 */
public class ModifiabilityBaseVisitor
    extends BaseTypeVisitor<ModifiabilityBaseAnnotatedTypeFactory> {

  // /** Package that contains the modifiability type-use qualifiers. */
  // private static final String MODIFIABILITY_QUAL_PACKAGE =
  //     "org.checkerframework.checker.modifiability.qual";

  /** Classes for which an error has been issued, to avoid issuing multiple errors. */
  private static final Set<@FullyQualifiedName String> classWarned = new HashSet<>();

  /**
   * Create a ModifiabilityBaseVisitor.
   *
   * @param checker the checker that uses this visitor
   */
  public ModifiabilityBaseVisitor(BaseTypeChecker checker) {
    super(checker);
  }

  @Override
  protected void checkThisOrSuperConstructorCall(
      MethodInvocationTree call, @CompilerMessageKey String errorKey) {
    // Nothing to do; it is handled by `processClassTree()`.
  }

  @Override
  public void processClassTree(ClassTree tree) {
    super.processClassTree(tree);
    processClassConstructors(tree);
  }

  private void processClassConstructors(ClassTree tree) {
    TypeMirror classTM = TreeUtils.elementFromDeclaration(tree).asType();
    if (!atypeFactory.isRelevant(classTM)) {
      return;
    }

    // It seems difficult to go from an ExecutableElement to the annotations.
    // List<ExecutableElement> constructors =
    // ElementFilter.constructorsIn(elem.getEnclosedElements());

    List<MethodTree> methods = new ArrayList<>();
    List<MethodTree> constructors = new ArrayList<>();
    for (Tree member : tree.getMembers()) {
      if (member instanceof MethodTree mt) {
        if (TreeUtils.isConstructor(mt)) {
          constructors.add(mt);
        } else {
          methods.add(mt);
        }
      }
    }

    @FullyQualifiedName String className = classTM.toString();
    boolean thisClassWarned = false;

    AnnotationMirror constructorAnno = null;
    for (MethodTree constructor : constructors) {
      AnnotatedExecutableType constructorType = atypeFactory.getAnnotatedType(constructor);
      AnnotatedTypeMirror returnType = constructorType.getReturnType();
      AnnotationMirror thisResultAnno =
          returnType.getPrimaryAnnotationInHierarchy(atypeFactory.topAnnotation);
      if (constructorAnno == null) {
        constructorAnno = thisResultAnno;
      } else if (!AnnotationUtils.areSameByName(thisResultAnno, constructorAnno)) {
        checker.reportError(
            constructor, "inconsistent.constructor.result.type", thisResultAnno, constructorAnno);
        classWarned.add(className);
        thisClassWarned = true;
      }
    }
    if (constructorAnno == null) {
      return;
    }

    if (thisClassWarned) {
      return;
    }

    // There is at least one constructor, and all constructors have the same result type annotation.
    // Examine:
    //  * the implementation of each method
    //  * the annotation of each inherited method, only if the superclass constructors have
    //    different type than this class's constructors.

    for (MethodTree method : methods) {
      AnnotatedDeclaredType receiverType = atypeFactory.getAnnotatedType(method).getReceiverType();
      if (receiverType != null) {
        AnnotationMirror receiverAnno = receiverType.getAnnotation();
        checkImplOK(method, receiverAnno, constructorAnno, className);
      }
    }

    // TODO: check for overridden methods,
  }

  /**
   * Issues an error if the method body does not conform to the given annotation.
   *
   * @param method a method declaration
   * @param constructorAnno the annotation on the class constructors
   * @param className the name of the enclosing class, used only for diagnostic messages
   */
  private void checkImplOK(
      MethodTree method,
      AnnotationMirror receiverAnno,
      AnnotationMirror constructorAnno,
      String className) {
    if (AnnotationUtils.areSameByName(receiverAnno, atypeFactory.topAnnotation)) {
      return;
    }

    String receiverAnnoName =
        receiverAnno.getAnnotationType().asElement().getSimpleName().toString();

    if (receiverAnnoName.startsWith("Bottom")) {
      checker.reportError(method, "bottom.annotation.on.receiver");
      classWarned.add(className);
    } else if (receiverAnnoName.startsWith("Un")) {
      // Nothing to check.
    } else if (receiverAnnoName.startsWith("Poly")) {
      // Nothing to check?
    } else {
      // The receiver annotation is positive (does not start with "Un").
      // Behavior now depends on the constructor annotation.
      String constructorAnnoName =
          constructorAnno.getAnnotationType().asElement().getSimpleName().toString();
      if (constructorAnnoName.startsWith("Un")) {
        if (!implIsUOE(method)) {
          checker.reportError(method, "method.implementation.not.uoe", constructorAnnoName);
          classWarned.add(className);
        }
      } else {
        if (implIsUOE(method)) {
          checker.reportError(method, "method.implementation.is.uoe", constructorAnnoName);
          classWarned.add(className);
        }
      }
    }
  }

  /**
   * Returns true if the method body is exactly {@code throws new
   * UnsupportedOperationException(...)}.
   *
   * @param method a method declaration
   * @return true if the method body is exactly {@code throws new
   *     UnsupportedOperationException(...)}
   */
  private boolean implIsUOE(MethodTree method) {
    BlockTree body = method.getBody();
    List<? extends StatementTree> statements = body.getStatements();
    if (statements.size() != 1) {
      return false;
    }
    StatementTree statement = statements.get(0);
    if (!(statement instanceof ThrowTree tt)) {
      return false;
    }
    ExpressionTree exception = tt.getExpression();
    if (!(exception instanceof NewClassTree nct)) {
      return false;
    }
    ExpressionTree identifier = nct.getIdentifier();
    if (identifier instanceof IdentifierTree it) {
      // TODO: This can be fooled if a different UnsupportedOperationException is imported.
      // You can check the type of exception:
      // types.isSameType(TreeUtils.typeOf(exception), ...);
      return it.getName().contentEquals("UnsupportedOperationException");
    } else if (identifier instanceof MemberSelectTree mst) {
      // TODO: For efficiency, to avoid call to `toString()`, could walk down the MemberSelectTree.
      return mst.toString().equals("java.lang.UnsupportedOperationException");
    }
    return false;
  }

  /**
   * Returns the positive qualifier for this checker's modifiability hierarchy, such as
   * {@code @Growable}, {@code @SeqGrowable}, {@code @Shrinkable}, or {@code @Replaceable}.
   *
   * @return this checker's positive capability qualifier
   */
  private AnnotationMirror positiveCapability() {
    return atypeFactory.positiveCapability();
  }

  /**
   * Checks the normal override rules, then requires overrides to preserve any positive
   * modifiability receiver capability from the overridden method.
   *
   * <p>For example, if the overridden method requires a {@code @Growable} receiver, then the
   * overriding method must also require a {@code @Growable} receiver.
   *
   * <p>The framework's ordinary receiver override rule allows an overriding method to relax
   * receiver preconditions. For modifiability operations, that would allow a subtype method to drop
   * a required {@code @Growable}, {@code @Shrinkable}, or {@code @Replaceable} receiver capability.
   *
   * <p>For example:
   *
   * <pre>{@code
   * class List {
   *   void add(@Growable List<String> list) {
   *     // ...
   *   }
   * }
   *
   * class A extends List {
   *   @Override
   *   void add() {
   *     throw new UnsupportedOperationException();
   *   }
   * }
   * }</pre>
   *
   * <p>Without requiring the override to preserve the {@code @Growable} receiver, {@code A.add()}
   * would be permitted even when {@code A} is only {@code @MaybeMod}.
   */
  @Override
  protected boolean checkOverride(
      MethodTree overriderTree,
      AnnotatedExecutableType overriderMethodType,
      AnnotatedDeclaredType overriderType,
      AnnotatedExecutableType overriddenMethodType,
      AnnotatedDeclaredType overriddenType) {
    if (!super.checkOverride(
        overriderTree, overriderMethodType, overriderType, overriddenMethodType, overriddenType)) {
      return false;
    }
    // Only capability checkers need to preserve receiver capabilities in overrides.
    // @IteratorPolyMod does not follow this special override rule.
    if (!shouldCheckReceiverOverrideCapabilityPreservation()) {
      return true;
    }

    AnnotatedDeclaredType overriderReceiver = overriderMethodType.getReceiverType();
    AnnotatedDeclaredType overriddenReceiver = overriddenMethodType.getReceiverType();
    if (overriderReceiver == null || overriddenReceiver == null) {
      return true;
    }

    AnnotationMirror positiveCapability = positiveCapability();

    if (overriddenReceiver.hasPrimaryAnnotation(positiveCapability)
        && !overriderReceiver.hasPrimaryAnnotation(positiveCapability)) {
      checker.reportError(
          overriderTree,
          "override.receiver",
          overriderReceiver,
          overriddenReceiver,
          overriderType,
          overriderMethodType,
          overriddenType,
          overriddenMethodType);
      return false;
    }
    return true;
  }

  /**
   * Returns true if overrides should preserve positive receiver capabilities from overridden
   * methods.
   *
   * @return true if overrides should preserve positive receiver capabilities
   */
  protected boolean shouldCheckReceiverOverrideCapabilityPreservation() {
    return true;
  }

  // /**
  //  * Returns true if {@code annotation} is a non-maybe modifiability qualifier that (if written
  // on a
  //  * constructor result type) should trigger a warning about an unverified collection class
  //  * implementation.
  //  *
  //  * @param annotation an annotation mirror written on a constructor result type
  //  * @return true if {@code annotation} should trigger a warning
  //  */
  // private boolean isNonTopModifiabilityAnnotation(AnnotationMirror annotation) {
  //   Element element = annotation.getAnnotationType().asElement();
  //   if (!(element instanceof TypeElement typeElement)) {
  //     return false;
  //   }
  //
  //   // Only annotations in the modifiability qualifier package are relevant.
  //   String qualifiedName = typeElement.getQualifiedName().toString();
  //   if (!qualifiedName.startsWith(MODIFIABILITY_QUAL_PACKAGE + ".")) {
  //     return false;
  //   }
  //
  //   // @Maybe* annotations and @UnmodifiableParam are the top in the hierarchy, so do not warn.
  //   String simpleName = typeElement.getSimpleName().toString();
  //   boolean isTopQualifier =
  //       simpleName.startsWith("Maybe") || simpleName.equals("UnmodifiableParam");
  //   return !isTopQualifier;
  // }

  // Suppresses the framework's "constructor result must be TOP" check.
  // Collection constructors (e.g., new ArrayList()) legitimately produce @Modifiable, which is a
  // subtype of the top type @MaybeModifiable. This suppression is sound: constructors are
  // typed by their stubs or by defaults inferred from the class's annotations, so they cannot
  // silently widen an unmodifiable collection to @Modifiable.
  @Override
  protected void checkConstructorResult(
      AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {}
}
