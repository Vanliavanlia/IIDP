package com.sie.iidp.common.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.sie.iidp.common.codesequence.model.ExampleCodeSequence;
import com.sie.iidp.common.util.database.DataBaseUtil;
import com.sie.iidp.common.util.database.SelectUtil;
import com.sie.iidp.common.util.database.UpdateUtil;
import com.sie.iidp.common.util.enums.CodeGenEnum;
import com.sie.snest.engine.exception.ValidationException;
import com.sie.snest.sdk.db.DbUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 编码生成工具类(临时)
 *
 * @author cxh
 * @date 2024年03月28日 15:52
 */
@Slf4j
public class CodeGenTempUtil {

    public static final String STU_TYPE = "0";

    public static final String BOOK_TYPE = "1";

    public static final String CLASS_TYPE = "2";

    public static final String READER_TYPE = "3";

    public static final String PRODUCT_TYPE = "5";

    public static final String UNIT_TYPE = "6";

    public static final String ITEM_TYPE = "7";

    public static final String ORDER_TYPE = "8";
    public static final String STU_PREFIX = "STU";

    public static final String BOOK_PREFIX = "BOOK";

    public static final String CLASS_PREFIX = "CLASS";

    public static final String READER_PREFIX = "READER";

    public static final String PRODUCT_PREFIX = "PRODUCT";

    public static final String UNIT_PREFIX = "UNIT";

    public static final String ITEM_PREFIX = "ITEM";

    public static final String ORDER_PREFIX = "ORDER";
    public static final Integer MAX_SEQUENCE = 99999;
    public static final String COLUMN_CODE_TYPE = "code_type";
    public static final String COLUMN_CURR_VALUE = "curr_value";

    public static String midDay;

    static {
        // 获取当前日期(yyyyMMdd格式)
        midDay = DateUtil.format(new Date(), DatePattern.PURE_DATE_PATTERN);
    }

    /**
     * 根据不同类型生成对应的单个编码(格式为 前缀 + 日期 + 5位流水)
     * <p>
     * 1、不用类型默认使用不同的前缀
     * 2、中间部分都使用当前日期
     * 3、末尾使用5位流水
     * <p>
     * 后序新增其他类型，可以在后面扩展
     *
     * @return
     */
    public static String genOneCode(String type) {
        return genManyCode(type, 1).get(0);
    }

    /**
     * 根据不同类型生成对应的多个编码(格式为 前缀 + 日期 + 5位流水)
     * <p>
     * 1、不用类型默认使用不同的前缀
     * 2、中间部分都使用当前日期
     * 3、末尾使用5位流水
     * <p>
     * 后序新增其他类型，可以在后面扩展
     *
     * @return
     */
    public static List<String> genManyCode(String type, Integer qty) {
        List<String> codeList = new ArrayList<>();
        switch (type) {
            case STU_TYPE:
                codeList = genCode(STU_PREFIX, type, qty);
                break;
            case BOOK_TYPE:
                codeList = genCode(BOOK_PREFIX, type, qty);
                break;
            case CLASS_TYPE:
                codeList = genCode(CLASS_PREFIX, type, qty);
                break;
            case READER_TYPE:
                codeList = genCode(READER_PREFIX, type, qty);
                break;
            case PRODUCT_TYPE:
                codeList = genCode(PRODUCT_PREFIX, type, qty);
                break;
            case UNIT_TYPE:
                codeList = genCode(UNIT_PREFIX, type, qty);
                break;
            case ITEM_TYPE:
                codeList = genCode(ITEM_PREFIX, type, qty);
                break;
            case ORDER_TYPE:
                codeList = genCode(ORDER_PREFIX, type, qty);
                break;
            default:
        }
        if (CollUtil.isEmpty(codeList)) {
            throw new ValidationException(CodeGenEnum.TYPE_ERROR_CODE_GEN_FAIL.getMsg());
        }
        return codeList;
    }

    /**
     * 根据不同类型生成对应的编码
     *
     * @param prefix
     * @param type
     * @param qty
     * @return
     */
    private static List<String> genCode(String prefix, String type, Integer qty) {
        // 获取该类型的当前序列值
        Integer currValue = getCurrValueByType(type);
        // 生成指定数量的编码
        return genCodeList(prefix, midDay, currValue, qty, type);
    }

    /**
     * 获取该类型的当前序列值(不存在即为0)
     *
     * @param type
     * @return
     */
    private static Integer getCurrValueByType(String type) {
        Integer currValue = 0;
        // 获取当前序列值
        String sql = new SelectUtil(ExampleCodeSequence.class).eq(COLUMN_CODE_TYPE, type).getSql();
        ExampleCodeSequence codeSequence = DataBaseUtil.getSelectOne(sql, ExampleCodeSequence.class);
        if (ObjectUtil.isNotNull(codeSequence)) {
            currValue = codeSequence.getCurrValue();
        }
        return currValue;
    }

    /**
     * 生成指定数量的编码
     *
     * @param prefix
     * @param midDay
     * @param currValue
     * @param qty
     * @param type
     * @return
     */
    private static List<String> genCodeList(String prefix, String midDay, Integer currValue, Integer qty, String type) {
        // 校验生成后的最大序列值不能超过99999
        validMaxSequence(currValue, qty);
        List<String> codeList = new ArrayList<>();
        String prefixAgg = prefix + midDay;
        Integer currActualValue = currValue;
        for (int i = 0; i < qty; i++) {
            // 序列转换为字符串，不足5位前面用0补齐
            String sequenceStr = String.format("%0" + 5 + "d", currActualValue + 1);
            String code = prefixAgg + sequenceStr;
            codeList.add(code);
            currActualValue++;
        }
        // 新增或更新 新的序列更新到数据库
        updateSequence(currValue, currActualValue, type);
        return codeList;
    }

    /**
     * 新增或更新 新的序列更新到数据库
     *
     * @param currValue
     * @param currActualValue
     * @param type
     */
    private static void updateSequence(Integer currValue, Integer currActualValue, String type) {
        // 若当前值为0，说明不存在该类型序列，则新增
        if (currValue.equals(0)) {
            ExampleCodeSequence codeSequence = new ExampleCodeSequence();
            codeSequence.setCodeType(type);
            codeSequence.setCurrValue(currActualValue);
            DbUtils.create(codeSequence, ExampleCodeSequence.class);
        } else {
            // 若当前值大于0，说明存在该类型序列，则更新
            String updateSql = new UpdateUtil(ExampleCodeSequence.class)
                    .eq(COLUMN_CODE_TYPE, type)
                    .set(COLUMN_CURR_VALUE, currActualValue)
                    .getSql();
            DataBaseUtil.executeUpdateSql(updateSql);
        }
    }

    /**
     * 校验生成后的最大序列值不能超过99999
     *
     * @param currValue
     * @param qty
     */
    private static void validMaxSequence(Integer currValue, Integer qty) {
        int result = currValue + qty;
        if (result > MAX_SEQUENCE) {
            throw new ValidationException(CodeGenEnum.OVER_SEQUENCE_MAX_VALUE.getMsg());
        }
    }
}
