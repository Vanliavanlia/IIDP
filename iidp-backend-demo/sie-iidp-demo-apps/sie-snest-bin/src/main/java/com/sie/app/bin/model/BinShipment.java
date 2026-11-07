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
 * 发货单
 *
 * <p>成品发货与物流跟踪
 */
@Model(name = "bin_shipment",
        tableName = "bin_shipment",
        displayName = "发货单",
        description = "成品发货与物流跟踪",
        isAutoLog = Bool.True
)
public class BinShipment extends BaseModel<BinShipment> {

    @Property(displayName = "生产工单")
    @ManyToOne
    @JoinColumn(name = "order_id")
    private BinOrder order;

    @Property(displayName = "发货单号")
    private String shipNo;

    public String getShipNo() {
        return getStr("shipNo");
    }

    public void setShipNo(String shipNo) {
        this.set("shipNo", shipNo);
    }

    @Property(displayName = "承运商")
    private String carrier;

    public String getCarrier() {
        return getStr("carrier");
    }

    public void setCarrier(String carrier) {
        this.set("carrier", carrier);
    }

    @Property(displayName = "物流单号")
    private String trackingNo;

    public String getTrackingNo() {
        return getStr("trackingNo");
    }

    public void setTrackingNo(String trackingNo) {
        this.set("trackingNo", trackingNo);
    }

    @Property(displayName = "发货数量")
    private Integer quantity;

    public Integer getQuantity() {
        return getInt("quantity");
    }

    public void setQuantity(Integer quantity) {
        this.set("quantity", quantity);
    }

    @Property(displayName = "收货人")
    private String receiver;

    public String getReceiver() {
        return getStr("receiver");
    }

    public void setReceiver(String receiver) {
        this.set("receiver", receiver);
    }

    @Property(displayName = "发货日期", dataType = DataType.DATE)
    private java.util.Date shipDate;

    public java.util.Date getShipDate() {
        return getDate("shipDate");
    }

    public void setShipDate(java.util.Date shipDate) {
        this.set("shipDate", shipDate);
    }

    @Property(displayName = "物流状态")
    @Selection(values = {@Option(value = "pending", label = "待发运"), @Option(value = "inTransit", label = "运输中"), @Option(value = "signed", label = "已签收")})
    private String status;

    @Property(displayName = "备注")
    private String remark;

    public String getRemark() {
        return getStr("remark");
    }

    public void setRemark(String remark) {
        this.set("remark", remark);
    }
}
