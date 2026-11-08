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

import java.util.Date;
import java.util.Map;

/**
 * 防水测试
 */
@Model(name = "waterproof_test", tableName = "mes_waterproof_test", displayName = "防水测试",
        description = "记录工单工序任务的防水测试结果", isAutoLog = Bool.True, orderBy = "id desc")
public class WaterproofTest extends BaseModel<WaterproofTest> {

    @ManyToOne(targetModel = "work_order_step", targetProperty = "id", displayName = "工单工序任务")
    @JoinColumn(name = "work_order_step_id", referencedProperty = "id")
    private Map<String, Object> workOrderStep;

    @Property(displayName = "测试批次", length = 64, displayForModel = true)
    private String testBatchNo;

    @Property(displayName = "测试数量")
    private Integer testQuantity;

    @Property(displayName = "测试标准", length = 200)
    private String testStandard;

    @Property(displayName = "结果", length = 32)
    @Selection(values = {
            @Option(label = "通过", value = "PASS"),
            @Option(label = "不通过", value = "FAIL")
    })
    private String result;

    @Property(displayName = "渗漏数量")
    private Integer leakageQuantity;

    @ManyToOne(targetModel = "employee", targetProperty = "id", displayName = "测试员")
    @JoinColumn(name = "tester_id", referencedProperty = "id")
    private Map<String, Object> tester;

    @Property(displayName = "测试时间", dataType = DataType.DATE_TIME)
    private Date testTime;

    @Property(displayName = "备注", dataType = DataType.TEXT)
    private String remark;
}
