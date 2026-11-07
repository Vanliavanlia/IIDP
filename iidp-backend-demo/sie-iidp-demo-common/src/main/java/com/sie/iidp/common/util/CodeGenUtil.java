package com.sie.iidp.common.util;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.sie.iidp.common.util.consts.Constant;
import com.sie.iidp.common.util.errors.Exceptions;
import com.sie.snest.engine.context.BaseContextHandler;
import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.exception.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 编码生成工具类
 */
public class CodeGenUtil {

    /**
     * 新增时对实体进行编码赋值
     *
     * @param valuesList
     * @param modelName
     * @param ruleName
     * @param codeFieldName
     * @return
     */
    public static List<Map<String, Object>> codeAssign(List<Map<String, Object>> valuesList, String modelName, String ruleName, String codeFieldName) {
        if (CollectionUtils.isEmpty(valuesList)) {
            return valuesList;
        }
        Optional<String> encoderRuleIdOp = CodeGenUtil.obtainEncoderRuleId(modelName,
                ruleName, true);
        if (!encoderRuleIdOp.isPresent() || StringUtils.isEmpty(encoderRuleIdOp.get())) {
            throw Exceptions.CODE_GEN_SETTING_DISABLED_EXCEPTION;
        }
        Queue<String> codeQueue = CodeGenUtil.generateCodeQueue(encoderRuleIdOp.get(), valuesList.size());
        for (Map<String, Object> value : valuesList) {
            String code = codeQueue.poll();
            value.put(codeFieldName, code);
        }
        return valuesList;
    }

    /**
     * 根据业务配置项生成单个编码
     *
     * @param modelName
     * @param settingName
     * @return
     */
    public static Optional<String> genSingleCode(String modelName, String settingName) {
        Optional<List<String>> codeListOp = genCode(modelName, settingName, 1);
        if (!codeListOp.isPresent()) {
            return Optional.empty();
        }
        List<String> codeList = codeListOp.get();
        if (CollectionUtils.isEmpty(codeList)) {
            return Optional.empty();
        }
        return Optional.of(codeList.get(0));
    }

    /**
     * 根据编码规则id生成单个编码
     *
     * @param encodeRuleId
     * @return
     */
    public static Optional<String> genSingeCode(String encodeRuleId) {
        List<String> codeList = generateCodeList(encodeRuleId, 1);
        if (CollectionUtils.isEmpty(codeList) || codeList.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(codeList.get(0));
    }

    /**
     * 生成多个条码，不存在配置的话，返回Optional.empty()
     *
     * @param modelName
     * @param settingName
     * @param genNum
     * @return
     */
    public static Optional<List<String>> genCode(String modelName, String settingName, int genNum) {
        Optional<String> encoderRuleIdOp = obtainEncoderRuleId(modelName, settingName, true);
        if (!encoderRuleIdOp.isPresent()) {
            return Optional.empty();
        }
        String encoderRuleId = encoderRuleIdOp.get();
        if (StringUtils.isEmpty(encoderRuleId)) {
            return Optional.empty();
        }
        List<String> codeList = generateCodeList(encoderRuleId, genNum);
        return Optional.of(codeList);
    }

    /**
     * 获取编码生成规则配置项
     *
     * @param modelName
     * @param settingName
     * @return
     */
    public static Optional<String> obtainEncoderRuleId(String modelName, String settingName, boolean ignoreUnConfigured) {
        return BizSettingUtil.getBizSettingValue(modelName, settingName, ignoreUnConfigured);
    }

    /**
     * 生成多个编码工具类，返回队列
     *
     * @param encoderRuleId
     * @param genNum
     * @return
     */

    public static Queue<String> generateCodeQueue(String encoderRuleId, int genNum) {
        List<String> codeList = generateCodeList(encoderRuleId, genNum);
        Queue<String> codeQueue = new LinkedList<>(codeList);
        return codeQueue;
    }

    /**
     * 获取生成的编码
     *
     * @param encoderRuleId
     * @param genNum
     * @return
     */
    public static List<String> generateCodeList(String encoderRuleId, int genNum) {
        if (StringUtils.isEmpty(encoderRuleId)) {
            throw Exceptions.CODE_GEN_SETTING_DISABLED_EXCEPTION;
        }
        if (genNum < 1) {
            throw Exceptions.GEN_NUM_EXCEPTION;
        }
        try {
            List<String> codes;
            RecordSet recordSet = BaseContextHandler.getMeta().get(Constant.ENCODER_RULE);
            // 生成编码
            // 因为获取序列号时，如果是MySql数据库的话，会先提交一次数据库
            // 如同一MethodService中，在获取编码前有数据库操作，后面出现异常时，是无法回滚事务的
            // 所以生成编码改成异步的，就不会提交调用线程的事务了
            CompletableFuture resultFuture = recordSet.callAsync(Constant.GET_ENCODER_API, encoderRuleId, genNum);

            return (List<String>) resultFuture.get();
        } catch (Exception e) {
            throw new ValidationException(String.format("生成编码失败，详细信息：%s",
                    ExceptionUtil.getSimpleMessage(ExceptionUtil.getRootCause(e))));
        }
    }

//    /**
//     * 获取生成的编码
//     *
//     * @param encoderRuleId
//     * @param genNum
//     * @return
//     */
//    public static EncoderRuleDummyDTO generateCodeRangePreview(String encoderRuleId, Integer genNum) {
//        if (genNum < 1) {
//            throw Exceptions.GEN_NUM_EXCEPTION;
//        }
//        try {
//            RecordSet recordSet = BaseContextHandler.getMeta().get(Constant.ENCODER_RULE);
//            Object result = recordSet.call(Constant.GET_ENCODER_RANGE_NO_SAVE_API, encoderRuleId, genNum);
//            EncoderRuleDummyDTO resultDto = JSON.parseObject(JSON.toJSONString(result), EncoderRuleDummyDTO.class);
//            if (StringUtils.isEmpty(resultDto.getStartCodeNumber())
//                    || StringUtils.isEmpty(resultDto.getEndCodeNumber())) {
//                throw Exceptions.INSUFFICIENT_NUM_EXCEPTION;
//            }
//            return resultDto;
//        } catch (Exception e) {
//            throw new ValidationException(String.format("生成编码失败，详细信息：%s", e.getMessage()));
//        }
//
//    }
}
