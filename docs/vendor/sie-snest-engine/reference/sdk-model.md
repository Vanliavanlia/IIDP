# sie-snest SDK — 模型定义层参考

包根：`com.sie.snest.sdk`。这是写业务时用的 API 面，是引擎 (`com.sie.snest.engine.*`) 之上的注解驱动门面。设计刻意对标 Odoo ORM。

## BaseModel —— Odoo `models.Model` 等价物

```java
public class BaseModel<T> extends LinkedHashMap { ... }
```

**关键事实：值存在 map 里，不在 Java 字段。** 声明的 Java 字段（`id`、`meta`、`recordSet`、`appClassLoad`、`proxy`）都是引擎管线，除 `id` 外全是 `@Ignore`。模型实例是「`Map<String,Object>` 的类型化视图」，等同 Odoo 中记录是句柄、值取自 ORM cache。

内部字段：
```java
@Ignore protected ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
@Property(columnName = PropertyMeta.ID) protected String id;
@Ignore protected Meta meta;
@Ignore protected RecordSet recordSet;
@Ignore protected ClassLoader appClassLoad;
@Ignore protected IDataAccess proxy;
```

### 字段访问

```java
public void   set(String property, Object value);  // = LinkedHashMap.put 的语义糖（无重写 put）
public Object load(String property);                // 原始读，不级联（= super.get）
public Object get(String property);                 // 重写！会解析关系字段（Odoo 式魔法）
```

`get(property)` 反射判断字段注解：
- 无关系注解 → 返回原始 map 值。
- `@ManyToOne` → 解析为引用记录（单个 BaseModel 子类或 Map）；若字段声明为 `String` 且配置 `ENGINE_SDK_MANY2ONE_RETURN_ID`，只返回 id 字符串；结果按 `"{id}_{字段名}"` 在每线程 `CACHE` 缓存；无则 `null`。
- `@OneToMany` → 返回 List（无则空 List）。
- `@ManyToMany` → 读 `@JoinTable` 元数据，构造 INNER JOIN SQL，列名映射回属性名，返回 List。
- 注意：关系解析异常被静默吞掉（`catch(Exception){}`），回退原始 map 值。

类型化 getter（都读 `super.get(attr)` 再用 `TypeKit` 转换）：
```java
getStr(attr)  // 还会拆解 Options / {value|pk} 形态的 Map
getInt getLong getBigInteger getDate getLocalDateTime(attr[,format])
getTime getTimestamp getDouble getFloat getShort getByte getBoolean
getBigDecimal getBytes getNumber
```

审计字段便捷读写：`getId()/setId()`、`getCreate_user()/setCreate_user()`、`getCreate_date()/setCreate_date()`、`getUpdate_user()/setUpdate_user()`、`getUpdate_date()/setUpdate_date()`（setter 返回 `BaseModel` 可链式）。

### 持久化 / 查询（委托 `DbUtils` 或 `RecordSet`）

```java
<T extends BaseModel> T select(Filter filter[, String... columns]);   // ≈ search limit 1
<T extends BaseModel> T selectById(String id[, String... columns]);   // ≈ browse
List<T> search(Filter filter, List<String> properties, Integer limit, Integer offset, String order);
T    create();                  // ≈ create
void batchCreate(List<T> tList);
long count(Filter filter);      // ≈ search_count
void update();                  // ≈ write
void batchUpdate(List<T> list);
void update(Filter filter);
void updateByFilter(Map<String,Object> values, Filter filter);  // 直 SQL，绕过默认 update
void delete();                  // ≈ unlink（带级联检查）
void delete(Filter filter);
void deleteByFilter(Filter filter[, boolean cascade]);          // 直 SQL，可选级联
```

### 事务 / 原生 SQL / 异步 / 服务

