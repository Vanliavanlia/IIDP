package com.sie.app.mes.model;

import com.sie.snest.engine.api.jsonrpc.JsonRpcServiceResponse;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 设备
 * <p>
 * 演示：模型自定义服务（@MethodService）+ 视图按钮调用服务 + 服务抛异常
 */
@Model(name = "device",
        tableName = "ems_device",
        displayName = "设备",
        description = "设备信息",
        isAutoLog = Bool.True
)
public class Device extends BaseModel<Device> {

    @Property(displayName = "设备名称", displayForModel = true)
    private String name;

    @Property(displayName = "设备编号")
    private String code;

    @Property(displayName = "设备状态")
    @Selection(
            values = {
                    @Option(value = "online", label = "在线"),
                    @Option(value = "offline", label = "离线")
            }
    )
    private String status;

    @Property(displayName = "区域")
    @ManyToOne
    @JoinColumn
    private Area area;

    @Property(displayName = "购买日期", dataType = DataType.DATE)
    private Date purchaseDate;

    public String getName() {
        return getStr("name");
    }

    public void setName(String name) {
        this.set("name", name);
    }

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    public String getStatus() {
        return getStr("status");
    }

    public void setStatus(String status) {
        this.set("status", status);
    }

    public Area getArea() {
        return (Area) this.get("area");
    }

    public void setArea(Area area) {
        this.set("area", area);
    }

    public Date getPurchaseDate() {
        return getDate("purchaseDate");
    }

    public void setPurchaseDate(Date purchaseDate) {
        this.set("purchaseDate", purchaseDate);
    }

    /**
     * 检查设备状态：在线/离线互转
     * <p>
     * 视图里的按钮通过 model=device & service=check 调用本方法
     */
    @MethodService(name = "check", auth = "check", description = "检查设备状态")
    public JsonRpcServiceResponse check(String[] ids) {
        System.out.println("Checking device: " + Arrays.toString(ids));

        // 1. 查询设备信息
        Device device = selectById(ids[0]);
        String curStatus = device.getStatus();

        // 2. 根据设备状态执行不同逻辑
        if ("offline".equals(curStatus)) {
            device.setStatus("online");
        } else {
            device.setStatus("offline");
        }

        // 3. 更新设备状态
        device.update();

        if ("SKJ-0001".equals(device.getCode())) {
            throw new RuntimeException("Device SKJ-0001 is not allowed to check");
        }

        return JsonRpcServiceResponse.reloadWindow();
    }

    /**
     * 批量把离线设备置为在线
     * <p>
     * 演示 Filter + BaseModel 实例方法 search：
     * <ul>
     *   <li>{@code Collections.singletonList("*")} 表示查询所有字段</li>
     *   <li>{@code limit = 0} 表示不分页、返回全部匹配记录</li>
     *   <li>{@code Filter.EMPTY_FILTER} 表示不加任何过滤条件</li>
     * </ul>
     */
    @MethodService(name = "modifyStatus", auth = "modifyStatus", description = "批量将离线设备置为在线")
    public void modifyStatus() {
        // 只查 status = offline 的设备
        List<Device> offlineDevices = search(Filter.equal("status", "offline"),
                Collections.singletonList("*"), 0, 0, null);

        // 也演示一下查询全部区域（EMPTY_FILTER = 不过滤）
        List<Area> areas = new Area().search(Filter.EMPTY_FILTER,
                Collections.singletonList("*"), 0, 0, null);
        System.out.println("areas: " + areas.size() + ", offline devices: " + offlineDevices.size());

        for (Device device : offlineDevices) {
            device.setStatus("online");
            device.update();
        }
    }
}
