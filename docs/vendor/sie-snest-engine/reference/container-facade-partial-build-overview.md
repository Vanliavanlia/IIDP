---
name: container-facade-partial-build-overview
description: ContainerFacade.buildMetaPartial(List<String>, String) L967 分发入口及三条分支(SDK/Workflow/Orchestration)的核心逻辑、对照表、风险清单。覆盖以 App 为粒度的整体重算能力,与租户维度重算体系平行但独立。
---

# ContainerFacade 部分计算(Partial Build)核心逻辑——buildMetaPartial 分发与三条分支

**模块**:`sie-snest-engine`
**文件**:`sie-snest-engine/src/main/java/com/sie/snest/engine/facade/ContainerFacade.java`
**核心方法**:
- `buildMetaPartial(List<String> appJsonList, String finalMetaJson)` — L967(**分发入口**)
- `buildMetaPartialSDK` — L1153
- `buildMetaPartialWorkflow` — L1548
- `buildMetaPartialOrchestration` — L998

## 1. 辨析:`ContainerFacade` 里同名的两个 `buildMetaPartial`

`ContainerFacade` 里有**两个签名完全不同、职责也完全不同**的 `buildMetaPartial` 静态方法,极易在交接/检索时混淆:

|  | 本文档:`buildMetaPartial(List<String>, String)` | 另一文档:`buildMetaPartial(String, String)` |
| --- | --- | --- |
| 位置 | L967 | L1982 |
| 入参 | 一批**完整 App JSON**(`appJsonList`)+ 变更日志 JSON | `type`(ProductLine/Menu/UiView/ViewSeed/CustomAuth/FunctionAuth)+ 该类型对应的**局部增量 JSON** |
| 处理粒度 | 以"App(应用)"为单位重新跑一遍依赖图计算和 `MetaBuilderSandbox` | 以"一条元数据记录"(如一个菜单项、一个视图节点)为单位,直接改 Redis 里的某个字段 |
| 返回值 | `List<AppGroupContainer>`(计算结果) | `void`(纯写库操作,无计算产出) |
| 典型触发场景 | App 市场安装/更新 SDK 应用、Workflow 应用、Orchestration(规则编排)应用 | 前端"菜单管理""视图设计器""自定义权限配置"页面对单条元数据的增删改 |

两者除了名字相同外,代码上没有相互调用关系,可以完全独立理解。本文档只讲 L967 这个重载。

## 2. `buildMetaPartial(List<String> appJsonList, String finalMetaJson)`(L967)—— 分发入口

```java
public static List<AppGroupContainer> buildMetaPartial(List<String> appJsonList, String finalMetaJson) {
    // 1. 非空校验:appJsonList 不能为空,且每个元素不能是空字符串
    // 2. appJsonList2JsonObjectList + groupByBuildFrom:把每个 appJson 按 Attributes.BuildFrom 字段分组
    //    分组结果只会落在三个桶之一:SDK / WORKFLOW / RULE_ORCHESTRATION
    // 3. 互斥校验:sdkApps / workflowApps / orchestrationApps 不能同时有值(不支持混合批次)
    // 4. 至少要有一个桶非空,否则视为"无需要计算内容"
    // 5. finalMetaJson 反序列化成 Map<String, List<MetaModelChangeDTO>>(仅 SDK 分支会用到)
    // 6. 按非空的桶分派:
    if (sdkApps 非空)          return buildMetaPartialSDK(appJsonList, sdkApps, metaModelChangeDTOS);
    else if (workflowApps 非空) return buildMetaPartialWorkflow(appJsonList, workflowApps);
    else if (orchestrationApps 非空) return buildMetaPartialOrchestration(appJsonList, orchestrationApps);
}
```

**分组依据**:`groupByBuildFrom` 读取每个 App JSON 的 `Attributes.BuildFrom` 属性,取值对应 `MetaConstant`:

- `SDK`(`"SDK"`)—— 常规业务应用(App 市场安装的 SDK 应用)
- `BUILD_FROM_WORKFLOW`(`"WORKFLOW"`)—— 工作流/审批流应用
- `BUILD_FROM_RULE_ORCHESTRATION`(`"RULE_ORCHESTRATION"`)—— 规则编排应用

