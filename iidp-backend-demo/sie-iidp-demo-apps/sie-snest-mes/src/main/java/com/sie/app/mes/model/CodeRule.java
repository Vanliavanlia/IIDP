package com.sie.app.mes.model;

import com.sie.app.mes.util.CodeRuleGenerator;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.List;

/**
 * 编码规则
 * <p>
 * 演示内容（对应第二天课件 3.5 编码规则）：
 * <ul>
 *   <li>编码规则模型：规则编码、前缀、流水号长度</li>
 *   <li>日期格式用 {@link Selection} 做下拉选择</li>
 *   <li>{@code @OneToMany} 关联编码规则明细（表单一对多页签）</li>
 *   <li>编码规则明细记录某个编码当前的流水号，服务里递增</li>
 * </ul>
 */
@Model(name = "code_rule",
        tableName = "ems_code_rule",
        displayName = "编码规则",
        description = "单据编码生成规则",
        isAutoLog = Bool.True
)
public class CodeRule extends BaseModel<CodeRule> {

    /**
     * 规则编码：业务上的唯一标识，例如 material / device / order
     */
    @Property(displayName = "规则编码", displayForModel = true)
    private String code;

    @Property(displayName = "规则名称")
    private String name;

    @Property(displayName = "前缀")
    private String prefix;

    @Property(displayName = "流水号长度")
    private Integer serialLength;

    /**
     * 日期格式：none 表示编码里不带日期
     */
    @Property(displayName = "日期格式")
    @Selection(
            values = {
                    @Option(value = "none", label = "无"),
                    @Option(value = "yyyy", label = "年（yyyy）"),
                    @Option(value = "yyyyMM", label = "年月（yyyyMM）"),
                    @Option(value = "yyyyMMdd", label = "年月日（yyyyMMdd）")
            }
    )
    private String dateFormat;

    /**
     * 一对多：一条规则对应多条流水号明细（每个日期段一条）
     */
    @Property(displayName = "规则明细")
    @OneToMany
    private List<CodeRuleDetail> details;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    public String getName() {
        return getStr("name");
    }

    public void setName(String name) {
        this.set("name", name);
    }

    public String getPrefix() {
        return getStr("prefix");
    }

    public void setPrefix(String prefix) {
        this.set("prefix", prefix);
    }

    public Integer getSerialLength() {
        return getInt("serialLength");
    }

    public void setSerialLength(Integer serialLength) {
        this.set("serialLength", serialLength);
    }

    public String getDateFormat() {
        return getStr("dateFormat");
    }

    public void setDateFormat(String dateFormat) {
        this.set("dateFormat", dateFormat);
    }

    public List<CodeRuleDetail> getDetails() {
        return (List<CodeRuleDetail>) this.get("details");
    }

    public void setDetails(List<CodeRuleDetail> details) {
        this.set("details", details);
    }

    /**
     * 按规则生成下一个编码（会同步递增流水号并落库）
     * <p>
     * 视图按钮可通过 model=code_rule & service=nextCode 调用。
     */
    @MethodService(name = "nextCode", auth = "nextCode", description = "按规则生成下一个编码")
    public String nextCode(String code) {
        return CodeRuleGenerator.generate(code);
    }
}
