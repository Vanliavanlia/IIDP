---
name: sie-snest-engine
description: Use when working in the sie-snest low-code engine or any app built on it (sie-tenant, sie-iidp-iam, sie-snest-apps/*) — understanding or writing Models, fields, services and method-override chains. It is a Java/Odoo-style metadata-driven ORM: a class + @Model = a table-backed model, fields are @Property annotations, model methods ARE the services (no Spring Controller/Service), all data access goes through a thread-bound Meta env and a RecordSet, and apps override base methods via callSuper. Reach for this skill when you see BaseModel, @Model, RecordSet, getMeta().get(...), call/callSuper, @MethodService, or JSON-RPC /{tenant}/rpc/service.
---

# sie-snest 引擎设计理念

sie-snest 是一套自研低代码引擎，**设计理念直接对标 Odoo 框架**，用 Java 实现。核心思想：**模型即一切**——一个 `@Model` 注解的类同时是「实体 + DTO + 服务 + ORM 句柄」，没有 Spring 的 `@RestController`/`@Service`/`@Repository` 分层。

> 模块定位：`sie-snest-sdk` = 对外的模型定义 API（写业务用这个）；`sie-snest-engine` = 运行时（容器、元数据、数据访问、JSON-RPC、多租户）；`sie-snest-server` = Spring Boot 启动器。业务 app 放在 `sie-snest-apps/*`（如 `sie-iidp-tenant`），租户/权限等仓库（`sie-tenant`、`sie-iidp-iam`）也是 app。

## 一句话心智模型

```
HTTP  POST /{tenant}/rpc/service   (JSON-RPC 2.0)
  → 责任链 (鉴权 → BuildMeta → 权限 → ExecuteMeta)
  → meta.get("model_name")  ……拿到 RecordSet（≈ Odoo env["model.name"]）
  → recordSet.call("方法名", args)        ……≈ self.method(args)
      → 走「方法重写链」：app 覆盖方法可 callSuper 调引擎基础实现（≈ super().create()）
      → 底层 BussModelDataAccess 落库，自动注入 tenant_id / 数据权限过滤
```

## Odoo 对照表（最重要的速查）

| Odoo | sie-snest | 备注 |
|---|---|---|
| `class X(models.Model): _name='x'` | `class X extends BaseModel<X>` + `@Model(name="x")` | |
| 记录值存在 ORM cache 里，非 Python 属性 | `BaseModel extends LinkedHashMap`，值存在 map 里 | 声明的 Java 字段是 `@Ignore` 摆设，**用 `set()/getStr()` 等访问，不是直接读字段** |
| `fields.Char/Integer/...` | `@Property` 注解字段 | |
| `_inherit` | `@Model(parent={...})` | String[]，支持多继承 |
| `_order` / `_table` / `_description` | `@Model.orderBy / tableName / description` | |
| `_rec_name` / `display_name` | `@Property(displayForModel=true)` | many2one 显示用的「名称」字段 |
| `Many2one` | `@ManyToOne(targetModel,targetProperty)` | 默认 `fetch=EAGER` |
| `One2many(comodel, inverse)` | `@OneToMany(targetModel,targetProperty)` | 默认 LAZY |
| `Many2many` | `@ManyToMany` + `@JoinTable`/`@JoinColumn` | 关联表要显式声明（JPA 风格） |
| `ondelete='set null'/'cascade'/'restrict'` | `CascadeType.DEL_SET_NULL / DELETE / DEL_NO_PERMIT_RELATIVE` | |
| `self.env` | `getMeta()` / `BaseContextHandler.getMeta()` | 线程绑定的执行环境 |
| `env["model"]` | `getMeta().get("model")` → `RecordSet` | |
| `self.method(args)` | `recordSet.call("method", args)` | |
| `super().create(vals)` | `rs.callSuper(本类.class, "create", rs, vals)` | 走方法重写链 |
| `browse(id)` / `search()` / `create/write/unlink` | `selectById` / `search` / `create/update/delete` | |
| `SUPERUSER_ID` / `sudo()` | `Meta.SUPERUSER`("rbac_user_superuser") / `Meta.executeAsSuperUser` | `Meta` 构造器有 `"superuser"`→`"rbac_user_superuser"` 兼容映射；绕过边界见认知 #9 |
| `ir.rule`（记录规则） | `flushSearch` 自动注入数据权限 + `tenant_id` 过滤、`@Rules.*` | |
| x2many 命令元组 `(0,0,{})…(6,0,[ids])` | `[4, id, {}]` 形式三元组 / `RecordSet.Command` 枚举 | 创建用户绑角色就是这个 |
| `@api.constrains` / SQL 约束 | `@Validate.*` 注解 | |
| JSON-RPC `/web/dataset/call_kw` | `POST /{tenant}/rpc/service` | |
| `TransientModel` / dict 入参出参 | `SdkModel` / `RequestModel` / `ResponseModel`（`ModelType.Mix`） | |

## 写业务的最小套路

定义一个业务模型（落表 + 自动暴露为服务）：

```java
@Model(name = "rbac_tenant", displayName = "租户", displayFormat = "{name}",
       type = Model.ModelType.Buss, isAutoLog = Bool.True)   // Buss=普通持久化模型；isAutoLog 自动加 create/update_user/date
public class Tenant extends BaseModel<Tenant> {

    @Validate.NotBlank
    @Property(displayName = "名称", length = 200, displayForModel = true, unique = true)
    private String name;                 // 字段值实际存在 map 里，字段声明只是元数据来源

    @ManyToOne(targetModel = "rbac_tenant", targetProperty = "id")
    private String parent_id;            // 多对一

    // 覆盖引擎基础 create —— 方法名 create 即对外服务名（无需 @RestController）
    public String[] create(@BaseService.Spec(doc = "k v") List<Tenant> valuesList) {
        // ...前置业务校验...
        Meta.executeAsSuperUser-ish 临时提权：BaseContextHandler.getMeta().setUserId(Meta.SUPERUSER);
        RecordSet rs = (RecordSet) this.getMeta().get("rbac_tenant")
                                        .callSuper(null, "create", valuesList);  // ≈ super().create()
        // ...后置：用 getMeta().get("其它模型").call("create", ...) 跨模型联动...
        return rs.getIds();
    }
}
```

跨模型协作（Odoo 的 `self.env['other'].create(...)`）：

```java
this.getMeta().get("rbac_organization").call("create", orgList);
this.getMeta().get("rbac_role").callSuper(null, "create", Arrays.asList(role));
```

## 关键认知（容易踩坑 / 容易问错）

1. **没有 Controller/Service 分层**。找接口入口 → 找 `@Model` 类里方法名 = 服务名的方法。grep `@RestController` 找不到东西是正常的。
2. **字段值在 map 里，不在 Java 字段**。`BaseModel extends LinkedHashMap`。用 `set("k",v)` / `getStr("k")` / `put(...)`。`get("关系字段")` 会触发级联查询（解析 many2one/one2many）；要原始值用 `load(k)`。
3. **`call`/`callSuper` 属于 `RecordSet`，不是 `BaseModel`**。模型里通过 `getMeta().get(模型名)` 拿 RecordSet 再 `call`。`callSuper(本类.class, "m", ...)` 才是真正的 super；`callSuper(null, ...)` 从链顶开始（引擎入口用）。
4. **`getMeta()` 永远返回线程绑定的 Meta**（`BaseContextHandler` 里按线程名存的），忽略实例字段。这是 Odoo `self.env`。`getRecordSet()` 已废弃。
5. **多租户是数据访问层自动注入的**，业务代码不用手写 `tenant_id` 过滤。读路径 `flushSearch` 注入行/列/作用域 + 租户过滤；写路径 `doCreate` 强制盖上当前 `tenant_id`。要跨租户用 `executeIgnoreTenant` / `*ForIgnoreSg` / `countForCrossTenant` 等显式逃逸口。
6. **方法名即重写链**。base app 和扩展 app 声明同名方法 → 形成 `ModelMeta.getMethods(name)` 链表，`callSuper` 沿链向下走。这是 Odoo 的 `_inherit` + `super()`。运行时可用 arthas ognl 直接 dump 这条链：`getModel("模型名").getMethods("方法名")`（表头=链顶 app/`callSuper(null)` 入口，表尾=引擎实现方向），命令见 [`arthas-debug`](../arthas-debug/SKILL.md) 场景三。
7. **位置透明调用**。`RecordSet.call` 在本地模式（CglInvocation）直接反射调用，分布式模式（RpcInvocation）通过 JSON-RPC 转发到目标 app 的 pod。同一套 API。
8. **临时提权常见模式**：跨租户建组织/角色/用户前，先 `setUserId(Meta.SUPERUSER)` + `setTenantId("...root")`（参考 `Tenant.create`），或用 `Meta.executeAsSuperUser(runnable)`。
9. **superuser 绕过一切数据权限，且在授权源头短路**。`AuthService.getDataAuth` 一见 `Meta.SUPERUSER` 就返回空策略 → `flushSearch` 里 SG作用域 / 行级过滤 / **租户过滤** / **列级权限** 四者全部随之失效（租户过滤本身就打包在这批策略的 `authFilter` 里，不是独立机制）；`AbstractDataAccess.getActionScopeFilter` 是第二道短路。仅**逻辑删除**（`delete_flag`）和**分表定位**不绕。安全含义：superuser 是能跨租户看全量、看全字段的逃生舱，生产上必须保护、初始密码必须改。
10. **种子数据（seed）由应用市场安装/更新/卸载时加载，不是引擎启动时**。`data/*.json` 顶层 key = 记录 external id，`@ref`/`@eval [4,@ref(x),0]` 建关联。要回答「某表应有哪些初始记录」先看对应 app 的 seed。详见 `reference/seed-data.md`。
11. **每个 app 一个 ClassLoader，类隔离到 JDK**。app fat-jar（Spring Boot 布局）由 `MetaAppClassLoader` 装载，parent 是 Ext loader 不是引擎；找不到类才兜底到引擎（仅白名单包：`com.sie.snest.*`(除 sdk)、slf4j/cglib/javax/fastjson/jackson 等；代码里还有公共库 `apps/libs` 兜底但**实际未启用**，唯一有效兜底就是引擎白名单）。含义：跨 app 同名类是不同 Class（强转必 CCE，所以跨 app 只能 `call("服务名")`）；**SDK 类不共享、随 app 自带**（跨 loader 传值靠 `BaseModel extends LinkedHashMap` 的 Map 接口——"值存 map"设计的另一层原因）；arthas 需 `--classLoaderClass` 选中目标 app 的 loader。详见 `reference/classloading.md`。
12. **两种 "search"，别混**：app 代码里 `new Xxx().search(...)` / `this.search(...)` 是 **SDK 本地辅助方法**（`BaseModel.search` → `DbUtils`），不走重写链；只有 `rs.call("search",...)` / `rs.callSuper(X.class,"search",...)` 才沿链落到引擎 `BussModelDataAccess.search`。分析「某模型的 search 服务」时先分清调用点是哪种。同理：**app 常不重写基础 CRUD，而是包装成新服务名**（如 rbac_role 的 `queryRoleTree`/`searchTenantRole` 包装 search + 可见性收敛 + 临时提权），grep 不到重写方法≠没有业务逻辑，找同前缀/同语义的 `@MethodService`。

## 手写 SQL 排查数据库时的铁律（实战经验）

当需要给用户生成排查/清理数据的 SQL 时，**绝不能凭习惯猜列名**，必须：

1. **先确认目标环境是哪种数据库**。
2. **先读 `@Model` 类核对真实字段，再写 SQL**。模型字段名（Java camelCase）→ 物理列名的映射以 `@Property`/`@JoinColumn` 声明为准。
3. **不要默认有 `delete_flag` 列**。逻辑删除列只有启用逻辑删除的表才有。

## 深入参考（按需读）

- `reference/sdk-model.md` — 模型定义层完整 API：`@Model`/`@Property`/关系注解/`@Validate`/`@Rules` 全部属性，`BaseModel` 全部方法（CRUD、字段访问、事务、异步、SQL）。
- `reference/engine-runtime.md` — 运行时：`Meta`(env)、`RecordSet`、`call`/`callSuper` 重写链机制、`BaseContextHandler`、JSON-RPC 责任链路由、`BussModelDataAccess` 持久化、多租户/数据权限注入、`Filter` 查询 DSL。
- `reference/seed-data.md` — 种子数据机制：`SeedLoader` + 应用市场 install/update/reset 三态语义、`meta_model_data` 登记表幂等、`@ref`/`@eval`/`@fileId` 语法、x2many 命令三元组落中间表。回答「某表应有哪些初始记录」看这里。
- `reference/classloading.md` — 类加载体系：每 app 一个 `MetaAppClassLoader`（Spring Boot loader 魔改）、引擎 API 导出白名单（唯一有效兜底；`apps/libs` 公共库机制存在但未启用）、SDK 不共享的设计含义、卸载/热更新、NoClassDefFoundError 与 arthas `--classLoaderClass` 排查。
- `reference/data-scope.md` — 数据权限/作用域（SG）体系（iam 侧）：作用域-角色-规则-维度-用户实例四方关系、GET_DATA_AUTH 委托、`${meta.user.CURSG_INSTANCES}` 占位符代入、空实例=放行语义、`#ALL_MODEL`、规则双来源（DB 优先）、「配了作用域为什么不生效」排查清单。

## SaaS 多租户/多版本架构（深入参考，按需读）

- `reference/container-facade-saas-architecture.md` — SaaS 化计算整体架构,涵盖多租户/单版本(SingleTag)/多版本(MultiTag)三种模式对比、App 市场 `SaaSAppStatus` 四态机(`ADD/UPDATE/REMOVED/UNCHANGED`)、单版本与多版本启动阶段差异、Redis 存储 key 差异、五大已知风险(`buildMetaWithGroup` `Optional.get()` bug、`workflow` 判断口径不一致、部分成功语义等)。接手 SaaS 化元模型改造、跨 Service 调度、多租户容器管理时必读。
- `reference/metabuilders-sandbox-internal.md` — `MetaBuilderSandbox.buildMeta()` 内部原理:三阶段构建流程(预取临时 → 二次构建处理继承 → 终态构建)、ThreadLocal 沙箱容器生命周期与 finally 清空机制、`sandboxBuildBussAppGroupContainer` 的两阶段处理、`BuildFrom` 三桶分类白名单(`SDK` / `BUILD_FROM_WORKFLOW` / `BUILD_FROM_RULE_ORCHESTRATION`)、业务容器与租户容器边界。分析 `computeMeta` 路径的元数据计算、依赖图计算、App 类型分类时必读。
- `reference/router-v2-distributed.md` — RouterV2 / InstallerV2 抽象层与 SaaS 多版本分布式路由机制:SPI 加载机制(`ServiceLoader.load`)、K8s Service → Pod → 租户三层数据模型(`listServiceMap` / `getTenantApps` / `getServices` 三元组)、`Route.inLocal` 路由决策(单版本按 `appNameTag` vs SaaS 按 `(tenantId, appNameTag)`)、安装阶段 `TenantGroup` 嵌套结构、按"组"而非按"App"调度的设计哲学。接手跨 Service 调度、应用市场安装/卸载、K8s 路由配置时必读。
- `reference/summary-manager-multinode.md` — `SummaryManager` 多节点同步与 Redis 摘要机制:五种摘要类型(`MODEL_SUMMARY_TYPE` / `APP_SUMMARY_TYPE` / `TENANT_MODEL_SUMMARY_TYPE` / `TENANT_APP_SUMMARY_TYPE` / `TENANT_API_WF_SUMMARY_TYPE`)、`publishEngineEvent` 集群总线广播、`RedissonCache.lock` 锁保护下的读写、`tenantHash` SHA-256 分桶存储、与 `buildMetaWithTenant` 的协作时序图。接手 SaaS 多节点一致性、跨 Pod 摘要同步、`tenantHash` 哈希比对时必读。

## ContainerFacade 元模型构建（深入参考，按需读）

- `reference/container-facade-tenant-meta.md` — 多租户维度元模型计算核心三方法（`computeMetaWithTenant` / `buildMetaWithTenant` / `buildMetaWithGroup`）的执行步骤、差异表、调用方场景与风险清单。接手 SaaS 元模型计算路径必读。
- `reference/container-facade-partial-build-overview.md` — `buildMetaPartial(List, String)` L967 分发入口及 SDK/Workflow/Orchestration 三条分支的详细业务逻辑、SaaS 白名单限制、九件套落库差异。
- `reference/container-facade-partial-build-single-meta.md` — `buildMetaPartial(String, String)` L1982 单条元数据增删改的五个 type 分支（ProductLine/Menu/UiView/CustomAuth/FunctionAuth），以及与 `buildMetaRemoved` 的关系、视图 type 常量不一致等风险。
- `reference/container-facade-m2m-view-menu-productline.md` — 三方法共享的"组间计算收口"四步：m2m 组间补齐 → 产品线合并 → 菜单继承/兼容/成环 → 视图构建；以及 `buildMetaWithGroup` 独有的跨虚拟租户 m2m 补齐。
- `reference/meta-model-view-productline-menu-auth.md` — 元模型四件套（ProductLineMeta / MenuMeta / UiViewMeta / ViewMeta / ResourceMeta）的字段结构、存储方式、id 规则、转换器；辨析最易混淆的 UiViewMeta（页面视图，初态）vs ViewMeta（功能权限，终态）。
- `reference/partial-build-redis-batch-update.md` — "写临时 key + Lua 原子生效"机制：`EMPTY_STR_TO_DELETE` 约定、`LUA_SCRIPT_RENAME` 合并语义、diffMeta 九件套、metaPartialStorage 落库时序与并发覆盖风险。

## 本仓库导航

- 引擎运行时：`sie-snest/sie-snest-engine/src/main/java/com/sie/snest/engine/`
- SDK（写业务用）：`sie-snest-sdk/src/main/java/com/sie/snest/sdk/`
- 启动器：`sie-snest/sie-snest-server/.../service/Server.java`
- 业务 app 范例：`sie-tenant/sie-snest-apps/sie-iidp-tenant/.../model/Tenant.java`（创建租户全流程）
- 构建/测试约定见 `sie-snest/AGENTS.md`（注意多模块默认 `skipTests=true`，需 `-DskipTests=false`）。
