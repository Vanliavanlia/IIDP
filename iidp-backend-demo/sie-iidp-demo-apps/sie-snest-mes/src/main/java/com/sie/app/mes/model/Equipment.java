package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;

/**
 * 设备
 */
@Model(name = "equipment", tableName = "mes_equipment", displayName = "设备",
        description = "车间生产设备主数据", isAutoLog = Bool.True, orderBy = "id desc")
public class Equipment extends BaseModel<Equipment> {

    @Property(displayName = "设备编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "设备类型", length = 100)
    private String equipmentType;

    @Property(displayName = "状态", length = 32, defaultValue = "IDLE")
    @Selection(values = {
            @Option(label = "空闲", value = "IDLE"),
            @Option(label = "运行", value = "RUNNING"),
            @Option(label = "维修", value = "REPAIR"),
            @Option(label = "停用", value = "DISABLED")
    })
    private String status;

    @Property(displayName = "位置", length = 200)
    private String location;

    @OneToMany(targetModel = "work_order_step", targetProperty = "equipment", displayName = "工单工序任务")
    private List<WorkOrderStep> workOrderStepList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "equipment"));
        }
        return (RecordSet) getMeta().get("equipment").callSuper(Equipment.class, "create", valuesList);
    }
}
