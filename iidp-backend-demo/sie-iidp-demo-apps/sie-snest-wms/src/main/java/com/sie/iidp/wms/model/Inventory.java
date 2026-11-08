package com.sie.iidp.wms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

/**
 * 库存
 */
@Model(name = "inventory", tableName = "mes_inventory", displayName = "库存",
        description = "按物料、批次与库位记录的库存数量", isAutoLog = Bool.True)
public class Inventory extends BaseModel<Inventory> {

    @ManyToOne(targetModel = "material", targetProperty = "id", displayName = "物料")
    @JoinColumn(name = "material_id", referencedProperty = "id")
    private Material material;

    @Property(displayName = "批次号", length = 64, displayForModel = true)
    private String batchNo;

    @Property(displayName = "库位", length = 100)
    private String location;

    @Property(displayName = "数量")
    private Double quantity;

    @Property(displayName = "库存类型", length = 32, defaultValue = "RAW")
    @Selection(values = {
            @Option(label = "原料", value = "RAW"),
            @Option(label = "半成品", value = "SEMI"),
            @Option(label = "成品", value = "FINISHED")
    })
    private String inventoryType;
}
