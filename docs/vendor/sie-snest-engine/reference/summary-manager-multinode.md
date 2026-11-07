---
name: summary-manager-multinode
description: SummaryManager 多节点同步与 Redis 摘要机制,涵盖五种摘要类型、publishEngineEvent 集群总线广播、RedissonCache 锁保护下的读写、与 buildMetaWithTenant 的协作时序。接手 SaaS 多节点一致性、跨 Pod 摘要同步、tenantHash 分桶存储时必读。
---

# SummaryManager — 多节点同步与 Redis 摘要机制

> **配套阅读**:`container-facade-saas-architecture.md`(SaaS 架构)、`router-v2-distributed.md`(跨 Service 路由)
>
> **核心问题**:`buildMetaWithTenant` 落库后,如何让**集群所有 Pod** 都知道这个租户的元数据已经更新了?

---

## 1. 问题域

### 1.1 为什么需要"摘要事件"?

| 场景 | 痛点 |
|---|---|
| Pod A 执行 `buildMetaWithTenant` | A 进程内的 `EngineContainer.tenantsMetaGroupContainers` 已更新 |
| Pod B 同时收到该租户的 RPC 请求 | B 进程的容器是旧的,**不知道**要重新加载 |
| 没有同步机制 | 跨 Pod 元数据不一致 → A 上能查到的数据 B 上查不到,或查到旧版本 |

### 1.2 解决思路

```
┌─────────────────────────────────────────────────────────────┐
│                  SaaS 多节点同步架构                          │
│                                                             │
│  Pod A (buildMetaWithTenant 执行方)                         │
│     │                                                       │
│     │ ① 落 Redis 终态                                         │
│     │ ② SummaryManager.sendTenantAppSummaryEvent()          │
│     │ ③ publishEngineEvent → EngineEventBus (集群总线)       │
│     │                                                       │
│     ▼                                                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Redis Pub/Sub 引擎事件总线                   │  │
│  │     (TENANT_APP_SUMMARY_TYPE 事件广播到所有 Pod)        │  │
│  └──────────────────────────────────────────────────────┘  │
│     │                                                       │
│     ├──→ Pod B (订阅者): readTenantModelSummary2Local()      │
│     │                    → 从 Redis 拉最新摘要               │
│     │                    → 更新本地 EngineContainer          │
│     ├──→ Pod C: 同上                                        │
│     └──→ Pod D: 同上                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 摘要类型与事件总线

### 2.1 五种摘要类型

```java
// SummaryManager.java 中定义的常量
public static final String MODEL_SUMMARY_TYPE            = "model_summary";
public static final String APP_SUMMARY_TYPE                = "app_summary";
public static final String TENANT_MODEL_SUMMARY_TYPE     = "tenant_model_summary";
public static final String TENANT_APP_SUMMARY_TYPE        = "tenant_app_summary";
public static final String TENANT_API_WF_SUMMARY_TYPE    = "tenant_api_wf_summary";
```

| 摘要类型 | 触发时机 | 数据范围 | 订阅者 |
|---|---|---|---|
| `MODEL_SUMMARY_TYPE` | 单版本构建后 | 全局 ModelSummary | `readModelSummary2Local` |
| `APP_SUMMARY_TYPE` | App 安装/更新后 | 全局 AppSummary | `readAppSummary` |
| `TENANT_MODEL_SUMMARY_TYPE` | SaaS 多版本租户构建后 | 指定 tenantId + tenantHash 的 ModelSummary | `readTenantModelSummary2Local` |
| `TENANT_APP_SUMMARY_TYPE` | SaaS 多版本租户构建后 | 指定 tenantId + tenantHash 的 AppSummary | `readTenantAppSummary2Local` |
| `TENANT_API_WF_SUMMARY_TYPE` | 租户 API/工作流变化后 | API/工作流相关 | `readTenantAPIWFSummary2Local` |

### 2.2 publishEngineEvent — 集群总线广播

```java
// SummaryManager.java L343
public void sendTenantModelSummaryEvent(String tenantId, String tenantHash, String tenantContentHash) {
    Map<String, Object> data = new HashMap<>();
    data.put(TENANT_ID, tenantId);
    data.put(TENANT_HASH, tenantHash);
    data.put(TENANT_CONTENT_HASH, tenantContentHash);
    ((MetaDataEventBusImpl) EventFacade.getEventBus()).publishEngineEvent(TENANT_MODEL_SUMMARY_TYPE, data);
}

