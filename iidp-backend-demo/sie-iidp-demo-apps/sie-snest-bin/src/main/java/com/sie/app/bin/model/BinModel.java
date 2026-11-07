package com.sie.app.bin.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

/**
 * 垃圾桶型号
 *
 * <p>可生产的垃圾桶型号与规格
 */
@Model(name = "bin_model",
        tableName = "bin_model",
        displayName = "垃圾桶型号",
        description = "可生产的垃圾桶型号与规格",
        isAutoLog = Bool.True
)
public class BinModel extends BaseModel<BinModel> {

    @Property(displayName = "型号名称", displayForModel = true)
    private String name;

    public String getName() {
        return getStr("name");
    }

    public void setName(String name) {
        this.set("name", name);
    }

    @Property(displayName = "型号编码")
    private String code;

    public String getCode() {
        return getStr("code");
    }

    public void setCode(String code) {
        this.set("code", code);
    }

    @Property(displayName = "产品类别")
    @Selection(values = {@Option(value = "household", label = "家用"), @Option(value = "outdoor", label = "户外"), @Option(value = "classification", label = "分类垃圾桶"), @Option(value = "sanitary", label = "环卫")})
    private String category;

    public String getCategory() {
        return getStr("category");
    }

    public void setCategory(String category) {
        this.set("category", category);
    }

    @Property(displayName = "筒体材质")
    @Selection(values = {@Option(value = "ss304", label = "304 不锈钢"), @Option(value = "ss201", label = "201 不锈钢"), @Option(value = "galvanized", label = "镀锌钢板")})
    private String material;

    public String getMaterial() {
        return getStr("material");
    }

    public void setMaterial(String material) {
        this.set("material", material);
    }

    @Property(displayName = "容量(L)")
    private Integer capacity;

    public Integer getCapacity() {
        return getInt("capacity");
    }

    public void setCapacity(Integer capacity) {
        this.set("capacity", capacity);
    }

    @Property(displayName = "直径(mm)")
    private Integer diameter;

    public Integer getDiameter() {
        return getInt("diameter");
    }

    public void setDiameter(Integer diameter) {
        this.set("diameter", diameter);
    }

    @Property(displayName = "高度(mm)")
    private Integer height;

    public Integer getHeight() {
        return getInt("height");
    }

    public void setHeight(Integer height) {
        this.set("height", height);
    }

    @Property(displayName = "单件重量(kg)")
    private Double unitWeight;

    public Double getUnitWeight() {
        return getDouble("unitWeight");
    }

    public void setUnitWeight(Double unitWeight) {
        this.set("unitWeight", unitWeight);
    }

    @Property(displayName = "备注")
    private String remark;

    public String getRemark() {
        return getStr("remark");
    }

    public void setRemark(String remark) {
        this.set("remark", remark);
    }
}