**关键约束(易忽略)**:`appJsonList` 这批 App **只能属于同一种 BuildFrom**,一次调用不能混装 SDK+Workflow 或 SDK+Orchestration,否则在第 3 步直接抛 `SnestException`。三条分支各自独立处理各自的 `computeMeta` 结果和 Redis 落库策略(见下)。

**参数 `finalMetaJson`**:`Map<appNameTag, List<MetaModelChangeDTO>>` 结构的变更日志(记录该 App 相对上一版本,菜单/权限等做了哪些改动)。只有 `buildMetaPartialSDK` 会用它(通过 `assembleJsonWithChangeLog` 把日志"回放"进 appJson),`buildMetaPartialWorkflow`/`buildMetaPartialOrchestration` 完全不使用这个参数。

**调用方**(据 CodeGraph 静态分析,共 3 处):
- `AppStoreServiceImpl`(App 市场安装/更新服务)—— SDK 应用安装场景,`finalMetaJson` 传 `null`
- `SingleAppLoader`(单机加载器)—— 传入真实的 `appNameTagChangeLogs`(菜单/权限变更日志),支持初始化(`init`)和增量两种触发
- `MetaModelFacade` —— `type.equalsIgnoreCase("api")` 时触发(对应 Workflow/Orchestration 这类"api 类型"应用),`finalMetaJson` 传 `null`

## 3. `buildMetaPartialOrchestration`(L998)—— 规则编排应用分支

**用途**:安装/更新"规则编排(RULE_ORCHESTRATION)"类型的应用,并把编排产生的 `ServiceMeta`(`ServiceType == Orchestration`)挂载到对应模型上。

**步骤**:

1. `getAllInstalledBussAppMetaGraph()` 取全量已安装业务 App 的 `MetaGraph`,`computeAppGroupGraph` 建依赖图。
2. `computeCanUnInstall(appJsonList, graphMap)`:校验 `appJsonList` 里这批 App 是否"可安全替换"——检查依赖图中是否存在**不在本次更新名单内**的其他 App 依赖着它们。只要有一张依赖图判定"有外部依赖",直接抛 `SnestException("计算终态错误,存在依赖")` 中断。
    - 命名虽是"UnInstall",但这里语义是"以卸载校验的逻辑,反向确保本次更新不会破坏其他 App 对它的依赖假设"(本质是把"替换"当成"先卸载再装"来做前置校验)。
3. `computeMeta(appJsonList)` 重新计算这批 App 的 `AppGroupContainer` 列表,取出其中 `WORKFLOW_SANDBOX` 分组的容器(`workflowAppGroupContainer`,承载编排产生的 `orchestrationServiceMap`)。
4. **求出受影响的模型集合**:分别计算"更新前"(旧 `MetaGraph` → `getOriginalMetas(tag, "api")` 反查旧 JSON → 重新计算旧图 → 取 `injectModel`)和"更新后"(新 `MetaGraph` 的 `injectModel`)两组模型名,取并集 `allModels`。
5. `ContainerFacade.readModels(allModels)` 批量读出这些模型的 `ModelMeta`,**先移除**每个模型上所有 `ServiceType == Orchestration` 的旧服务(`getServicesMap().values().removeIf(...)`)——即"先清空旧编排服务,再挂新的",避免残留失效的编排服务。
6. 把 `workflowAppGroupContainer.getOrchestrationServiceMap()` 按 `ServiceMeta.getModel()` 分组,对每组:解析模型的 `refPath` 属性(没有则用模型名本身)作为目标模型名,若目标模型在 `allModelMetaMap` 里存在,则把该组下所有 `ServiceMeta` 通过 `refModel.addService(...)` 挂上去。
7. **落库**(不经过 `metaPartialStorage`/`diffMeta`,是本分支独有的简化落库方式):
    - `SummaryManager.getInstance().updateModelSummary(allModelMetaMap, suffix)` 写模型摘要临时 key。
    - 遍历 `allModelMetaMap`,把每个 `ModelMeta` 写到 `getBussModelKey(name) + suffix` 临时 key,正式 key 加入 `changeKeySet`。
    - `RedissonCache.batchUpdateRedis(changeKeySet, suffix)` 原子生效(详见 Redis 批量更新交接文档)。
    - 发 `sendAppSummaryEvent()` + `sendModelSummaryEvent()` 两个通知事件。
