package com.sie.app.bin.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.Date;
import java.util.Map;

/**
 * 工序记录
 *
 * <p>一张工单按 {@code BinStages.ALL} 生成十条记录：钢材初加工 → 裁剪 → 定型 →
 * 上底漆 → 上面漆 → 上保护膜 → LOGO 制作 → 防水测试 → 打包装 → 发货。
 */
@Model(name = "bin_operation",
        tableName = "bin_operation",
        displayName = "工序记录",
        description = "工单的十道工序执行记录",
        isAutoLog = Bool.True
)
public class BinOperation extends BaseModel<BinOperation> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "工序号")
    private Integer seq;

    @Property(displayName = "工序")
    @Selection(values = {
            @Option(value = "steel", label = "钢材初加工"),
            @Option(value = "cutting", label = "裁剪"),
            @Option(value = "forming", label = "定型"),
            @Option(value = "primer", label = "上底漆"),
            @Option(value = "topcoat", label = "上面漆"),
            @Option(value = "film", label = "上保护膜"),
            @Option(value = "logo", label = "LOGO 制作"),
            @Option(value = "waterproof", label = "防水测试"),
            @Option(value = "packing", label = "打包装"),
            @Option(value = "shipping", label = "发货")
    })
    private String stage;

    @Property(displayName = "工序名称")
    private String stageName;

    @Property(displayName = "使用设备")
    private String machine;

    @Property(displayName = "操作工")
    private String operator;

    @Property(displayName = "开始时间", dataType = DataType.DATE_TIME)
    private Date startTime;

    @Property(displayName = "完成时间", dataType = DataType.DATE_TIME)
    private Date endTime;

    @Property(displayName = "执行状态")
    @Selection(values = {
            @Option(value = "pending", label = "待开工"),
            @Option(value = "running", label = "进行中"),
            @Option(value = "done", label = "已完成")
    })
    private String status;

    @Property(displayName = "质检结果")
    @Selection(values = {
            @Option(value = "pass", label = "合格"),
            @Option(value = "fail", label = "不合格"),
            @Option(value = "rework", label = "返工")
    })
    private String result;

    @Property(displayName = "备注")
    private String remark;

    /**
     * 开工：状态进行中，记录开始时间；工单同步置为「生产中」。
     */
    @MethodService(name = "start", auth = "start", description = "工序开工")
    public void start(String id) {
        BinOperation op = selectById(id);
        if (op == null) {
            throw new RuntimeException("工序记录不存在：" + id);
        }
        if ("done".equals(op.getStr("status"))) {
            throw new RuntimeException("该工序已完成，不能重复开工");
        }
        op.set("status", "running");
        op.set("startTime", new Date());
        op.update();

        String orderId = refId(op.get("order"));
        if (orderId != null) {
            BinOrder order = new BinOrder().selectById(orderId);
            if (order != null && "draft".equals(order.getStr("status"))) {
                order.set("status", "working");
                order.update();
            }
        }
    }

    /**
     * 完工：记录完成时间与质检结果；全部工序完成后工单转「待发货」。
     */
    @MethodService(name = "complete", auth = "complete", description = "工序完工")
    public void complete(String id) {
        BinOperation op = selectById(id);
        if (op == null) {
            throw new RuntimeException("工序记录不存在：" + id);
        }
        if (op.getStr("result") == null) {
            op.set("result", "pass");
        }
        op.set("status", "done");
        op.set("endTime", new Date());
        op.update();

        String orderId = refId(op.get("order"));
        if (orderId == null) {
            return;
        }
        long left = new BinOperation().count(
                Filter.AND(Filter.equal("order", orderId), Filter.notEqual("status", "done")));
        if (left == 0) {
            BinOrder order = new BinOrder().selectById(orderId);
            if (order != null) {
                order.set("status", "toship");
                order.update();
            }
        }
    }

    private static String refId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Object id = ((Map<?, ?>) value).get("id");
            return id == null ? null : id.toString();
        }
        return value.toString();
    }

    public Integer getSeq() {
        return getInt("seq");
    }

    public void setSeq(Integer seq) {
        this.set("seq", seq);
    }

    public String getStageName() {
        return getStr("stageName");
    }

    public void setStageName(String stageName) {
        this.set("stageName", stageName);
    }

    public String getStatus() {
        return getStr("status");
    }

    public void setStatus(String status) {
        this.set("status", status);
    }
}
