package com.sie.app.mes.model;

import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

/**
 * 区域模型的「扩展」
 * <p>
 * name == parent 表示扩展：不新建模型、不新建表，直接在 area 模型上追加属性，
 * 并可以重写 area 原有服务（写一个同名 @MethodService 方法即可覆盖）。
 * 表结构上表现为 ems_area 多出 area_manager 一列。
 */
@Model(name = "area", parent = "area")
public class AreaExt extends BaseModel<AreaExt> {

    @Property(displayName = "区域负责人")
    private String areaManager;

    public String getAreaManager() {
        return getStr("areaManager");
    }

    public void setAreaManager(String areaManager) {
        set("areaManager", areaManager);
    }
}
