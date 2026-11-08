package com.sie.app.cms.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;

/**
 * 客户
 */
@Model(name = "customer", tableName = "mes_customer", displayName = "客户",
        description = "客户主数据", isAutoLog = Bool.True, orderBy = "id desc")
public class Customer extends BaseModel<Customer> {

    @Property(displayName = "客户编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "联系人", length = 100)
    private String contact;

    @Property(displayName = "电话", length = 32)
    private String phone;

    @Property(displayName = "地址", length = 500)
    private String address;

    @OneToMany(targetModel = "sales_order", targetProperty = "customer", displayName = "销售订单")
    private List<SalesOrder> salesOrderList;

    @OneToMany(targetModel = "logo_spec", targetProperty = "customer", displayName = "LOGO规格")
    private List<LogoSpec> logoSpecList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "customer"));
        }
        return (RecordSet) getMeta().get("customer").callSuper(Customer.class, "create", valuesList);
    }
}
