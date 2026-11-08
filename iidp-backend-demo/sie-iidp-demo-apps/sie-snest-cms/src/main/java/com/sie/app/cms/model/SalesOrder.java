package com.sie.app.cms.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单
 */
@Model(name = "sales_order", tableName = "mes_sales_order", displayName = "销售订单",
        description = "客户销售订单", isAutoLog = Bool.True, orderBy = "id desc")
public class SalesOrder extends BaseModel<SalesOrder> {

    @Property(displayName = "订单号", length = 64, unique = true, displayForModel = true)
    private String orderNo;

    @ManyToOne(targetModel = "customer", targetProperty = "id", displayName = "客户")
    @JoinColumn(name = "customer_id", referencedProperty = "id")
    private Customer customer;

    @Property(displayName = "下单日期", dataType = DataType.DATE)
    private Date orderDate;

    @Property(displayName = "交期", dataType = DataType.DATE)
    private Date deliveryDate;

    @Property(displayName = "状态", length = 32, defaultValue = "CREATED")
    @Selection(values = {
            @Option(label = "已创建", value = "CREATED"),
            @Option(label = "已确认", value = "CONFIRMED"),
            @Option(label = "生产中", value = "IN_PRODUCTION"),
            @Option(label = "已完成", value = "COMPLETED"),
            @Option(label = "已取消", value = "CANCELLED")
    })
    private String status;

    @Property(displayName = "总金额", dataType = DataType.BIG_DECIMAL, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(targetModel = "sales_order_line", targetProperty = "salesOrder", displayName = "订单行")
    private List<SalesOrderLine> salesOrderLineList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("orderNo", getMeta().get("barcode_rule").call("generateCode", "sales_order"));
        }
        return (RecordSet) getMeta().get("sales_order").callSuper(SalesOrder.class, "create", valuesList);
    }
}
