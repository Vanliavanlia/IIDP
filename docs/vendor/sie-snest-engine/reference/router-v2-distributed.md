---
name: router-v2-distributed
description: RouterV2 / InstallerV2 抽象层与 SaaS 多版本分布式路由机制,涵盖 K8s Service→Pod→租户三层数据模型、SPI 加载机制、Route.inLocal 路由决策、安装阶段 TenantGroup 嵌套结构。接手跨 Service 调度、应用市场安装卸载、K8s 路由配置时必读。
---

# RouterV2 / InstallerV2 — SaaS 多版本分布式路由与安装

> **配套阅读**:`container-facade-saas-architecture.md`(上层 SaaS 架构)、`summary-manager-multinode.md`(多节点同步)
>
> **核心问题**:K8s 集群下,App 跨租户授权 → Service 路由 → Pod 调度的链路是如何运转的?

---

## 1. 抽象层:SPI 接口

### 1.1 RouterV2 接口(路由层)

> **文件**:`spi/distributed/RouterV2.java`

```java
public interface RouterV2 {
    /** 列出所有 service 及其元数据 */
    List<Map<String, Object>> listServiceMap();

    /** 给定 service 名,获取该 service 上所有租户的 app 列表 */
    Map<String, List<String>> getTenantApps(String... serviceName);

    /** 给定租户 + app,返回该 app 运行在哪些 service 上 */
    List<String> getServices(String tenantId, String appNameTag);
}
```

### 1.2 InstallerV2 接口(安装层)

> **文件**:`spi/distributed/InstallerV2.java`

```java
public interface InstallerV2 {
    class GroupApp {
        public String groupID;
        public List<String> appNameList;
    }
    class TenantGroup {
        public String tenantID;
        public List<GroupApp> groupAppList;
    }

    boolean installApps(String serviceName, String kind, int replicas, String image,
                        List<TenantGroup> tgaList);

    void uninstallApps(List<TenantGroup> tgaList);

    boolean updateApps(List<TenantGroup> tgaList);

    void restart(String ... serviceList);

    void uninstallApps(String serviceName, List<TenantGroup> tgaList);

    // metadata
    void updateMetadata(String serviceName, Map<String, String> metadata);
    void deleteMetadata(String serviceName, List<String> keyList);
    Map<String, String> getMetadata(String serviceName);
}
```

### 1.3 SPI 加载机制

```java
// Route.java L27-39
static {
    if (WebMode.DISTRIBUTED_V2.equals(curRunMode)) {
        route = loadSpi(Router.class);
        routerV2 = loadSpi(RouterV2.class);
    }
    if (route == null || routerV2 == null) {
        throw new EngineException("No Router found !!! Please check your Router implementation.");
    }
}

static <T> T loadSpi(Class<T> clazz) {
    ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz, clazz.getClassLoader());
    Iterator<T> iterator = serviceLoader.iterator();
    if (iterator.hasNext()) {
        return iterator.next();
    } else {
        throw new EngineException("No implementation found for " + clazz.getName());
    }
}
```

| 要点 | 说明 |
|---|---|
| **SPI 解耦** | 引擎只依赖接口;具体实现由 `sie-iidp-app-store-app` jar 通过 `META-INF/services/` 注册 |
| **WebMode.DISTRIBUTED_V2 才加载** | 单机/单进程模式不会触发 RouterV2 初始化 |
| **fail-fast** | 找不到实现直接抛异常 — 生产环境必须依赖 `sie-iidp-app-store-app` |

> ⚠️ **本仓库未包含 RouterV2/InstallerV2 的具体实现**(实现在 `sie-iidp-app-store-app` jar 中);引擎侧只消费接口。本文档基于接口契约 + 调用方代码反推其语义。

---

## 2. 租户 → Service → App 的数据模型

### 2.1 三层映射关系