public void sendTenantAppSummaryEvent(String tenantId, String tenantHash, String tenantContentHash) {
    Map<String, Object> data = new HashMap<>();
    data.put(TENANT_ID, tenantId);
    data.put(TENANT_HASH, tenantHash);
    data.put(TENANT_CONTENT_HASH, tenantContentHash);
    ((MetaDataEventBusImpl) EventFacade.getEventBus()).publishEngineEvent(TENANT_APP_SUMMARY_TYPE, data);
}
```

| 字段 | 含义 |
|---|---|
| `TENANT_ID` | 租户 ID(如 `"tenantA"`) |
| `TENANT_HASH` | 该租户全部 App 集合的哈希(多组时为 `keyHash`) |
| `TENANT_CONTENT_HASH` | 全部 App 内容的哈希(用于判断"名单相同但内容是否变化") |

> **设计哲学**:通过哈希值 + Redis 摘要的二级缓存,订阅者收到事件后只需拉取一份小体积的摘要(而非整个终态),按需加载。

---

## 3. SummaryManager 单例生命周期

### 3.1 启动时:订阅与初始加载

```java
// SummaryManager.java L70-77
public void init() {
    readModelSummary2Local();
    readAppSummary(true);
    EventFacade.getEventBus().subscribe(null, APP_SUMMARY_TYPE, this);
}
```

| 阶段 | 动作 |
|---|---|
| 启动时 | 主动拉取 Redis 摘要到本地(冷启动数据预热) |
| 启动时 | 订阅 `APP_SUMMARY_TYPE`(注意:单版本初始化只订阅 App 级;多版本在 `multiTagListAndWatchInitTenantsMetaGroupContainer` 中补齐订阅) |

### 3.2 SaaS 模式:补齐多租户订阅

```java
// SummaryManager.java L84-94
public void multiTagListAndWatchInitTenantsMetaGroupContainer(Set<String> allTenantIds) {
    for (String tenantId : allTenantIds) {
        EngineContainer.tenantsMetaGroupContainers.computeIfAbsent(
            tenantId, TenantMetaGroupContainer::new);
    }
    readTenantModelSummary2Local();
    EventFacade.getEventBus().subscribe(null, TENANT_MODEL_SUMMARY_TYPE, this);
    EventFacade.getEventBus().subscribe(null, TENANT_API_WF_SUMMARY_TYPE, this);
    readTenantAppSummary2Local();
    EventFacade.getEventBus().subscribe(null, TENANT_APP_SUMMARY_TYPE, this);
}

