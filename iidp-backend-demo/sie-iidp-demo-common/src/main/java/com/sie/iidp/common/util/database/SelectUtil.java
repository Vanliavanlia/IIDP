package com.sie.iidp.common.util.database;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import com.sie.snest.sdk.annotation.meta.Model;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chenjunliang1
 * @date 2023/12/6 9:18
 * 目前仅支持单表查询功能,自动生成sql
 * 可适用    =  > >=  <  <=  !=  like  in  groupBy orderBy is null  between count方法
 */
@Data
@NoArgsConstructor
public class SelectUtil {

    private List<String> conditions = new ArrayList<>();

    private List<String> orConditions = new ArrayList<>();

    private List<String> groupByConditions = new ArrayList<>();

    private List<String> orderByConditions = new ArrayList<>();

    private String allColumns = "*";

    private String count = "";

    private String tableName;

    private Class entityType;

    /**
     * 是否开启数据库识别的字符串转换
     */
    private Boolean changeObjToSqlCharFlag = true;

    private String selectColumns = "";

    private Integer pageSize;

    private Integer current;

    public SelectUtil setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public SelectUtil setCurrent(Integer current) {
        this.current = current;
        return this;
    }

    public SelectUtil(Class modelClazz, Boolean changeObjToSqlCharFlag) {
        this.entityType = modelClazz;
        this.changeObjToSqlCharFlag = changeObjToSqlCharFlag;
    }

    public SelectUtil(Class modelClazz) {
        this.entityType = modelClazz;
    }

    public SelectUtil(String tableName, Boolean changeObjToSqlCharFlag) {
        this.tableName = tableName;
        this.changeObjToSqlCharFlag = changeObjToSqlCharFlag;
    }

    public SelectUtil(String tableName) {
        this.tableName = tableName;
    }

    /**
     * eq 就是 equal等于
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil eq(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " = " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil eq(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return eq(column, value);
        }
        return this;
    }

    /**
     * or条件查询
     *
     * @param selectUtil
     * @return
     */
    public SelectUtil or(SelectUtil selectUtil) {
        StringBuilder sb = new StringBuilder();
        List<String> conditions = selectUtil.getConditions();
        if (!CollectionUtil.isEmpty(conditions)) {
            sb.append(" OR ( ");
            sb.append(String.join(" AND ", conditions));
        }
        sb.append(" ) ");
        orConditions.add(sb.toString());
        return this;
    }

    public SelectUtil or(Boolean aBoolean, SelectUtil selectUtil) {
        if (aBoolean) {
            return or(selectUtil);
        }
        return this;
    }

    /**
     * like 就是 模糊查询
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil like(String column, Object value) {
        conditions.add(column + " Like '%" + value + "%'");
        return this;
    }

    public SelectUtil like(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return like(column, value);
        }
        return this;
    }

    /**
     * not like 就是 模糊查询不像
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil notLike(String column, Object value) {
        conditions.add(column + " NOT Like '%" + value + "%'");
        return this;
    }

    public SelectUtil notLike(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return notLike(column, value);
        }
        return this;
    }

    /**
     * lt 就是 less than小于
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil lt(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " < " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil lt(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return lt(column, value);
        }
        return this;
    }

    /**
     * 小于等于 less than or equal
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil le(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " <= " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil le(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return le(column, value);
        }
        return this;
    }

    /**
     * gt 就是 greater than大于
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil gt(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " > " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil gt(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return gt(column, value);
        }
        return this;
    }

    /**
     * ge 就是 greater than or equal 大于等于
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil ge(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " >= " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil ge(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return ge(column, value);
        }
        return this;
    }

    /**
     * isNull 就是 等于null
     *
     * @param column
     * @return
     */
    public SelectUtil isNull(String column) {
        conditions.add(column + " IS NULL");
        return this;
    }

    public SelectUtil isNull(Boolean aBoolean, String column) {
        if (aBoolean) {
            return isNull(column);
        }
        return this;
    }

    /**
     * 字段不为null
     *
     * @param column
     * @return
     */
    public SelectUtil isNotNull(String column) {
        conditions.add(column + " IS NOT NULL");
        return this;
    }

    public SelectUtil isNotNull(Boolean aBoolean, String column) {
        if (aBoolean) {
            return isNotNull(column);
        }
        return this;
    }

