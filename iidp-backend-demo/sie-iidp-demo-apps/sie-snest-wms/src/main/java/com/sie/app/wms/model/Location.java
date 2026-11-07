package com.sie.app.wms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

/**
 * 库位
 */
@Model(name = "location",
        tableName = "wms_location",
        displayName = "库位",
        description = "库位信息",
        isAutoLog = Bool.True
)
public class Location extends BaseModel<Location> {

    @Property(displayName = "库位名称", displayForModel = true)
    private String name;

    @Property(displayName = "库位编码")
    private String code;

    @Property(displayName = "所属仓库")
    @ManyToOne
    @JoinColumn
    private Warehouse warehouse;

    @Property(displayName = "描述")
    private String description;

    public String getName() {
        return getStr("name");
    }

    public void setName(String name) {
        this.set("name", name);
    }

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    public Warehouse getWarehouse() {
        return (Warehouse) this.get("warehouse");
    }

    public void setWarehouse(Warehouse warehouse) {
        this.set("warehouse", warehouse);
    }

    public String getDescription() {
        return getStr("description");
    }

    public void setDescription(String description) {
        this.set("description", description);
    }
}
