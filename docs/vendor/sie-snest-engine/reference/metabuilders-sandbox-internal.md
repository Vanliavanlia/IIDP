---
name: metabuilders-sandbox-internal
description: MetaBuilderSandbox 内部执行原理,涵盖 buildMeta 的三阶段构建(App JSON 分桶 → 二次构建处理继承 → 终态构建)、ThreadLocal 沙箱生命周期、sandboxBuildBussAppGroupContainer 的两阶段处理。接手 computeMeta 路径的元数据计算、BuildFrom 分类、依赖图计算时必读。
---

# MetaBuilderSandbox 内部原理

> **配套阅读**:`container-facade-saas-architecture.md`(上层 SaaS 架构)、`engine-runtime.md`(运行时基础)
>
> **核心问题**:`ContainerFacade.computeMeta()` 究竟做了什么?底层是如何把一批 App JSON 转化为 `AppGroupContainer` 的?

---

## 1. 入口与执行骨架

### 1.1 调用链

```
ContainerFacade.computeMeta(appJsonList)              [ContainerFacade.java:463]
  │
  └─ MetaBuilderSandbox.buildMeta()                  [MetaBuilderSandbox.java:45]
        │
        ├─ Step 1: appJsonList2JsonObjectList       [L50]  JSON 字符串 → JSONObject
        ├─ Step 2: groupByBuildFrom                  [L54]  按 BuildFrom 分桶
        ├─ Step 3: checkBuildBaseModel()             [L59]  setUpBottomModel 装载引擎内置 base 模型
        └─ Step 4: internalBuildMeta(buildFromMap)   [L60]
              │
              ├─ internalBuildBuss(sdkApps)         [L107] 业务 App → bussAppGroupCtr
              └─ internalBuildApi(appMap, buss)     [L109] workflow + orchestration → wfAppGroupCtr
        │
        └─ finally: EngineContainer.clearThreadLocalBussSandboxAppGroupContainer()
```

### 1.2 buildFromSet 枚举(白名单)

```java
// MetaBuilderSandbox.java L27
public static final Set<String> buildFromSet = new HashSet<>(Arrays.asList(
    MetaConstant.SDK,                       // 业务 SDK App
    MetaConstant.BUILD_FROM_WORKFLOW,       // 工作流 App
    MetaConstant.BUILD_FROM_RULE_ORCHESTRATION  // 规则编排 App
));
```

> 每个 App JSON 必须在 `Attributes.BuildFrom` 字段声明属于哪一类;否则直接抛 `EngineException`(L94)。

### 1.3 三阶段输出

```java
// MetaBuilderSandbox.java L61
return Stream.of(bussAppGroupCtr, wfAppGroupCtr)
             .filter(Objects::nonNull)
             .collect(Collectors.toList());
```

**返回最多两个 AppGroupContainer**:
- `bussAppGroupCtr`(组名 = `BUSSINESS` 或 `BUSSINESS_SANDBOX`)— 业务 SDK App 的计算结果
- `wfAppGroupCtr`(组名 = `WORKFLOW_SANDBOX`)— 工作流/规则编排 App 的计算结果(仅当存在此类 App 时才非空)

---

## 2. groupByBuildFrom — 三桶分组

### 2.1 实现(L81-102)

```java
public static Map<String, List<JSONObject>> groupByBuildFrom(List<JSONObject> appJsonList) {
    Map<String, List<JSONObject>> map = new HashMap<>();
    for (int i = 0; i < appJsonList.size(); i++) {
        JSONObject jsonObject = appJsonList.get(i);
        JSONObject attributes = jsonObject.getJSONObject(MetaConstant.ATTRIBUTES);
        if (attributes == null) {
            throw new EngineException("app的Attributes不能为null。AppJson:%s", jsonObject.toJSONString());
        }
        String buildFrom = attributes.getString(MetaConstant.BUILD_FROM);
        if (buildFrom == null) {
            throw new EngineException("The Attributes of AppMeta should contain BuildFrom.AppJson:%s", jsonObject.toJSONString());
        }
        if (!buildFromSet.contains(buildFrom)) {
            throw new EngineException("不包含的buildFrom:%s, AppJson:%s", buildFrom, jsonObject.toJSONString());
        }

        // 记录 app 的安装顺序
        attributes.put(AttributesConstant.APP_INSTALLED_SEQUENCE, i);
        map.computeIfAbsent(buildFrom, k -> new ArrayList<>()).add(jsonObject);
    }
    return map;
}
```

