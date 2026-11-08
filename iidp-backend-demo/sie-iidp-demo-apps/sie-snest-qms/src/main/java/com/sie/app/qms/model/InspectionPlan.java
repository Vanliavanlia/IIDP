package com.sie.app.qms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;
import java.util.Map;

/**
 * 检验计划
 */
@Model(name = "inspection_plan", tableName = "mes_inspection_plan", displayName = "检验计划",
        description = "定义工序的检验项目与判定标准", isAutoLog = Bool.True, orderBy = "id desc")
public class InspectionPlan extends BaseModel<InspectionPlan> {

    @ManyToOne(targetModel = "process_step", targetProperty = "id", displayName = "所属工序")
    @JoinColumn(name = "process_step_id", referencedProperty = "id")
    private Map<String, Object> processStep;

    @Validate.NotBlank(message = "检验项不能为空")
    @Property(displayName = "检验项", length = 200, displayForModel = true)
    private String inspectionItem;

    @Property(displayName = "抽样规则", length = 200)
    private String samplingRule;

    @Property(displayName = "判定标准", length = 500)
    private String judgmentStandard;

    @Property(displayName = "是否必检", defaultValue = "false")
    private Boolean mandatory;

    @OneToMany(targetModel = "inspection_record", targetProperty = "inspectionPlan", displayName = "检验记录")
    private List<InspectionRecord> inspectionRecordList;
}
