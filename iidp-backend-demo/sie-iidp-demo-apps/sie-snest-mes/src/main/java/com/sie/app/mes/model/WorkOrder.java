package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;

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
 * 生产工单
 */
@Model(name = "work_order", tableName = "mes_work_order", displayName = "生产工单",
        description = "下达给车间的生产工单", isAutoLog = Bool.True, orderBy = "id desc")
public class WorkOrder extends BaseModel<WorkOrder> {

    @Property(displayName = "工单号", length = 64, unique = true, displayForModel = true)
    private String workOrderNo;

    @ManyToOne(targetModel = "sales_order_line", targetProperty = "id", displayName = "订单行")
    @JoinColumn(name = "sales_order_line_id", referencedProperty = "id")
    private Map<String, Object> salesOrderLine;

    @ManyToOne(targetModel = "product", targetProperty = "id", displayName = "产品")
    @JoinColumn(name = "product_id", referencedProperty = "id")
    private Product product;

    @Property(displayName = "计划数量")
    private Integer plannedQuantity;

    @Property(displayName = "完成数量")
    private Integer completedQuantity;

    @Property(displayName = "计划开始日期", dataType = DataType.DATE_TIME)
    private Date plannedStartDate;

    @Property(displayName = "计划结束日期", dataType = DataType.DATE_TIME)
    private Date plannedEndDate;

    @Property(displayName = "状态", length = 32, defaultValue = "CREATED")
    @Selection(values = {
            @Option(label = "已创建", value = "CREATED"),
            @Option(label = "已释放", value = "RELEASED"),
            @Option(label = "进行中", value = "IN_PROGRESS"),
            @Option(label = "已完成", value = "COMPLETED"),
            @Option(label = "已关闭", value = "CLOSED"),
            @Option(label = "已取消", value = "CANCELLED")
    })
    private String status;

    @Property(displayName = "优先级", length = 32, defaultValue = "NORMAL")
    @Selection(values = {
            @Option(label = "低", value = "LOW"),
            @Option(label = "普通", value = "NORMAL"),
            @Option(label = "高", value = "HIGH"),
            @Option(label = "紧急", value = "URGENT")
    })
    private String priority;

    @OneToMany(targetModel = "work_order_step", targetProperty = "workOrder", displayName = "工单工序任务")
    private List<WorkOrderStep> workOrderStepList;

    @OneToMany(targetModel = "material_issue", targetProperty = "workOrder", displayName = "领料记录")
    private List<Map<String, Object>> materialIssueList;

    @OneToMany(targetModel = "wip", targetProperty = "workOrder", displayName = "在制品")
    private List<Wip> wipList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("workOrderNo", getMeta().get("barcode_rule").call("generateCode", "work_order"));
        }
        return (RecordSet) getMeta().get("work_order").callSuper(WorkOrder.class, "create", valuesList);
    }
}
