package com.sie.iidp.common.util.condition;

public enum QueryType {
    LIKE,

    NOT_LIKE,
    /**
     * 起始范围,一般用作时间范围查询
     */
    RANGE,
    EQUAL,
    NOT_EQUAL,
    IN,
    NOT_IN,
    GE, // 大于等于
    GT, //大于
    LE, //小于等于
    LT, //小于
    IS_NULL, //为null，建议声明为布尔值的属性，然后赋值为true则生效，不赋值则加入这个条件
    IS_NOT_NULL, //不为null  建议声明为布尔值的属性，然后赋值为true则生效，不赋值则加入这个条件
}