```
┌──────────────────────────────────────────────────────────────┐
│                    K8s 集群中的 Service                         │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│   │ snest-v1     │  │ snest-v2     │  │ snest-v3     │  ...    │
│   │ (Service A)  │  │ (Service B)  │  │ (Service C)  │         │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│          │                 │                 │                  │
└──────────┼─────────────────┼─────────────────┼──────────────────┘
           │                 │                 │
   ┌───────┴───────┐ ┌───────┴───────┐ ┌───────┴───────┐
   │  Pod-1 副本1   │ │  Pod-2 副本1   │ │  Pod-3 副本1   │
   │  ┌─────────┐  │ │  ┌─────────┐  │ │  ┌─────────┐  │
   │  │ Tenant A│  │ │  │ Tenant A│  │ │  │ Tenant B│  │
   │  │ Tenant B│  │ │  │ Tenant C│  │ │  │ Tenant D│  │
   │  └─────────┘  │ │  └─────────┘  │ │  └─────────┘  │
   └───────────────┘ └───────────────┘ └───────────────┘

RouterV2.getTenantApps(serviceName) →
   "Tenant A" → ["sie-iidp-iam.v3.0.4", "sie-iidp-tenant.v3.0.4"]
   "Tenant B" → ["sie-iidp-iam.v3.0.4", "sie-iidp-cache.v3.0.0"]
   "Tenant C" → ["sie-iidp-iam.v3.0.4", "sie-iidp-tenant.v3.0.4"]
   "Tenant D" → ["sie-iidp-iam.v3.0.4"]

RouterV2.getServices(tenantId, appNameTag) →
   ("Tenant A", "sie-iidp-iam.v3.0.4") → ["snest-v1", "snest-v2"]
   ("Tenant A", "sie-iidp-tenant.v3.0.4") → ["snest-v1"]
```

### 2.2 Service 间差异主要在「授权的租户组合」不同

| Service | 部署的 Pod 镜像 | 授权的 App 列表 | 授权的租户 | 用途 |
|---|---|---|---|---|
| `snest-v1` | 同一份 snest-engine 镜像 | 全量必装 + 各租户授权 App | 多租户 | 标准 Service |
| `snest-v2` | 同上 | 同上 | 多租户 | 灰度发布时并存 |
| `snest-v3` | 同上 | 同上 | 多租户 | 全量发布后灰度回收 |

> **关键洞察**：Service 间**镜像相同**，差异仅在「通过 metadata/ConfigMap 注入的 tenant→app 映射表」。这就是为什么 `installerV2.installApps(serviceName, kind, replicas, image, tgaList)` 的核心操作是「写 ConfigMap」而非「打镜像」。

---

## 3. 启动阶段:SaaSDistributedAppLoader 如何消费 RouterV2

### 3.1 容器启动时的两阶段加载

```java
// SaaSDistributedAppLoader.java L53-87
public void setUp() {
    if (!AppLifecycleFacade.supportMultiTag()) {
        //单版本启动
        super.setUp();
        return;
    }
    super.commonInit();
    String serviceName = Route.currentServiceName();
    ...
    if (isInit) {
        // 必装 APP 走原启动逻辑
        requiredAppSetUp(metaAppMap, serviceName, meta);
        // 租户分组 APP 走 SAAS 启动逻辑
        tenantAppSetUp(metaAppMap, serviceName);
    }
}
```

### 3.2 必装 App 加载:按 Service 维度

```java
// SaaSDistributedAppLoader.java L135-150
private void requiredAppSetUp(Map<String, AppDataInfo> metaAppMap,
                              String serviceName, Meta meta) {
    // 获取属于当前 service 的"必装" App 列表
    List<String> appNameList = Route.getInstalledAppsByServiceName(serviceName);
    List<AppDataInfo> requiredAppList = appNameList.stream()
            .map(metaAppMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    if (!requiredAppList.isEmpty()) {
        super.setUp(requiredAppList, eventExceptions, loadSdkExceptions);
        super.ddlLog(meta);
    }
}
```

### 3.3 租户 App 加载:按 Service + 租户维度

```java
// SaaSDistributedAppLoader.java L166-261
public void tenantAppSetUp(Map<String, AppDataInfo> metaAppMap, String serviceName) {
    RouterV2 routerV2 = Route.getRouterV2();
    Map<String, List<String>> appNameMap = routerV2.getTenantApps(serviceName);
    //   serviceName → {tenantId → [appNameTag]}

    if (MapUtils.isEmpty(appNameMap)) {
        SummaryManager.getInstance().multiTagListAndWatch();
        return;
    }

    // 构造 tenantAppInfoMap: {tenantId → [SaaSAppInfo]}
    // 收集所有租户 App,去重得到 tenantAppList
    collectTenantAppInfo(metaAppMap, appNameMap, tenantAppList, tenantAppInfoMap);

    // 拉取所有 App JSON
    Map<String, String> installedAppJsonMap = buildAppDataJson(tenantAppList);

    // ★ 核心调用:按租户计算
    Map<String, TenantMetaGroupContainer> tenantContainerMap =
        ContainerFacade.computeMetaWithTenant(tenantAppInfoMap, installedAppJsonMap);

    // 处理 appDataInfo、ClassLoader、SDK 加载
    ...

    // ★ 写入 EngineContainer
    tenantContainerMap.forEach((tenantId, tenantMetaGroupContainer) -> {
        EngineContainer.tenantsMetaGroupContainers.put(tenantId, tenantMetaGroupContainer);
        tenantMetaGroupContainer.getGroupContainers().forEach((groupId, appGroupContainer) -> {
            ...
            EngineContainer.saasGroupContainers.put(groupId, appGroupContainer);
        });
    });
}
```

