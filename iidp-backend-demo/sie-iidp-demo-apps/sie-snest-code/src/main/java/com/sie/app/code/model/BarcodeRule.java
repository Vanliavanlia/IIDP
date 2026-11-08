package com.sie.app.code.model;

import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Model(name = "barcode_rule", description = "编码规则")
public class BarcodeRule extends BaseModel<BarcodeRule> {

    @Property(displayName = "编码")
    private String code;

    @Property(displayName = "前缀")
    private String prefix;

    @Property(displayName = "长度")
    private Integer length;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        set("code", code);
    }

    public String getPrefix() {
        return getStr("prefix");
    }

    public void setPrefix(String prefix) {
        set("prefix", prefix);
    }

    public Integer getLength() {
        return getInt("length");
    }

    public void setLength(Integer length) {
        set("length", length);
    }

    @MethodService
    public String generateCode(String code) {
        List<BarcodeRule> rules = search(Filter.equal("code", code),
                Collections.singletonList("*"), 0, 0, null);
        if (CollectionUtils.isEmpty(rules)) {
            throw new RuntimeException("编码规则不存在");
        }
        String prefix = rules.get(0).getPrefix();
        Integer length = rules.get(0).getLength();

        List<BarcodeRuleDetail> details = new BarcodeRuleDetail()
                .search(Filter.equal("code", code),
                Collections.singletonList("*"), 0, 0, null);

        int seq = 1;

        if (CollectionUtils.isEmpty(details)) {
            BarcodeRuleDetail detail = new BarcodeRuleDetail();
            detail.setCode(code);
            detail.setSeq(seq);
            detail.create();
        } else {
            BarcodeRuleDetail detail = details.get(0);
            Integer curSeq = detail.getSeq();
            seq = curSeq + 1;
            detail.setSeq(seq);
            detail.update();
        }

        return prefix + String.format("%0" + length + "d", seq);
    }
}
