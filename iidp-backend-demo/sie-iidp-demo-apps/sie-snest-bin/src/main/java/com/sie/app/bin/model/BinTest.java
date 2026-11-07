package com.sie.app.bin.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

/**
 * 防水测试
 *
 * <p>成品防水性能检测记录
 */
@Model(name = "bin_test",
        tableName = "bin_test",
        displayName = "防水测试",
        description = "成品防水性能检测记录",
        isAutoLog = Bool.True
)
public class BinTest extends BaseModel<BinTest> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "测试水压(kPa)")
    private Double testPressure;

    public Double getTestPressure() {
        return getDouble("testPressure");
    }

    public void setTestPressure(Double testPressure) {
        this.set("testPressure", testPressure);
    }

    @Property(displayName = "测试时长(秒)")
    private Integer duration;

    public Integer getDuration() {
        return getInt("duration");
    }

    public void setDuration(Integer duration) {
        this.set("duration", duration);
    }

    @Property(displayName = "抽检数量")
    private Integer sampleQty;

    public Integer getSampleQty() {
        return getInt("sampleQty");
    }

    public void setSampleQty(Integer sampleQty) {
        this.set("sampleQty", sampleQty);
    }

    @Property(displayName = "渗漏位置")
    private String leakPoint;

    public String getLeakPoint() {
        return getStr("leakPoint");
    }

    public void setLeakPoint(String leakPoint) {
        this.set("leakPoint", leakPoint);
    }

    @Property(displayName = "检验员")
    private String inspector;

    public String getInspector() {
        return getStr("inspector");
    }

    public void setInspector(String inspector) {
        this.set("inspector", inspector);
    }

    @Property(displayName = "测试日期", dataType = DataType.DATE)
    private java.util.Date testDate;

    public java.util.Date getTestDate() {
        return getDate("testDate");
    }

    public void setTestDate(java.util.Date testDate) {
        this.set("testDate", testDate);
    }

    @Property(displayName = "测试结论")
    @Selection(values = {@Option(value = "pass", label = "合格"), @Option(value = "fail", label = "不合格"), @Option(value = "rework", label = "返工")})
    private String result;

    @Property(displayName = "备注")
    private String remark;

    public String getRemark() {
        return getStr("remark");
    }

    public void setRemark(String remark) {
        this.set("remark", remark);
    }
}
