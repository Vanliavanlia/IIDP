package com.sie.iidp.common.util.consts;

/**
 * 共用的常量
 */
public class Constant {

    /**
     * 时间格式
     */
    public final static String TIME = "yyyy-MM-dd HH:mm:ss";
    /**
     * 业务配置项模块名称的字段名
     */
    public final static String MODEL_CODE_FIELD_NAME = "moduleCode";

    /**
     * 业务配置项编码的字段名
     */
    public final static String CODE_FIELD_NAME = "code";

    /**
     * 业务配置项的模型名
     */
    public static final String BIZ_SETTING = "biz_setting";

    /**
     * 业务配置项的配置值的字段名
     */
    public static final String SETTING_VALUE = "settingValue";

    /**
     * 业务配置项的配置是否已配置字段名
     */
    public static final String CONFIGURED_FIELD_NAME = "isConfigured";

    /**
     * 编码生成的编码数量参数名称
     */
    public static final String ENCODER_COUNT = "encoderCount";

    /**
     * 编码生成的编码规则ID参数名称
     */
    public static final String ENCODER_RULE_ID = "encoderRuleId";

    /**
     * 编码规则的模型名
     */
    public static final String ENCODER_RULE = "encoder_rule";

    /**
     * 生成编码的服务名称
     */
    public static final String GET_ENCODER_API = "getEncoder";

    //返回数据接口 {"code":0,"msg":null,"data":["C01"]}

    /**
     * 生成编码的返回状态码的参数名称
     */
    public static final String RETURN_CODE = "code";

    /**
     * 生成编码的返回消息的参数名称
     */
    public static final String RETURN_MSG = "msg";

    /**
     * 生成编码的返回数据的参数名称
     */
    public static final String RETURN_DATA = "data";

    /**
     * 模板可打印报表的模型名
     */
    public static final String TEMPLATE_PRINTABLE = "template_printable";

    /**
     * 模板可打印实体是否存在
     */
    public static final String GET_TEMPLATE_PRINTABLE_API = "getTemplatePrintableApi";

    /**
     * 编码范围获取预览
     */
    public static final String GET_ENCODER_RANGE_NO_SAVE_API = "getEncoderRangeNoSave";
}
