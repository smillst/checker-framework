package org.checkerframework.checker.modifiability.qual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Convenience alias meaning {@code @PolyGrowable @PolyReplaceable @PolyShrinkable}. A polymorphic
 * qualifier for all three modifiability hierarchies.
 *
 * <p>You should write {@code @PolyModifiable} on methods that preserve or transfer modifiability,
 * such as {@code List.subList()}, {@code iterator()}, and {@code stream()}.
 *
 * <p>For example:
 *
 * <pre><code>
 * class Example {
 * &nbsp; @PolyModifiable List&lt;E&gt; subList(@PolyModifiable List&lt;E&gt; this, int from, int to);
 * }
 * </code></pre>
 *
 * If the receiver type at a given call site is {@code @Unmodifiable}, the return type at that call
 * site {@code @Unmodifiable}. If the receiver type is {@code @Modifiable}, the return type is
 * {@code @Modifiable}. Likewise for growing and shrinking type qualifiers.
 *
 * @checker_framework.manual #modifiability-checker Modifiability Checker
 * @checker_framework.manual #qualifier-polymorphism Qualifier polymorphism
 */
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyModifiable {}
