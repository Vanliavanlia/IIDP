package com.sie.app.mes.model;

import com.sie.app.mes.util.CodeRuleGenerator;
import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.model.Bool;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.Dict;
import com.sie.snest.sdk.annotation.meta.MethodService;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

import java.util.List;
import java.util.Map;

/**
 * @author Kris
 */
@Model(name = "material",
        tableName = "ems_material",
        displayName = "物料",
        description = "物料信息",
        isAutoLog = Bool.True
)
public class Material extends BaseModel<Material> {

    @Property(displayName = "名称", displayForModel = true)
    private String name;

    @Property(displayName = "编码")
    private String code;

    @Dict(typeCode = "material_type")
    @Property(displayName = "类型")
    private String type;

    @Dict(typeCode = "material_unit")
    @Property(displayName = "单位")
    private String unit;

    @Property(displayName = "描述")
    private String description;

    @Property(displayName = "库存量")
    private Integer stockQuantity;

    @Property(displayName = "单价")
    private Double price;


    @ManyToOne(displayName = "供应商")
    @JoinColumn
    private Supplier supplier;

    public Material setName(String name) {
        this.set("name", name);
        return this;
    }

    public String getName() {
        return getStr("name");
    }

    public Material setCode(String code) {
        this.set("code", code);
        return this;
    }

    public String getCode() {
        return getStr("code");
    }

    public Material setType(String type) {
        this.set("type", type);
        return this;
    }

    public String getType() {
        return getStr("type");
    }

    public Material setUnit(String unit) {
        this.set("unit", unit);
        return this;
    }

    public String getUnit() {
        return getStr("unit");
    }

    public Material setDescription(String description) {
        this.set("description", description);
        return this;
    }

    public String getDescription() {
        return getStr("description");
    }

    public Material setStockQuantity(Integer stockQuantity) {
        this.set("stockQuantity", stockQuantity);
        return this;
    }

    public Integer getStockQuantity() {
        return getInt("stockQuantity");
    }

    public Material setPrice(Double price) {
        this.set("price", price);
        return this;
    }

    public Double getPrice() {
        return getDouble("price");
    }

    public Material setSupplier(Supplier supplier) {
        this.set("supplier", supplier);
        return this;
    }

    public Supplier getSupplier() {
        return (Supplier) this.get("supplier");
    }

    @MethodService(name = "create",auth = "create",description = "创建")
    public RecordSet create(RecordSet rs, List<Material> valueList){
        valueList.forEach((item) ->{
            /**
             * 按【编码规则】生成物料编码：前缀 M + 日期（yyyyMMdd）+ 4 位流水号
             * 例如 M202610310001；流水号存在 ems_code_rule_detail，每次调用自动递增
             */
            item.setCode(CodeRuleGenerator.generate("material"));
            /**
             * 获取当前上下文用户id
             */
            item.setCreate_user(rs.getMeta().getUserId());
        });
        return (RecordSet) rs.callSuper(Material.class,"create",valueList);
    }

    @MethodService(name="search",auth = "search",description = "分页查询方法")
    public List<Material> search(RecordSet rs, Filter filter,
                                           List<String> properties,
                                           Integer limit,Integer offset,String order){
        /**
         * 获取创建人userId
         * 1、查询前增加查询属性；
         *   properties.add("create_user");
         * 2、获取方式如下：
         * ((Map<String,Object>)result.get(0)).get("create_user")
         */

        //com.sie.snest.engine.rule.Filter
        List<Material> result =
                (List<Material>) rs.callSuper(Material.class,"search",filter,properties,limit,offset,order);
        System.out.println("分页查询方法返回参数"+result);
        return result;
    }

    /**
     * 数据隔离演示：只返回「当前登录用户创建」的物料
     * <p>
     * isAutoLog=true 时引擎会自动写入 create_user（见 {@link #create} 里显式 setCreate_user 的那行，
     * 自动写与手工写同时存在也不冲突）。这里把它翻译成一次带 create_user 条件的查询，
     * 前端就能实现「我的物料」这种按人隔离的列表。
     * <p>
     * 注意：这是「业务层主动加条件」的隔离方式，够 demo 用；真正的行级/字段级隔离应该配
     * 数据权限（作用域 SG + 规则），见 docs/vendor/sie-snest-engine/reference/data-scope.md。
     */
    @MethodService(name = "searchMine", auth = "searchMine", description = "只查询当前登录用户创建的物料")
    public List<Material> searchMine(RecordSet rs, Filter filter,
                                     List<String> properties,
                                     Integer limit, Integer offset, String order) {
        String userId = rs.getMeta().getUserId();
        Filter mine = Filter.equal("create_user", userId);
        // and 会修改左侧对象，透传进来的 filter 为空时直接用 mine
        Filter merged = (filter == null) ? mine : mine.and(filter);
        List<Material> result = (List<Material>) rs.callSuper(Material.class, "search",
                merged, properties, limit, offset, order);
        System.out.println("当前用户 " + userId + " 可见物料 " + (result == null ? 0 : result.size()) + " 条");
        return result;
    }



}