```java
void commit();  void rollback();          // = getMeta().commit()/rollback()
void execute(String sql[, Collection<?> params]);  void execute(List<String> sqlList);
void batchExecute(List<String> sqlList);
List<Object[]>            fetchAll();
List<Map<String,Object>>  fetchMapAll();
<T> List<T> fetchEntryAll([Class<?> clazz]);
CompletableFuture<Void> runAsync(Runnable);
<R> CompletableFuture<R> supplyAsync(Supplier<R>);
<S extends SdkService> S initService(Class<S>);      // 单例/原型
<S extends SdkService> S initNewService(Class<S>);   // 新原型
```

### env / 模型标识

```java
public Meta getMeta() { return BaseContextHandler.getMeta(); }   // 永远取线程绑定 Meta（≈ self.env），忽略实例字段
@Deprecated public RecordSet getRecordSet();                     // 已废弃
public String getModelName();          // @Model.name()，无则简单类名
private Class<?> getTargetClass();      // 剥离 CGLIB 代理（含 "$$"）
public List<String> getAllProperties();
public ClassLoader getAppClassLoad();
```

> ⚠️ `call`/`callSuper` **不在 BaseModel 上**，在 `RecordSet` 上。模型里通过 `getMeta().get(modelName)` 拿 RecordSet 再调用。

## @Model（`@Target(TYPE)`）—— 类级模型声明

```java
String name() default "";                 // 模型名（Odoo _name）
Bool isAutoLog() default Bool.Default;     // 自动 create_user/date、update_user/date
Bool isAutoLogTimeZone() default Bool.Default;
Bool isAutoLock() default Bool.Default;    // 自动 LockUser/IsLock 字段
String[] parent() default {};             // 继承父模型（Odoo _inherit，支持多继承）
String displayName() default "";
String displayFormat() default "";
String description() default "";          // Odoo _description
String tableName() default "";            // Odoo _table
Bool isAbstract() default Bool.Default;    // 抽象模型，无表（Odoo AbstractModel）
Model.DbMode dbMode() default Model.DbMode.Relational;
String orderBy() default "";              // Odoo _order
Model.ModelType type() default Model.ModelType.Default;
String[] files() default {};              // 配置型模型的后端文件（.properties/.json/.yml）
Index[] indexes() default {};             // 复合索引
Bool isLogicDelete() default Bool.False;   // 软删
Bool isShard() default Bool.False;  Shard shard() default ...;
Function function() default @Function();
Bool isPrint() default Bool.False;
Bool isScope() default Bool.False;         // 基于作用域的数据隔离
Model.Trace trace() default Trace.Unset;
String[] hiddenApi() default {};          // API 文档隐藏的服务
String dataSource() default "";           // 命名数据源
Bool broadcast() default Bool.Default;
```

嵌套枚举：
- `DbMode { Default, Relational, Tree }`
- `ModelType { Default, Define, Buss, Memory, Data, Cache, Config, Tree, Template, View, Mix }`
  - `Buss` = 普通持久化业务模型（最常用）。
  - `Mix` = DTO 类型（`SdkModel`/`RequestModel`/`ResponseModel` 用）。
  - `Define` = 元模型自身；`Tree`/`Config`/`View`/`Memory`/`Cache` 各对应一种数据访问实现。
- `Trace { Enable, Disable, Unset }`

## @Property（`@Target(METHOD, FIELD)`）—— 字段描述符

```java
String columnName() default "";
String displayName() default "";
DataType dataType() default DataType.NONE;
boolean store() default true;             // ≈ Odoo store=
Encrypt encrypt() default @Encrypt(...);
boolean readonly() default false;
boolean dbIndex() default false;
boolean display() default true;
boolean unique() default false;
boolean nullable() default true;          // DDL 可空
int length() default 0;  int scale() default 0;
ModelEnum[] values() default {};          // 枚举/选择项
String defaultValue() default "";
boolean displayForModel() default false;  // 该模型的「名称」字段（Odoo _rec_name / display_name）
String toolTips();  String widget();  int indexNo() default -1;  boolean percent();
String dateFormat();  String startDate();  String endDate();  String ruleScript();
String related() default "";              // ≈ Odoo related=
boolean password() default false;
String groovy();  String computeScript();
String defaultMethod() default "";        // ≈ Odoo default=
String computeMethod() default "";        // ≈ Odoo compute=
String validationMethod() default "";
String deserializeJavaType() default "";
boolean multiple() default false;
String contentType() default "";
String onChange() default "";             // ≈ Odoo @api.onchange
String[] linkageFields() default "";
boolean translate() default true;         // ≈ Odoo translate=
Bool isTimeZone() default Bool.Default;
String scopeModel() default "";
String jsonField() default "";  String jsonPath() default "";  // 如 ->author->>last_name
```

