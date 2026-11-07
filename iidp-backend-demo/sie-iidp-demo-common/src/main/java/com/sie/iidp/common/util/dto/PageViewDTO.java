package com.sie.iidp.common.util.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 分页返回结果
 *
 * @author cxh
 * @date 2024年03月27日 14:19
 */
@Getter
@Setter
public class PageViewDTO<T> {

    /**
     * 记录集
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 分页大小
     */
    private long size;

    /**
     * 当前页
     */
    private long current;

    /**
     * 总页数
     */

    private long pages;

    public PageViewDTO() {
        this.records = Collections.emptyList();
        this.total = 0L;
        this.size = 10L;
        this.current = 1L;
        this.pages = 0L;
    }

    public PageViewDTO(List<T> list, long total) {
        this.records = list;
        this.total = total;
    }

    /**
     * 其它信息
     */
    private Object tag;

    /**
     * 获取空页对象
     *
     * @return
     */
    public static <T> PageViewDTO<T> obtainEmptyPage(Class<T> clazz) {
        return new PageViewDTO<T>(Collections.emptyList(), 0);
    }
}
