package com.sie.iidp.common.util.errors;

import com.sie.snest.engine.exception.ValidationException;

public class Exceptions {
    public static ValidationException GEN_NUM_EXCEPTION = new ValidationException("生成编码数量应大于1");
    public static ValidationException INSUFFICIENT_NUM_EXCEPTION = new ValidationException("生成编码数量不足");
    public static ValidationException CODE_GEN_SETTING_DISABLED_EXCEPTION = new ValidationException("编码生成规则待配置!");
    public static ValidationException ENABLE_ERROR_EXCEPTION = new ValidationException("状态类型传参有误!");
    public static ValidationException DATE_FORMAT_ERROR_EXCEPTION = new ValidationException("时间转换错误!");
    public static ValidationException UPDATE_ID_NULL_EXCEPTION = new ValidationException("更新必须传入id!");
    public static ValidationException GET_ONE_PARAM_EXCEPTION = new ValidationException("id不能为空!");
    public static ValidationException IMPORT_FILE_ID_NULL_EXCEPTION = new ValidationException("导入文件id不能为空!");
    public static ValidationException IMPORT_FILE_ID_ERROR_EXCEPTION = new ValidationException("导入文件id有误!");

    public static ValidationException getModelNotFoundException(String modelName) {
        return new ValidationException(
                String.format("系统中无法找到模型【%1$s】，请联系系统管理员！", modelName));
    }

    /**
     * 打印实体不存在，请配置【单据数据绑定】
     */
    public static final ValidationException PRINTABLE_IS_NOT_FOUND
            = new ValidationException("打印实体不存在，请配置【单据数据绑定】！");

    /**
     * 组件的前端地址没有配置，请在检查配置中心的配置
     */
    public static final ValidationException COMPONENT_FRONT_URL_IS_NOT_FOUND
            = new ValidationException("组件的前端地址没有配置，请在检查配置中心的配置！");
}