### 2.2 设计要点

| 要点 | 说明 |
|---|---|
| **入参顺序敏感** | 用 `i` 记录安装顺序,写回 `Attributes.APP_INSTALLED_SEQUENCE` 元数据,后续依赖图计算依赖此序号 |
| **强制 BuildFrom 字段** | 缺字段直接抛异常——`BuildFrom` 是 App 类型分类的唯一维度 |
| **三桶分组** | SDK / WORKFLOW / RULE_ORCHESTRATION 各自独立成桶,**互不混杂** |
| **顺序保留** | 同桶内 JSONObject 顺序与入参一致(依赖计算可依赖此顺序) |

---

## 3. checkBuildBaseModel — 引擎内置 base 模型装载

### 3.1 实现(AppLifecycleProcess.java L460-477)

```java
public synchronized void setUpBottomModel() {
    if (!EngineContainer.getOrCreateBaseAppGroupContainer().getFinalModelMap().isEmpty()) {
        return;  // 幂等保护:已加载则跳过
    }
    AppGroupContainer appGroupContainer = EngineContainer.getOrCreateBaseAppGroupContainer();
    AppDataInfo baseAppInfo = new AppDataInfo();
    baseAppInfo.setId(MetaConstant.BASE);
    baseAppInfo.setName(MetaConstant.BASE);
    baseAppInfo.setVersion(MetaConstant.MASTER);
    baseAppInfo.setTag(MetaConstant.MASTER);
    baseAppInfo.setDisplayName(MetaConstant.BASE);
    baseAppInfo.setState("installed");
    baseAppInfo.setSource("base");
    baseAppInfo.setClassLoader(DistributedAppLoaderV2.class.getClassLoader());
    AppContainer baseAppContainer = appGroupContainer.getOrCreateAppContainer(baseAppInfo);
    this.loadInnerModel(baseAppContainer);          // 加载引擎内置 BaseModel 等
    sandboxBuildAppGroupContainer(appGroupContainer); // 触发终态构建
}
```

### 3.2 设计要点

| 要点 | 说明 |
|---|---|
| **双检 + synchronized** | JVM 级幂等保护 — 多次调用只装载一次 |
| **Base App 不占 tenant** | `id/name=MetaConstant.BASE`、`source="base"`,不进入租户维度 |
| **依赖引擎 ClassLoader** | `classLoader = DistributedAppLoaderV2.class.getClassLoader()` — 引擎类加载器 |
| **装载内容** | BaseModel、Options 等"系统内置"模型;这些是所有 App 的"基类" |

---

## 4. internalBuildBuss — 业务 App 计算核心

### 4.1 实现(L119-128)

```java
protected AppGroupContainer internalBuildBuss(List<JSONObject> appList) {
    bussAppGroupCtr = EngineContainer.createThreadLocalBussSandboxAppGroupContainer();
    if (appList == null || appList.isEmpty()) {
        return bussAppGroupCtr;
    }

    AppLifecycleProcess loader = getAppLifecycleProcess();
    loader.sandboxBuildBussAppGroupContainer(bussAppGroupCtr, appList);
    return bussAppGroupCtr;
}
```

### 4.2 关键设计:ThreadLocal 沙箱容器

```java
// EngineContainer.java:64
private static ThreadLocal<AppGroupContainer> bussSandBoxAppGroupContainer = new ThreadLocal<>();
```

| 设计 | 说明 |
|---|---|
| **ThreadLocal 隔离** | 每次 `buildMeta` 调用创建一个独立的 `AppGroupContainer`,挂在当前线程;避免并发计算时容器串扰 |
| **finally 清空** | `buildMeta` 的 finally 块调 `clearThreadLocalBussSandboxAppGroupContainer()` — 防止线程复用导致容器泄漏 |
| **仅业务组** | 只有 `BUSSINESS` 业务组用 ThreadLocal;workflow 组是普通的成员变量 |

### 4.3 sandboxBuildBussAppGroupContainer 内部细节

> **位置**:`AppLifecycleProcess.java:4351`

