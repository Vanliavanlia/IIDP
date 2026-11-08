package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;

/**
 * 班次
 */
@Model(name = "shift", tableName = "mes_shift", displayName = "班次",
        description = "生产班次定义", isAutoLog = Bool.True, orderBy = "id desc")
public class Shift extends BaseModel<Shift> {

    @Property(displayName = "班次编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 100, displayForModel = true)
    private String name;

    @Property(displayName = "开始时间", length = 16)
    private String startTime;

    @Property(displayName = "结束时间", length = 16)
    private String endTime;

    @OneToMany(targetModel = "production_report", targetProperty = "shift", displayName = "报工记录")
    private List<ProductionReport> productionReportList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "shift"));
        }
        return (RecordSet) getMeta().get("shift").callSuper(Shift.class, "create", valuesList);
    }
}
