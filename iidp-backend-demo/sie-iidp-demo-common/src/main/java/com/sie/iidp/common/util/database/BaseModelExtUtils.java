package com.sie.iidp.common.util.database;

import com.sie.iidp.common.util.consts.FieldConst;
import com.sie.iidp.common.util.fifter.FilterBuilder;
import com.sie.snest.engine.container.Meta;
import com.sie.snest.engine.context.BaseContextHandler;
import com.sie.snest.engine.data.RecordSet;
import com.sie.snest.engine.rule.Filter;
import com.sie.snest.sdk.BaseModel;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 基于iidp查询拓展工具类
 */
public class BaseModelExtUtils {


    /**
     * 调用iidp create创建
     *
     * @param entityList
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> Object create(List<T> entityList, Supplier<T> supplier) {
        T entity = supplier.get();
        Meta meta = entity.getMeta();
        RecordSet rs = meta.get(entity.getModelName());
        Object result = rs.callSuper(entity.getClass(), "create", entityList);
        return result;
    }

    /**
     * 复制baseMode
     *
     * @param source
     * @param target
     * @param ignoreProperty
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> T copy(T source, T target, String... ignoreProperty) {
        Set<String> ignorePropertySet = new HashSet<>();
        if (ignoreProperty != null) {
            for (String property : ignoreProperty) {
                ignorePropertySet.add(property);
            }
        }
        for (Object entry : source.entrySet()) {
            Map.Entry<String, Object> entryVal = (Map.Entry<String, Object>) entry;
            String entryKey = entryVal.getKey();
            if (ignorePropertySet.contains(entryKey)) {
                continue;
            }
            target.put(entryKey, entryVal.getValue());
        }
        return target;
    }


    /**
     * 根据id进行查找
     *
     * @param clazz
     * @param modelName
     * @param id
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> Optional<T> findById(Class<T> clazz, String modelName, String id, Supplier<T> supplier) {
        RecordSet modelRs = supplier.get().getMeta().get(modelName);
        Filter filter = Filter.equal(FieldConst.ID_FIELD_NAME, id);
        List<T> resultList = (List<T>) modelRs.callSuper(clazz, "search", filter, Collections.singletonList("*"), 0, 0, null);
        if (CollectionUtils.isEmpty(resultList)) {
            return Optional.empty();
        }
        T entity = (T) convert(resultList.get(0), supplier);
        return Optional.of(entity);
    }

    /**
     * 根据key进行查找
     *
     * @param fieldKey
     * @param queryVal
     * @param supplier
     * @param <T>
     * @return
     */

    public static <T extends BaseModel<T>> Optional<T> findOneByKey(String fieldKey, String queryVal, Supplier<T> supplier) {
        Filter filter = Filter.equal(fieldKey, queryVal);
        return findOneByFilter(filter, supplier);
    }

    /**
     * 根据filter查询一个实体
     *
     * @param filter
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> Optional<T> findOneByFilter(Filter filter, Supplier<T> supplier) {
        T queryEntity = supplier.get();
        List<T> entityList = queryEntity.search(filter, Collections.singletonList("*"), 1, 0, null);
        if (CollectionUtils.isEmpty(entityList)) {
            return Optional.empty();
        }
        T firstEntity = entityList.get(0);
        return Optional.of(firstEntity);
    }

    public static <T extends BaseModel<T>> List<T> findByIdList(List<String> idList, Supplier<T> supplier) {
        if (CollectionUtils.isEmpty(idList)) {
            return Collections.emptyList();
        }
        Filter idFilter = FilterBuilder.buildInFilter(FieldConst.ID_FIELD_NAME, idList);
        return findByFilter(idFilter, supplier);
    }

    /**
     * 根据filter查询实体
     *
     * @param filter
     * @param supplier
     * @param <T>
     * @return
     */

    public static <T extends BaseModel<T>> List<T> findByFilter(Filter filter, Supplier<T> supplier) {
        T queryEntity = supplier.get();
        List<T> entityList = queryEntity.search(filter, Collections.singletonList("*"), 0, 0, null);
        return entityList;
    }

