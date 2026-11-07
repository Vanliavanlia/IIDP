package com.sie.app.mes.util;

import com.sie.app.mes.model.CodeRule;
import com.sie.app.mes.model.CodeRuleDetail;
import com.sie.snest.engine.rule.Filter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 编码生成器：前缀 + 日期段 + 补零流水号
 * <p>
 * 规则来自 {@code ems_code_rule}，当前流水号来自 {@code ems_code_rule_detail}
 * （按「规则编码 + 日期段」分别计数，所以换一天流水号会重新从 1 开始）。
 * <p>
 * 示例：prefix=M、dateFormat=yyyyMMdd、serialLength=4
 * → M202610310001、M202610310002 …
 */
public class CodeRuleGenerator {

    /**
     * 默认流水号长度
     */
    private static final int DEFAULT_SERIAL_LENGTH = 4;

    /**
     * 不带日期时使用的日期段
     */
    private static final String NO_DATE = "";

    private CodeRuleGenerator() {
    }

    /**
     * 生成下一个编码，并把这个流水号记到明细表里
     *
     * @param code 规则编码，例如 material
     */
    public static synchronized String generate(String code) {
        CodeRule rule = findRule(code);
        if (rule == null) {
            throw new RuntimeException("编码规则不存在，请先在【编码规则】里配置：" + code);
        }

        String dateKey = dateKey(rule.getDateFormat());
        int serial = nextSerial(code, dateKey, rule);
        return nullToEmpty(rule.getPrefix()) + dateKey + pad(serial, rule.getSerialLength());
    }

    /**
     * 查询某个编码当前已用的流水号（不递增）
     *
     * @param code 规则编码，例如 material
     */
    public static int currentSerial(String code) {
        CodeRule rule = findRule(code);
        if (rule == null) {
            return 0;
        }
        CodeRuleDetail detail = findDetail(code, dateKey(rule.getDateFormat()));
        if (detail == null || detail.getCurrentSerial() == null) {
            return 0;
        }
        return detail.getCurrentSerial();
    }

    /**
     * 生成编码时顺带预览：只算下一个流水号，不改库
     */
    public static String preview(String code) {
        CodeRule rule = findRule(code);
        if (rule == null) {
            throw new RuntimeException("编码规则不存在，请先在【编码规则】里配置：" + code);
        }
        String dateKey = dateKey(rule.getDateFormat());
        CodeRuleDetail detail = findDetail(code, dateKey);
        int serial = (detail == null || detail.getCurrentSerial() == null ? 0 : detail.getCurrentSerial()) + 1;
        return nullToEmpty(rule.getPrefix()) + dateKey + pad(serial, rule.getSerialLength());
    }

    private static CodeRule findRule(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return new CodeRule().select(Filter.equal("code", code));
    }

    private static CodeRuleDetail findDetail(String code, String dateKey) {
        return new CodeRuleDetail().select(
                Filter.AND(Filter.equal("code", code), Filter.equal("dateKey", dateKey)));
    }

    /**
     * 取下一个流水号：没有明细就新建一条从 1 开始，有就 +1 后更新
     */
    private static int nextSerial(String code, String dateKey, CodeRule rule) {
        CodeRuleDetail detail = findDetail(code, dateKey);
        if (detail == null) {
            detail = new CodeRuleDetail();
            detail.setCode(code);
            detail.setDateKey(dateKey);
            detail.setCurrentSerial(1);
            /**
             * 外键列（rule）存的是被关联记录的主键 id，不能直接塞模型对象
             */
            detail.set("rule", rule.getId());
            detail.create();
            return 1;
        }

        int next = (detail.getCurrentSerial() == null ? 0 : detail.getCurrentSerial()) + 1;
        detail.setCurrentSerial(next);
        detail.update();
        return next;
    }

    /**
     * 按日期格式截取当前日期段；none / 空 表示编码里不带日期
     */
    private static String dateKey(String dateFormat) {
        if (dateFormat == null || dateFormat.trim().isEmpty() || "none".equalsIgnoreCase(dateFormat.trim())) {
            return NO_DATE;
        }
        return LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormat.trim()));
    }

    /**
     * 流水号补零
     */
    private static String pad(int serial, Integer length) {
        int len = (length == null || length <= 0) ? DEFAULT_SERIAL_LENGTH : length;
        String text = String.valueOf(serial);
        StringBuilder sb = new StringBuilder();
        for (int i = text.length(); i < len; i++) {
            sb.append('0');
        }
        return sb.append(text).toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