public void multiTagListAndWatch() {
    Set<String> allTenantIds = Route.getAllTenantIds();
    multiTagListAndWatchInitTenantsMetaGroupContainer(allTenantIds);
}
```

| 阶段 | 动作 |
|---|---|
| SaaS 启动时 | `Route.getAllTenantIds()` 拉取所有 tenantId |
| | 用 `computeIfAbsent` 预占位 `EngineContainer.tenantsMetaGroupContainers` |
| | 订阅三种租户级事件 |

### 3.3 关闭时:取消订阅

```java
// SummaryManager.java L96-105
public void unListen() {
    if (!listened.compareAndSet(true, false)) {
        return;
    }
    EventFacade.getEventBus().unsubscribe(null, MODEL_SUMMARY_TYPE, this);
    EventFacade.getEventBus().unsubscribe(null, APP_SUMMARY_TYPE, this);
    EventFacade.getEventBus().unsubscribe(null, TENANT_MODEL_SUMMARY_TYPE, this);
    EventFacade.getEventBus().unsubscribe(null, TENANT_APP_SUMMARY_TYPE, this);
    EventFacade.getEventBus().unsubscribe(null, TENANT_API_WF_SUMMARY_TYPE, this);
}
```

| 设计 | 说明 |
|---|---|
| **CAS 守护** | `listened.compareAndSet(true, false)` 保证只关闭一次 |
| **对称订阅** | 订阅什么事件就反订阅什么事件,避免 Pod 关闭后事件总线残留回调 |

---

## 4. 事件回调:onEvent 分发

```java
// SummaryManager.java L107-124
@Override
public void onEvent(String app, String eventType, Map<String, Object> data) {
    if (MODEL_SUMMARY_TYPE.equals(eventType)) {
        readModelSummary2Local();
    }
    if (APP_SUMMARY_TYPE.equals(eventType)) {
        readAppSummary(true);
    }
    if (TENANT_MODEL_SUMMARY_TYPE.equals(eventType)) {
        readTenantModelSummary2Local(
            (String) data.get(TENANT_ID),
            (String) data.get(TENANT_HASH),
            (String) data.get(TENANT_CONTENT_HASH));
    }
    if (TENANT_APP_SUMMARY_TYPE.equals(eventType)) {
        readTenantAppSummary2Local(
            (String) data.get(TENANT_ID),
            (String) data.get(TENANT_HASH),
            (String) data.get(TENANT_CONTENT_HASH));
    }
    if (TENANT_API_WF_SUMMARY_TYPE.equals(eventType)) {
        readTenantAPIWFSummary2Local((String) data.get(TENANT_ID));
    }
}
```

| 关键设计 | 说明 |
|---|---|
| **if-else 而非 switch** | 历史遗留——允许事件类型继续叠加而不破坏现有逻辑 |
| **参数从 data 字段取** | 不同事件类型携带的 data 不同;按需解析 |
| **摘要事件不含大对象** | 事件总线只传 ID + 哈希;摘要内容从 Redis 拉 |

---

## 5. 摘要读写与本地缓存同步

### 5.1 全局 Model 摘要

```java
// SummaryManager.java L126-167
public Map<String, ModelSummary> readModelSummary() {
    String str = RedissonCache.getRedisson()
        .<String>getBucket(MetaKeyFacade.getBussSummaryModelKey(),
                           SnestLZ4CompressCodeC.STRING_INSTANCE)
        .get();
    if (str != null) {
        return JsonUtil.readValue(str, modelTypeRef);
    }
    return new HashMap<>();
}

