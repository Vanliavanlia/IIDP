package com.sie.app.express.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.Date;
import java.util.Map;

/**
 * 包裹操作流水（入库 / 上架 / 取件 / 退货 / 换码）
 */
@Model(name = "parcel_log", tableName = "ex_parcel_log", displayName = "操作记录")
public class ParcelLog extends BaseModel<ParcelLog> {

    @Property(displayName = "包裹")
    @ManyToOne(targetModel = "parcel", targetProperty = "id", displayName = "包裹")
    @JoinColumn(name = "parcel_id", referencedProperty = "id")
    private Map<String, Object> parcel;

    @Property(displayName = "动作", length = 32)
    private String action;

    @Property(displayName = "取件码", length = 32)
    private String code;

    @Property(displayName = "操作人", length = 64)
    private String operator;

    @Property(displayName = "操作人姓名", length = 64)
    private String operatorName;

    @Property(displayName = "发生时间", dataType = DataType.DATE_TIME)
    private Date happenTime;

    @Property(displayName = "说明", length = 240)
    private String remark;

    public String getAction() { return getStr("action"); }
    public ParcelLog setAction(String action) { set("action", action); return this; }

    public String getCode() { return getStr("code"); }
    public ParcelLog setCode(String code) { set("code", code); return this; }

    public String getOperator() { return getStr("operator"); }
    public ParcelLog setOperator(String operator) { set("operator", operator); return this; }

    public String getOperatorName() { return getStr("operatorName"); }
    public ParcelLog setOperatorName(String operatorName) { set("operatorName", operatorName); return this; }

    public Date getHappenTime() { return getDate("happenTime"); }
    public ParcelLog setHappenTime(Date happenTime) { set("happenTime", happenTime); return this; }

    public String getRemark() { return getStr("remark"); }
    public ParcelLog setRemark(String remark) { set("remark", remark); return this; }

    public String getParcelId() {
        Object v = get("parcel");
        if (v instanceof Map) {
            Object id = ((Map<?, ?>) v).get("id");
            return id == null ? null : String.valueOf(id);
        }
        return v == null ? null : String.valueOf(v);
    }

    public ParcelLog setParcel(String parcelId) { set("parcel", parcelId); return this; }
}
