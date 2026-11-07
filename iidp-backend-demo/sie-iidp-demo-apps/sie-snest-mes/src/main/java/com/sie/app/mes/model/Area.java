package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;

import java.util.List;

/**
 * 区域（设备所属区域）
 */
@Model(name = "area",
        tableName = "ems_area",
        displayName = "区域",
        description = "区域信息",
        isAutoLog = Bool.True
)
public class Area extends BaseModel<Area> {

    @Property(displayName = "区域名称", displayForModel = true)
    private String name;

    @Property(displayName = "区域编码")
    private String code;

    @Property(displayName = "描述")
    private String description;

    @Property(displayName = "设备列表")
    @OneToMany
    private List<Device> devices;

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

    public String getDescription() {
        return getStr("description");
    }

    public void setDescription(String description) {
        this.set("description", description);
    }
}
