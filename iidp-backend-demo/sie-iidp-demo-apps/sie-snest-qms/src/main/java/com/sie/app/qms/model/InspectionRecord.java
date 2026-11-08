package com.sie.app.qms.model;

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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 检验记录
 */
@Model(name = "inspection_record", tableName = "mes_inspection_record", displayName = "检验记录",
        description = "记录工单工序任务的检验结果", isAutoLog = Bool.True, orderBy = "id desc")
public class InspectionRecord extends BaseModel<InspectionRecord> {

    @ManyToOne(targetModel = "work_order_step", targetProperty = "id", displayName = "工单工序任务")
    @JoinColumn(name = "work_order_step_id", referencedProperty = "id")
    private Map<String, Object> workOrderStep;

    @ManyToOne(targetModel = "inspection_plan", targetProperty = "id", displayName = "检验计划")
    @JoinColumn(name = "inspection_plan_id", referencedProperty = "id")
    private InspectionPlan inspectionPlan;

    @Property(displayName = "检验项", length = 200)
    private String inspectionItem;

    @Property(displayName = "结果", length = 32)
    @Selection(values = {
            @Option(label = "合格", value = "PASS"),
            @Option(label = "不合格", value = "FAIL")
    })
    private String result;

    @Property(displayName = "抽样数量")
    private Integer sampleQuantity;

    @Property(displayName = "不良数量")
    private Integer defectQuantity;

    @ManyToOne(targetModel = "employee", targetProperty = "id", displayName = "检验员")
    @JoinColumn(name = "inspector_id", referencedProperty = "id")
    private Map<String, Object> inspector;

    @Property(displayName = "检验时间", dataType = DataType.DATE_TIME, displayForModel = true)
    private Date inspectionTime;

    @Property(displayName = "备注", dataType = DataType.TEXT)
    private String remark;

    @OneToMany(targetModel = "defect_record", targetProperty = "inspectionRecord", displayName = "不良记录")
    private List<DefectRecord> defectRecordList;
}
