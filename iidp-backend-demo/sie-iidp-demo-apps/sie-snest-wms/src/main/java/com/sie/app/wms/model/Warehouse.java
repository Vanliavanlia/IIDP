package com.sie.app.wms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;

import java.util.List;

/**
 * 仓库
 */
@Model(name = "warehouse",
        tableName = "wms_warehouse",
        displayName = "仓库",
        description = "仓库信息",
        isAutoLog = Bool.True
)
public class Warehouse extends BaseModel<Warehouse> {

    @Property(displayName = "仓库名称", displayForModel = true)
    private String name;

    @Property(displayName = "仓库编码")
    private String code;

    @Property(displayName = "仓库地址")
    private String address;

    @Property(displayName = "负责人")
    private String manager;

    @Property(displayName = "描述")
    private String description;

    @Property(displayName = "库位列表")
    @OneToMany
    private List<Location> locations;

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

    public String getAddress() {
        return getStr("address");
    }

    public void setAddress(String address) {
        this.set("address", address);
    }

    public String getManager() {
        return getStr("manager");
    }

    public void setManager(String manager) {
        this.set("manager", manager);
    }

    public String getDescription() {
        return getStr("description");
    }

    public void setDescription(String description) {
        this.set("description", description);
    }
}