```plaintext
sandboxBuildBussAppGroupContainer(appGroupContainer, apps)   [L4351]
  │
  ├─ Step 1: sandboxBuildAppGroupContainer(appGroupContainer, apps)  [L4320]
  │    │
  │    ├─ 1.1 publishedApps = apps.stream().map(convert2AppDataInfo)
  │    ├─ 1.2 GraphUtil.checkAppDependency(publishedApps)        // 校验依赖图
  │    ├─ 1.3 GraphUtil.calculateAppInstallOrder(publishedApps)   // 计算安装顺序
  │    ├─ 1.4 loadOriginalMetas(appGroupContainer, publishedApps) // 把 App JSON 加载为 BaseMeta
  │    ├─ 1.5 prefetchAllTempMeta(appGroupContainer)              // 预取临时元模型
  │    ├─ 1.6 for each appDataInfo:
  │    │       secondBuild(appContainer, originalMetas.values())  // 二次构建(继承、扩展)
  │    ├─ 1.7 if buss 组 && 启动时逻辑: 读取其他 App 的 m2m list
  │    └─ 1.8 finalBuild(appGroupContainer, many2manyList)        // 终态构建
  │
  ├─ Step 2: repeatModelCheck — 重复模型名校验                       [L4364-4380]
  │   └─ 同名且 parents 为空的 Model 出现 >1 次抛 EngineException
  │
  ├─ Step 3: buildViewMetaFromViews(appGroupContainer, apps)        [L4346]
  │   └─ 视图关系建立(独立于 finalBuild,因为依赖菜单)
  │
  └─ Step 4: AppMetaPublisher.publishAppMetas(...)                  [末尾]
      └─ 把 AppContainer 写入 Redis 终态
```

### 4.4 sandboxBuildAppGroupContainer 的"先临时再终态"两步走

```java
// AppLifecycleProcess.java L4288
public void sandboxBuildAppGroupContainer(AppGroupContainer appGroupContainer) {
    appGroupContainer.getTempModelMap().putAll(prefetchAllTempMeta(appGroupContainer));
    for (AppContainer appContainer : appGroupContainer.getAppContainerMap().values()) {
        Map<String, BaseMeta> originalMetas = appContainer.getMetas();
        secondBuild(appContainer, originalMetas.values());
        appGroupContainer.getTempModelMap().putAll(appContainer.getModels());
    }
    this.finalBuild(appGroupContainer, new ArrayList<>());
}
```

| 阶段 | 容器字段 | 作用 |
|---|---|---|
| **预取** | `prefetchAllTempMeta` → `tempModelMap` | 把每个 App 的"临时模型"（含父类、扩展基类）合并入临时池 |
| **二构** | `secondBuild(appContainer, originalMetas)` | 处理 `_inherit` / `_extends` 等继承关系，产出每个 App 的 `models` |
| **终构** | `finalBuild(appGroupContainer, m2mList)` | 跨 App 合并，处理多对多关系，产出最终 `finalModelMap` |

> **设计哲学**：先"求并集 → 找依赖"，再"按依赖序合并 → 得终态"。这是 Odoo `_inherit` 链 + super() 链思想的 Java 落地。

---

## 5. internalBuildApi — 工作流/规则编排 计算

### 5.1 实现(L130-146)

```java
AppGroupContainer internalBuildApi(Map<String, List<JSONObject>> appMap, AppGroupContainer bussAppGroupContainer) {
    List<JSONObject> wfApps = appMap.get(MetaConstant.BUILD_FROM_WORKFLOW);
    if (wfApps != null && !wfApps.isEmpty()) {
        if (wfAppGroupCtr == null) {
            wfAppGroupCtr = createAppGroupContainer(WORKFLOW_SANDBOX);
        }
        internalBuildWorkflow(wfApps, bussAppGroupContainer, wfAppGroupCtr);
    }
    List<JSONObject> orchestrationApps = appMap.get(MetaConstant.BUILD_FROM_RULE_ORCHESTRATION);
    if (orchestrationApps != null && !orchestrationApps.isEmpty()) {
        if (wfAppGroupCtr == null) {
            wfAppGroupCtr = createAppGroupContainer(WORKFLOW_SANDBOX);
        }
        internalBuildOrchestration(orchestrationApps, bussAppGroupContainer, wfAppGroupCtr);
    }
    return wfAppGroupCtr;
}
```

