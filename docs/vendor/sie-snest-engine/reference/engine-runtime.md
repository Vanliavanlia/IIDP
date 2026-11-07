# sie-snest 引擎运行时参考

包根：`com.sie.snest.engine`。模型/字段/方法都是**元数据**（`ModelMeta`/`PropertyMeta`/`MethodMeta`）；所有数据访问经由绑定到 env 式对象 `Meta` 的 `RecordSet`；跨 app 方法覆盖通过同名方法链表复刻 Odoo 的 `super()`。

## 1. Meta —— Odoo 的 `env`

`container/Meta.java`。每请求的执行环境，`Closeable`，绑定当前线程，携带身份 + 上下文 + 数据访问句柄。

```java
public class Meta implements Closeable {
    public static final String SUPERUSER = "rbac_user_superuser";   // ≈ Odoo SUPERUSER_ID
    private final static ThreadLocal<Meta> metaThreadLocal = ...;
    private Map<String,Object> arguments;   // 调用入参
    private Map<String,Object> result;      // 结果
    private Map<String,Object> context;     // ≈ env.context（lang, tz, tenant, token…）
    private String userId;                  // ≈ env.uid
    private String tenantId;
    private String modelName, serviceName, app, tag, timeZone, dataSource;
    private IDataAccess dataAccess, modelDefineDataAccess;
}
```

构造即绑定线程，user 默认 SUPERUSER：
```java
public Meta(String userId, Map<String,Object> context) {
    this.userId = StringUtils.defaultIfBlank(userId, SUPERUSER);
    if ("superuser".equals(userId)) this.userId = SUPERUSER;
    this.modelDefineDataAccess = ModelDataAccessFactory.getDataAccess(ModelTypeEnum.Define);
    metaThreadLocal.set(this);
    BaseContextHandler.setMeta(this);
}
```

`meta.get("model")` → RecordSet（= `env["model"]`）：
```java
public RecordSet get(String modelName, String... ids) {
    this.setModelName(modelName);
    return ((ModelDefineDataAccess) ModelDataAccessFactory.getDataAccess(ModelTypeEnum.Define))
              .getModel(this, modelName, ids);   // 查 ModelMeta → browse；不存在则返回空哨兵 RecordSet
}
```

携带的上下文（Odoo env 对照）：
- `getUserId()` = `env.uid`（默认 SUPERUSER）；`getUser()` 懒查 `get("rbac_user", userId)` = `env.user`。
- `getTenantId()` 从 `context["tenantId"]` 取，SUPERUSER 时回退 `ROOT`("root")，否则取 `rbac_user.getTenantId()`。
- `getLocale()`/`getTimeZone()` 从 context。`isAdmin()` = `get("rbac_security").call("isAdmin", userId)`。
- 会话（角色、作用域组 sg、全局过滤）经 `getSession(userId)`（`UserSessionService`）。

事务生命周期（≈ Odoo cursor）：
```java
public void commit()  { flush(); ...Buss.commit(this); }
public void flush()   { /* 把 Meta.toUpdate 按模型刷写 */ }
public void rollback(){ ...Buss.rollback(this); }
public void close()   { metaThreadLocal.remove(); ...Buss.close(this); cleanThreadLocalCache(); }
```

提权（≈ `sudo()`）：
```java
public static void executeAsSuperUser(Runnable runnable);
public static void executeAs(String userId, Map<String,Object> context, Runnable runnable);  // 临时换 userId+context，finally 还原
public static Meta getCurrentMeta();
```
> `Meta.call/callSuper/callAsync(app,model,...)` 已 `@Deprecated`（旧 MBM 跨容器路径）。用 `RecordSet.call/callSuper`。

## 2. RecordSet —— Odoo recordset，模型方法的统一入口

`data/RecordSet.java`。由 `(meta, model, ids)` 构建。

