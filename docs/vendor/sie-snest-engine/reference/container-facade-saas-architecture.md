---
name: container-facade-saas-architecture
description: SaaS 化计算整体架构文档,涵盖多租户、单版本(SingleTag)、多版本(MultiTag)三种模式的差异对比、加载体系、应用市场状态机、构建流程。接手 SaaS 化元模型改造、跨 Service 调度、多租户容器管理时必读。
---

# SaaS 化计算:多租户 · 单版本 · 多版本 完整架构

> **配套阅读**:本文件提供 SaaS 化计算的"全景视角",具体实现细节见:
> - `container-facade-tenant-meta.md` — 三个核心计算方法的步骤详解
> - `metabuilders-sandbox-internal.md` — `computeMeta` 的底层沙箱机制
> - `router-v2-distributed.md` — RouterV2/InstallerV2 跨 Service 路由
> - `summary-manager-multinode.md` — SummaryManager 多节点同步

---

## 1. 三个模式的核心区分

| 特性 | **非 SaaS 单版本** | **SaaS 单版本** | **SaaS 多版本 (multiTag)** |
|---|---|---|---|
| 开关 | `supportMultiTag()=false` | `supportMultiTag()=false` | `supportMultiTag()=true` |
| 引擎启动器 | `DistributedAppLoader` / `SingleAppLoader` | 同左 | `SaaSDistributedAppLoader` |
| 租户维度容器 | ❌ 无 | ❌ 无 | ✅ `EngineContainer.tenantsMetaGroupContainers` |
| 多版本 App 管理 | ❌ 无 | ❌ 无 | ✅ App 市场 + `SaaSAppInfo` 状态机 |
| 核心构建入口 | `AppLifecycleProcess.buildMeta` | 同左 | `ContainerFacade.computeMetaWithTenant` / `buildMetaWithTenant` |
| 数据隔离 | 共享同一套终态 | 共享同一套终态 | 每个租户独立终态(Redis 分 key 存储) |

> **关键认知**:`supportMultiTag()` 是 SaaS 多租户/多版本的唯一开关配置,值来自 `engine.feature.multiTag.enabled`。

### 1.1 关键方法

```java
// AppLifecycleFacade.java:847
public static boolean supportMultiTag() {
    return ConfigUtils.getBoolean("engine.feature.multiTag.enabled", false);
}

public static boolean isSaaSMode() {
    return ConfigUtils.getBoolean("engine.feature.saas.enabled", false);
}

public static boolean supportMetaGroup() {
    return ConfigUtils.getBoolean("engine.feature.metaGroup.enabled", false);
}
```

---

## 2. 两大 App 加载体系