    /**
     * ne就是 not equal不等于
     *
     * @param column
     * @param value
     * @return
     */
    public SelectUtil ne(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        conditions.add(column + " <> " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    public SelectUtil ne(Boolean aBoolean, String column, Object value) {
        if (aBoolean) {
            return ne(column, value);
        }
        return this;
    }

    /**
     * between 就是 在2个条件之间(包括边界值)
     *
     * @param column
     * @param start
     * @param end
     * @return
     */
    public SelectUtil between(String column, Object start, Object end) {
        String startStr = "";
        String endStr = "";
        if (changeObjToSqlCharFlag) {
            startStr = DataBaseUtil.changeObjToSqlChar(start);
            endStr = DataBaseUtil.changeObjToSqlChar(end);
        }
        conditions.add(column + " BETWEEN " + (StrUtil.isEmpty(startStr) ? start : startStr) + " AND " + (StrUtil.isEmpty(endStr) ? end : endStr));
        return this;
    }

    public SelectUtil between(Boolean aBoolean, String column, Object start, Object end) {
        if (aBoolean) {
            return between(column, start, end);
        }
        return this;
    }

    /**
     * 统计
     *
     * @return
     */
    public SelectUtil count() {
        if (!StrUtil.isEmpty(this.count)) {
            throw new ValidateException("count方法不允许多次调用!");
        }
        this.count = "COUNT(*) AS count";
        return this;
    }

    /**
     * 自定义查询列
     *
     * @param selectColumns
     * @return
     */
    public SelectUtil selectColumns(String selectColumns) {
        if (!StrUtil.isEmpty(this.selectColumns)) {
            throw new ValidateException("selectColumns方法不允许多次调用!");
        }
        this.selectColumns = selectColumns;
        return this;
    }

    /**
     * 分组
     *
     * @param condition
     * @return
     */
    public SelectUtil groupBy(String condition) {
        groupByConditions.add(condition);
        return this;
    }

    /**
     * 以什么字段顺序排序
     *
     * @param column
     * @return
     */
    public SelectUtil orderByAsc(String column) {
        String orderByCondition = column + " ASC";
        orderByConditions.add(orderByCondition);
        return this;
    }

    /**
     * 以什么字段倒序排序
     *
     * @param column
     * @return
     */
    public SelectUtil orderByDesc(String column) {
        String orderByCondition = column + " DESC";
        orderByConditions.add(orderByCondition);
        return this;
    }

    /**
     * in 就是 in 包含（数组）
     *
     * @param column
     * @param values
     * @return
     */
    public SelectUtil in(String column, List<?> values) {
        StringBuilder sb = new StringBuilder();

        sb.append(column).append(" IN (");

        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            String str = "";
            if (changeObjToSqlCharFlag) {
                str = DataBaseUtil.changeObjToSqlChar(value);
            }
            sb.append(StrUtil.isEmpty(str) ? value : str);
            if (i < values.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(")");

        conditions.add(sb.toString());

        return this;
    }

    public SelectUtil in(Boolean aBoolean, String column, List<?> values) {
        if (aBoolean) {
            return in(column, values);
        }
        return this;
    }

    /**
     * not in 就是 not in 不包含（数组）
     *
     * @param column
     * @param values
     * @return
     */
    public SelectUtil notIn(String column, List<?> values) {
        StringBuilder sb = new StringBuilder();

        sb.append(column).append(" NOT IN (");

        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            String str = "";
            if (changeObjToSqlCharFlag) {
                str = DataBaseUtil.changeObjToSqlChar(value);
            }
            sb.append(StrUtil.isEmpty(str) ? value : str);
            if (i < values.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(")");

        conditions.add(sb.toString());

        return this;
    }

    public SelectUtil notIn(Boolean aBoolean, String column, List<?> values) {
        if (aBoolean) {
            return notIn(column, values);
        }
        return this;
    }

    public SelectUtil apply(String condition) {
        conditions.add(condition);
        return this;
    }


    public String getSql() {

        StringBuilder sb = new StringBuilder();

        if (StrUtil.isEmpty(tableName)) {
            Model model = (Model) entityType.getAnnotation(Model.class);
            tableName = model.tableName();
            if (StrUtil.isEmpty(tableName)) {
                throw new ValidateException("表名不能为空");
            }
        }

        String columns = allColumns;
        if (!StrUtil.isEmpty(count)) {
            columns = count;
        } else if (!StrUtil.isEmpty(selectColumns)) {
            columns = selectColumns;
        }

        sb.append("SELECT ").append(columns).append(" FROM ").append(tableName);
        if (!CollectionUtil.isEmpty(conditions)) {
            sb.append(" WHERE ");
            sb.append(String.join(" AND ", conditions));
        }

        if (!CollectionUtil.isEmpty(orConditions)) {
            orConditions.stream().forEach(item -> {
                sb.append(item);
            });
        }

        if (!CollectionUtil.isEmpty(groupByConditions)) {
            sb.append(" GROUP BY ");
            sb.append(String.join(", ", groupByConditions));
        }

        if (!CollectionUtil.isEmpty(orderByConditions)) {
            sb.append(" ORDER BY ");
            sb.append(String.join(", ", orderByConditions));
        }

        if (!ObjectUtils.isEmpty(current) && !ObjectUtils.isEmpty(pageSize)) {
            sb.append(" LIMIT ");
            sb.append((current - 1) * pageSize);
            sb.append(" , ");
            sb.append(pageSize);
            sb.append(" ");
        }

        return sb.toString();
    }
}