```java
public class RecordSet implements Iterable<RecordSet> {
    private String[] ids;  private Supplier<String[]> prefetchIds;  private Meta meta;  private ModelMeta model;
    @JsonIgnore public Meta getMeta();  @JsonIgnore public ModelMeta getModel();
    public String[] getIds(); public String getId(); public boolean any(); public int size();
    public void ensureOne();                 // = self.ensure_one()
    public RecordSet browse(String... ids);  public RecordSet first();  public RecordSet filter(Predicate<RecordSet>);
}
```

CRUD/search 都是 `call(...)` 的薄封装（所以都走重写链 + 数据访问派发）：
```java
public RecordSet create(List<Map<String,Object>> valuesList){ return (RecordSet) call("create", this, valuesList); }
public void      update(Map<String,Object> values)         { call("update", this, values); }
public void      delete()                                  { call("delete", this); }
public RecordSet find(Filter f, Integer limit, Integer offset, String order);
public List<Map<String,Object>> search(Filter f, List<String> props, Integer limit, Integer offset, String order);
public List<Map<String,Object>> read(List<String> properties);
public long count(Filter f);
public Object get(String propertyName);                    // = call("get", propertyName)
public void   set(String propertyName, Object value);      // = getModel().getProperty(p).set(this, value)
```

### call —— 派发核心

```java
public Object call(@ModelMethod String method, Object... args) {
    ModelMeta modelMeta = getModel();
    if (modelMeta.isRef()) modelMeta = meta.get(modelMeta.getRefPath()).getModel();   // 解析模型别名/ref
    if (this.meta.getServiceName() == null) this.meta.setServiceName(method);
    Optional<ServiceMeta> serviceOpt = modelMeta.getServiceByName(method);
    if (serviceOpt.isPresent() && serviceOpt.get().isHttpService())                   // 旧 HTTP 服务
        return HttpServiceImpl.executeByHttpService(getMeta(), serviceOpt.get(), args);
    WebMode curRunMode = WebMode.of(ConfigUtils.get(MetaConstant.ENGINE_RUN_MODE));
    Invocation invocation = InvFactory.getWeM(curRunMode);    // 本地 Cgl vs 分布式 Rpc
    return invocation.invoke(this.meta, modelMeta, method, getArgs(args));
}
```

`getArgs` 保证 receiver RecordSet 永远是参数 0（模型方法签名即 `method(RecordSet rs, ...)`，等于 Python 的 `self`）：
```java
private Object[] getArgs(Object[] args) {
    if (args.length > 0 && args[0] instanceof RecordSet) return args;
    Object[] result = new Object[args.length + 1];
    result[0] = this;  System.arraycopy(args, 0, result, 1, args.length);  return result;
}
```

### callSuper —— Odoo `super()`（沿重写链）

一个方法名映射到**有序 `MethodMeta` 列表**（base app + 各扩展 app 每个声明同名方法的类一项）。`callSuper` 找到当前类位置，调**下一个**：
```java
public Object callSuper(Class<?> current, @ModelMethod String method, Object... args) {
    List<MethodMeta> methods = getModel().getMethods(method);   // 重写链
    int methodIdx = 0;
    for (MethodMeta m : methods) { if (current != null && m.getDeclaringClass() == current) break; methodIdx++; }
    MethodMeta callMethodMeta = (methods.size() == methodIdx)
        ? methods.get(methodIdx - 1)     // current 是链尾 → 无真 super，重调最后一个
        : methods.get(methodIdx + 1);    // ← 父实现
    if (callMethodMeta != null) { ModelInterceptor.INVOKE_CURRENT.set(true); return callMethodMeta.invoke(getArgs(args)); }
    throw new ModelException("模型[%s]找不到方法:%s", getModel().getName(), method);
}
```
约定：子类覆盖里写 `rs.callSuper(本类.class, "create", rs, vals)` = `super().create(vals)`。`current==null` 从链顶开始（引擎入口用）。