private Map<String, ModelMeta> readModelSummary2Local() {
    Map<String, ModelSummary> modelSummary = readModelSummary();
    Map<String, ModelMeta> modelMap = new HashMap<>(modelSummary.size());
    for (Map.Entry<String, ModelSummary> entry : modelSummary.entrySet()) {
        ModelMeta modelMeta = entry.getValue().convert2ModelMeta();
        modelMeta.setName(entry.getKey());
        modelMap.put(entry.getKey(), modelMeta);
    }
    updateLocalSummaryModelMap(modelMap);
    return modelMap;
}
```

| 关键字段 | 说明 |
|---|---|
| `BussSummaryModelKey` | Redis key,存放全量 ModelSummary |
| `convert2ModelMeta` | 摘要 → 完整 ModelMeta 反序列化 |
| `updateLocalSummaryModelMap` | 过滤被卸载的模型 + 触发 ContainerFacade.readModels 拉详情 |

### 5.2 锁保护下的写

```java
// SummaryManager.java L350-365
public Map<String, String> getModelSummary(Map<String, ModelMeta> modelMetaMap) {
    Map<String, ModelSummary> summaryMap = new HashMap<>(modelMetaMap.size());
    for (Map.Entry<String, ModelMeta> entry : modelMetaMap.entrySet()) {
        summaryMap.put(entry.getKey(), ModelSummary.convert2Summary(entry.getValue()));
    }
    Map<String, String> modelSummaryMap = new HashMap<>();
    try {
        RedissonCache.lock(changeBussModelSummary);
        String fullStr = JsonUtil.toString(summaryMap);
        RedissonCache.getRedisson()
            .getBucket(MetaKeyFacade.getBussSummaryModelKey(),
                       SnestLZ4CompressCodeC.STRING_INSTANCE)
            .set(fullStr);
        modelSummaryMap.put(MetaKeyFacade.getBussSummaryModelKey(), fullStr);
    } finally {
        RedissonCache.unlock(changeBussModelSummary);
    }
    return modelSummaryMap;
}
```

| 锁机制 | 说明 |
|---|---|
| `changeBussModelSummary` 锁 | 防止两个 Pod 同时写 ModelSummary 导致覆盖 |
| **try-finally 守护** | 即使异常也释放锁 |
| **全覆盖式** | 不增量合并,直接整体覆盖(与"摘要"的定位一致) |

### 5.3 增量模型移除

```java
// SummaryManager.java L420-442
public void removeModelSummary(Collection<String> modelNames) {
    try {
        RedissonCache.lock(changeBussModelSummary);
        Map<String, ModelSummary> modelMap = readModelSummary();
        boolean removed = false;
        for (String modelName : modelNames) {
            ModelSummary removedModel = modelMap.remove(modelName);
            if (removedModel != null) {
                removed = true;
            }
        }
        if (removed) {
            String str = JsonUtil.toString(modelMap);
            RedissonCache.getRedisson()
                .getBucket(MetaKeyFacade.getBussSummaryModelKey(),
                           SnestLZ4CompressCodeC.STRING_INSTANCE)
                .set(str);
            ((MetaDataEventBusImpl) EventFacade.getEventBus()).publishEngineEvent(MODEL_SUMMARY_TYPE);
        }
    } finally {
        RedissonCache.unlock(changeBussModelSummary);
    }
}
```

| 设计要点 | 说明 |
|---|---|
| **删除后才发** | 只有真的移除了模型才发布事件 — 避免无意义广播 |
| **仍走锁** | 删除 + 写 + 广播一气呵成,事务性 |

---

## 6. 多版本特有:getModelSummaryGroups / getAppSummaryGroups

### 6.1 多租户分组写入

```java
// SummaryManager.java L388-407
public Map<String, String> getModelSummaryGroups(Map<String, ModelMeta> modelMetaMap, String groupNames) {
    if (groupNames == null) {
        return getModelSummary(modelMetaMap);
    }
    Map<String, ModelSummary> summaryMap = new HashMap<>(modelMetaMap.size());
    for (Map.Entry<String, ModelMeta> entry : modelMetaMap.entrySet()) {
        summaryMap.put(entry.getKey(), ModelSummary.convert2Summary(entry.getValue()));
    }
    Map<String, String> modelSummaryMap = new HashMap<>();
    String fullStr = JsonUtil.toString(summaryMap);
    modelSummaryMap.put(MetaKeyFacade.getSaaSBussGroupsSummaryModelKey(groupNames), fullStr);
    return modelSummaryMap;
}