8. 返回 `computeMeta` 产出的 `appGroupContainerList`(**注意**:返回值是"计算结果",不包含本方法后续对模型服务做的挂载改动的完整落库快照——调用方如果依赖返回值里的模型服务信息,需确认是否与 Redis 实际落库内容一致,见风险)。

**与 `buildMetaPartialSDK` 的关键差异**:本分支**不调用** `metaPartialStorage`/`diffMeta`,因此:
- 不会更新 `MetaGraph` 的 Redis 快照(`getAppMetaGraphKey`),意味着下次部分计算读取该 App 的 `MetaGraph` 缓存时,缓存内容可能已经过期。
- 不涉及菜单/视图/自定义权限/产品线的 diff(编排应用本身通常不产出这些)。

## 4. `buildMetaPartialSDK`(L1153)—— 常规业务(SDK)应用分支

**用途**:安装/更新/新增普通业务应用(`BuildFrom=SDK`),是三条分支里逻辑最完整、覆盖面最广的一条(含依赖图双向传播、菜单父子关系重建、完整 diff 落库)。

**步骤**:

1. **多租户(SaaS)白名单校验**:若 `AppLifecycleFacade.supportMultiTag()`(SaaS 多租户模式)为真,要求 `appJsonList` 里的每个 App 都必须属于"必装 App"(`getRequiredAppNames()`,来自配置 `ConfigConstant.IMAGE_MINIMAL_REQUIRE_APPS`),否则抛异常。即:**SaaS 模式下,本方法(非租户维度路径)只能用来装平台级必装应用**,普通租户业务应用要走 `buildMetaWithTenant` 那一套租户维度体系(见 `container-facade-tenant-meta.md`)。非 SaaS(单机/传统多租户前的模式)则不做此限制。
2. 计算新旧依赖图:`getAllInstalledPlatBussAppMetaGraph()`(SaaS 模式下会先过滤成只保留必装 App 的 `MetaGraph`)作为"旧图全集"基线,替换掉本次更新的 App 后加上新算的 `MetaGraph`,得到 `afterGraph`,再 `computeAppGroupGraph`(`ENGINE_CHECK_EXTEND` 配置控制是否校验继承关系)得到新依赖图。
3. `computeOtherPlatRelatedApp`:BFS 双向遍历(前驱+后继,即"谁依赖我"和"我依赖谁"都算),求出这批更新 App 在新依赖图上的**全部关联 App**(`otherRelatedAppList`,含自身)。SaaS 模式下额外按"必装 App"白名单过滤结果。
4. 求"更新前也关联"的部分:`beforeNeedCalApp`(新关联集合中,本来就在旧全集里的)→ 再对**旧图**做一次同样的双向 BFS,得到 `beforeAllRelatedAppSet`——目的是"变更前后关联应用可能不完全一致,两边都要纳入重算范围",取并集 `allAppTag`。
5. 对 `allAppTag` 里"不属于本次直接更新"的 App,从 Redis 反查其原始 JSON(`getOriginalMetas(tag, "buss")`,兜底 `"api"`)补进 `appJsonList` 一起参与本次 `computeMeta`——即:**虽然只更新了几个 App,但所有受依赖关系影响的关联 App 都要跟着重新算一遍**,保证依赖图和跨 App 引用(如 many2many、菜单挂载)的一致性。
6. `assembleJsonWithChangeLog`:把 `finalMetaJson` 解析出的变更日志(`MetaModelChangeDTO`)按 `appName.tag` 回放进对应的 appJson。
7. `computeMeta(...)` 正式计算出 `appGroupContainers`。
8. `dealMenuParentAndChildren`:处理菜单父子关系——通过遍历各 `AppContainer` 里的 `MenuMeta.MenuConnectionMeta`(`point.src→dst` 表达"菜单连接")结合 Redis 里已有的终态菜单表,反推/补齐父子 `parentId`/`childrenIds`。
9. 计算 `afterRelatedMetaGraph`(本次涉及但未被直接更新的旧 `MetaGraph` + 本次新算的 `MetaGraph`)与 `beforeAllMetaGraph`(步骤 4 的旧图快照),交给 `metaPartialStorage` 做完整 diff 落库(详见 Redis 批量更新交接文档)。
10. 返回 `appGroupContainers`。