内嵌 `Command` 枚举 `ADD(0)…REPLACE(6)` 是 Odoo x2many 命令元组 `(0,0,{}),(1,id,{}),(2,id),(3,id),(4,id),(5),(6,0,[ids])` 的逐一翻版。

## 3. BaseContextHandler —— 线程局部上下文

`context/BaseContextHandler.java`。静态 `ThreadLocal<Map<String,Object>>`，连 `Meta` 本身也存里面（按线程名 key）：
```java
public static void set(String key, Serializable value);   // 推荐（可序列化→可深拷给异步）
public static Object get(String key);  public static void remove(String key);  public static void remove();
public static void setMeta(Object value) { set(Thread.currentThread().getName(), value); }
public static Meta  getMeta()            { return (Meta) get(Thread.currentThread().getName()); }
```
- `getSync()` 产出深拷快照（排除当前线程 Meta 项）给异步任务隔离——故非 `Serializable` 值会泄漏共享状态。
- `setInApp/getInApp` 给 key 加 `<app>_` 前缀，隔离不同 app。
- `backup()/recover()` 快照 `app/tag/model/service/dataSource/...` + `CURRENT_APP/MODEL/SERVICE`；`MethodMeta.execute` 每次调用前后用它保证嵌套调用返回后「当前模型/服务」正确还原（重入安全）。

## 4. 方法调用与重写链（`model/`）

### ModelMeta —— 方法注册表
```java
private Map<String, LinkedList<MethodMeta>> methods = new LinkedHashMap<>();
public void addMethod(MethodMeta m);       // 追加：子类在 base 之后
public void addFirstMethod(MethodMeta m);  // 前插
public MethodMeta findMethod(String name); // 链顶
public List<MethodMeta> getMethods(String m);  // 整链（callSuper 用）
public Optional<ServiceMeta> getServiceByName(String name);
```
base app A 定义 `create`、扩展 app B 也声明 `create` → 加载时 append 到同一链表。`callSuper(B.class,"create",…)` 走到 A 的项——复刻 Python MRO `super()`。

### MethodMeta —— 反射方法 + CGLIB 代理
```java
public Object invoke(Object[] args);   // RecordSet.call/callSuper 的落点
@JsonIgnore public Class<?> getDeclaringClass();
```
`invoke` 决定目标：
- 若声明类是内置数据访问类（`ModelDefineDataAccess`，或按模型类型匹配的 `BussModelDataAccess`/`TreeModelDataAccess`）→ 对工厂单例数据访问执行（`create/update/delete/search` 落库的路径）。
- 否则对用户模型类的 CGLIB 代理（`getInstance()`，callback=`ModelInterceptor.INTERCEPTOR`）执行，使缓存/`@MCache` 拦截器生效。

`execute(...)` 包裹每次调用：规整参数（注入/剥离首位 RecordSet）→ `parseArgs`（跨 app 参数类型 JSON 转换）→ 设 `meta.modelName/serviceName` → **备份上下文帧** → 开 trace Span → 反射调用 → `finally` 里 `recover` + 结束 span。

### ModelInterceptor
CGLIB `MethodInterceptor`，当前主要驱动 `@MCache` 数据缓存层（`CacheAnnotationResolver`）。`INVOKE_CURRENT` 是 `callSuper` 设的「调这一项、别重新派发」标记。注意：自动覆盖解析逻辑当前是注释掉的——重写链由 `RecordSet.callSuper` 显式遍历 `getMethods` 驱动，不是拦截器。

### 调用策略（`api/distributed/`）
```java
public interface Invocation { Object invoke(Meta meta, ModelMeta modelMeta, String methodName, Object[] args); }
// CglInvocation（本地）→ MethodMetaInvoker.invokeFirstMethod → methods.get(0).invoke（链顶）
// RpcInvocation/V2（分布式）→ 经 JSON-RPC + SideCar 转发到目标 app 的 pod
// InvFactory.getWeM(WebMode) 选择
```
→ `RecordSet.call` **位置透明**：本地或跨容器，同一 API。

