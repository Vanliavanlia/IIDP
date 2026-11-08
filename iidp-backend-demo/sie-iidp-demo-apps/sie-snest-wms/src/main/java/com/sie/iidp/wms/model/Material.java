package com.sie.iidp.wms.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;
import java.util.Map;

/**
 * 物料
 */
@Model(name = "material", tableName = "mes_material", displayName = "物料",
        description = "生产所需的原料、半成品与成品", isAutoLog = Bool.True)
public class Material extends BaseModel<Material> {

    @Property(displayName = "物料编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "物料类型", length = 32, defaultValue = "RAW")
    @Selection(values = {
            @Option(label = "原料", value = "RAW"),
            @Option(label = "半成品", value = "SEMI"),
            @Option(label = "成品", value = "FINISHED")
    })
    private String materialType;

    @Property(displayName = "单位", length = 32)
    private String unit;

    @Property(displayName = "规格", length = 200)
    private String spec;

    @Property(displayName = "安全库存")
    private Double safetyStock;

    @Property(displayName = "状态", length = 32, defaultValue = "ENABLED")
    @Selection(values = {
            @Option(label = "启用", value = "ENABLED"),
            @Option(label = "停用", value = "DISABLED")
    })
    private String status;

    @OneToMany(targetModel = "product_bom", targetProperty = "material", displayName = "产品BOM")
    private List<Map<String, Object>> productBomList;

    @OneToMany(targetModel = "inventory", targetProperty = "material", displayName = "库存")
    private List<Inventory> inventoryList;

    @OneToMany(targetModel = "material_issue", targetProperty = "material", displayName = "领料单")
    private List<MaterialIssue> materialIssueList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "material"));
        }
        return (RecordSet) getMeta().get("material").callSuper(Material.class, "create", valuesList);
    }
}
