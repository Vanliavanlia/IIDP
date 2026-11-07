package com.sie.app.mes.model;

import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.validate.Validate;

/**
 * 产品BOM
 * <p>
 * 实训任务「产品BOM管理」：用 {@code @Validate.Unique} 实现 BOM 编码唯一校验。
 * <p>
 * 与手工写 validate 相比，注解方式的优点是：
 * <ul>
 *   <li>引擎在 create / update 的统一校验阶段执行，不依赖业务代码调用；</li>
 *   <li>{@code properties} 支持多字段组合唯一（如 product + material）；</li>
 *   <li>校验失败返回平台标准错误码，前端能直接展示 {@code message}。</li>
 * </ul>
 *
 * @author Kris
 */
@Model(name = "product_bom", description = "产品BOM", displayName = "产品BOM")
public class ProductBom extends BaseModel<ProductBom> {

    /**
     * BOM 编码：唯一校验写在本字段上，properties 指定参与唯一性判断的字段集合
     */
    @Validate.Unique(properties = {"code"}, message = "BOM编码已存在，请更换一个")
    @Property(displayName = "BOM编码", displayForModel = true)
    private String code;

    @ManyToOne(displayName = "产品")
    @JoinColumn
    private Product product;

    @ManyToOne(displayName = "物料")
    @JoinColumn
    private Material material;

    @Property(displayName = "数量")
    private Integer amount;

    public String getCode() {
        return getStr("code");
    }

    public ProductBom setCode(String code) {
        this.set("code", code);
        return this;
    }

    public Product getProduct() {
        return (Product) this.get("product");
    }

    public ProductBom setProduct(Product product) {
        this.set("product", product);
        return this;
    }

    public Material getMaterial() {
        return (Material) this.get("material");
    }

    public ProductBom setMaterial(Material material) {
        this.set("material", material);
        return this;
    }

    public Integer getAmount() {
        return getInt("amount");
    }

    public ProductBom setAmount(Integer amount) {
        this.set("amount", amount);
        return this;
    }
}
