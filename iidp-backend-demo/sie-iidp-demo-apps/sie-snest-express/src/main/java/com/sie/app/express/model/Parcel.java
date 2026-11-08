package com.sie.app.express.model;

import com.sie.snest.engine.api.jsonrpc.JsonRpcServiceResponse;
import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 包裹（驿站的核心单据）
 * 状态机：IN_STOCK 在库待上架 -> ON_SHELF 已上架 -> PICKED 已取件
 *        ON_SHELF -> OVERDUE 超期未取 -> PICKED
 *        IN_STOCK / ON_SHELF / OVERDUE -> RETURNED 已退货
 */
@Model(name = "parcel", tableName = "ex_parcel", displayName = "包裹", isAutoLog = Bool.True)
public class Parcel extends BaseModel<Parcel> {

    @Property(displayName = "包裹编号", length = 64, unique = true, displayForModel = true)
    private String parcelNo;

    @Property(displayName = "运单号", length = 64, unique = true)
    @Validate.Unique(properties = {"waybillNo"}, message = "该运单号已入库，请勿重复登记")
    private String waybillNo;

    @Property(displayName = "驿站")
    @ManyToOne(targetModel = "station", targetProperty = "id", displayName = "驿站")
    @JoinColumn(name = "station_id", referencedProperty = "id")
    private Map<String, Object> station;

    @Property(displayName = "收件人", length = 64)
    private String receiverName;

    @Property(displayName = "手机号", length = 32)
    private String receiverPhone;

    @Property(displayName = "手机后四位", length = 8)
    private String phoneTail;

    @Property(displayName = "快递公司", length = 64)
    private String courier;

    @Property(displayName = "入库时间", dataType = DataType.DATE_TIME)
    private Date inboundTime;

    @Property(displayName = "货架")
    @ManyToOne(targetModel = "shelf", targetProperty = "id", displayName = "货架")
    @JoinColumn(name = "shelf_id", referencedProperty = "id")
    private Map<String, Object> shelf;

    @Property(displayName = "取件码", length = 32)
    private String pickupCode;

    @Property(displayName = "状态", length = 32, defaultValue = "IN_STOCK")
    private String status;

    @Property(displayName = "上架时间", dataType = DataType.DATE_TIME)
    private Date onShelfTime;

    @Property(displayName = "出库时间", dataType = DataType.DATE_TIME)
    private Date outboundTime;

    @Property(displayName = "备注", length = 240)
    private String remark;

    public String getParcelNo() { return getStr("parcelNo"); }
    public Parcel setParcelNo(String parcelNo) { set("parcelNo", parcelNo); return this; }

    public String getWaybillNo() { return getStr("waybillNo"); }
    public Parcel setWaybillNo(String waybillNo) { set("waybillNo", waybillNo); return this; }

    public String getReceiverName() { return getStr("receiverName"); }
    public Parcel setReceiverName(String receiverName) { set("receiverName", receiverName); return this; }

    public String getReceiverPhone() { return getStr("receiverPhone"); }
    public Parcel setReceiverPhone(String receiverPhone) { set("receiverPhone", receiverPhone); return this; }

    public String getPhoneTail() { return getStr("phoneTail"); }
    public Parcel setPhoneTail(String phoneTail) { set("phoneTail", phoneTail); return this; }

    public String getCourier() { return getStr("courier"); }
    public Parcel setCourier(String courier) { set("courier", courier); return this; }

    public Date getInboundTime() { return getDate("inboundTime"); }
    public Parcel setInboundTime(Date inboundTime) { set("inboundTime", inboundTime); return this; }

    public String getPickupCode() { return getStr("pickupCode"); }
    public Parcel setPickupCode(String pickupCode) { set("pickupCode", pickupCode); return this; }

    public String getStatus() { return getStr("status"); }
    public Parcel setStatus(String status) { set("status", status); return this; }

    public Date getOnShelfTime() { return getDate("onShelfTime"); }
    public Parcel setOnShelfTime(Date onShelfTime) { set("onShelfTime", onShelfTime); return this; }

