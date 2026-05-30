package org.checkerframework.checker.modifiability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated method preserves modifiability capabilities from its first argument to its return
 * value.
 *
 * <p>For example, if the first argument is {@code @Modifiable} or {@code @IteratorPolyMod}, then
 * the return type is also {@code @Modifiable} or {@code @IteratorPolyMod}. If the first argument is
 * anything other than {@code @Modifiable} or {@code @IteratorPolyMod}, then the return type is the
 * top {@code @MaybeModifiable}.
 *
 * <p>If the annotated method has no parameters, then this annotation has no effect on the method.
 *
 * @checker_framework.manual #modifiability-checker Modifiability Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PreservesModifiability {}