**与另外两条分支的关键差异**:本分支是唯一会调用 `metaPartialStorage`(完整 `diffMeta`:产品线/App/模型/视图/资源/菜单/m2m/摘要/UI视图九件套 diff + `MetaGraph` 快照回写)的分支,落库覆盖面最全;也是唯一处理"关联 App 双向传播 + 菜单父子重建"的分支。

## 5. `buildMetaPartialWorkflow`(L1548)—— 工作流/审批流应用分支

**用途**:安装/更新工作流(Workflow)应用,把流程定义(`ProcessDefinitionMap`)里的 `ServiceMeta` 挂载到对应模型。

**步骤**:与 `buildMetaPartialOrchestration`(第 3 节)**结构几乎逐行相同**,主要差异点:

1. 取"更新前"旧模型集合时,调用的是 `MetaModelFacade.getWorkflowOriginalMetas(tag)`(工作流专属的初态获取方法)而不是通用的 `getOriginalMetas(tag, "api")`。
2. 挂载的服务来自 `workflowAppGroupContainer.getProcessDefinitionMap()`(流程定义),而不是 `getOrchestrationServiceMap()`;且**不需要按 `refPath` 分组转发**——直接按 `serviceMeta.getModel().getName()` 查表后 `addService`,逻辑更简单。
3. **不会先移除模型上的旧服务**(Orchestration 分支第 5 步会 `removeIf(ServiceType == Orchestration)` 清理旧编排服务,Workflow 分支没有对应的"先清空旧 Workflow 类型服务"步骤)——这意味着如果一个模型的工作流服务被删除/改名,旧的 `ServiceMeta` 可能不会被自动摘除,需要接手后确认这是否是遗留缺口(见风险)。
4. 落库时把 `entry.getKey() + suffix` 整体传给 `MetaKeyFacade.getBussModelKey(...)`(Orchestration 分支是 `getBussModelKey(entry.getKey()) + suffix`)。两种写法最终产出的 Redis key 字符串**完全等价**,不构成功能差异,但属于代码风格不一致。

## 6. 三条分支落库方式对比小结

|  | Orchestration | Workflow | SDK |
| --- | --- | --- | --- |
| 是否调用 `metaPartialStorage`/`diffMeta` | 否 | 否 | **是** |
| 是否更新 `MetaGraph` Redis 快照 | 否 | 否 | 是 |
| 是否处理菜单父子关系 | 否 | 否 | 是(`dealMenuParentAndChildren`) |
| 是否有前置"依赖校验" | 是(`computeCanUnInstall`) | 否 | 否(但有关联 App 双向传播兜底) |
| 落库对象 | 模型上的 Orchestration 服务 + 模型摘要 | 模型上的流程定义服务 + 模型摘要 | 全量业务元数据(产品线/菜单/视图/资源/模型/m2m/摘要/App/MetaGraph) |
| SaaS 多租户白名单限制 | 无显式限制 | 无显式限制 | **有**(`AppLifecycleFacade.supportMultiTag()` 时仅限必装 App) |

## 7. 已知风险 / 待确认事项清单

