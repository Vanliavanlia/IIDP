package com.sie.app.mes.model;

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
import java.util.Map;

/**
 * 工单工序任务
 */
@Validate.Unique(properties = {"workOrder", "sequence"}, message = "同一工单内工序序号不能重复")
@Model(name = "work_order_step", tableName = "mes_work_order_step", displayName = "工单工序任务",
        description = "生产工单拆解后的工序任务", isAutoLog = Bool.True, orderBy = "sequence asc")
public class WorkOrderStep extends BaseModel<WorkOrderStep> {

    @ManyToOne(targetModel = "work_order", targetProperty = "id", displayName = "所属工单")
    @JoinColumn(name = "work_order_id", referencedProperty = "id")
    private WorkOrder workOrder;

    @ManyToOne(targetModel = "process_step", targetProperty = "id", displayName = "工序")
    @JoinColumn(name = "process_step_id", referencedProperty = "id")
    private ProcessStep processStep;

    @Property(displayName = "工序序号", displayForModel = true)
    private Integer sequence;

    @Property(displayName = "计划数量")
    private Integer plannedQuantity;

    @Property(displayName = "完成数量")
    private Integer completedQuantity;

    @Property(displayName = "合格数量")
    private Integer qualifiedQuantity;

    @Property(displayName = "不良数量")
    private Integer defectQuantity;

    @ManyToOne(targetModel = "equipment", targetProperty = "id", displayName = "设备")
    @JoinColumn(name = "equipment_id", referencedProperty = "id")
    private Equipment equipment;

    @ManyToOne(targetModel = "team", targetProperty = "id", displayName = "班组")
    @JoinColumn(name = "team_id", referencedProperty = "id")
    private Team team;

    @Property(displayName = "计划开始时间", dataType = DataType.DATE_TIME)
    private Date plannedStartTime;

    @Property(displayName = "计划结束时间", dataType = DataType.DATE_TIME)
    private Date plannedEndTime;

    @Property(displayName = "实际开始时间", dataType = DataType.DATE_TIME)
    private Date actualStartTime;

    @Property(displayName = "实际结束时间", dataType = DataType.DATE_TIME)
    private Date actualEndTime;

    @Property(displayName = "面漆颜色", length = 64)
    private String color;

    @ManyToOne(targetModel = "logo_spec", targetProperty = "id", displayName = "LOGO规格")
    @JoinColumn(name = "logo_spec_id", referencedProperty = "id")
    private Map<String, Object> logoSpec;

    @Property(displayName = "状态", length = 32, defaultValue = "PENDING")
    @Selection(values = {
            @Option(label = "待开工", value = "PENDING"),
            @Option(label = "进行中", value = "IN_PROGRESS"),
            @Option(label = "待检验", value = "WAIT_INSPECTION"),
            @Option(label = "已完成", value = "COMPLETED"),
            @Option(label = "已暂停", value = "PAUSED")
    })
    private String status;

    @OneToMany(targetModel = "production_report", targetProperty = "workOrderStep", displayName = "报工记录")
    private List<ProductionReport> productionReportList;

    @OneToMany(targetModel = "inspection_record", targetProperty = "workOrderStep", displayName = "检验记录")
    private List<Map<String, Object>> inspectionRecordList;

    @OneToMany(targetModel = "waterproof_test", targetProperty = "workOrderStep", displayName = "防水测试")
    private List<Map<String, Object>> waterproofTestList;

    @OneToMany(targetModel = "defect_record", targetProperty = "workOrderStep", displayName = "不良记录")
    private List<Map<String, Object>> defectRecordList;

    @OneToMany(targetModel = "material_issue", targetProperty = "workOrderStep", displayName = "领料记录")
    private List<Map<String, Object>> materialIssueList;
}