```
┌─────────────────────────────────────────────────────────────┐
│                    Engine 启动阶段                           │
│                                                             │
│  LoaderFactory.getAppLoader(WebMode.of(runMode))           │
│       │                                                     │
│       ├── WebMode.SINGLE  → SingleAppLoader                 │
│       │                    ├── 非 SaaS:走传统 buildMeta      │
│       │                    └── SaaS 多版本:走 setUpTenantApps│
│       │                         └── buildMetaWithTenant       │
│       │                                                     │
│       └── WebMode.DISTRIBUTED → SaaSDistributedAppLoader    │
│                              └── SaaS 多版本                  │
│                                   ├── 必装 App:super.setUp() │
│                                   └── 租户 App:              │
│                                        computeMetaWithTenant  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              App 市场运行时(安装/更新/卸载)              │ │
│  │                                                       │ │
│  │  AppStoreServiceImpl.buildMetaWithTenant()            │ │
│  │      ├── 安装 → SaaSAppStatus.ADD                     │ │
│  │      ├── 更新 → SaaSAppStatus.UPDATE                  │ │
│  │      ├── 卸载 → SaaSAppStatus.REMOVED                 │ │
│  │      └── 不变 → SaaSAppStatus.UNCHANGED              │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 三个构建方法对比

| 维度 | `computeMetaWithTenant` (L2933) | `buildMetaWithTenant` (L3051) | `buildMetaWithGroup` (L3277) |
|---|---|---|---|
| 入参 | `Map<租户ID, List<SaaSAppInfo>>` + appTagJsonMap | 同左 | `virtualTenant`(String) + 单 `List<SaaSAppInfo>` |
| 返回 | `Map<租户ID, TenantMetaGroupContainer>` | 同左 | 单条目 Map |
| 持久化+发布 | ❌ 只算不落库 | ✅ 加锁落库 + 发事件 + 摘要 |
| 模型重复名校验 | ❌ 无 | ✅ 有(`repeatModelCheck` 落库前拦截) | ❌ 无 |
| 分组缓存复用 | ✅ `getAppGroupContainerFromBuildOrCache` | ✅ 同左 | ❌ 直接 computeMeta 每组 |
| m2m 收尾通知 | ❌ 无 | ❌ 无 | ✅ `new MetaM2MComputeUtil().accept()`(独有) |
| workflow 容器创建 | `name.contains("workflow")`(子串) | 精确匹配 `workflow/workflow-extend/rule` | ❌ 完全无 |
| 典型调用方 | 启动时预览(SaaSDistributedAppLoader.tenantAppSetUp) | AppStoreServiceImpl 安装/更新/卸载 | 仅 ContainerFacadeManualTest 覆盖 |

---

## 4. App 市场与 SaaS 状态机

### 4.1 SaaSAppStatus 四态流转

```java
// ContainerFacade.java:2857
public enum SaaSAppStatus {
    ADD, UPDATE, REMOVED, UNCHANGED;
}
```

```
┌──────────┐    上架/安装     ┌──────────┐
│  UNKNOWN │ ──────────────→ │   ADD    │
└──────────┘                 └────┬─────┘
       ↑                           │
       │                           │ 版本更新
       │    ┌──────────────────────┘
       │    ↓
  ┌────┴─────┐
  │  UPDATE  │ ←─── 同 App+Tag,JSON 内容变化
  └────┬─────┘
       │
       │                           App 市场卸载操作
       └──────────────┐
                      ↓
              ┌──────────────┐
              │   REMOVED   │ ←─── 从租户授权组中移除
              └──────────────┘

  ┌──────────┐
  │ UNCHANGED │ ←─── App 存在且内容无变化,走 Redis 缓存
  └──────────┘
