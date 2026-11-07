package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.DataType;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;
import com.sie.snest.sdk.annotation.orm.Option;
import com.sie.snest.sdk.annotation.orm.Selection;

import java.util.Date;

@Model(name = "order",displayName = "生产订单",isAutoLog = Bool.True)
public class Order extends BaseModel<Order> {

    @Property(displayName = "编码")
    private String code;

    @ManyToOne
    @JoinColumn
    private Product product;

    @Property(displayName = "数量")
    private Integer quantity;

    @Property(displayName = "订单状态",defaultValue = "1",widget = "select",length = 256)
    @Selection(values = {@Option(label = "待生产",value = "1"),
            @Option(label = "生产中",value = "2"),
            @Option(label = "已完成",value = "3")})
    private String status;


    @Property(displayName = "预计完成时间",dataType = DataType.DATE_TIME)
    private Date dueDate;



}
