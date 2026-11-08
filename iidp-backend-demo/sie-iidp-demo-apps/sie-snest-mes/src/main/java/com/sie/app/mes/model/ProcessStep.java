package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;
import java.util.Map;

/**
 * 工序
 */
@Model(name = "process_step", tableName = "mes_process_step", displayName = "工序",
        description = "工艺路线下的工序节点", isAutoLog = Bool.True, orderBy = "sequence asc")
public class ProcessStep extends BaseModel<ProcessStep> {

    @ManyToOne(targetModel = "process", targetProperty = "id", displayName = "所属工艺")
    @JoinColumn(name = "process_id", referencedProperty = "id")
    private Process process;

    @Property(displayName = "工序序号")
    private Integer sequence;

    @Property(displayName = "工序编码", length = 64)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "工序类型", length = 32)
    @Selection(values = {
            @Option(label = "裁剪", value = "CUTTING"),
            @Option(label = "定型", value = "SHAPING"),
            @Option(label = "底漆", value = "PRIMER"),
            @Option(label = "面漆", value = "TOPCOAT"),
            @Option(label = "保护漆", value = "CLEARCOAT"),
            @Option(label = "LOGO", value = "LOGO"),
            @Option(label = "防水测试", value = "WATERPROOF_TEST")
    })
    private String stepType;

    @Property(displayName = "标准工时")
    private Double standardHours;

    @Property(displayName = "是否关键工序", defaultValue = "false")
    private Boolean keyStep;

    @Property(displayName = "是否质量门", defaultValue = "false")
    private Boolean qualityGate;

    @OneToMany(targetModel = "process_step_parameter", targetProperty = "processStep", displayName = "工序参数")
    private List<ProcessStepParameter> parameterList;

    @OneToMany(targetModel = "inspection_plan", targetProperty = "processStep", displayName = "检验计划")
    private List<Map<String, Object>> inspectionPlanList;

    @OneToMany(targetModel = "work_order_step", targetProperty = "processStep", displayName = "工单工序")
    private List<WorkOrderStep> workOrderStepList;

    @OneToMany(targetModel = "wip", targetProperty = "currentStep", displayName = "在制品")
    private List<Wip> wipList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "process_step"));
        }
        return (RecordSet) getMeta().get("process_step").callSuper(ProcessStep.class, "create", valuesList);
    }
}