```

### 4.2 buildUpdatedAppTags 合并逻辑 (L3643)

```java
public static Map<String,String> buildUpdatedAppTags(Map<String,String> appTagJsonMap,
                                                      Map<String,String> originalMetasMap,
                                                      List<SaaSAppInfo> appInfo) {
    Map<String,String> updatedAppTags = new HashMap<>();
    for (SaaSAppInfo info : appInfo) {
        String appTag = info.app + "." + info.tag;
        switch (info.saaSAppStatus) {
            case ADD:       updatedAppTags.put(appTag, appTagJsonMap.get(appTag)); break;
            case UPDATE:    updatedAppTags.put(appTag, appTagJsonMap.get(appTag)); break;
            case REMOVED:   updatedAppTags.remove(appTag); break;
            case UNCHANGED: updatedAppTags.put(appTag, originalMetasMap.get(appTag)); break;
        }
    }
    return updatedAppTags;
}
```

---

## 5. 启动阶段:单版本 vs 多版本对比

| 阶段 | 单版本(SingleAppLoader / DistributedAppLoader) | 多版本(SaaSDistributedAppLoader) |
|---|---|---|
| 开关判断 | `supportMultiTag()=false` → 走父类 `setUp()` | `supportMultiTag()=true` → 走 SaaS 路径 |
| 必装 App | 全量加载,不区分租户 | `Route.getInstalledAppsByServiceName(serviceName)` 只加载必装 App |
| 租户 App | ❌ 无租户维度 | `RouterV2.getTenantApps(serviceName)` → 按租户分组 |
| 容器计算 | `buildMeta`(单次全量) | `computeMetaWithTenant`(按租户批量计算) |
| 容器存储 | `AppGroupContainer`(单例) | `TenantMetaGroupContainer` × N(每个租户独立) |
| 内存索引 | `EngineContainer.appGroupContainer` | `EngineContainer.tenantsMetaGroupContainers` (ConcurrentHashMap) |

### 5.1 SaaSDistributedAppLoader 启动骨架 (L53-87)

```java
public void setUp() {
    if (!AppLifecycleFacade.supportMultiTag()) {
        super.setUp();  // 单版本回退
        return;
    }
    super.commonInit();
    String serviceName = Route.currentServiceName();
    try (Meta meta = new Meta(Meta.SUPERUSER, null, context,
                              ModelDataAccessFactory.getDataAccess(ModelTypeEnum.Buss))) {
        BaseContextHandler.setMeta(meta);
        ...
        if (isInit) {
            requiredAppSetUp(metaAppMap, serviceName, meta);  // 必装 App
            tenantAppSetUp(metaAppMap, serviceName);          // 租户 App
        }
    }
}
```

---

## 6. Redis 存储 key 差异

| 单版本 key | 多版本 key(带 tenantHash) |
|---|---|
| `BussModel_{modelName}` | `SaaSBussGroupsModel_{tenantHash}_{modelName}` |
| `BussMenu` | `BussMenu`(全局共享) |
| `BussProduct` | `BussProduct`(全局共享) |
| `BussAppContainer_{appTag}` | 同左(App 维度) |
| N/A | `SaaSBussGroupsSummary_{hash}`(多租户组摘要) |
| N/A | `SaaSBussGroupSummary_{hash}`(单组摘要) |

---

## 7. 已知风险清单

> ⚠️ 接手 SaaS 多版本元模型计算时必看。

1. **【高风险】**`buildMetaWithGroup` 中 `Optional.get()` 缺少 `continue`(`ContainerFacade.java:L3322`)
2. **【中风险】**三个方法对"是否含 workflow 相关 App"的判断口径不一致(子串 vs 精确 vs 无)
3. **【需确认】**`buildMetaWithTenant` 循环中途抛异常的"部分成功"语义
4. **【低中风险】**`computeMetaWithTenant` 不做 `repeatModelCheck`,依赖调用方
5. **【设计问题】**`buildMetaWithGroup` 独有的 `MetaM2MComputeUtil.accept()` 收尾调用,与另两个方法行为不一致

---

## 8. 关键代码位置速查

| 符号 | 文件:行号 |
|---|---|
| `computeMetaWithTenant` | `ContainerFacade.java:2933` |
| `buildMetaWithTenant` | `ContainerFacade.java:3051` |
| `buildMetaWithGroup` | `ContainerFacade.java:3277` |
| `SaaSAppStatus` 枚举 | `ContainerFacade.java:2857` |
| `SaaSAppInfo` 类 | `ContainerFacade.java:2861` |
| `buildUpdatedAppTags` | `ContainerFacade.java:3643` |
| `hasWorkflowApp` | `ContainerFacade.java:3184` |
| `repeatModelCheck` | `ContainerFacade.java:3198` |
| `lockedMetaStorage` | `ContainerFacade.java:3386` |
| `supportMultiTag` | `AppLifecycleFacade.java:847` |
| `isSaaSMode` | `AppLifecycleFacade.java:851` |
| `supportMetaGroup` | `AppLifecycleFacade.java:859` |
| `SaaSDistributedAppLoader.setUp` | `SaaSDistributedAppLoader.java:53` |
| `SaaSDistributedAppLoader.requiredAppSetUp` | `SaaSDistributedAppLoader.java:135` |
| `SaaSDistributedAppLoader.tenantAppSetUp` | `SaaSDistributedAppLoader.java:166` |
| `SingleAppLoader.buildTenant` | `SingleAppLoader.java:637` |
| `tenantsMetaGroupContainers` | `EngineContainer.java:75` |
| `saasGroupContainers` | `EngineContainer.java:74` |

---

## 9. 设计哲学总结

1. **`supportMultiTag` 是单一开关** — 整个架构围绕"是否开启多版本/多租户"展开
2. **按需加载** — Pod 启动时只加载当前 Service 必装 App + 当前 Service 授权的租户 App,避免内存膨胀
3. **每个租户独立终态** — Redis 按 `tenantHash` 分桶,内存用 `ConcurrentHashMap<tenantId, container>`
4. **SaaSAppStatus 四态机** — 安装/更新/卸载/不变 对应不同计算路径
5. **多节点同步靠 SummaryManager** — 详见 `summary-manager-multinode.md`