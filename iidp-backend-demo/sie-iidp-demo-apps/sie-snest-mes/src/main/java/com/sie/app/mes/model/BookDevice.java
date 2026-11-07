package com.sie.app.mes.model;

import com.sie.snest.sdk.BaseModel;
import com.sie.snest.sdk.annotation.meta.Model;

/**
 * 设备模型的「继承」
 * <p>
 * name != parent 表示继承：新建一张 book_device 表，完整继承 device 的所有属性与服务，
 * 之后可以在这个子模型上继续加属性、加服务，而不影响父模型 device。
 */
@Model(name = "book_device", parent = "device", displayName = "预约设备", description = "预约设备（继承自设备）")
public class BookDevice extends BaseModel<BookDevice> {

}
