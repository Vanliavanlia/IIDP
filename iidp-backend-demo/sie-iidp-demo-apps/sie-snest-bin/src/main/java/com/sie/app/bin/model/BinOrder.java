package com.sie.app.bin.model;

import com.sie.app.bin.util.BinCodeGenerator;
import com.sie.app.bin.util.BinStages;
import com.sie.snest.engine.api.jsonrpc.JsonRpcServiceResponse;
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
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 生产工单
 *
 * <p>一个工单 = 一批垃圾桶的生产任务：客户、型号、数量、面漆颜色、是否需要 LOGO。
 * 下达后用 {@code plan} 生成十道工序，之后每道工序由 {@link BinOperation} 记录。
 */
@Model(name = "bin_order",
        tableName = "bin_order",
        displayName = "生产工单",
        description = "按客户需求下达的垃圾桶生产工单",
        isAutoLog = Bool.True
)
public class BinOrder extends BaseModel<BinOrder> {

    @Property(displayName = "工单号", displayForModel = true)
    private String code;

    @Property(displayName = "客户")
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private BinCustomer customer;

    @Property(displayName = "垃圾桶型号")
    @ManyToOne
    @JoinColumn(name = "bin_model_id")
    private BinModel binModel;

    @Property(displayName = "生产数量")
    private Integer quantity;

    @Property(displayName = "面漆颜色")
    private String colorName;

    @Property(displayName = "是否加客户LOGO")
    @Selection(values = {
            @Option(value = "yes", label = "需要"),
            @Option(value = "no", label = "不需要")
    })
    private String logoRequired;

    @Property(displayName = "计划开工", dataType = DataType.DATE)
    private Date planStart;

    @Property(displayName = "交付日期", dataType = DataType.DATE)
    private Date dueDate;

    @Property(displayName = "工单状态")
    @Selection(values = {
            @Option(value = "draft", label = "待投产"),
            @Option(value = "working", label = "生产中"),
            @Option(value = "toship", label = "待发货"),
            @Option(value = "shipped", label = "已发货"),
            @Option(value = "done", label = "已完工")
    })
    private String status;

    @Property(displayName = "备注")
    private String remark;

    @Property(displayName = "工序计划")
    @OneToMany
    private List<BinOperation> operations;

    /**
     * 建工单：自动生成工单号 BZyyyyMMdd0001，并记录创建人。
     */
    @MethodService(name = "create", auth = "create", description = "创建生产工单（自动生成工单号）")
    public RecordSet create(RecordSet rs, List<BinOrder> valueList) {
        for (BinOrder item : valueList) {
            if (item.getStr("code") == null || item.getStr("code").isEmpty()) {
                item.setCode(BinCodeGenerator.orderCode());
            }
            item.setCreate_user(rs.getMeta().getUserId());
            if (item.getStr("status") == null) {
                item.set("status", "draft");
            }
        }
        return (RecordSet) rs.callSuper(BinOrder.class, "create", valueList);
    }

    /**
     * 按工艺路线生成该工单的十道工序（重复调用会先清空再重建）。
     */
    @MethodService(name = "plan", auth = "plan", description = "按工艺路线生成十道工序")
    public JsonRpcServiceResponse plan(String id) {
        BinOrder order = selectById(id);
        if (order == null) {
            throw new RuntimeException("工单不存在：" + id);
        }
        List<BinOperation> exists = new BinOperation().search(
                Filter.equal("order", id), Collections.singletonList("id"), 0, 0, null);
        for (BinOperation op : exists) {
            op.delete();
        }
        int seq = 1;
        for (String[] stage : BinStages.ALL) {
            BinOperation op = new BinOperation();
            op.set("order", id);
            op.set("seq", seq);
            op.set("stage", stage[0]);
            op.set("stageName", stage[1]);
            op.set("machine", stage[2]);
            op.set("status", "pending");
            op.create();
            seq++;
        }
        order.set("status", "working");
        order.update();
        return JsonRpcServiceResponse.reloadWindow();
    }

    /**
     * 工单投产：状态置为「生产中」。
     */
    @MethodService(name = "startProduction", auth = "startProduction", description = "工单投产")
    public JsonRpcServiceResponse startProduction(String id) {
        BinOrder order = selectById(id);
        if (order == null) {
            throw new RuntimeException("工单不存在：" + id);
        }
        order.set("status", "working");
        order.update();
        return JsonRpcServiceResponse.reloadWindow();
    }

    /**
     * 查询工单进度：已完成工序数 / 总工序数 + 下一道工序。
     */
    @MethodService(name = "progress", auth = "progress", description = "查询工单生产进度")
    public String progress(String id) {
        List<BinOperation> all = new BinOperation().search(
                Filter.equal("order", id), Arrays.asList("seq", "stageName", "status"), 0, 0, "seq");
        int done = 0;
        String next = "已全部完成";
        for (BinOperation op : all) {
            if ("done".equals(op.getStr("status"))) {
                done++;
            } else if ("已全部完成".equals(next)) {
                next = op.getStr("stageName");
            }
        }
        return "进度 " + done + "/" + all.size() + "，下一道：" + next;
    }

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    public Integer getQuantity() {
        return getInt("quantity");
    }

    public void setQuantity(Integer quantity) {
        this.set("quantity", quantity);
    }

    public String getStatus() {
        return getStr("status");
    }

    public void setStatus(String status) {
        this.set("status", status);
    }
}
