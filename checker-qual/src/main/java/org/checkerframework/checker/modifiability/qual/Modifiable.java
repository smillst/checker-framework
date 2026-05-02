package org.checkerframework.checker.modifiability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience alias usually meaning {@code @Growable @Shrinkable @Replaceable}. Calling a mutating
 * operation (growing, shrinking, or replacing) on this collection will not result in throwing
 * {@link UnsupportedOperationException}.
 *
 * <p>As an exception, {@code @Modifiable} means {@code @Unknown*} if the type does not support a
 * given category of operation; for example, {@code @Modifiable Iterator} means
 * {@code @UnknownGrow @UnknownShrink @Replaceable Iterator}.
 *
 * @checker_framework.manual #modifiability-checker Modifiability Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Modifiable {}
