package com.sie.app.express.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 货架（驿站内的存放单元）
 */
@Model(name = "shelf", tableName = "ex_shelf", displayName = "货架", isAutoLog = Bool.True)
public class Shelf extends BaseModel<Shelf> {

    @Property(displayName = "货架编号", length = 64, displayForModel = true)
    private String code;

    @Property(displayName = "驿站", displayForModel = true)
    @ManyToOne(targetModel = "station", targetProperty = "id", displayName = "驿站")
    @JoinColumn(name = "station_id", referencedProperty = "id")
    private Map<String, Object> station;

    @Property(displayName = "区域", length = 64)
    private String area;

    @Property(displayName = "容量")
    private Integer capacity;

    @Property(displayName = "已用")
    private Integer used;

    @Property(displayName = "状态", length = 32, defaultValue = "FREE")
    private String status;

    public String getCode() { return getStr("code"); }
    public Shelf setCode(String code) { set("code", code); return this; }

    public String getArea() { return getStr("area"); }
    public Shelf setArea(String area) { set("area", area); return this; }

    public Integer getCapacity() { return getInt("capacity"); }
    public Shelf setCapacity(Integer capacity) { set("capacity", capacity); return this; }

    public Integer getUsed() { return getInt("used"); }
    public Shelf setUsed(Integer used) { set("used", used); return this; }

    public String getStatus() { return getStr("status"); }
    public Shelf setStatus(String status) { set("status", status); return this; }

    public String getStationId() {
        Object v = get("station");
        if (v instanceof Map) {
            Object id = ((Map<?, ?>) v).get("id");
            return id == null ? null : String.valueOf(id);
        }
        return v == null ? null : String.valueOf(v);
    }

    public Shelf setStation(String stationId) { set("station", stationId); return this; }

    /** 自动把该驿站第一个还有空位的货架找出来 */
    @MethodService(name = "findAvailable", auth = "findAvailable", description = "查找可用货架")
    public String findAvailable(String stationId) {
        List<Shelf> list = search(
                Filter.equal("station", stationId).and(Filter.equal("status", "FREE")),
                Collections.singletonList("*"), 0, 0, "code");
        if (list == null) {
            return null;
        }
        for (Shelf s : list) {
            int cap = s.getCapacity() == null ? 0 : s.getCapacity();
            int use = s.getUsed() == null ? 0 : s.getUsed();
            if (use < cap) {
                return s.getId();
            }
        }
        return null;
    }
}
