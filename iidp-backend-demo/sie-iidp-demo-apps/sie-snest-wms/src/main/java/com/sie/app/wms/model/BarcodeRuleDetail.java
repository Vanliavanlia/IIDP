package com.sie.app.wms.model;

import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

/**
 * 编码规则明细：每个编码一行，记录当前已生成到第几个流水号。
 */
@Model(name = "barcode_rule_detail", description = "编码规则明细")
public class BarcodeRuleDetail extends BaseModel<BarcodeRuleDetail> {

    @Property(displayName = "编码")
    private String code;

    @Property(displayName = "当前流水号")
    private Integer seq;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        set("code", code);
    }

    public Integer getSeq() {
        return getInt("seq");
    }

    public void setSeq(Integer seq) {
        set("seq", seq);
    }
}
