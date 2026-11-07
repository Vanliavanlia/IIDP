package com.sie.app.wms.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;

import java.util.List;
import java.util.Map;

/**
 * 模型扩展：name 与 parent 相同（order == order）表示在 WMS 应用里扩展 MES 的 order 模型。
 * 重写 create，入库前用编码规则生成订单编号，再 callSuper 调回父模型（MES）的 create。
 */
@Model(name = "order", parent = "order")
public class OrderExt extends BaseModel<OrderExt> {

    @MethodService(description = "新建订单时按编码规则生成订单编号")
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            String orderCode = new BarcodeRule().generateCode("order");
            map.put("code", orderCode);
        }

        return (RecordSet) getMeta().get("order")
                .callSuper(OrderExt.class, "create", valuesList);
    }
}