public Map<String, String> getAppSummaryGroups(Map<String, AppMeta> appMetaMap, String tenantHash) {
    Map<String, AppMeta> summaryAppMetaMap = new HashMap<>(appMetaMap.size());
    for (Map.Entry<String, AppMeta> entry : appMetaMap.entrySet()) {
        summaryAppMetaMap.put(entry.getKey(), getSummaryAppMeta(entry.getValue()));
    }
    String fullStr = JsonUtil.metaMapToString(summaryAppMetaMap);
    Map<String, String> appSummaryMapWrapper = new HashMap<>();
    appSummaryMapWrapper.put(MetaKeyFacade.getSaaSBussMultiGroupSummaryAppKey(tenantHash), fullStr);
    return appSummaryMapWrapper;
}
```

| API | Redis Key | 用途 |
|---|---|---|
| `getModelSummary(modelMetaMap)` | `BussSummaryModel` | 全局 Model 摘要 |
| `getModelSummaryGroups(modelMetaMap, groupNames)` | `SaaSBussGroupsSummaryModel_{hash}` | 多组下的 Model 摘要 |
| `getAppSummary(appContainerMap)` | `BussSummaryApp` | 全局 App 摘要 |
| `getAppSummaryGroups(appMetaMap, tenantHash)` | `SaaSBussMultiGroupSummaryApp_{tenantHash}` | 多组下的 App 摘要 |

> **关键洞察**:每个 `tenantHash` 对应一个独立的摘要桶——一个 tenant 只能有多个 App 分组时,其摘要按 `tenantHash` 分桶存储;不同 tenant 的 `tenantHash` 不冲突。

---

## 7. SaaS 本地拉取:updateLocalSaasSummaryModelMap

### 7.1 多租户版本的拉取逻辑

```java
// SummaryManager.java L169-192
private synchronized ModelMeta updateLocalSaasSummaryModelMap(String modelName) {
    Meta meta = BaseContextHandler.getMeta();
    String tenantId = meta.getTenantId();
    TenantMetaGroupContainer tenantMetaGroupContainer =
        EngineContainer.tenantsMetaGroupContainers.get(tenantId);
    if (tenantMetaGroupContainer == null) {
        return null;
    }
    ModelMeta saasModel = tenantMetaGroupContainer.getDistributeModelMap().get(modelName);
    if (saasModel != null && !saasModel.isSummaryGen()) {
        return saasModel;  // 已加载,直接返回
    }
    if (saasModel != null && saasModel.isSummaryGen()) {
        Map<String, ModelMeta> allTenantModel =
            ContainerFacade.getAllTenantModel(tenantId, Collections.singletonList(modelName));
        if (allTenantModel.isEmpty()) {
            return null;
        }
        ModelMeta redisModel = allTenantModel.get(modelName);
        if (redisModel != null) {
            tenantMetaGroupContainer.getDistributeModelMap().put(modelName, redisModel);
        }
        return redisModel;
    }
    return null;
}
```

| 关键字段 | 说明 |
|---|---|
| `tenantMetaGroupContainer.getDistributeModelMap()` | 该租户的本地 Model 缓存 |
| `saasModel.isSummaryGen()` | 是否为"摘要生成"的占位(缺字段信息) |
| `getAllTenantModel(tenantId, modelNames)` | 拉取该租户的完整 Model(带 tenantHash) |

### 7.2 单版本与多版本的拉取路径差异

| 模式 | 拉取 API | 写入位置 |
|---|---|---|
| 单版本 | `updateLocalSummaryModelMap` | `EngineContainer.getBussinessAppGroupContainer().setDistributeModelMap(modelMap)` |
| SaaS 多版本 | `updateLocalSaasSummaryModelMap` | `EngineContainer.tenantsMetaGroupContainers.get(tenantId).getDistributeModelMap()` |

---

## 8. 与 buildMetaWithTenant 的协作时序

```plaintext
Pod A 触发 buildMetaWithTenant
  │
  │  ContainerFactory.computeMetaWithTenant() 内:
  │
  ├─ 1. 计算 TenantMetaGroupContainer(内存)
  ├─ 2. repeatModelCheck 校验
  ├─ 3. lockedMetaStorage
  │     └─ metaStorage → storeBasicMetas
  │           ├─ SummaryManager.getModelSummaryGroups(modelMap, tenantHash)
  │           │     → 写入 SaaSBussGroupsSummaryModel_{tenantHash}
  │           └─ SummaryManager.getAppSummaryGroups(appMetaMap, tenantHash)
  │                 → 写入 SaaSBussMultiGroupSummaryApp_{tenantHash}
  ├─ 4. publishFinalBuildFinished(final_build_finished 事件)
  └─ 5. SummaryManager.sendTenantAppSummaryEvent(tenantId, tenantHash, tenantContentHash)
        SummaryManager.sendTenantModelSummaryEvent(tenantId, tenantHash, tenantContentHash)
              ↓
        MetaDataEventBusImpl.publishEngineEvent(TENANT_APP_SUMMARY_TYPE, data)
              ↓
        Redis Pub/Sub 广播到所有 Pod
              ↓
        ┌─────────┴─────────┐
        ▼                   ▼
      Pod B              Pod C
      onEvent(TENANT_APP_SUMMARY_TYPE, data)
        └─ readTenantAppSummary2Local(tenantId, tenantHash, tenantContentHash)
              ├─ 从 Redis 拉 SaaSBussMultiGroupSummaryApp_{tenantHash}
              ├─ 反序列化为 AppMeta Map
              └─ 更新 EngineContainer.tenantsMetaGroupContainers[tenantId]
