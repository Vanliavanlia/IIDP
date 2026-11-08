package com.sie.app.cms.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

/**
 * LOGO规格（订单行与 LOGO 规格一对一关系的 owning 侧）
 */
@Model(name = "logo_spec", tableName = "mes_logo_spec", displayName = "LOGO规格",
        description = "订单行的 LOGO 制作规格", isAutoLog = Bool.True, orderBy = "id desc")
public class LogoSpec extends BaseModel<LogoSpec> {

    @Property(displayName = "描述", length = 255, displayForModel = true)
    private String description;

    @ManyToOne(targetModel = "sales_order_line", targetProperty = "id", displayName = "所属订单行")
    @JoinColumn(name = "sales_order_line_id", referencedProperty = "id", unique = true)
    private SalesOrderLine salesOrderLine;

    @ManyToOne(targetModel = "customer", targetProperty = "id", displayName = "客户")
    @JoinColumn(name = "customer_id", referencedProperty = "id")
    private Customer customer;

    @Property(displayName = "LOGO文件地址", length = 500)
    private String fileUrl;

    @Property(displayName = "位置", length = 100)
    private String position;

    @Property(displayName = "宽")
    private Double width;

    @Property(displayName = "高")
    private Double height;

    @Property(displayName = "工艺方式", length = 32)
    @Selection(values = {
            @Option(label = "丝印", value = "SILK_SCREEN"),
            @Option(label = "贴标", value = "STICKER"),
            @Option(label = "激光", value = "LASER")
    })
    private String processMethod;

    @Property(displayName = "LOGO颜色", length = 64)
    private String logoColor;
}