## @MethodService（`@Target(METHOD)`）—— 把方法暴露为服务

```java
String name() default "";          // 服务名，默认=方法名
String displayName(); String description(); String auth();  // auth=权限
boolean isRecordSet() default false;
Class<?> ServiceOrchestrateDefine() default void.class;
boolean sync() default false;
String timeZone() default "";      // 最高优先级时区覆盖
boolean ignoreScope() default false;
Model.Trace trace() default Model.Trace.Unset;
boolean hiddenApi() default false;
String doc() default "";  String principal(); String introduction(); String dataSource();
```

> ⚠️ 远程/RPC 调用传的是**服务名**，而 `call`/`callSuper` 传的是**方法名**——两者可能不同。

## @Service / @Action

```java
// @Service @Target(TYPE) —— 声明服务类
String name(); String description(); String auth();
Class<?> path() default void.class;       // 导入路径
boolean isExtension() default false;      // 是否扩展已有服务
String[] targets() default {};            // 继续调用的下游服务（扩展链）
String[] remove() default {};

// @Action @Target(METHOD) —— UI 按钮动作（≈ Odoo button method）
String name(); String description();
```

## ORM 关系注解（`annotation/orm/`）

全部 `@Target({METHOD,FIELD})`、`@Retention(RUNTIME)`。关系用「字段 + 关系类型 + targetModel/targetProperty」声明（Odoo 的 comodel + inverse）。

```java
// @ManyToOne ≈ Many2one（默认 EAGER）
String targetModel();  String targetProperty();
CascadeType[] cascade() default {};         // DEL_SET_NULL | DELETE | DEL_NO_PERMIT_RELATIVE
FetchType fetch() default FetchType.EAGER;
JoinType joinType() default JoinType.LEFT;
String displayName();  String displayFormat();   // 如 ${name}_${code}，默认取 displayForModel 字段
String filter() default "";                 // 域过滤（≈ Odoo domain=）
Bool foreignKey() default Bool.False;        // 是否建 DB 外键约束
String scopeModel();

// @OneToMany ≈ One2many（默认 LAZY，虚拟反向，无列）
String targetModel(); String targetProperty();  // targetProperty = 对方的 many2one 字段（Odoo inverse_name）
FetchType fetch() default FetchType.LAZY;  JoinType joinType() default JoinType.LEFT;
SubQueryType subQueryType() default SubQueryType.ROW;  String displayName();

// @ManyToMany ≈ Many2many（关联表用 @JoinTable 显式声明）
String targetModel(); String targetProperty();
CascadeType[] cascade() default {CascadeType.DELETE};
FetchType fetch() default FetchType.LAZY;  String displayName();

// @OneToOne（JPA 风格，无直接 Odoo 等价）
CascadeType[] cascade(); FetchType fetch() default LAZY; String targetModel(); String targetProperty();
String displayName(); JoinType joinType() default LEFT; String mappedBy(); String relatedProperty(); String relatedValue();

// @JoinColumn —— 外键列描述（JPA 风格）
String name(); @Deprecated String referencedColumnName() default "id"; String referencedProperty();
@Deprecated boolean store() default true; int length(); boolean unique(); boolean nullable() default true;

// @JoinTable —— M2M 关联表描述
String name(); String catalog(); String schema();
JoinColumn[] joinColumns();         // 本侧 FK 列
JoinColumn[] inverseJoinColumns();  // 对侧 FK 列

// @Cascade —— 独立级联标记
CascadeType[] value();
```

