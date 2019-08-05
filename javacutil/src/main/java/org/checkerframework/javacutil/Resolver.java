package org.checkerframework.javacutil;

import com.sun.source.tree.Scope;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Kinds.KindSelector;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

/** A Utility class to find symbols corresponding to string references. */
public class Resolver {
    private final Resolve resolve;
    private final Names names;
    private final Trees trees;
    private final Log log;

    private static final Method FIND_IDENT;
    private static final Method FIND_IDENT_IN_TYPE;
    private static final Method FIND_IDENT_IN_PACKAGE;
    private static final Method FIND_TYPE;

    private static final Class<?> ACCESSERROR;
    // Note that currently access(...) is defined in InvalidSymbolError, a superclass of AccessError
    private static final Method ACCESSERROR_ACCESS;

    static {
        try {

            FIND_IDENT =
                    Resolve.class.getDeclaredMethod(
                            "findIdent", Env.class, Name.class, KindSelector.class);
            FIND_IDENT.setAccessible(true);

            FIND_IDENT_IN_TYPE =
                    Resolve.class.getDeclaredMethod(
                            "findIdentInType",
                            Env.class,
                            Type.class,
                            Name.class,
                            KindSelector.class);
            FIND_IDENT_IN_TYPE.setAccessible(true);

            FIND_IDENT_IN_PACKAGE =
                    Resolve.class.getDeclaredMethod(
                            "findIdentInPackage",
                            Env.class,
                            TypeSymbol.class,
                            Name.class,
                            KindSelector.class);
            FIND_IDENT_IN_PACKAGE.setAccessible(true);

            FIND_TYPE = Resolve.class.getDeclaredMethod("findType", Env.class, Name.class);
            FIND_TYPE.setAccessible(true);
        } catch (Exception e) {
            Error err =
                    new AssertionError(
                            "Compiler 'Resolve' class doesn't contain required 'find' method");
            err.initCause(e);
            throw err;
        }

        try {
            ACCESSERROR = Class.forName("com.sun.tools.javac.comp.Resolve$AccessError");
            ACCESSERROR_ACCESS = ACCESSERROR.getMethod("access", Name.class, TypeSymbol.class);
            ACCESSERROR_ACCESS.setAccessible(true);
        } catch (ClassNotFoundException e) {
            throw new BugInCF("Compiler 'Resolve$AccessError' class could not be retrieved.", e);
        } catch (NoSuchMethodException e) {
            throw new BugInCF(
                    "Compiler 'Resolve$AccessError' class doesn't contain required 'access' method",
                    e);
        }
    }

    Types types;

    public Resolver(ProcessingEnvironment env) {
        Context context = ((JavacProcessingEnvironment) env).getContext();
        this.types = env.getTypeUtils();
        this.resolve = Resolve.instance(context);
        this.names = Names.instance(context);
        this.trees = Trees.instance(env);
        this.log = Log.instance(context);
    }

    /**
     * Determine the scope for the given path.
     *
     * @param path the tree path to the local scope
     * @return the corresponding scope
     */
    public Scope getScopePath(TreePath path) {
        TreePath iter = path;
        JavacScope scope = null;
        while (scope == null && iter != null) {
            try {
                scope = (JavacScope) trees.getScope(iter);
            } catch (Throwable t) {
                // Work around Issue #1059 by skipping through the TreePath until something
                // doesn't crash. This probably returns the class scope, so users might not
                // get the variables they expect. But that is better than crashing.
                iter = iter.getParentPath();
            }
        }
        if (scope != null) {
            return scope;
        } else {
            throw new BugInCF("Could not determine any possible scope for path: " + path.getLeaf());
        }
    }

    /**
     * Determine the environment for the given path.
     *
     * @param path the tree path to the local scope
     * @return the corresponding attribution environment
     */
    public Env<AttrContext> getEnvForPath(TreePath path) {
        return ((JavacScope) getScopePath(path)).getEnv();
    }

