package com.sie.iidp.common.util.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 编码生成枚举类
 *
 * @author cxh
 * @date 2024年03月28日 17:52
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public enum CodeGenEnum {

    TYPE_ERROR_CODE_GEN_FAIL("type.error.code.gen.fail", "类型错误，编码生成失败"),
    OVER_SEQUENCE_MAX_VALUE("over.sequence.max.value", "超过序列最大值99999"),

    ;

    private String code;

    private String msg;

}