    /**
     * 根据filter查询实体
     *
     * @param filter
     * @param modelName
     * @return
     */
    public static List<Map<String, Object>> findByFilter(Filter filter, List<String> properties, String modelName) {
        List<Map<String, Object>> entityList = BaseContextHandler.getMeta().get(modelName).search(filter, properties, 0, 0, null);
        return entityList;
    }

    public static List<Map<String, Object>> findByFilter(Filter filter, List<String> properties, String modelName, String order) {
        List<Map<String, Object>> entityList = BaseContextHandler.getMeta().get(modelName).search(filter, properties, 0, 0, order);
        return entityList;
    }


    /**
     * 根据filter查询实体
     *
     * @param filter
     * @param modelName
     * @return
     */

    public static List<Map<String, Object>> findByFilter(Filter filter, String modelName) {
        List<Map<String, Object>> entityList = BaseContextHandler.getMeta().get(modelName).search(filter, Collections.singletonList("*"), 0, 0, null);
        return entityList;
    }

    /**
     * 根据实体
     *
     * @param fieldKey
     * @param queryValSet
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> List<T> findByKey(String fieldKey, Set<String> queryValSet, Supplier<T> supplier) {
        if (CollectionUtils.isEmpty(queryValSet)) {
            return Collections.emptyList();
        }
        Filter keyFilter = Filter.in(fieldKey, queryValSet);
        return findByFilter(keyFilter, supplier);
    }

    /**
     * 根据key集合查询多个实体
     *
     * @param fieldKey
     * @param queryValSet
     * @param modelName
     * @return
     */
    public static List<Map<String, Object>> findByKey(String fieldKey, Set<String> queryValSet, String modelName) {
        if (CollectionUtils.isEmpty(queryValSet)) {
            return Collections.emptyList();
        }
        Filter keyFilter = Filter.in(fieldKey, queryValSet);
        return findByFilter(keyFilter, modelName);
    }

    /**
     * 根据key查询一个实体
     *
     * @param fieldKey
     * @param queryVal
     * @param modelName
     * @return
     */
    public static Optional<Map<String, Object>> findOneByKey(String fieldKey, String queryVal, String modelName) {
        Filter filter = Filter.equal(fieldKey, queryVal);
        return findOneByFilter(filter, modelName);
    }

    /**
     * 根据filter查询一个实体
     *
     * @param filter
     * @param modelName
     * @return
     */

    public static Optional<Map<String, Object>> findOneByFilter(Filter filter, String modelName) {
        RecordSet recordSet = BaseContextHandler.getMeta().get(modelName);
        List<Map<String, Object>> entityList = recordSet.search(filter, Collections.singletonList("*"), 1, 0, null);
        if (CollectionUtils.isEmpty(entityList)) {
            return Optional.empty();
        }
        Map<String, Object> entity = entityList.get(0);
        return Optional.of(entity);
    }

    /**
     * 将mapList转换为entityList
     *
     * @param dataList
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> List<T> convert(List<Map<String, Object>> dataList, Supplier<T> supplier) {
        return convert(dataList, supplier, null);
    }

    /**
     * 根据mapList转换成对应的EntityList
     *
     * @param dataList
     * @param supplier
     * @param entityConsumer
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> List<T> convert(List<Map<String, Object>> dataList, Supplier<T> supplier, Consumer<T> entityConsumer) {
        List<T> resultList = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            T entity = convert(data, supplier);
            if (entityConsumer != null) {
                entityConsumer.accept(entity);
            }
            resultList.add(entity);
        }
        return resultList;
    }

    /**
     * 根据map转换为实体
     *
     * @param data
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> T convert(Map<String, Object> data, Supplier<T> supplier) {
        T entity = supplier.get();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                entity.set(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return entity;
    }

    /**
     * 统计记录数
     *
     * @param filter
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T extends BaseModel<T>> long count(Filter filter, Supplier<T> supplier) {
        T entity = supplier.get();
        return entity.count(filter);
    }

}
