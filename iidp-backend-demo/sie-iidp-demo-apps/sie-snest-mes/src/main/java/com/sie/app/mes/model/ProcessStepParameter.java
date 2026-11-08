package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

/**
 * 工序参数
 */
@Model(name = "process_step_parameter", tableName = "mes_process_step_parameter", displayName = "工序参数",
        description = "工序的标准参数值及上下限", isAutoLog = Bool.True)
public class ProcessStepParameter extends BaseModel<ProcessStepParameter> {

    @ManyToOne(targetModel = "process_step", targetProperty = "id", displayName = "所属工序")
    @JoinColumn(name = "process_step_id", referencedProperty = "id")
    private ProcessStep processStep;

    @Property(displayName = "参数名", length = 100, displayForModel = true)
    private String name;

    @Property(displayName = "标准值")
    private Double standardValue;

    @Property(displayName = "单位", length = 32)
    private String unit;

    @Property(displayName = "下限")
    private Double lowerLimit;

    @Property(displayName = "上限")
    private Double upperLimit;
}