### 3.4 调用链总结

```
K8s 启动 Pod
  │
  ├─ env SVC_NAME=snest-v1 → Route.currentServiceName() = "snest-v1"
  │
  └─ SaaSDistributedAppLoader.setUp()
       │
       ├─ Route.getInstalledAppsByServiceName("snest-v1")
       │   └─ RouterV2 (SPI) → 当前 Service 必装 App 列表
       │
       └─ RouterV2.getTenantApps("snest-v1")
           └─ 返回 {Tenant A: [...], Tenant B: [...], Tenant C: [...]}
              │
              └─ ContainerFacade.computeMetaWithTenant(tenantAppInfoMap, appJsonMap)
                  └─ 对每个租户:
                       - 计算 TenantMetaGroupContainer
                       - 写入 EngineContainer.tenantsMetaGroupContainers
```

---

## 4. 运行时:远程调用如何路由

### 4.1 Route.inLocal — 决定本地执行还是远程 RPC

```java
// Route.java L82-109
public static boolean inLocal(String appNameTag) {
    if (EngineContainer.getAppDataInfo(appNameTag) != null) {
        return true;  // 当前 Pod 已加载,直接本地执行
    }

    String curServiceName = Route.currentServiceName();
    List<String> serviceNames;
    Meta meta = BaseContextHandler.getMeta();
    if (AppLifecycleFacade.supportMultiTag() && meta != null
        && StringUtils.isNotBlank(meta.getTenantId())) {
        // SaaS 多版本模式:根据 tenantId 路由
        serviceNames = Route.getRouterV2().getServices(meta.getTenantId(), appNameTag);
    } else {
        // 单版本模式:根据 app 路由
        serviceNames = Route.getServices(appNameTag);
    }
    if (CollectionUtils.isEmpty(serviceNames)
        || StringUtils.isBlank(curServiceName)
        || serviceNames.contains(curServiceName)) {
        String msg = String.format(
            "No app info in local and remote, invoke appName: %s, routeName: %s, curServiceName: %s",
            appNameTag, serviceNames, curServiceName);
        logger.error(msg);
        throw new EngineException(msg);
    }
    return false;  // 远程调用
}
```

### 4.2 路由策略对比

| 模式 | 路由 Key | 查询接口 | 命中后 |
|---|---|---|---|
| **单版本** | `appNameTag` | `route.getServices(app)` | 跨 Service 调用 |
| **SaaS 多版本** | `(tenantId, appNameTag)` | `routerV2.getServices(tenantId, appNameTag)` | 跨 Service 调用 |
| **本地命中** | — | `EngineContainer.getAppDataInfo` | 直接本地反射 |

### 4.3 路由决策时序

```
HTTP 请求 → /{tenant}/rpc/service  (JSON-RPC 2.0)
  │
  ├─ BaseModel 解析 → recordSet.call("create", args)
  │
  └─ CglInvocation / RpcInvocation 路由判断:
       │
       ├─ EngineContainer.getAppDataInfo(appNameTag) != null
       │  → 本地反射(CglInvocation)
       │
       └─ 否则:RpcController 通过 JSON-RPC 转发到目标 Service 的 Pod
            │
            ├─ 当前 serviceName ∈ 列表 → 抛出 EngineException(数据不一致)
            │
            └─ 否则 → RpcController 通过 JSON-RPC 转发到目标 Service 的 Pod
```

---

## 5. 安装阶段:AppStoreServiceImpl 如何调用 InstallerV2

### 5.1 调用入口

```java
// AppLifecycleFacade.java L867
public static void uninstallAppV2(List<InstallerV2.TenantGroup> tgaList){
    InstallerV2 installer = getInstallerV2();
    installer.uninstallApps(tgaList);
}

public static void restartV2(Set<String> serviceList){
    InstallerV2 installer = getInstallerV2();
    installer.restart(serviceList.toArray(new String[0]));
}
```

### 5.2 InstallerV2 的生命周期

