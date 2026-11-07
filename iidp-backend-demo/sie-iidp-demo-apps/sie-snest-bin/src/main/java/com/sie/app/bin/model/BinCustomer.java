package com.sie.app.bin.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

/**
 * 客户
 *
 * <p>客户档案（LOGO 与发货对象）
 */
@Model(name = "bin_customer",
        tableName = "bin_customer",
        displayName = "客户",
        description = "客户档案（LOGO 与发货对象）",
        isAutoLog = Bool.True
)
public class BinCustomer extends BaseModel<BinCustomer> {

    @Property(displayName = "客户名称", displayForModel = true)
    private String name;

    public String getName() {
        return getStr("name");
    }

    public void setName(String name) {
        this.set("name", name);
    }

    @Property(displayName = "客户编码")
    private String code;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    @Property(displayName = "联系人")
    private String contact;

    public String getContact() {
        return getStr("contact");
    }

    public void setContact(String contact) {
        this.set("contact", contact);
    }

    @Property(displayName = "联系电话")
    private String phone;

    public String getPhone() {
        return getStr("phone");
    }

    public void setPhone(String phone) {
        this.set("phone", phone);
    }

    @Property(displayName = "收货地址")
    private String address;

    public String getAddress() {
        return getStr("address");
    }

    public void setAddress(String address) {
        this.set("address", address);
    }

    @Property(displayName = "备注")
    private String remark;

    public String getRemark() {
        return getStr("remark");
    }

    public void setRemark(String remark) {
        this.set("remark", remark);
    }
}