枚举：`CascadeType { DEL_SET_NULL, DELETE, DEL_NO_PERMIT_RELATIVE }`（≈ `ondelete='set null'/'cascade'/'restrict'`）；`FetchType { LAZY, EAGER }`。

> 与 Odoo 区别：comodel/inverse 用字符串（同 Odoo），但 M2M 关联表必须 `@JoinTable`/`@JoinColumn` 显式声明（JPA 遗风），Odoo 是自动建表。

## @Validate（容器类，每个校验器是嵌套 `@interface`）

`@Retention(RUNTIME)`，多数可重复（嵌套 `List`），JSR-380 风格：
- `AssertTrue / AssertFalse / Null / NotBlank` — `message, groups`
- `Max / Min` — `long value()`；`DecimalMax / DecimalMin` — `String value(), boolean inclusive() default true`
- `Range` — `int min()=0, int max()=Integer.MAX_VALUE`；`Size` — `int min, int max`
- `Email` — `regexp() default "email"`, `Pattern.Flag[] flags()`；`Phone` — `regexp() default "mobile"`；`Password`
- `Pattern` — `String regexp()`（必填）, `Flag[] flags()`；`enum Flag { UNIX_LINES, CASE_INSENSITIVE, COMMENTS, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ }`
- `Date` — `format, minDate, maxDate`；`DateTime` — `format, startDate, endDate, minDate, maxDate`
- `ScriptAssert` — `String lang()`, `String script()`（必填）, `alias() default "_this"`, `reportOn()`
- `Unique`（`@Target{TYPE,FIELD}`）— `String[] properties()`（多列唯一）
- `Validated`（`@Target{TYPE,METHOD,PARAMETER}`）— `Class<?>[] value()`（校验分组，≈ Spring `@Validated`）

## @Rules（容器类）—— 记录规则 / ACL / 自动化（≈ Odoo ir.rule + ir.model.access + 自动动作）

- `@Rules.Condition`（TYPE）— `name, ScopeEnum scope() default USER, String[] object(), script`
- `@Rules.Action`（TYPE）— `name, condition, Resource[] resources(), Method[] methods(), Filter[] filters()`
- `@Rules.Rule`（METHOD,TYPE）— `name, description, int priority() default 0`
- `@Rules.Resource`（METHOD,TYPE）— `name, action, ResourceType type() default MODEL, resource, Effect effect() default REFUSE`（默认拒绝）
- `@Rules.Method`（METHOD）— `name, OrderEnum[] BEA() default {AFTER}`（前/后置钩子序）, `methodSrc, methodDst, model, modelDst, String[] parameter()`
- `@Rules.Filter`（TYPE）— `name, model, String[] filter(), context`（按模型注入域过滤，最接近 `ir.rule` domain_force）
- `@Rules.Compose`（TYPE）— `name, String[] rules()`（组合多个规则）

## DTO 基类

```java
@Model(name="SdkModel", type=Model.ModelType.Mix) public class SdkModel extends LinkedHashMap { ... }
@Model(name="RequestModel",  type=Model.ModelType.Mix) public class RequestModel  extends SdkModel {}  // 入参 DTO
@Model(name="ResponseModel", type=Model.ModelType.Mix) public class ResponseModel extends SdkModel {}  // 出参 DTO
```

`SdkModel` 也是 map-backed，但**无持久化方法**——纯数据载体。`set()` 会查 `PropertyMeta`，对 DATE/DATE_TIME 做显示转换（元数据感知）但不落库。成员 `protected`，仅子类用。`RequestModel`/`ResponseModel` 是空标记子类，仅用于区分入/出参类型。

## SdkService —— 服务层基类

```java
@Service public class SdkService<T extends BaseModel> {
    public SdkService();                 // 反射推断泛型 T（不推荐）
    public SdkService(Class<T> modelClass);  // 显式构造（推荐：new XxxService(XxxService.class)）
    public Meta   getMeta()      { return BaseContextHandler.getMeta(); }  // ≈ self.env
    public String getModelName() { return ModelUtils.getModelName(modelClass); }
}
```
