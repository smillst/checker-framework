package org.checkerframework.checker.modifiability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience alias meaning {@code @MaybeGrowable @MaybeShrinkable @MaybeReplaceable}. This is a
 * syntactic sugar for {@link MaybeModifiable}. It is intended to be used on parameters of methods
 * that are not designed to change the parameter. This annotation is only allowed to be written
 * within method or constructor parameter types, or explicit receiver parameter types.
 *
 * <p>This annotation is not part of the type hierarchy; the Modifiability Checker expands it to
 * {@code @MaybeGrowable @MaybeShrinkable @MaybeReplaceable} on each annotated type.
 *
 * @checker_framework.manual #modifiability-checker Modifiability Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface UnmodifiableParam {}