## 5. JSON-RPC 派发（`api/jsonrpc/` + `api/RpcController.java`）

协议：JSON-RPC 2.0，地址 `/{tenant}/rpc/service`。
```java
public class JsonRpcRequest { RpcId id; String jsonrpc="2.0"; String method; JsonRpcParameter params; byte[] data; }
// params map: { app, tag="master", model, service, context, args }
// 请求体: { id, jsonrpc:"2.0", method:"service", params:{...} }
```
`args` 支持惰性「view」反序列化：先记录字节偏移，等知道目标方法参数类型再解析。

`RpcController.service(...)` 映射 `POST/GET /{tenant}/rpc/service/**`，跑责任链：
```
SideCarRpcHandler        // 分布式转发到拥有该 app 的 pod
→ EntranceIdRpcHandler   // 入口 id / 白名单标记
→ AppAuthRpcHandler → AuthCodeRpcHandler   // token/app 鉴权
→ BuildMetaRpcHandler    // 用 tenant+context+args 构造 Meta
→ CheckPermissionRpcHandler   // RBAC 服务级权限
→ ExecuteMetaRpcHandler  // ★ 真正执行模型服务
→ FileReturnRpcHandler   // 文件/流响应
```
`ExecuteMetaRpcHandler.doMetaExecute`：`meta.setTenantId(req.getTenant())`（租户来自 URL）→ 设 app/tag/service/model/args → `getServiceMeta`（权限校验后查 `model.getService(serviceName)`，不可达抛 `PermissionException`）→ `serviceMeta.execute(meta)` → `meta.flush()`。

`ServiceMeta.execute`：`svc.before(meta) → svc.execute(meta) → WorkflowProcess.execute → svc.after(meta)`。普通模型方法的 service 由 `importPath`("model.method") 拆出，最终汇入同一 `MethodMeta.invoke`/`RecordSet.call`。另有白名单入口 `POST /rpc/open/service` 及 `login/register/upload/download/preview`。

## 6. 数据访问 / 持久化（`data/access/`）

`ModelDataAccessFactory` 是按 `ModelTypeEnum` 注册的单例 `IDataAccess`：
```java
Buss→BussModelDataAccess（关系业务表）  Define→ModelDefineDataAccess（元模型自身）
Cache→CacheModelDataAccess  Data→DataModelDataAccess  Memory→MemoryModelDataAccess
Tree→TreeModelDataAccess  Config→ConfigModelDataAccess  View→ViewModelDataAccess  Mix→MixModelDataAccess
getBussModelDataAccess();
```

`BussModelDataAccess` —— 普通模型的持久化底座（`callSuper(null,"create",…)` 最终到这）：
```java
RecordSet find(RecordSet rs, Filter filter, Integer limit, Integer offset, String order);
long      count(RecordSet rs, Filter filter);
long      countForCrossTenant(RecordSet rs, Filter filter);     // 显式跨租户
PageResult<Map<String,Object>> searchPage(...);
List<Map<String,Object>> search(RecordSet rs, Filter filter, List<String> properties, Integer limit, Integer offset, String order);
RecordSet create(RecordSet rs, List<Map<String,Object>> valuesList);
RecordSet update(RecordSet rs, Map<String,Object> values);
RecordSet updateByFilter(RecordSet rs, Map<String,Object> values, Filter filter);
boolean   delete(RecordSet rs);  boolean deleteByFilter(RecordSet rs, Filter filter, Boolean cascade);
void      validate(RecordSet rs, Map<String,Object> values, boolean create);
RelationDBAccessor getRelationDBAccessor();  RelationDBAccessor getShardRelationDBAccessor();
```
- `create`：补默认值 → 解析数据作用域默认 → 过滤 stored/compute 属性 → `doCreate`（建 INSERT、`IdGenerator.nextId()` 分配 id、`isAutoLog()` 填审计列、`isLogicDelete()` 填软删标记、**注入 `tenant_id`**）→ `OperatorLog.logCreate` + `DataChangeInterceptor.create`。
- `search`：复制 filter（不改原）→ 解析 ref → `*` 展开为全部 stored 属性 → `flushSearch` 注入行/列/作用域/租户 → `whereCalc`/`Query` 构 SQL。

