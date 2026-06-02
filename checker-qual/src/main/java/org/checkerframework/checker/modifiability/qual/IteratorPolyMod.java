package org.checkerframework.checker.modifiability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.SubtypeOf;

/**
 * If a collection's type is {@code @IteratorPolyMod}, then its {@code iterator()} and {@code
 * listIterator()} methods preserve the ability to call modification methods in its iterator.
 *
 * <p>For example, if collection {@code c} has type {@code @Shrinkable}, then {@code c.iterator()}
 * also has type {@code @Shrinkable}. If list {@code l} has type {@code @Replaceable}, then {@code
 * l.listIterator()} also has type {@code @Replaceable}. For any collection whose type is
 * {@code @MaybeIteratorPolyMod}, its iterator is always {@code @MaybeShrinkable}.
 *
 * <p>This annotation is used in GrowChecker, ShrinkChecker, and ReplaceChecker.
 *
 * @checker_framework.manual #modifiability-checker Modifiability Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf(MaybeIteratorPolyMod.class)
public @interface IteratorPolyMod {}
