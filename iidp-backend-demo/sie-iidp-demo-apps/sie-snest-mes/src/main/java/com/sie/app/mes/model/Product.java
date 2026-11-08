package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;
import java.util.Map;

/**
 * 产品
 */
@Model(name = "product", tableName = "mes_product", displayName = "产品",
        description = "垃圾桶型号的静态属性", isAutoLog = Bool.True, orderBy = "id desc")
public class Product extends BaseModel<Product> {

    @Property(displayName = "产品编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "规格描述", dataType = DataType.TEXT)
    private String specDescription;

    @Property(displayName = "容量（升）")
    private Double capacity;

    @Property(displayName = "主体材质", length = 100)
    private String mainMaterial;

    @Property(displayName = "状态", length = 32, defaultValue = "ENABLED")
    @Selection(values = {
            @Option(label = "启用", value = "ENABLED"),
            @Option(label = "停用", value = "DISABLED")
    })
    private String status;

    @OneToMany(targetModel = "product_bom", targetProperty = "product", displayName = "产品BOM")
    private List<ProductBom> productBomList;

    @OneToMany(targetModel = "process", targetProperty = "product", displayName = "工艺路线")
    private List<Process> processList;

    @OneToMany(targetModel = "sales_order_line", targetProperty = "product", displayName = "销售订单行")
    private List<Map<String, Object>> salesOrderLineList;

    @OneToMany(targetModel = "work_order", targetProperty = "product", displayName = "工单")
    private List<WorkOrder> workOrderList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "product"));
        }
        return (RecordSet) getMeta().get("product").callSuper(Product.class, "create", valuesList);
    }
}