### 5.2 关键设计

| 要点 | 说明 |
|---|---|
| **共用 wfAppGroupCtr** | workflow 和 rule_orchestration 都放进同一个 `WORKFLOW_SANDBOX` 组（不是各自分桶） |
| **依赖业务组** | `internalBuildWorkflow(..., bussAppGroupContainer, ...)` — 工作流计算依赖业务组（因为流程会调用业务模型/服务） |
| **空时返回 null** | 不存在 workflow + rule App 时 `wfAppGroupCtr == null`，最终结果只含 `bussAppGroupCtr` |

---

## 6. 沙箱生命周期管理

### 6.1 ThreadLocal 清空时机

```java
// MetaBuilderSandbox.java L62-64
} finally {
    EngineContainer.clearThreadLocalBussSandboxAppGroupContainer();
}
```

| 时机 | 动作 |
|---|---|
| `buildMeta` 正常完成 | finally 清空 → 释放 ThreadLocal 引用 |
| `buildMeta` 抛异常 | finally 仍清空 → 防止线程复用导致数据泄漏 |
| `getAppLifecycleProcess().sandboxBuildBussAppGroupContainer(...)` 内部 | 通过 ThreadLocal 拿到当前线程的沙箱容器 |

### 6.2 与 EngineContainer.tenantsMetaGroupContainers 的边界

| 容器 | 作用域 | 生命周期 | 来源 |
|---|---|---|---|
| `bussSandBoxAppGroupContainer` (ThreadLocal) | 单线程、瞬态 | 一次 `buildMeta` 调用内 | `MetaBuilderSandbox` 创建 |
| `EngineContainer.bussinessAppGroupContainer` | 全局、持久 | JVM 生命周期 | `EngineContainer.getBussinessAppGroupContainer()` |
| `EngineContainer.baseAppGroupContainer` | 全局、持久 | JVM 生命周期 | `setUpBottomModel` 初始化 |
| `EngineContainer.tenantsMetaGroupContainers` | 全局、持久 | JVM 生命周期 | `SummaryManager.multiTagListAndWatch*` 初始化 |

> **设计哲学**：沙箱用于"试算/预览"——调用方拿到结果后自己决定是否落库。业务容器和租户容器是"持久化的最终态"。

---

## 7. 关键代码位置速查

| 符号 | 文件:行号 |
|---|---|
| `MetaBuilderSandbox.buildMeta` | `MetaBuilderSandbox.java:45` |
| `appJsonList2JsonObjectList` | `MetaBuilderSandbox.java:71` |
| `groupByBuildFrom` | `MetaBuilderSandbox.java:81` |
| `buildFromSet` | `MetaBuilderSandbox.java:27` |
| `internalBuildMeta` | `MetaBuilderSandbox.java:104` |
| `internalBuildBuss` | `MetaBuilderSandbox.java:119` |
| `internalBuildApi` | `MetaBuilderSandbox.java:130` |
| `getAppLifecycleProcess` | `MetaBuilderSandbox.java:113` |
| `sandboxBuildAppGroupContainer`(无参版) | `AppLifecycleProcess.java:4288` |
| `sandboxBuildAppGroupContainer`(带 apps) | `AppLifecycleProcess.java:4320` |
| `sandboxBuildBussAppGroupContainer` | `AppLifecycleProcess.java:4351` |
| `setUpBottomModel` | `AppLifecycleProcess.java:460` |
| `bussSandBoxAppGroupContainer` (ThreadLocal) | `EngineContainer.java:64` |
| `tenantsMetaGroupContainers` | `EngineContainer.java:75` |
| `computeMeta` | `ContainerFacade.java:463` |

---

## 8. 设计哲学总结

> **MetaBuilderSandbox 是 sie-snest 元模型计算的中枢**。其设计哲学可总结为三句话：

1. **沙箱无副作用** — ThreadLocal 隔离 + finally 清空，保证"试算"不污染全局状态
2. **白名单分类** — 用 `BuildFrom` 枚举三桶（SDK / Workflow / Rule），业务 App 必须显式声明类型
3. **三阶段构建** — 预取临时 → 二次构建（继承）→ 终态构建（合并），与 Odoo 的 `_inherit` + super() 链一致