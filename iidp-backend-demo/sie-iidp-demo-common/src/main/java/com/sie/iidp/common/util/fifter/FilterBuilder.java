package com.sie.iidp.common.util.fifter;

import com.sie.iidp.common.util.condition.Condition;
import com.sie.iidp.common.util.condition.ConvertType;
import com.sie.snest.engine.rule.Filter;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.*;

public class FilterBuilder {

    private final static int MAX_PARAM_NUM = 1000;

    /**
     * 根据Condition注解构建filter
     *
     * @param object
     * @param <K>
     * @return
     */
    public static <K> Filter buildFilter(K object) {
        if (object == null) {
            return new Filter();
        }
        Field[] fields = object.getClass().getDeclaredFields();
        Filter filter = new Filter();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Condition.class)) {
                Condition condition = field.getAnnotation(Condition.class);
                Optional<Filter> singleFieldFilter = null;
                try {
                    singleFieldFilter = buildSingleFilter(condition, object, field);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (singleFieldFilter.isPresent()) {
                    filter = filter.and(singleFieldFilter.get());
                }
            }
        }
        return filter;
    }

    /**
     * 根据condition注解，够建filter
     *
     * @param condition
     * @param object
     * @param field
     * @return
     * @throws IllegalAccessException
     */
    public static Optional<Filter> buildSingleFilter(Condition condition, Object object, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object val = field.get(object);
        if (!isValidVal(val)) {
            return Optional.empty();
        }
        String fieldName = condition.fieldName();
        if (StringUtils.isEmpty(fieldName)) {
            fieldName = field.getName();
        }
        Filter filter = new Filter();
        Object handleVal = convertHandleVal(condition, val);
        switch (condition.queryType()) {
            case LIKE:
                filter = filter.like(fieldName, handleVal);
                break;
            case NOT_LIKE:
                filter = filter.notLike(fieldName, handleVal);
                break;
            case EQUAL:
                filter = filter.equal(fieldName, handleVal);
                break;
            case NOT_EQUAL:
                filter = filter.notEqual(fieldName, handleVal);
                break;
            case IN:
                filter = filter.in(fieldName, handleVal);
                break;
            case NOT_IN:
                filter = filter.notIn(fieldName, handleVal);
                break;
            case GE:
                filter = filter.greaterOrEqual(fieldName, handleVal);
                break;
            case GT:
                filter = filter.greater(fieldName, handleVal);
                break;
            case LE:
                filter = filter.lessOrEqual(fieldName, handleVal);
                break;
            case LT:
                filter = filter.less(fieldName, handleVal);
                break;
            case IS_NULL:
                filter = filter.equalNull(fieldName);
                break;
            case IS_NOT_NULL:
                filter = filter.notEqualNull(fieldName);
                break;
        }
        return Optional.of(filter);
    }

    /**
     * 拼接in查询
     *
     * @param fieldName
     * @param valList
     * @return
     */
    public static Filter buildInFilter(String fieldName, List<String> valList) {
        if (valList.size() <= MAX_PARAM_NUM) {
            return Filter.in(fieldName, valList);
        }
        List<List<String>> splitLists = new ArrayList<>();
        List<String> currentList = new ArrayList<>(); // 当前子列表
        for (String val : valList) {
            currentList.add(val);
            if (currentList.size() >= MAX_PARAM_NUM) {
                splitLists.add(currentList);
                currentList = new ArrayList<>(); // 创建新的子列表
            }
        }
        if (!currentList.isEmpty()) {
            splitLists.add(currentList);
        }
        Filter inFilter = new Filter();
        for (List<String> splitList : splitLists) {
            Filter filter = Filter.in(fieldName, splitList);
            inFilter.or(filter);
        }
        return inFilter;
    }

    /**
     * 判断值是否有效
     *
     * @param val
     * @return
     */
    private static boolean isValidVal(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof String) {
            return StringUtils.isNotEmpty(String.valueOf(val));
        }
        if (val instanceof Collection) {
            return !CollectionUtils.isEmpty((Collection<?>) val);
        }
        return true;
    }

    /**
     * 获取真正需要处理的的值
     *
     * @param condition
     * @param val
     * @return
     */
    private static Object convertHandleVal(Condition condition, Object val) {
        if (condition.convertType() == ConvertType.NOT_NEED) {
            return val;
        }
        if (val instanceof Long && condition.convertType() == ConvertType.DATE) {
            return new Date(Long.parseLong(String.valueOf(val)));
        }
        return val;
    }
}
