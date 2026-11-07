package com.sie.app.wms.model;

import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;

import java.util.Collections;
import java.util.List;

/**
 * 编码规则：一行规则 = 编码(key) + 前缀 + 流水号长度。
 * 流水号由 BarcodeRuleDetail 按 编码 记录当前值，generateCode 每次调用自增并返回拼接结果。
 */
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

    /**
     * 按编码规则生成下一个编码。
     * <p>
     * 并发说明：这里做了「查明细 → 序号 +1 → 更新」三步，非原子。单实例部署时用
     * {@code synchronized} 串行化够用；多实例/集群部署必须换成数据库层的原子自增
     * （例如 {@code UPDATE ... SET seq = seq + 1} 后回查，或对明细行加行锁），
     * 否则两个 pod 同时取号会拿到同一个流水号。
     */
    @MethodService(description = "按编码规则生成下一个编码")
    public synchronized String generateCode(String code) {
        List<BarcodeRule> rules = search(Filter.equal("code", code),
                Collections.singletonList("*"), 0, 0, null);
        if (rules == null || rules.isEmpty()) {
            throw new RuntimeException("编码规则不存在");
        }
        String prefix = rules.get(0).getPrefix();
        Integer length = rules.get(0).getLength();

        List<BarcodeRuleDetail> details = new BarcodeRuleDetail()
                .search(Filter.equal("code", code),
                        Collections.singletonList("*"), 0, 0, null);

        int seq = 1;

        if (details == null || details.isEmpty()) {
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