## 7. 多租户 / 数据权限隔离

数据访问层自动注入（≈ Odoo ir.rule），业务代码不用手写。关键常量：`PropertyMeta.TENANT_ID="tenant_id"`、`MetaConstant.ROOT="root"`、`CTX_TENANT_ID="tenantId"`。

租户来源：HTTP 路径 `/{tenant}/...` → `meta.setTenantId(req.getTenant())`；否则 `Meta.getTenantId()` 从 context 取，SUPERUSER 回退 ROOT，或取 `rbac_user.getTenantId()`。

**读路径 `flushSearch`**（除非 `ignoreSg`）：
```java
List<MetaPolicy> metaPolicies = AuthService.getDataAuth(rs, modelMeta.getId(), ActionType.Select.getCode());
Optional<Filter> authFilter = AuthService.getAuthFilter(rs, metaPolicies);  // 1 数据作用域 2 行权限 3 租户
if (authFilter.isPresent()) filter.and(authFilter.get());
properties = AuthService.getAuthProperties(metaPolicies, properties);        // 列级脱敏
if (modelMeta.isLogicDelete() && !ignoreLogicDelete) filter.and(Filter.notEqual("delete_flag","1"));
```
租户谓词通常被放宽为 `tenant_id IN (用户租户, "rbac_tenant_root", "root")`；在 `TENANT_ID_NULL_TABLES` 白名单表（`meta_app`、`base_dict_type`、`operator_log`、`wf_process_definition`…共享元/配置表）还 `OR tenant_id IS NULL`。

**写路径 `doCreate`**：插入时强制盖租户列（有所属租户的用户不能往别的租户写）：
```java
String tenantId = rs.getMeta().getTenantId();
if (hasTenantColumn && Utils.isBlank(stored.get(TENANT_ID))) { /* 写入 tenant_id = 当前 tenantId */ }
```

逃逸口：`ignoreSg`/`IGNORE_SCOPE` 标记、`existForIgnoreSg`、`countWithoutAuth`、`countForCrossTenant`、`findIdsForIgnoreSg(...)`、`IGNORE_LOGIC_DELETE`；`AuthService.isIgnoreScope(rs)` 集中决策。app 层常用 `ContextUtils.executeIgnoreTenant(supplier)` 包裹跨租户查询。

## 8. Filter —— 查询 DSL（`rule/Filter.java`，≈ Odoo domain）

静态工厂：
```java
Filter.equal/notEqual/equalNull/notEqualNull(field[,value])
Filter.greater/greaterOrEqual/less/lessOrEqual(field, value)
Filter.like/llike/rlike/notLike/ilike(field, value)
Filter.in/notIn(field, value)
Filter.inselect/notInSelect(field, subQuery, value)   // 子查询
Filter.where(field, op, value)   Filter.compareField(field, op, field2)
Filter.AND(Filter...)  Filter.OR(Filter...)           // 静态组合
filter.and(Filter...)  filter.or(Filter...)           // 实例链式
Filter.parse(List<Object>)  Filter.parse(String json) // 反序列化（前端 domain 数组）
filter.copy()  filter.remove(name)
```
用法示例：
```java
new User().search(
    Filter.AND(Filter.in("role_ids", roleIds), Filter.equal("tenant_id", tenantId)),
    Collections.singletonList("*"), Integer.MAX_VALUE, 0, null);
```
