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
 * 喷涂记录
 *
 * <p>底漆、面漆与保护膜三段喷涂工艺参数
 */
@Model(name = "bin_paint",
        tableName = "bin_paint",
        displayName = "喷涂记录",
        description = "底漆、面漆与保护膜三段喷涂工艺参数",
        isAutoLog = Bool.True
)
public class BinPaint extends BaseModel<BinPaint> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "底漆型号")
    private String primerCode;

    public String getPrimerCode() {
        return getStr("primerCode");
    }

    public void setPrimerCode(String primerCode) {
        this.set("primerCode", primerCode);
    }

    @Property(displayName = "底漆批次")
    private String primerBatch;

    public String getPrimerBatch() {
        return getStr("primerBatch");
    }

    public void setPrimerBatch(String primerBatch) {
        this.set("primerBatch", primerBatch);
    }

    @Property(displayName = "面漆色号")
    private String colorCode;

    public String getColorCode() {
        return getStr("colorCode");
    }

    public void setColorCode(String colorCode) {
        this.set("colorCode", colorCode);
    }

    @Property(displayName = "面漆颜色")
    private String colorName;

    public String getColorName() {
        return getStr("colorName");
    }

    public void setColorName(String colorName) {
        this.set("colorName", colorName);
    }

    @Property(displayName = "喷涂遍数")
    private Integer coatCount;

    public Integer getCoatCount() {
        return getInt("coatCount");
    }

    public void setCoatCount(Integer coatCount) {
        this.set("coatCount", coatCount);
    }

    @Property(displayName = "保护膜型号")
    private String filmType;

    public String getFilmType() {
        return getStr("filmType");
    }

    public void setFilmType(String filmType) {
        this.set("filmType", filmType);
    }

    @Property(displayName = "烘干温度(℃)")
    private Integer ovenTemp;

    public Integer getOvenTemp() {
        return getInt("ovenTemp");
    }

    public void setOvenTemp(Integer ovenTemp) {
        this.set("ovenTemp", ovenTemp);
    }

    @Property(displayName = "喷涂日期", dataType = DataType.DATE)
    private java.util.Date paintDate;

    public java.util.Date getPaintDate() {
        return getDate("paintDate");
    }

    public void setPaintDate(java.util.Date paintDate) {
        this.set("paintDate", paintDate);
    }

    @Property(displayName = "喷涂工")
    private String operator;

    public String getOperator() {
        return getStr("operator");
    }

    public void setOperator(String operator) {
        this.set("operator", operator);
    }

    @Property(displayName = "外观检验")
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