1. **【需确认】`buildMetaPartialWorkflow` 挂载新服务前未清理模型上旧的工作流类服务**:对比 `buildMetaPartialOrchestration` 第 5 步会 `removeIf(ServiceType == Orchestration)`,Workflow 分支没有对应清理动作。如果一个模型的流程定义被删除或重命名,旧的 `ServiceMeta` 是否会残留在 `ModelMeta.getServicesMap()` 里,需要接手后写用例验证。
2. **【需确认】`buildMetaPartialOrchestration` / `buildMetaPartialWorkflow` 不更新 `MetaGraph` Redis 快照**:只有 SDK 分支会通过 `metaPartialStorage` 回写 `getAppMetaGraphKey`。如果后续有代码假设"只要 App 更新过,`MetaGraph` 缓存一定是最新的",这两条分支可能导致缓存与实际不一致。
3. **【需确认】`computeCanUnInstall` 语义与命名**:只在 Orchestration 分支调用,SDK/Workflow 分支没有类似的前置依赖校验。需要与原作者确认:是"只有编排应用需要这层保护"的有意设计,还是 SDK/Workflow 分支遗漏了同等校验。
4. **【需确认】`buildMetaPartialSDK` 中 `assembleJsonWithChangeLog` 对 `metaModelChangeDTOS == null` 的容忍度**:`AppStoreServiceImpl`/`MetaModelFacade` 两处调用方传的 `finalMetaJson` 均为 `null`,需确认 `assembleJsonWithChangeLog` 和其内部 `assembleAppJsonWithChangeLog` 对 `null` map 的处理是"安全跳过"还是可能抛 NPE。
5. **【低风险,代码风格】** `buildMetaPartialWorkflow`/`buildMetaPartialOrchestration` 里 `getBussModelKey(...)` 的后缀拼接写法不一致,功能等价,建议重构时顺手统一写法。
6. **【测试覆盖】** 三个分支均标注"⚠️ no covering tests found",仅能通过 `ContainerFacadeManualTest` 里 `testBuildMetaPartial*` 系列间接覆盖,建议接手后评估是否需要为三条分支分别补充针对性单测。

## 8. 排查 / 验证建议

- **日志关键字**:`开始构建部分SDK元数据信息`、`待更新应用`、`所有应用`、`计算依赖图`、`计算在更新前涉及所有相关应用`、`部分计算应用组容器,更新应用`(均在 `buildMetaPartialSDK` 内,`logger.info` 级别)
- **关键异常信息**:`"计算终态错误,不支持同时计算 Workflow, Orchestration 以及SDK"`(混合批次)、`"计算终态错误,存在依赖"`(Orchestration 分支 `computeCanUnInstall` 失败)、`"多租户模式仅支持必装app,该app不属于必装app"`(SaaS 白名单校验失败)
- **相关测试**:`ContainerFacadeManualTest.java`
    - `testBuildMetaPartial1`(L366)、`testBuildMetaPartial`(L373)、`testBuildMetaPartial_meta`(L447)、`testBuildMetaPartial_duplicate_model`(L697)—— 均调用的是 `buildMetaPartial(List<String>, String)` 这个重载,覆盖的多是 SDK 分支路径。
    - 未发现专门针对 Orchestration/Workflow 分支的独立测试用例,建议接手后确认是否有遗漏。

## 9. 参考文件索引(file:line)

| 符号 | 文件:行号 |
| --- | --- |
| `buildMetaPartial(List<String>, String)` | `ContainerFacade.java:967` |
| `buildMetaPartialOrchestration` | `ContainerFacade.java:998` |
| `buildMetaPartialSDK` | `ContainerFacade.java:1153` |
| `computeOtherPlatRelatedApp` | `ContainerFacade.java:1247` |
| `dealMenuParentAndChildren` | `ContainerFacade.java:1351` |
| `metaPartialStorage` | `ContainerFacade.java:1484` |
| `assembleJsonWithChangeLog` | `ContainerFacade.java:1513` |
| `buildMetaPartialWorkflow` | `ContainerFacade.java:1548` |
| `computeCanUnInstall` | `MetaGraphComputeUtil.java:616` |
| `getAllInstalledPlatBussAppMetaGraph` / `getAllInstalledBussAppMetaGraph` | `ContainerFacade.java:1906` / `:1874` |
| `getRequiredAppNames` | `ContainerFacade.java:1924` |
| `AppLifecycleFacade.supportMultiTag` | `sie-snest-engine/src/main/java/com/sie/snest/engine/facade/AppLifecycleFacade.java` |
| `MetaModelFacade.getWorkflowOriginalMetas` | `MetaModelFacade.java` |
| 调用方 | `AppStoreServiceImpl.java:565`、`SingleAppLoader.java:210`、`MetaModelFacade.java:576` |
| 集成测试 | `ContainerFacadeManualTest.java`(L366/373/447/697 等) |
