package com.sie.iidp.wms.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.List;
import java.util.Map;

import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

@Model(name = "device", displayName = "设备")
public class Device extends BaseModel<Device> {

    @Property(displayName = "名称", displayForModel = true)
    private String name;

    @Property(displayName = "编码")
    private String code;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "device"));
        }
        return (RecordSet) getMeta().get("device").callSuper(Device.class, "create", valuesList);
    }
}
