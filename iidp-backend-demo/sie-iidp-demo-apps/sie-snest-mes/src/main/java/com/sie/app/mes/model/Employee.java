package com.sie.app.mes.model;

import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.sdk.annotation.meta.MethodService;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.OneToMany;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;
import com.sie.snest.sdk.annotation.validate.Validate;

import java.util.List;
import java.util.Map;

/**
 * 员工
 */
@Model(name = "employee", tableName = "mes_employee", displayName = "员工",
        description = "车间员工主数据", isAutoLog = Bool.True, orderBy = "id desc")
public class Employee extends BaseModel<Employee> {

    @Property(displayName = "工号", length = 64, unique = true)
    private String employeeNo;

    @Validate.NotBlank(message = "姓名不能为空")
    @Property(displayName = "姓名", length = 100, displayForModel = true)
    private String name;

    @ManyToOne(targetModel = "team", targetProperty = "id", displayName = "班组")
    @JoinColumn(name = "team_id", referencedProperty = "id")
    private Team team;

    @Property(displayName = "技能", length = 200)
    private String skill;

    @Property(displayName = "状态", length = 32, defaultValue = "ENABLED")
    @Selection(values = {
            @Option(label = "启用", value = "ENABLED"),
            @Option(label = "停用", value = "DISABLED")
    })
    private String status;

    @OneToMany(targetModel = "production_report", targetProperty = "operator", displayName = "报工记录")
    private List<ProductionReport> productionReportList;

    @OneToMany(targetModel = "material_issue", targetProperty = "issuer", displayName = "领料记录")
    private List<Map<String, Object>> materialIssueList;

    @OneToMany(targetModel = "inspection_record", targetProperty = "inspector", displayName = "检验记录")
    private List<Map<String, Object>> inspectionRecordList;

    @OneToMany(targetModel = "waterproof_test", targetProperty = "tester", displayName = "防水测试")
    private List<Map<String, Object>> waterproofTestList;

    /**
     * 覆盖引擎 create 服务：新增数据时调用编码规则 app（barcode_rule）自动生成编码。
     */
    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            map.put("employeeNo", getMeta().get("barcode_rule").call("generateCode", "employee"));
        }
        return (RecordSet) getMeta().get("employee").callSuper(Employee.class, "create", valuesList);
    }
}
