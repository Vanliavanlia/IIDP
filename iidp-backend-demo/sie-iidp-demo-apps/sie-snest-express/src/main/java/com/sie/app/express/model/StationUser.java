package com.sie.app.express.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驿站人员（数据隔离的载体）
 *
 * 一个登录账号可以绑定一个或多个驿站：绑定之后，该账号在「包裹管理」里
 * 只能看到 / 操作自己驿站的包裹；没有绑定的账号（管理员）不受限制。
 */
@Model(name = "station_user", tableName = "ex_station_user", displayName = "驿站人员", isAutoLog = Bool.True)
public class StationUser extends BaseModel<StationUser> {

    @Property(displayName = "登录账号", columnName = "user_login", unique = true, displayForModel = true)
    private String userLogin;

    @Property(displayName = "姓名")
    private String userName;

    @Property(displayName = "所属驿站")
    @ManyToOne(targetModel = "station", targetProperty = "id", displayName = "所属驿站")
    @JoinColumn(name = "station_id", referencedProperty = "id")
    private Map<String, Object> station;

    @Property(displayName = "状态")
    @Selection(values = {
            @Option(value = "enabled", label = "启用"),
            @Option(value = "disabled", label = "停用")
    })
    private String status;

    @Property(displayName = "备注")
    private String remark;

    public String getUserLogin() { return getStr("userLogin"); }
    public StationUser setUserLogin(String userLogin) { set("userLogin", userLogin); return this; }

    public String getUserName() { return getStr("userName"); }
    public StationUser setUserName(String userName) { set("userName", userName); return this; }

    public String getStatus() { return getStr("status"); }
    public StationUser setStatus(String status) { set("status", status); return this; }

    public String getRemark() { return getStr("remark"); }
    public StationUser setRemark(String remark) { set("remark", remark); return this; }

    public String getStationId() { return refId(get("station")); }
    public StationUser setStation(String stationId) { set("station", stationId); return this; }

    private String refId(Object v) {
        if (v instanceof Map) {
            Object id = ((Map<?, ?>) v).get("id");
            return id == null ? null : String.valueOf(id);
        }
        return v == null ? null : String.valueOf(v);
    }

    /** 取当前登录账号的 login：优先用引擎会话里的用户对象，取不到再回退查 rbac_user */
    private String loginOfCurrentUser() {
        try {
            Object user = getMeta().getUser();
            if (user instanceof Map) {
                Object v = ((Map<?, ?>) user).get("login");
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    return String.valueOf(v).trim();
                }
            }
        } catch (Exception ignored) {
            // 忽略：继续走下面的兜底逻辑
        }
        String userId = getMeta().getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> users = getMeta().get("rbac_user")
                .search(Filter.equal("id", userId), Collections.singletonList("*"), 0, 1, null);
        if (users == null || users.isEmpty()) {
            return null;
        }
        Object v = users.get(0).get("login");
        return v == null ? null : String.valueOf(v);
    }

    /** 当前登录账号的显示名（兜底匹配用） */
    private String displayNameOfCurrentUser() {
        try {
            String name = getMeta().getUserName();
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 当前登录账号绑定的驿站 id 列表（enabled 的才算） */
    @MethodService(name = "myStationIds", auth = "myStationIds", description = "当前登录账号绑定的驿站")
    public List<String> myStationIds() {
        List<String> ids = new ArrayList<String>();
        String login = loginOfCurrentUser();
        String displayName = displayNameOfCurrentUser();
        if ((login == null || login.trim().isEmpty()) && displayName == null) {
            return ids;
        }
        Filter byUser;
        if (login != null && displayName != null && !login.equals(displayName)) {
            byUser = Filter.OR(Filter.equal("userLogin", login), Filter.equal("userName", displayName));
        } else if (login != null && !login.trim().isEmpty()) {
            byUser = Filter.equal("userLogin", login);
        } else {
            byUser = Filter.equal("userName", displayName);
        }
        List<Map<String, Object>> links = getMeta().get("station_user")
                .search(Filter.AND(byUser, Filter.equal("status", "enabled")),
                        Collections.singletonList("*"), 0, 0, null);
        if (links == null || links.isEmpty()) {
            return ids;
        }
        for (Map<String, Object> link : links) {
            String sid = refId(link.get("station"));
            if (sid != null && !ids.contains(sid)) {
                ids.add(sid);
            }
        }
        return ids;
    }

    /** 自检：当前账号的数据隔离范围（前端/调试用） */
    @MethodService(name = "myScope", auth = "myScope", description = "查看当前账号的数据隔离范围")
    public Map<String, Object> myScope() {
        List<String> ids = myStationIds();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("userId", getMeta().getUserId());
        result.put("scoped", !ids.isEmpty());
        result.put("stationIds", ids);
        List<String> names = new ArrayList<String>();
        for (String id : ids) {
            List<Map<String, Object>> rows = getMeta().get("station")
                    .search(Filter.equal("id", id), Collections.singletonList("*"), 0, 1, null);
            if (rows != null && !rows.isEmpty()) {
                names.add(String.valueOf(rows.get(0).get("name")));
            }
        }
        result.put("stationNames", names);
        result.put("tip", ids.isEmpty() ? "未绑定驿站：可查看全部驿站的数据（管理员视角）"
                : "已绑定驿站：只能查看所绑定驿站的包裹");
        return result;
    }

}
