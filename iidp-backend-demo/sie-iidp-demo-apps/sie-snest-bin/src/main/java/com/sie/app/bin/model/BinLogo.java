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
 * LOGO 制作记录
 *
 * <p>在桶身正面制作客户 LOGO
 */
@Model(name = "bin_logo",
        tableName = "bin_logo",
        displayName = "LOGO 制作记录",
        description = "在桶身正面制作客户 LOGO",
        isAutoLog = Bool.True
)
public class BinLogo extends BaseModel<BinLogo> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "LOGO 归属客户")
    private String customerName;

    public String getCustomerName() {
        return getStr("customerName");
    }

    public void setCustomerName(String customerName) {
        this.set("customerName", customerName);
    }

    @Property(displayName = "制作位置")
    private String position;

    public String getPosition() {
        return getStr("position");
    }

    public void setPosition(String position) {
        this.set("position", position);
    }

    @Property(displayName = "LOGO 宽(mm)")
    private Integer logoWidth;

    public Integer getLogoWidth() {
        return getInt("logoWidth");
    }

    public void setLogoWidth(Integer logoWidth) {
        this.set("logoWidth", logoWidth);
    }

    @Property(displayName = "LOGO 高(mm)")
    private Integer logoHeight;

    public Integer getLogoHeight() {
        return getInt("logoHeight");
    }

    public void setLogoHeight(Integer logoHeight) {
        this.set("logoHeight", logoHeight);
    }

    @Property(displayName = "制作工艺")
    @Selection(values = {@Option(value = "silk", label = "丝网印刷"), @Option(value = "pad", label = "移印"), @Option(value = "laser", label = "激光雕刻"), @Option(value = "uv", label = "UV 打印")})
    private String method;

    @Property(displayName = "LOGO 文件")
    private String filePath;

    public String getFilePath() {
        return getStr("filePath");
    }

    public void setFilePath(String filePath) {
        this.set("filePath", filePath);
    }

    @Property(displayName = "制作人")
    private String operator;

    public String getOperator() {
        return getStr("operator");
    }

    public void setOperator(String operator) {
        this.set("operator", operator);
    }

    @Property(displayName = "完成日期", dataType = DataType.DATE)
    private java.util.Date finishDate;

    public java.util.Date getFinishDate() {
        return getDate("finishDate");
    }

    public void setFinishDate(java.util.Date finishDate) {
        this.set("finishDate", finishDate);
    }

    @Property(displayName = "检验结果")
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
