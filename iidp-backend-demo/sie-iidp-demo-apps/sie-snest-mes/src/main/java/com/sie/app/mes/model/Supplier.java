package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;


@Model(name = "supplier",displayName = "供应商",isAutoLog = Bool.True)
public class Supplier extends BaseModel<Supplier> {

    @Property(displayName = "名称",displayForModel = true)
    private String name;

    @Property(columnName = "phone",displayName = "联系方式")
    @Validate.Phone(message = "联系方式不正确")
    private String phone;


    @Property(displayName = "地址")
    private String address;

    @OneToMany
    private List<Material> materialList;


}
