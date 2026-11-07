package com.sie.app.bin.util;

import com.sie.app.bin.model.BinOrder;
import com.sie.snest.engine.rule.Filter;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 工单号生成器：BZ + yyyyMMdd + 4 位流水，例如 BZ202611070001。
 *
 * <p>单实例用 synchronized 足够；多实例部署必须换成数据库原子自增或行锁。
 */
public final class BinCodeGenerator {

    private BinCodeGenerator() {
    }

    public static synchronized String orderCode() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String prefix = "BZ" + day;
        long today = new BinOrder().count(Filter.llike("code", prefix));
        return prefix + String.format("%04d", today + 1);
    }
}
