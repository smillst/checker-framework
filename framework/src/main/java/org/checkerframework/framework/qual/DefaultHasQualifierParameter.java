package org.checkerframework.framework.qual;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * When written on a package declaration, this annotation equivalent to writing the {@code
 * HasQualifierParamer} annotation on every class in this package with the same arguments. It
 * indicates that every class in this package has an implicit qualifier parameter. This can be
 * disabled for specific classes by writing {@code NoQualifierParameter} on this class.
 *
 * @see HasQualifierParameter
 * @see NoQualifierParameter
 */
@Target(ElementType.PACKAGE)
@Documented
public @interface DefaultHasQualifierParameter {

    /**
     * Class of the top qualifier for the hierarchy for which classes in this package have a
     * qualifier parameter.
     *
     * @return the value
     */
    Class<? extends Annotation>[] value();
}