```
应用市场安装 App
  │
  ├─ AppStoreServiceImpl.installApps(...)    [AppStoreServiceImpl.java]
  │   │
  │   ├─ 1. 保存 App 元数据到 meta_app / meta_app_store
  │   ├─ 2. 上架 → MetaModelFacade.publishOriginalMetas(...)
  │   ├─ 3. 触发 RouterV2/InstallerV2 跨 Service 推送
  │   │      └─ InstallerV2.installApps(serviceName, kind, replicas, image, tgaList)
  │   │
  │   └─ 4. 计算终态
  │        └─ ContainerFacade.buildMetaWithTenant(tenantAppInfoMap, appJsonMap)
  │
  ├─ 应用市场卸载 App
  │   └─ AppLifecycleFacade.uninstallAppV2(tgaList)
  │       └─ InstallerV2.uninstallApps(tgaList)
  │
  ├─ 应用市场更新 App(同 app.tag 新版本)
  │   └─ InstallerV2.updateApps(tgaList)
  │
  └─ 应用市场重启 Service
      └─ AppLifecycleFacade.restartV2(serviceList)
          └─ InstallerV2.restart(serviceNames)
```

### 5.3 安装的核心数据:TenantGroup 嵌套结构

```java
InstallerV2.TenantGroup {
    String tenantID;
    List<GroupApp> groupAppList;
}
InstallerV2.GroupApp {
    String groupID;
    List<String> appNameList;
}
```

| 字段 | 含义 |
|---|---|
| `tenantID` | 租户 ID |
| `groupID` | App 授权组 ID(一个组内的 App 一起安装/卸载) |
| `appNameList` | 该组内 App 的 `appNameTag` 列表 |

> **设计哲学**:按「组」而非按「App」调度 — 同一组的 App 一起部署到同一 Pod,避免 App 间的运行时依赖碎片化。

---

## 6. metadata 操作

### 6.1 三件套(AppLifecycleFacade)

| 操作 | 方法 | 用途 |
|---|---|---|
| `updateMetadata(serviceName, keyValue)` | 写 | 更新 Service 的 metadata(如版本号、健康状态) |
| `deleteMetadata(serviceName, keyList)` | 删除 | 删除指定 key |
| `getMetadata(serviceName)` | 读 | 拉取 Service 当前 metadata |

> 这些 metadata通常通过 K8s ConfigMap/Secret 注入到 Pod 环境变量;具体由 RouterV2/InstallerV2 的实现决定。

---

## 7. 关键代码位置速查

| 符号 | 文件:行号 |
|---|---|
| `RouterV2` 接口 | `spi/distributed/RouterV2.java` |
| `InstallerV2` 接口 | `spi/distributed/InstallerV2.java` |
| `Route.route` (静态 SPI 实例) | `api/distributed/Route.java:24` |
| `Route.routerV2` (静态 SPI 实例) | `api/distributed/Route.java:25` |
| `Route.loadSpi` | `api/distributed/Route.java:41` |
| `Route.getRouterV2` | `api/distributed/Route.java:56` |
| `Route.getAllTenantIds` | `api/distributed/Route.java:64` |
| `Route.inLocal` | `api/distributed/Route.java:82` |
| `Route.getInstalledAppsByServiceName` | `api/distributed/Route.java:135` |
| `Route.currentServiceName` | `api/distributed/Route.java:127` |
| `AppLifecycleFacade.getInstallerV2` | `facade/AppLifecycleFacade.java:872` |
| `AppLifecycleFacade.uninstallAppV2` | `facade/AppLifecycleFacade.java:867` |
| `AppLifecycleFacade.restartV2` | `facade/AppLifecycleFacade.java:886` |
| `AppLifecycleFacade.getTenantApps` | `facade/AppLifecycleFacade.java:896` |
| `SaaSDistributedAppLoader.setUp` | `container/load/impl/SaaSDistributedAppLoader.java:53` |
| `SaaSDistributedAppLoader.requiredAppSetUp` | `container/load/impl/SaaSDistributedAppLoader.java:135` |
| `SaaSDistributedAppLoader.tenantAppSetUp` | `container/load/impl/SaaSDistributedAppLoader.java:166` |
| `SummaryManager.multiTagListAndWatch` | `facade/summary/SummaryManager.java:79` |

---

## 8. 设计哲学总结

> **RouterV2/InstallerV2 是 sie-snest 与 K8s 集群的解耦边界**。

1. **SPI 解耦** — 引擎只依赖接口;具体 K8s 操作(kubectl/Helm/CDK/CDK8s)由 `sie-iidp-app-store-app` 实现
2. **三要素路由** — `(tenantId, appNameTag, serviceName)` 三元组决定目标 Pod
3. **按"组"而非按"App"调度** — 同一组 App 同生共死,避免运行时依赖碎片化
4. **镜像相同,配置不同** — Pod 间差异仅在 metadata/ConfigMap,无须重新打镜像即可完成版本切换