package com.sie.app.mes.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 编码生成工具类
 */
public class CodeGenerator {

    /**
     * 流水号
     */
    private static int sequence = 0;

    /**
     * 时间格式化
     */
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 生成唯一编码方法
     * @param prefixStr
     * @return
     */
    public synchronized static String generateCode(String prefixStr){

        /**
         * 获取当前时间
         */
        LocalDateTime now = LocalDateTime.now();

        /**
         * 流水号递增
         */
        int cuurrentSeq = ++sequence;

        /**
         * 当前时间格式化
         */
        String formattedTime = now.format(formatter);
        return String.format("%s%s%05d",prefixStr,formattedTime,cuurrentSeq);
    }

    public static void main(String[] args) {
        System.out.println("第一个编码:"+generateCode("M"));

        System.out.println("第二个编码:"+generateCode("M"));
    }




}