```

---

## 9. Redis Key 命名规范

| Redis Key | 内容 |
|---|---|
| `BussSummaryModel` | 全局 Model 摘要(单版本用) |
| `BussSummaryApp` | 全局 App 摘要 |
| `SaaSBussGroupsSummaryModel_{tenantHash}` | 多组 Model 摘要(SaaS) |
| `SaaSBussMultiGroupSummaryApp_{tenantHash}` | 多组 App 摘要(SaaS) |
| `SaaSBussGroupsSummary_{tenantHash}` | 多租户分组摘要 |
| `SaaSBussGroupSummary_{groupHash}` | 单组摘要 |

> `tenantHash` 是租户的全部 App 集合的 SHA-256 哈希值;不同租户因 App 组合不同,哈希也不同,从而实现 key 隔离。

---

## 10. 关键代码位置速查

| 符号 | 文件:行号 |
|---|---|
| `SummaryManager` 单例获取 | `facade/summary/SummaryManager.java` |
| `init` (单版本初始化) | `SummaryManager.java:70` |
| `multiTagListAndWatch` | `SummaryManager.java:79` |
| `multiTagListAndWatchInitTenantsMetaGroupContainer` | `SummaryManager.java:84` |
| `unListen` | `SummaryManager.java:96` |
| `onEvent` | `SummaryManager.java:107` |
| `readModelSummary` | `SummaryManager.java:126` |
| `readModelSummary2Local` | `SummaryManager.java:140` |
| `updateLocalSummaryModelMap` | `SummaryManager.java:152` |
| `updateLocalSaasSummaryModelMap` | `SummaryManager.java:169` |
| `sendTenantModelSummaryEvent` | `SummaryManager.java:338` |
| `sendTenantAppSummaryEvent` | `SummaryManager.java:478` |
| `getModelSummary` (单版本) | `SummaryManager.java:350` |
| `getModelSummaryGroups` (多组) | `SummaryManager.java:388` |
| `getAppSummaryGroups` (多组) | `SummaryManager.java:409` |
| `removeModelSummary` | `SummaryManager.java:420` |
| `updateAppSummary` | `SummaryManager.java:444` |
| `publishEngineEvent` | `facade/event/MetaDataEventBusImpl.java` |

---

## 11. 设计哲学总结

> **SummaryManager 是 sie-snest 多节点一致性架构的中枢**。

1. **事件驱动 + Redis 摘要** — 集群总线上只传 ID + 哈希,订阅者按需从 Redis 拉详情
2. **两级缓存** — 本地 `distributeModelMap`(线程级)+ Redis `SaaSBussGroupsSummaryModel_{hash}`(全局级)
3. **懒加载 + 摘要占位** — 第一次访问 Model 时只填摘要字段;后续按需 `getAllTenantModel` 拉完整元数据
4. **锁保护覆盖写** — 摘要写入用 `RedissonCache.lock(changeBussModelSummary)` 防止并发覆盖
5. **per-tenantHash 隔离** — 多租户的摘要按 `tenantHash` 分桶 key,互不干扰