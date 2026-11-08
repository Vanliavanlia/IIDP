package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.Date;

/**
 * 报工记录
 */
@Model(name = "production_report", tableName = "mes_production_report", displayName = "报工记录",
        description = "工单工序任务的报工明细", isAutoLog = Bool.True, orderBy = "id desc")
public class ProductionReport extends BaseModel<ProductionReport> {

    @ManyToOne(targetModel = "work_order_step", targetProperty = "id", displayName = "工单工序任务")
    @JoinColumn(name = "work_order_step_id", referencedProperty = "id")
    private WorkOrderStep workOrderStep;

    @Property(displayName = "报工数量")
    private Integer reportQuantity;

    @Property(displayName = "合格数量")
    private Integer qualifiedQuantity;

    @Property(displayName = "不良数量")
    private Integer defectQuantity;

    @ManyToOne(targetModel = "employee", targetProperty = "id", displayName = "操作员")
    @JoinColumn(name = "operator_id", referencedProperty = "id")
    private Employee operator;

    @ManyToOne(targetModel = "shift", targetProperty = "id", displayName = "班次")
    @JoinColumn(name = "shift_id", referencedProperty = "id")
    private Shift shift;

    @Property(displayName = "报工时间", dataType = DataType.DATE_TIME, displayForModel = true)
    private Date reportTime;

    @Property(displayName = "备注", dataType = DataType.TEXT)
    private String remark;
}
