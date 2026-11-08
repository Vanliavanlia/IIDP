package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.Date;
import java.util.List;

/**
 * 工艺路线
 */
@Model(name = "process", tableName = "mes_process", displayName = "工艺路线",
        description = "产品加工工艺路线的版本信息", isAutoLog = Bool.True)
@Validate.Unique(properties = {"code", "version"}, message = "同一产品的工艺编码+版本不能重复")
public class Process extends BaseModel<Process> {

    @ManyToOne(targetModel = "product", targetProperty = "id", displayName = "产品")
    @JoinColumn(name = "product_id", referencedProperty = "id")
    private Product product;

    @Property(displayName = "工艺编码", length = 64, displayForModel = true)
    private String code;

    @Validate.NotBlank(message = "版本不能为空")
    @Property(displayName = "版本", length = 32)
    private String version;

    @Property(displayName = "状态", length = 32, defaultValue = "DRAFT")
    @Selection(values = {
            @Option(label = "草稿", value = "DRAFT"),
            @Option(label = "生效", value = "ACTIVE"),
            @Option(label = "停用", value = "DISABLED")
    })
    private String status;

    @Property(displayName = "生效日期", dataType = DataType.DATE)
    private Date effectiveDate;

    @OneToMany(targetModel = "process_step", targetProperty = "process", displayName = "工序")
    private List<ProcessStep> processStepList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "process"));
        }
        return (RecordSet) getMeta().get("process").callSuper(Process.class, "create", valuesList);
    }
}