    public Date getOutboundTime() { return getDate("outboundTime"); }
    public Parcel setOutboundTime(Date outboundTime) { set("outboundTime", outboundTime); return this; }

    public String getRemark() { return getStr("remark"); }
    public Parcel setRemark(String remark) { set("remark", remark); return this; }

    public String getStationId() { return refId(get("station")); }
    public Parcel setStation(String stationId) { set("station", stationId); return this; }

    public String getShelfId() { return refId(get("shelf")); }
    public Parcel setShelf(String shelfId) { set("shelf", shelfId); return this; }

    private String refId(Object v) {
        if (v instanceof Map) {
            Object id = ((Map<?, ?>) v).get("id");
            return id == null ? null : String.valueOf(id);
        }
        return v == null ? null : String.valueOf(v);
    }

    // ===================== 服务 =====================

    /** 入库：生成包裹编号、置为在库、记录入库时间 */
    @MethodService(name = "create", auth = "create", description = "包裹入库登记")
    public RecordSet create(RecordSet rs, List<Map<String, Object>> valuesList) {
        Date now = new Date();
        for (Map<String, Object> item : valuesList) {
            if (item.get("parcelNo") == null || String.valueOf(item.get("parcelNo")).isEmpty()) {
                item.put("parcelNo", nextParcelNo());
            }
            item.put("status", "IN_STOCK");
            item.put("inboundTime", now);
            String phone = item.get("receiverPhone") == null ? "" : String.valueOf(item.get("receiverPhone"));
            if (phone.length() >= 4 && (item.get("phoneTail") == null || String.valueOf(item.get("phoneTail")).isEmpty())) {
                item.put("phoneTail", phone.substring(phone.length() - 4));
            }
            item.put("create_user", rs.getMeta().getUserId());
        }
        return (RecordSet) rs.callSuper(Parcel.class, "create", valuesList);
    }

    /** 自动上架：按驿站找一个还有空位的货架，生成取件码 */
    @MethodService(name = "autoShelf", auth = "autoShelf", description = "自动上架并生成取件码")
    public JsonRpcServiceResponse autoShelf(String[] ids) {
        if (ids == null || ids.length == 0) {
            throw new RuntimeException("请先选择要上架的包裹");
        }
        for (String id : ids) {
            Parcel p = selectById(id);
            if (p == null) {
                continue;
            }
            if (!"IN_STOCK".equals(p.getStatus())) {
                throw new RuntimeException("包裹[" + p.getParcelNo() + "]当前状态为 " + p.getStatus() + "，只有「在库」的包裹可以上架");
            }
            String stationId = p.getStationId();
            String shelfId = new Shelf().findAvailable(stationId);
            if (shelfId == null) {
                throw new RuntimeException("驿站没有可用货架，请先增加货架或扩容");
            }
            Shelf shelf = new Shelf().selectById(shelfId);
            int used = shelf.getUsed() == null ? 0 : shelf.getUsed();
            shelf.setUsed(used + 1);
            if (shelf.getCapacity() != null && used + 1 >= shelf.getCapacity()) {
                shelf.setStatus("FULL");
            }
            shelf.update();

            String code = nextPickupCode();
            p.setShelf(shelfId);
            p.setPickupCode(code);
            p.setStatus("ON_SHELF");
            p.setOnShelfTime(new Date());
            p.update();
            log(p.getId(), "ON_SHELF", code, "上架到货架 " + shelf.getCode());
        }
        return JsonRpcServiceResponse.reloadWindow();
    }

    /** 取件（按取件码）：重复取件会被直接拦下 */
    @MethodService(name = "pickup", auth = "pickup", description = "凭取件码取件出库")
    public Map<String, Object> pickup(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("请输入取件码");
        }
        String c = code.trim();
        // 取件码只在「有效期」内唯一，历史上已取件/已退货的包裹可能留着同一个码，
        // 因此优先命中仍在架（ON_SHELF/OVERDUE）的那一条。
        Filter activeFilter = Filter.AND(Filter.equal("pickupCode", c),
                Filter.OR(Filter.equal("status", "ON_SHELF"), Filter.equal("status", "OVERDUE")));
        List<Parcel> active = search(activeFilter, Collections.singletonList("*"), 0, 0, null);
        if (active != null && !active.isEmpty()) {
            Parcel p = active.get(0);
            releaseShelf(p);
            p.setStatus("PICKED");
            p.setOutboundTime(new Date());
            p.update();
            log(p.getId(), "PICKED", p.getPickupCode(), "取件出库");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("parcelNo", p.getParcelNo());
            result.put("waybillNo", p.getWaybillNo());
            result.put("receiverName", p.getReceiverName());
            result.put("code", p.getPickupCode());
            result.put("status", "PICKED");
            result.put("message", "取件成功，欢迎下次光临");
            return result;
        }

