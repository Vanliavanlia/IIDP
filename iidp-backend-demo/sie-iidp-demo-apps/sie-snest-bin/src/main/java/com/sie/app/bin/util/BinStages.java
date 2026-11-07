package com.sie.app.bin.util;

/**
 * 垃圾桶生产工艺路线：十道工序。
 */
public final class BinStages {

    private BinStages() {
    }

    /** 工序编码、工序名称、默认设备 */
    public static final String[][] ALL = {
            {"steel", "钢材初加工", "剪板机"},
            {"cutting", "裁剪", "激光切割机"},
            {"forming", "定型", "液压成型机"},
            {"primer", "上底漆", "喷涂线"},
            {"topcoat", "上面漆", "喷涂线"},
            {"film", "上保护膜", "喷涂线"},
            {"logo", "LOGO 制作", "丝印台"},
            {"waterproof", "防水测试", "水压测试台"},
            {"packing", "打包装", "包装工位"},
            {"shipping", "发货", "成品库"}
    };
}
