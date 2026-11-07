package com.sie.app.mes.model;

import com.sie.snest.engine.model.Bool;
import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;
import com.sie.snest.sdk.annotation.meta.Property;
import com.sie.snest.sdk.annotation.orm.JoinColumn;
import com.sie.snest.sdk.annotation.orm.ManyToOne;

@Model(name = "process_route",displayName = "工艺路线",isAutoLog = Bool.True)
public class ProcessRoute extends BaseModel<ProcessRoute> {

    @Property(displayName = "名称")
    private String name;

    @Property(displayName = "编码")
    private String code;

    @ManyToOne(displayName = "产品")
    @JoinColumn
    private Product product;

}
