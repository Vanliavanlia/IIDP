package com.sie.iidp.common.util.database;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sie.iidp.common.util.dto.PageViewDTO;
import com.sie.snest.engine.data.access.BussModelDataAccess;
import com.sie.snest.engine.data.access.ModelDataAccessFactory;
import com.sie.snest.engine.data.access.ModelTypeEnum;
import com.sie.snest.engine.db.relationdb.RelationDBAccessor;
import com.sie.snest.engine.utils.CamelCaseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库操作工具类
 *
 * @author cxh
 * @date 2024年03月27日 13:54
 */
@Slf4j
public class DataBaseUtil {

    public final static String NULL_CHAR = "null";

    /**
     * 查询操作
     *
     * @param sql
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> List<T> executeSelectSQL(String sql, Class<T> clazz) {
        log.info("executeSelectSQL输出:{}", sql);
        if (StrUtil.isBlank(sql)) {
            throw new ValidateException("sql不能为空!");
        }
        List<Map<String, Object>> list = getMaps(sql);
        return JSONArray.parseArray(JSON.toJSONString(list, SerializerFeature.WriteMapNullValue), clazz);
    }

    private static List<Map<String, Object>> getMaps(String sql) {
        RelationDBAccessor dataBaseLink = getDataBaseLink();
        dataBaseLink.execute(sql);
        List<Map<String, Object>> tempList = dataBaseLink.fetchMapAll();
        List<Map<String, Object>> list = new ArrayList<>();
        tempList.forEach(item -> {
            Map<String, Object> map = new HashMap<>();
            item.forEach((key, value) -> {
                map.put(CamelCaseUtils.toCamelCase(key), value);
            });
            list.add(map);
        });
        return list;
    }

    public static List<Map<String, Object>> executeSelectSQL(String sql) {
        return getMaps(sql);
    }

    public static <T> T getSelectOne(String sql, Class<T> clazz) {
        List<T> list = executeSelectSQL(sql, clazz);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        if (list.size() > 1) {
            throw new ValidateException("你查询的数据不唯一!请确认数据唯一性,sql: " + sql);
        }
        return list.get(0);
    }

    public static Map<String, Object> getSelectOne(String sql) {
        List<Map<String, Object>> list = executeSelectSQL(sql);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        if (list.size() > 1) {
            throw new ValidateException("你查询的数据不唯一!请确认数据唯一性,sql: " + sql);
        }
        return list.get(0);
    }


    /**
     * 修改 或插入操作
     */
    public static void executeUpdateSql(String sql) {
        RelationDBAccessor dataBaseLink = getDataBaseLink();
        if (StringUtils.isEmpty(sql)) {
            throw new ValidateException("sql不能为空!");
        }
        log.info("executeUpdateSql输出:{}", sql);
        dataBaseLink.execute(sql);
    }


    private static RelationDBAccessor getDataBaseLink() {
        BussModelDataAccess bussModelDataAccess = (BussModelDataAccess) ModelDataAccessFactory.getDataAccess(ModelTypeEnum.Buss);
        RelationDBAccessor dataAccessor = bussModelDataAccess.getRelationDBAccessor();
        return dataAccessor;
    }


    /**
     * 可支持List<String> String[]  String  Date LocalDateTime Timestamp
     *
     * @param obj
     * @return
     */
    public static String changeObjToSqlChar(Object obj) {
        String sqlChar = "";
        if (null == obj) {
            return null;
        }
        if (ObjectUtils.isEmpty(obj)) {
            return "''";
        }
        if (obj instanceof List) {
            List list = (List) obj;
            Object o = list.get(0);
            if (o instanceof String) {
                List<String> quotedList = (List<String>) list.stream().map(s -> "'" + s + "'").collect(Collectors.toList());
                sqlChar = String.join(",", quotedList);
            } else {
                sqlChar = (String) list.stream().map(Object::toString).collect(Collectors.joining(","));
            }
        }
        if (obj instanceof String[]) {
            String[] array = (String[]) obj;
            List<String> quotedList = Arrays.stream(array).map(s -> "'" + s + "'").collect(Collectors.toList());
            sqlChar = String.join(",", quotedList);
        }
        if (obj instanceof String) {
            if (NULL_CHAR.equals(String.valueOf(obj))) {
                sqlChar = String.valueOf(obj);
            } else {
                sqlChar = "'" + String.valueOf(obj) + "'";
            }
        }
        if (obj instanceof Date) {
            Date date = (Date) obj;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sqlChar = "'" + sdf.format(date) + "'";
        }
        if (obj instanceof LocalDateTime) {
            LocalDateTime localDateTime = (LocalDateTime) obj;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            sqlChar = "'" + localDateTime.format(formatter) + "'";
        }
        if (obj instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) obj;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sqlChar = "'" + sdf.format(timestamp) + "'";
        }
        return sqlChar;
    }

    public static <T> PageViewDTO<T> page(SelectUtil selectUtil, Integer current, Integer pageSize, Class<T> clazz) {
        //深拷贝
        SelectUtil countSelectUtil = JSON.parseObject(JSON.toJSONString(selectUtil), SelectUtil.class);

        String totalSql = countSelectUtil.count().getSql();
        Map<String, Object> totalMap = DataBaseUtil.getSelectOne(totalSql);
        Long total = (Long) totalMap.get("count");
        if (total == 0L) {
            return new PageViewDTO<>();
        }
        String sql = selectUtil.setCurrent(current).setPageSize(pageSize).getSql();
        List<T> records = DataBaseUtil.executeSelectSQL(sql, clazz);
        PageViewDTO<T> pageViewDTO = new PageViewDTO<>();
        pageViewDTO.setTotal(total);
        pageViewDTO.setSize(pageSize);
        pageViewDTO.setCurrent(current);
        pageViewDTO.setRecords(records);
        pageViewDTO.setPages((long) Math.ceil((double) total / pageSize));
        return pageViewDTO;
    }

    public static <T> PageViewDTO<T> customPage(String sql, Integer current, Integer pageSize, Class<T> clazz) {
        String totalSql = "select count(1) as count from(" + sql + ") temp";
        Map<String, Object> totalMap = DataBaseUtil.getSelectOne(totalSql);
        Long total = (Long) totalMap.get("count");
        if (total == 0L) {
            return new PageViewDTO<>();
        }
        sql = sql + " limit " + (current - 1) * pageSize + " , " + pageSize;
        List<T> records = DataBaseUtil.executeSelectSQL(sql, clazz);
        PageViewDTO<T> pageViewDTO = new PageViewDTO<>();
        pageViewDTO.setTotal(total);
        pageViewDTO.setSize(pageSize);
        pageViewDTO.setCurrent(current);
        pageViewDTO.setRecords(records);
        pageViewDTO.setPages((long) Math.ceil((double) total / pageSize));
        return pageViewDTO;
    }

}
