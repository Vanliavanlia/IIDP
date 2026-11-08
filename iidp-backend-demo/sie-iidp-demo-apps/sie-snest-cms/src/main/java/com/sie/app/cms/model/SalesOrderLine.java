package com.sie.app.cms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.List;
import java.util.Map;

/**
 * 订单行（LOGO 规格由 logo_spec 侧的一对一外键维护）
 */
@Model(name = "sales_order_line", tableName = "mes_sales_order_line", displayName = "订单行",
        description = "销售订单明细行", isAutoLog = Bool.True, orderBy = "id desc")
public class SalesOrderLine extends BaseModel<SalesOrderLine> {

    @ManyToOne(targetModel = "sales_order", targetProperty = "id", displayName = "所属订单")
    @JoinColumn(name = "sales_order_id", referencedProperty = "id")
    private SalesOrder salesOrder;

    @ManyToOne(targetModel = "product", targetProperty = "id", displayName = "产品")
    @JoinColumn(name = "product_id", referencedProperty = "id")
    private Map<String, Object> product;

    @Property(displayName = "数量")
    private Integer quantity;

    @Property(displayName = "面漆颜色", length = 64, displayForModel = true)
    private String topcoatColor;

    @Property(displayName = "是否需要LOGO", defaultValue = "false")
    private Boolean needLogo;

    @Property(displayName = "状态", length = 32, defaultValue = "CREATED")
    @Selection(values = {
            @Option(label = "已创建", value = "CREATED"),
            @Option(label = "生产中", value = "IN_PRODUCTION"),
            @Option(label = "已完成", value = "COMPLETED"),
            @Option(label = "已取消", value = "CANCELLED")
    })
    private String status;

    @OneToMany(targetModel = "work_order", targetProperty = "salesOrderLine", displayName = "生产工单")
    private List<Map<String, Object>> workOrderList;
}
