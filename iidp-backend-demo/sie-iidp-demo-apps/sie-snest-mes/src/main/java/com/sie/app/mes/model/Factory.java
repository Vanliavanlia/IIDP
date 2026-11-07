package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

/**
 * 工厂
 */
@Model(name = "factory",
        tableName = "ems_factory",
        displayName = "工厂",
        description = "工厂信息",
        isAutoLog = Bool.True
)
public class Factory extends BaseModel<Factory> {

    @Property(displayName = "工厂名称", displayForModel = true)
    private String name;

    @Property(displayName = "工厂编码")
    private String code;

    @Property(displayName = "地址")
    private String address;

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

    public String getAddress() {
        return getStr("address");
    }

    public void setAddress(String address) {
        this.set("address", address);
    }

    public String getDescription() {
        return getStr("description");
    }

    public void setDescription(String description) {
        this.set("description", description);
    }
}