    /**
     * Finds the package with name {@code name}.
     *
     * @param name the name of the package
     * @param path the tree path to the local scope
     * @return the {@code PackageSymbol} for the package if it is found, {@code null} otherwise
     */
    public PackageSymbol findPackage(String name, TreePath path) {
        Log.DiagnosticHandler discardDiagnosticHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            Env<AttrContext> env = getEnvForPath(path);
            Element res =
                    wrapInvocationOnResolveInstance(
                            FIND_IDENT, env, names.fromString(name), Kinds.KindSelector.PCK);
            // findIdent will return a PackageSymbol even for a symbol that is not a package,
            // such as a.b.c.MyClass.myStaticField. "exists()" must be called on it to ensure
            // that it exists.
            if (res.getKind() == ElementKind.PACKAGE) {
                PackageSymbol ps = (PackageSymbol) res;
                return ps.exists() ? ps : null;
            } else {
                return null;
            }
        } finally {
            log.popDiagnosticHandler(discardDiagnosticHandler);
        }
    }

    /**
     * Finds the field with name {@code name} in a given type.
     *
     * <p>The method adheres to all the rules of Java's scoping (while also considering the imports)
     * for name resolution.
     *
     * @param name the name of the field
     * @param type the type of the receiver (i.e., the type in which to look for the field).
     * @param path the tree path to the local scope
     * @return the element for the field
     */
    public VariableElement findField(String name, TypeMirror type, TreePath path) {
        Log.DiagnosticHandler discardDiagnosticHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            Env<AttrContext> env = getEnvForPath(path);
            Element res =
                    wrapInvocationOnResolveInstance(
                            FIND_IDENT_IN_TYPE,
                            env,
                            type,
                            names.fromString(name),
                            Kinds.KindSelector.VAR);

            if (res.getKind() == ElementKind.FIELD) {
                return (VariableElement) res;
            } else if (res.getKind() == ElementKind.OTHER && ACCESSERROR.isInstance(res)) {
                // Return the inaccessible field that was found
                return (VariableElement) wrapInvocation(res, ACCESSERROR_ACCESS, null, null);
            } else {
                // Most likely didn't find the field and the Element is a SymbolNotFoundError
                return null;
            }
        } finally {
            log.popDiagnosticHandler(discardDiagnosticHandler);
        }
    }

    /**
     * Finds the local variable with name {@code name} in the given scope.
     *
     * @param name the name of the local variable
     * @param path the tree path to the local scope
     * @return the element for the local variable
     */
    public VariableElement findLocalVariableOrParameter(String name, TreePath path) {
        Log.DiagnosticHandler discardDiagnosticHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            Scope scope = getScopePath(path);
            for (Element element : scope.getLocalElements()) {
                if (element.getSimpleName().contentEquals(name)) {
                    return (VariableElement) element;
                }
            }
            return null;
        } finally {
            log.popDiagnosticHandler(discardDiagnosticHandler);
        }
    }

    /**
     * Finds the class literal with name {@code name}.
     *
     * <p>The method adheres to all the rules of Java's scoping (while also considering the imports)
     * for name resolution.
     *
     * @param name the name of the class
     * @param path the tree path to the local scope
     * @return the element for the class
     */
    public Element findClass(String name, TreePath path) {
        Log.DiagnosticHandler discardDiagnosticHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            Env<AttrContext> env = getEnvForPath(path);
            return wrapInvocationOnResolveInstance(FIND_TYPE, env, names.fromString(name));
        } finally {
            log.popDiagnosticHandler(discardDiagnosticHandler);
        }
    }

    /**
     * Finds the class with name {@code name} in a given package.
     *
     * @param name the name of the class
     * @param pck the PackageSymbol for the package
     * @param path the tree path to the local scope
     * @return the {@code ClassSymbol} for the class if it is found, {@code null} otherwise
     */
    public ClassSymbol findClassInPackage(String name, PackageSymbol pck, TreePath path) {
        Log.DiagnosticHandler discardDiagnosticHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            Env<AttrContext> env = getEnvForPath(path);
            Element res =
                    wrapInvocationOnResolveInstance(
                            FIND_IDENT_IN_PACKAGE,
                            env,
                            pck,
                            names.fromString(name),
                            Kinds.KindSelector.TYP);
            if (res.getKind() == ElementKind.CLASS) {
                return (ClassSymbol) res;
            } else {
                return null;
            }
        } finally {
            log.popDiagnosticHandler(discardDiagnosticHandler);
        }
    }

    public Element findMethod(
            String methodName, TypeMirror receiverType, java.util.List<TypeMirror> argTypes) {
        TypeElement typeElt = TypesUtils.getTypeElement(receiverType);
        return getExecutableElement(methodName, typeElt, argTypes);
    }

    public ExecutableElement getExecutableElement(
            String methodName, TypeElement typeElt, java.util.List<TypeMirror> argTypes) {
        for (ExecutableElement exec : ElementFilter.methodsIn(typeElt.getEnclosedElements())) {
            if (exec.getParameters().size() == argTypes.size()
                    && exec.getSimpleName().contentEquals(methodName)) {
                boolean typesMatch = true;
                java.util.List<? extends VariableElement> params = exec.getParameters();
                for (int i = 0; i < argTypes.size(); i++) {
                    VariableElement ve = params.get(i);
                    TypeMirror paramType = ve.asType();
                    if (!types.isAssignable(argTypes.get(i), paramType)) {
                        typesMatch = false;
                        break;
                    }
                }
                if (typesMatch) {
                    return exec;
                }
            }
        }
        return null;
    }

    private Symbol wrapInvocationOnResolveInstance(Method method, Object... args) {
        return wrapInvocation(resolve, method, args);
    }

    private Symbol wrapInvocation(Object receiver, Method method, Object... args) {
        try {
            return (Symbol) method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Error err =
                    new AssertionError(
                            String.format(
                                    "Unexpected Reflection error in wrapInvocation(%s, %s, %s)",
                                    receiver, method, args));
            err.initCause(e);
            throw err;
        }
    }
}
