package com.sie.iidp.wms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.Date;
import java.util.Map;

/**
 * 领料记录
 */
@Model(name = "material_issue", tableName = "mes_material_issue", displayName = "领料记录",
        description = "工单及工序任务的领料明细", isAutoLog = Bool.True, orderBy = "id desc")
public class MaterialIssue extends BaseModel<MaterialIssue> {

    @ManyToOne(targetModel = "work_order", targetProperty = "id", displayName = "工单")
    @JoinColumn(name = "work_order_id", referencedProperty = "id")
    private Map<String, Object> workOrder;

    @ManyToOne(targetModel = "work_order_step", targetProperty = "id", displayName = "工单工序任务")
    @JoinColumn(name = "work_order_step_id", referencedProperty = "id")
    private Map<String, Object> workOrderStep;

    @ManyToOne(targetModel = "material", targetProperty = "id", displayName = "物料")
    @JoinColumn(name = "material_id", referencedProperty = "id")
    private Material material;

    @Property(displayName = "批次号", length = 64, displayForModel = true)
    private String batchNo;

    @Property(displayName = "领用数量")
    private Double issueQuantity;

    @Property(displayName = "领料时间", dataType = DataType.DATE_TIME)
    private Date issueTime;

    @ManyToOne(targetModel = "employee", targetProperty = "id", displayName = "领料人")
    @JoinColumn(name = "issuer_id", referencedProperty = "id")
    private Map<String, Object> issuer;
}
