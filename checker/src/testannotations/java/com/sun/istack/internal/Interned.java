// Upstream version (this is a clean-room reimplementation of its interface):
// in the OpenJDK repository, file
// jaxws/src/share/jaxws_classes/com/sun/istack/internal/Interned.java

package com.sun.istack.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface Interned {}
