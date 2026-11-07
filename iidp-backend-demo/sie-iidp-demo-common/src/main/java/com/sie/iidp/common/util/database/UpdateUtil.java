package com.sie.iidp.common.util.database;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import com.sie.snest.sdk.annotation.meta.Model;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chenjunliang1
 * @date 2023/12/7 11:36
 */
@Data
@NoArgsConstructor
public class UpdateUtil {

    private List<String> setConditions = new ArrayList<>();

    private List<String> whereConditions = new ArrayList<>();

    private List<String> orConditions = new ArrayList<>();

    private String tableName;

    private Class entityType;

    /**
     * 是否开启数据库识别的字符串转换
     */
    private Boolean changeObjToSqlCharFlag = true;

    public UpdateUtil(Class modelClazz, Boolean changeObjToSqlCharFlag) {
        this.entityType = modelClazz;
        this.changeObjToSqlCharFlag = changeObjToSqlCharFlag;
    }

    public UpdateUtil(Class modelClazz) {
        this.entityType = modelClazz;
    }

    public UpdateUtil(String tableName, Boolean changeObjToSqlCharFlag) {
        this.tableName = tableName;
        this.changeObjToSqlCharFlag = changeObjToSqlCharFlag;
    }

    public UpdateUtil(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 设置更新的字段和值
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil set(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        setConditions.add(column + " = " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：等于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil eq(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " = " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * or条件查询
     *
     * @param updateUtil
     * @return
     */
    public UpdateUtil or(UpdateUtil updateUtil) {
        StringBuilder sb = new StringBuilder();
        List<String> conditions = updateUtil.getWhereConditions();
        if (!CollectionUtil.isEmpty(conditions)) {
            sb.append(" OR ( ");
            sb.append(String.join(" AND ", conditions));
        }
        sb.append(" ) ");
        orConditions.add(sb.toString());
        return this;
    }

    /**
     * 设置更新的条件：模糊匹配
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil like(String column, String value) {
        whereConditions.add(column + " LIKE '%" + value + "%'");
        return this;
    }

    /**
     * not Like
     *
     * @param column
     * @param value
     * @return
     */
    public UpdateUtil notLike(String column, String value) {
        whereConditions.add(column + " NOT LIKE '%" + value + "%'");
        return this;
    }

    /**
     * 设置更新的条件：小于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil lt(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " < " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：小于等于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil le(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " <= " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：大于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil gt(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " > " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：大于等于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil ge(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " >= " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：字段为空
     *
     * @param column 字段名
     * @return UpdateUtil对象
     */
    public UpdateUtil isNull(String column) {
        whereConditions.add(column + " IS NULL");
        return this;
    }

    /**
     * 字段不为null
     *
     * @param column
     * @return
     */
    public UpdateUtil isNotNull(String column) {
        whereConditions.add(column + " IS NOT NULL");
        return this;
    }

    /**
     * 设置更新的条件：不等于
     *
     * @param column 字段名
     * @param value  值
     * @return UpdateUtil对象
     */
    public UpdateUtil ne(String column, Object value) {
        String str = "";
        if (changeObjToSqlCharFlag) {
            str = DataBaseUtil.changeObjToSqlChar(value);
        }
        whereConditions.add(column + " <> " + (StrUtil.isEmpty(str) ? value : str));
        return this;
    }

    /**
     * 设置更新的条件：在两个值之间（包括边界值）
     *
     * @param column 字段名
     * @param start  起始值
     * @param end    结束值
     * @return UpdateUtil对象
     */
    public UpdateUtil between(String column, Object start, Object end) {
        String startStr = "";
        String endStr = "";
        if (changeObjToSqlCharFlag) {
            startStr = DataBaseUtil.changeObjToSqlChar(start);
            endStr = DataBaseUtil.changeObjToSqlChar(end);
        }
        whereConditions.add(column + " BETWEEN " + (StrUtil.isEmpty(startStr) ? start : startStr)
                + " AND " + (StrUtil.isEmpty(endStr) ? end : endStr));
        return this;
    }

    /**
     * in查询
     *
     * @param column
     * @param values
     * @return
     */
    public UpdateUtil in(String column, List<?> values) {
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

        whereConditions.add(sb.toString());

        return this;
    }

    /**
     * not in 查询
     *
     * @param column
     * @param values
     * @return
     */
    public UpdateUtil notIn(String column, List<?> values) {
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

        whereConditions.add(sb.toString());

        return this;
    }

    public UpdateUtil apply(String whereCondition) {
        whereConditions.add(whereCondition);
        return this;
    }


    /**
     * 获取生成的更新SQL语句
     *
     * @return 更新SQL语句
     */
    public String getSql() {
        StringBuilder sb = new StringBuilder();

        if (StrUtil.isEmpty(tableName)) {
            Model model = (Model) entityType.getAnnotation(Model.class);
            tableName = model.tableName();
            if (StrUtil.isEmpty(tableName)) {
                throw new ValidateException("表名不能为空");
            }
        }

        if (CollectionUtils.isEmpty(setConditions)) {
            throw new ValidateException("updateUtil set 条件不能为空!");
        }

        sb.append("UPDATE ").append(tableName).append(" SET ");
        sb.append(String.join(", ", setConditions));
        if (!CollectionUtils.isEmpty(whereConditions)) {
            sb.append(" WHERE ");
            sb.append(String.join(" AND ", whereConditions));
        }

        if (!CollectionUtil.isEmpty(orConditions)) {
            orConditions.stream().forEach(item -> {
                sb.append(item);
            });
        }

        return sb.toString();
    }
}