        List<Parcel> any = search(Filter.equal("pickupCode", c),
                Collections.singletonList("*"), 0, 0, null);
        if (any == null || any.isEmpty()) {
            throw new RuntimeException("取件码不存在：" + c);
        }
        Parcel p = any.get(0);
        String st = p.getStatus();
        if ("PICKED".equals(st)) {
            throw new RuntimeException("该包裹已于 " + p.getOutboundTime() + " 取件，不能重复出库");
        }
        if ("RETURNED".equals(st) || "RETURNING".equals(st)) {
            throw new RuntimeException("该包裹已退货，不能取件");
        }
        throw new RuntimeException("包裹状态为 " + st + "，还没上架，无法取件");
    }

    /** 取件（按记录 id，供视图按钮直接调用） */
    @MethodService(name = "pickupByIds", auth = "pickupByIds", description = "按记录取件出库")
    public JsonRpcServiceResponse pickupByIds(String[] ids) {
        if (ids == null || ids.length == 0) {
            throw new RuntimeException("请先选择要取件的包裹");
        }
        for (String id : ids) {
            Parcel p = selectById(id);
            if (p == null) {
                continue;
            }
            pickup(p.getPickupCode());
        }
        return JsonRpcServiceResponse.reloadWindow();
    }

    /** 一键标记超期（默认 3 天，供视图按钮调用） */
    @MethodService(name = "markOverdueNow", auth = "markOverdueNow", description = "标记超期未取的包裹")
    public JsonRpcServiceResponse markOverdueNow() {
        markOverdue(3);
        return JsonRpcServiceResponse.reloadWindow();
    }

    /** 退货出库 */
    @MethodService(name = "returnOut", auth = "returnOut", description = "包裹退货出库")
    public JsonRpcServiceResponse returnOut(String[] ids) {
        if (ids == null || ids.length == 0) {
            throw new RuntimeException("请先选择要退货的包裹");
        }
        for (String id : ids) {
            Parcel p = selectById(id);
            if (p == null) {
                continue;
            }
            String st = p.getStatus();
            if ("PICKED".equals(st)) {
                throw new RuntimeException("包裹[" + p.getParcelNo() + "]已取件，不能退货");
            }
            if ("RETURNED".equals(st)) {
                throw new RuntimeException("包裹[" + p.getParcelNo() + "]已经退过货了");
            }
            releaseShelf(p);
            p.setStatus("RETURNED");
            p.setOutboundTime(new Date());
            p.setRemark("退货出库");
            p.update();
            log(p.getId(), "RETURNED", p.getPickupCode(), "退货出库");
        }
        return JsonRpcServiceResponse.reloadWindow();
    }

    /** 重新生成取件码：旧码立即失效 */
    @MethodService(name = "reissue", auth = "reissue", description = "重新生成取件码")
    public JsonRpcServiceResponse reissue(String[] ids) {
        if (ids == null || ids.length == 0) {
            throw new RuntimeException("请先选择包裹");
        }
        for (String id : ids) {
            Parcel p = selectById(id);
            if (p == null) {
                continue;
            }
            if (!"ON_SHELF".equals(p.getStatus()) && !"OVERDUE".equals(p.getStatus())) {
                throw new RuntimeException("只有「已上架 / 超期」的包裹才能重新生成取件码");
            }
            String old = p.getPickupCode();
            String code = nextPickupCode();
            p.setPickupCode(code);
            p.update();
            log(p.getId(), "REISSUE", code, "旧取件码 " + old + " 已作废");
        }
        return JsonRpcServiceResponse.reloadWindow();
    }

    /** 标记超期：上架超过 N 天仍未取件 */
    @MethodService(name = "markOverdue", auth = "markOverdue", description = "把超期未取的包裹标记为超期")
    public Map<String, Object> markOverdue(Integer days) {
        int limit = (days == null || days <= 0) ? 3 : days;
        long deadline = System.currentTimeMillis() - limit * 24L * 3600L * 1000L;
        List<Parcel> list = search(Filter.equal("status", "ON_SHELF"),
                Collections.singletonList("*"), 0, 0, null);
        int changed = 0;
        if (list != null) {
            for (Parcel p : list) {
                Date t = p.getOnShelfTime();
                if (t != null && t.getTime() < deadline) {
                    p.setStatus("OVERDUE");
                    p.update();
                    log(p.getId(), "OVERDUE", p.getPickupCode(), "超过 " + limit + " 天未取件");
                    changed++;
                }
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("days", limit);
        r.put("changed", changed);
        return r;
    }

    /** 包裹总览 */
    @MethodService(name = "overview", auth = "overview", description = "包裹状态总览")
    public Map<String, Object> overview() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("inStock", count(Filter.equal("status", "IN_STOCK")));
        r.put("onShelf", count(Filter.equal("status", "ON_SHELF")));
        r.put("picked", count(Filter.equal("status", "PICKED")));
        r.put("overdue", count(Filter.equal("status", "OVERDUE")));
        r.put("returned", count(Filter.equal("status", "RETURNED")));
        return r;
    }

    // ===================== 内部方法 =====================

    private void releaseShelf(Parcel p) {
        String shelfId = p.getShelfId();
        if (shelfId == null) {
            return;
        }
        Shelf shelf = new Shelf().selectById(shelfId);
        if (shelf != null) {
            int used = shelf.getUsed() == null ? 0 : shelf.getUsed();
            shelf.setUsed(Math.max(0, used - 1));
            if ("FULL".equals(shelf.getStatus())) {
                shelf.setStatus("FREE");
            }
            shelf.update();
        }
        p.setShelf(null);
    }

    private void log(String parcelId, String action, String code, String remark) {
        String userId = getMeta().getUserId();
        String userName = userId;
        List<Map<String, Object>> users = getMeta().get("rbac_user")
                .search(Filter.equal("id", userId), Collections.singletonList("*"), 0, 0, null);
        if (users != null && !users.isEmpty() && users.get(0).get("name") != null) {
            userName = String.valueOf(users.get(0).get("name"));
        }
        ParcelLog l = new ParcelLog();
        l.setParcel(parcelId);
        l.setAction(action);
        l.setCode(code);
        l.setOperator(userId);
        l.setOperatorName(userName);
        l.setHappenTime(new Date());
        l.setRemark(remark);
        l.create();
    }

    /** 取件码：MMDD + 4 位当日流水；只保证「有效（在架/超期）包裹」之间不重复 */
    private String nextPickupCode() {
        String day = new SimpleDateFormat("MMdd").format(new Date());
        for (int i = 1; i < 10000; i++) {
            String code = day + String.format("%04d", i);
            Filter f = Filter.equal("pickupCode", code)
                    .and(Filter.OR(Filter.equal("status", "ON_SHELF"), Filter.equal("status", "OVERDUE")));
            if (count(f) == 0) {
                return code;
            }
        }
        throw new RuntimeException("当天取件码已用尽");
    }

    /** 包裹编号：优先用统一编码中心（code APP）的规则，取不到则本地兜底 */
    private String nextParcelNo() {
        try {
            Object code = getMeta().get("barcode_rule").call("generateCode", "parcel");
            if (code != null && !String.valueOf(code).isEmpty()) {
                return String.valueOf(code);
            }
        } catch (Exception ignored) {
            // code APP 未安装或规则未配置时走本地兜底
        }
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        long n = count(Filter.llike("parcelNo", "PK" + day)) + 1;
        return "PK" + day + String.format("%04d", n);
    }
}
