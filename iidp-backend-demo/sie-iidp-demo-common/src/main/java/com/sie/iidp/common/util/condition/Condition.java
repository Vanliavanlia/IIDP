package com.sie.iidp.common.util.condition;

import org.apache.logging.log4j.util.Strings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Condition {
    QueryType queryType() default QueryType.EQUAL;

    String fieldName() default Strings.EMPTY;

    ConvertType convertType() default ConvertType.NOT_NEED;
}
