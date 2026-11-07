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
 * 包装记录
 *
 * <p>成品打包记录
 */
@Model(name = "bin_packing",
        tableName = "bin_packing",
        displayName = "包装记录",
        description = "成品打包记录",
        isAutoLog = Bool.True
)
public class BinPacking extends BaseModel<BinPacking> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "包装方式")
    @Selection(values = {@Option(value = "carton", label = "纸箱"), @Option(value = "wooden", label = "木箱"), @Option(value = "pallet", label = "托盘")})
    private String boxType;

    @Property(displayName = "每箱数量")
    private Integer qtyPerBox;

    public Integer getQtyPerBox() {
        return getInt("qtyPerBox");
    }

    public void setQtyPerBox(Integer qtyPerBox) {
        this.set("qtyPerBox", qtyPerBox);
    }

    @Property(displayName = "箱数")
    private Integer boxCount;

    public Integer getBoxCount() {
        return getInt("boxCount");
    }

    public void setBoxCount(Integer boxCount) {
        this.set("boxCount", boxCount);
    }

    @Property(displayName = "毛重(kg)")
    private Double grossWeight;

    public Double getGrossWeight() {
        return getDouble("grossWeight");
    }

    public void setGrossWeight(Double grossWeight) {
        this.set("grossWeight", grossWeight);
    }

    @Property(displayName = "打包日期", dataType = DataType.DATE)
    private java.util.Date packDate;

    public java.util.Date getPackDate() {
        return getDate("packDate");
    }

    public void setPackDate(java.util.Date packDate) {
        this.set("packDate", packDate);
    }

    @Property(displayName = "打包人")
    private String operator;

    public String getOperator() {
        return getStr("operator");
    }

    public void setOperator(String operator) {
        this.set("operator", operator);
    }

    @Property(displayName = "备注")
    private String remark;

    public String getRemark() {
        return getStr("remark");
    }

    public void setRemark(String remark) {
        this.set("remark", remark);
    }
}
