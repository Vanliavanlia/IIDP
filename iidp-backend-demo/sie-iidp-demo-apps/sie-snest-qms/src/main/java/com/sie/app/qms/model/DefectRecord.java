package com.sie.app.qms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.Map;

/**
 * 不良记录
 */
@Model(name = "defect_record", tableName = "mes_defect_record", displayName = "不良记录",
        description = "记录检验发现的不良项及处理方式", isAutoLog = Bool.True, orderBy = "id desc")
public class DefectRecord extends BaseModel<DefectRecord> {

    @ManyToOne(targetModel = "inspection_record", targetProperty = "id", displayName = "检验记录")
    @JoinColumn(name = "inspection_record_id", referencedProperty = "id")
    private InspectionRecord inspectionRecord;

    @ManyToOne(targetModel = "work_order_step", targetProperty = "id", displayName = "工单工序任务")
    @JoinColumn(name = "work_order_step_id", referencedProperty = "id")
    private Map<String, Object> workOrderStep;

    @Property(displayName = "不良类型", length = 100, displayForModel = true)
    private String defectType;

    @Property(displayName = "不良数量")
    private Integer defectQuantity;

    @Property(displayName = "处理方式", length = 32)
    @Selection(values = {
            @Option(label = "返工", value = "REWORK"),
            @Option(label = "报废", value = "SCRAP"),
            @Option(label = "让步接收", value = "CONCESSION")
    })
    private String handlingMethod;

    @Property(displayName = "描述", dataType = DataType.TEXT)
    private String description;
}
