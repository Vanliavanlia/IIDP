package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

/**
 * 在制品
 */
@Model(name = "wip", tableName = "mes_wip", displayName = "在制品",
        description = "车间在制品流转记录", isAutoLog = Bool.True, orderBy = "id desc")
public class Wip extends BaseModel<Wip> {

    @ManyToOne(targetModel = "work_order", targetProperty = "id", displayName = "工单")
    @JoinColumn(name = "work_order_id", referencedProperty = "id")
    private WorkOrder workOrder;

    @ManyToOne(targetModel = "process_step", targetProperty = "id", displayName = "当前工序")
    @JoinColumn(name = "current_step_id", referencedProperty = "id")
    private ProcessStep currentStep;

    @Property(displayName = "批次号", length = 64, displayForModel = true)
    private String batchNo;

    @Property(displayName = "在制数量")
    private Integer wipQuantity;

    @Property(displayName = "状态", length = 32, defaultValue = "PROCESSING")
    @Selection(values = {
            @Option(label = "加工中", value = "PROCESSING"),
            @Option(label = "待流转", value = "WAIT_TRANSFER"),
            @Option(label = "待检验", value = "WAIT_INSPECTION")
    })
    private String status;
}
