package com.sie.app.mes.model;

import com.sie.app.mes.util.CodeRuleGenerator;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

/**
 * 编码规则明细
 * <p>
 * 记录「某个规则编码 + 某个日期段」当前已经用到第几号，例如：
 * code=material, dateKey=20261031, currentSerial=7
 */
@Model(name = "code_rule_detail",
        tableName = "ems_code_rule_detail",
        displayName = "编码规则明细",
        description = "记录某个编码当前的流水号",
        isAutoLog = Bool.True
)
public class CodeRuleDetail extends BaseModel<CodeRuleDetail> {

    @Property(displayName = "规则编码", displayForModel = true)
    private String code;

    /**
     * 日期段：按日期格式截取，例如 2026 / 202610 / 20261031；不带日期时为空串
     */
    @Property(displayName = "日期段")
    private String dateKey;

    @Property(displayName = "当前流水号")
    private Integer currentSerial;

    @Property(displayName = "所属规则")
    @ManyToOne
    @JoinColumn
    private CodeRule rule;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    public String getDateKey() {
        return getStr("dateKey");
    }

    public void setDateKey(String dateKey) {
        this.set("dateKey", dateKey);
    }

    public Integer getCurrentSerial() {
        return getInt("currentSerial");
    }

    public void setCurrentSerial(Integer currentSerial) {
        this.set("currentSerial", currentSerial);
    }

    public CodeRule getRule() {
        return (CodeRule) this.get("rule");
    }

    public void setRule(CodeRule rule) {
        this.set("rule", rule);
    }

    /**
     * 查询某个编码当前已用的流水号（不递增、不落库）
     * <p>
     * 第二天课件要求的「编写一个服务查询 code 当前的流水号」。
     */
    @MethodService(name = "currentSerial", auth = "currentSerial", description = "查询某个编码当前的流水号")
    public Integer currentSerial(String code) {
        return CodeRuleGenerator.currentSerial(code);
    }
}
