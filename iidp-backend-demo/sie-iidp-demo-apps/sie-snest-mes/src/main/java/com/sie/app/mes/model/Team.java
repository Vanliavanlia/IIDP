package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;
import java.util.Map;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;

/**
 * 班组
 */
@Model(name = "team", tableName = "mes_team", displayName = "班组",
        description = "车间生产班组", isAutoLog = Bool.True, orderBy = "id desc")
public class Team extends BaseModel<Team> {

    @Property(displayName = "班组编码", length = 64, unique = true)
    private String code;

    @Validate.NotBlank(message = "名称不能为空")
    @Property(displayName = "名称", length = 200, displayForModel = true)
    private String name;

    @Property(displayName = "班制", length = 100)
    private String shiftPattern;

    @OneToMany(targetModel = "employee", targetProperty = "team", displayName = "员工")
    private List<Employee> employeeList;

    @OneToMany(targetModel = "work_order_step", targetProperty = "team", displayName = "工单工序任务")
    private List<WorkOrderStep> workOrderStepList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("code", getMeta().get("barcode_rule").call("generateCode", "team"));
        }
        return (RecordSet) getMeta().get("team").callSuper(Team.class, "create", valuesList);
    }
}
