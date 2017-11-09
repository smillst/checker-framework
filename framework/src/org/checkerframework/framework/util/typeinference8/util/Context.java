package org.checkerframework.framework.util.typeinference8.util;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.util.typeinference8.infer.InvocationTypeInference;
import org.checkerframework.framework.util.typeinference8.types.ProperType;
import org.checkerframework.framework.util.typeinference8.types.Theta;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

public class Context {
    public final ProcessingEnvironment env;
    public final ProperType object;
    public final AnnotatedTypeFactory factory;
    public final TreePath treePath;
    public final InvocationTypeInference inference;
    public final Types types;
    public final DeclaredType enclosingType;
    public final Map<ExpressionTree, Theta> maps;
    public int variableCount = 0;

    public Context(
            ProcessingEnvironment env,
            AnnotatedTypeFactory factory,
            TreePath expression,
            InvocationTypeInference inference) {
        this.env = env;
        this.factory = factory;
        this.treePath = expression;
        this.inference = inference;
        JavacProcessingEnvironment javacEnv = (JavacProcessingEnvironment) env;
        this.types = Types.instance(javacEnv.getContext());
        TypeMirror objecTypeMirror =
                TypesUtils.typeFromClass(
                        factory.getContext().getTypeUtils(),
                        factory.getElementUtils(),
                        Object.class);
        this.object = new ProperType(objecTypeMirror, this);
        ClassTree clazz = TreeUtils.enclosingClass(treePath);
        this.enclosingType = (DeclaredType) InternalUtils.typeOf(clazz);
        this.maps = new HashMap<>();
    }

    public int getNextVariableId() {
        return variableCount++;
    }
}
