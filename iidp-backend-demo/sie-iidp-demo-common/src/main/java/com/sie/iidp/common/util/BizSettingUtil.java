package com.sie.iidp.common.util;

import com.sie.iidp.common.util.consts.Constant;
import com.sie.iidp.common.util.errors.Exceptions;
import com.sie.snest.engine.context.BaseContextHandler;
import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.utils.TypeKit;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务配置项的工具类
 */
public final class BizSettingUtil {
    private static final String CONFIGURABLE_VAL = "1";

    /**
     * 获取配置项的值
     *
     * @param modelName   模块编码
     * @param settingName 配置项编码
     * @return 配置项的值
     */
    public static Optional<String> getBizSettingValue(String modelName, String settingName, boolean ignoreUnConfigured) {
        Filter filter = new Filter();
        filter.and(Filter.equal(Constant.MODEL_CODE_FIELD_NAME, modelName));
        filter.and(Filter.equal(Constant.CODE_FIELD_NAME, settingName));

//        if (ignoreUnConfigured) {
//            filter.and(Filter.equal(Constant.CONFIGURED_FIELD_NAME, CONFIGURABLE_VAL));
//        }

        //查找生成规则
        RecordSet recordSet = BaseContextHandler.getMeta().get(Constant.BIZ_SETTING);
        if (recordSet.getModel() == null) {
            throw Exceptions.getModelNotFoundException(Constant.BIZ_SETTING);
        }

        List<Map<String, Object>> ruleList = recordSet
                .search(filter, Arrays.asList(Constant.SETTING_VALUE), 1, 0, "");

        if (CollectionUtils.isEmpty(ruleList)) {
            return Optional.empty();
        }

        Map<String, Object> rule = ruleList.get(0);
        String ruleSettingValue = TypeKit.toStr(rule.get(Constant.SETTING_VALUE));
        if (ruleSettingValue == null) {
            return Optional.empty();
        }

        return Optional.of(ruleSettingValue);
    }
}
