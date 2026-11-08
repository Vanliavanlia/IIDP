package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.Map;

/**
 * 产品物料清单
 */
@Model(name = "product_bom", tableName = "mes_product_bom", displayName = "产品物料清单",
        description = "产品与组成物料之间的用量关系", isAutoLog = Bool.True)
public class ProductBom extends BaseModel<ProductBom> {

    @ManyToOne(targetModel = "product", targetProperty = "id", displayName = "父产品")
    @JoinColumn(name = "product_id", referencedProperty = "id")
    private Product product;

    @ManyToOne(targetModel = "material", targetProperty = "id", displayName = "子物料")
    @JoinColumn(name = "material_id", referencedProperty = "id")
    private Map<String, Object> material;

    @Property(displayName = "用量")
    private Double amount;

    @Property(displayName = "单位", length = 32)
    private String unit;

    @Property(displayName = "BOM版本", length = 32, displayForModel = true)
    private String version;
}
