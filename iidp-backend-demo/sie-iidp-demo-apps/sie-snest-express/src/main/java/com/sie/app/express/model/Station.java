package com.sie.app.express.model;

import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驿站（网点）
 */
@Model(name = "station", tableName = "ex_station", displayName = "驿站", isAutoLog = Bool.True)
public class Station extends BaseModel<Station> {

    @Property(displayName = "驿站名称", length = 120, displayForModel = true)
    private String name;

    @Property(displayName = "驿站编码", length = 64, unique = true)
    private String code;

    @Property(displayName = "地址", length = 240)
    private String address;

    @Property(displayName = "联系电话", length = 32)
    private String phone;

    @Property(displayName = "负责人", length = 64)
    private String manager;

    @Property(displayName = "状态", length = 32, defaultValue = "ENABLED")
    private String status;

    @Property(displayName = "货架")
    @OneToMany(targetModel = "shelf", targetProperty = "station")
    private List<Shelf> shelves;

    public String getName() { return getStr("name"); }
    public Station setName(String name) { set("name", name); return this; }

    public String getCode() { return getStr("code"); }
    public Station setCode(String code) { set("code", code); return this; }

    public String getAddress() { return getStr("address"); }
    public Station setAddress(String address) { set("address", address); return this; }

    public String getPhone() { return getStr("phone"); }
    public Station setPhone(String phone) { set("phone", phone); return this; }

    public String getManager() { return getStr("manager"); }
    public Station setManager(String manager) { set("manager", manager); return this; }

    public String getStatus() { return getStr("status"); }
    public Station setStatus(String status) { set("status", status); return this; }

    /**
     * 驿站运营统计：在库、已上架、今日取件、超期
     */
    @MethodService(name = "stats", auth = "stats", description = "驿站运营统计")
    public Map<String, Object> stats(String id) {
        String stationId = (id == null || id.isEmpty()) ? null : id;

        long inStock = countBy(stationId, "IN_STOCK");
        long onShelf = countBy(stationId, "ON_SHELF");
        long picked = countBy(stationId, "PICKED");
        long overdue = countBy(stationId, "OVERDUE");

        List<Shelf> shelves = new Shelf().search(
                stationId == null ? Filter.EMPTY_FILTER : Filter.equal("station", stationId),
                Collections.singletonList("*"), 0, 0, null);
        long capacity = 0;
        long used = 0;
        if (shelves != null) {
            for (Shelf s : shelves) {
                capacity += (s.getCapacity() == null ? 0 : s.getCapacity());
                used += (s.getUsed() == null ? 0 : s.getUsed());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stationId", stationId);
        result.put("inStock", inStock);
        result.put("onShelf", onShelf);
        result.put("picked", picked);
        result.put("overdue", overdue);
        result.put("shelfCount", shelves == null ? 0 : shelves.size());
        result.put("shelfCapacity", capacity);
        result.put("shelfUsed", used);
        result.put("usage", capacity == 0 ? "0%" : (used * 100 / capacity) + "%");
        return result;
    }

    private long countBy(String stationId, String status) {
        if (stationId == null) {
            return new Parcel().count(Filter.equal("status", status));
        }
        return new Parcel().count(Filter.AND(Filter.equal("station", stationId), Filter.equal("status", status)));
    }
}
