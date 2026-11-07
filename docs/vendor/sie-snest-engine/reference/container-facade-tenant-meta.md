---
name: container-facade-tenant-meta
description: 三个租户维度的元模型计算方法(computeMetaWithTenant / buildMetaWithTenant / buildMetaWithGroup)的核心逻辑、对照表、执行步骤、依赖方法、关键数据结构、调用方场景、风险清单,以及与 Partial Build 体系的边界划分。接手 SaaS 多租户元模型计算路径时必读。
---

# ContainerFacade 多租户元模型计算核心逻辑

**模块**:`sie-snest-engine`
**文件**:`sie-snest-engine/src/main/java/com/sie/snest/engine/facade/ContainerFacade.java`
**核心方法**:
- `computeMetaWithTenant` — L2788
- `buildMetaWithTenant` — L2911
- `buildMetaWithGroup` — L3123

## 1. 概述:这三个方法解决什么问题

SaaS 场景下,多个租户(tenant)可以各自安装不同组合的业务 App(每个 App 有 `app.tag` 唯一标识,及对应的初态元模型 JSON)。三个方法都在做同一件事的不同变体:

> 给定"某个(些)租户当前应该装哪些 App、新增/更新的 App JSON 是什么",重新计算出这个(些)租户完整的元模型集合(Model、ProductLine、菜单 Menu、UI 视图、多对多关系等),并按需落库/发布事件。

它们是 SaaS 租户维度"按需增量重算"能力的落地实现,与非 SaaS 场景下"全量一次性 build"的 `AppLifecycleProcess`/`buildMeta` 体系是平行的两套路径,但复用了同一套底层计算原语(`MetaGraphComputeUtil`、`MultiGroupCompute`、`MetaBuilderSandbox`、`FrontMetaBuilder`、`ViewMetaBuilder`)。

| 方法 | 是否持久化+发布 | 作用对象 | 一句话定位 |
| --- | --- | --- | --- |
| `computeMetaWithTenant` | 否(纯内存计算,只读) | `Map<租户ID, appInfo列表>`(多租户批量) | "预览/试算":算出结果给调用方看,不落库、不触发下游事件 |
| `buildMetaWithTenant` | 是(加锁落库+发布事件+摘要) | `Map<租户ID, appInfo列表>`(多租户批量) | "正式生效":按租户安装/更新非必装 App 后的真正激活入口 |
| `buildMetaWithGroup` | 是(加锁落库+发布事件,但无模型重复校验) | 单个"虚拟租户/组"(`virtualTenant` 字符串 + 单个 appInfo 列表) | 面向"虚拟租户"(多组场景下的一个组,而非真实租户)的构建变体 |

## 2. 背景与术语

- **`SaaSAppInfo` / `SaaSAppStatus`**(L2712-2779):描述一个 App 相对当前状态的变更类型:`ADD`(新装)/ `UPDATE`(更新)/ `REMOVED`(卸载)/ `UNCHANGED`(未变化,沿用已装配置)。`ADD`/`UPDATE` 时必须能在 `appTagJsonList` 中找到对应 JSON。
- **`appTagJsonList` / `appTagJsonMap`**:`key = app.tag`,`value = App 的初态元模型 JSON 字符串`。只包含"本次新增/更新"的 App,不包含未变化的 App(未变化的走 Redis 缓存的初态,见 `getAllOriginalMetasByAppTagList`)。
- **`MetaGraph`**:单个 App 的元模型依赖图(模型、父子关系、依赖等),由 `MetaGraphComputeUtil.computeMetaGraph(appJson)` 从 JSON 计算得到,或从 Redis 缓存读取(已安装的 buss 应用,见 `getInstalledBussAppMetaGraph`,L1936)。
- **"分组"(Group)**:`MultiGroupCompute.computeGroups`(L206,`MultiGroupCompute.java`)把一个租户下的多个 App 按依赖关系(含"非平台 App 不能依赖平台 App"的非法依赖校验)拆分成若干连通分量(Group),每组内部互相有依赖、组间无依赖。这样做的目的是让"结果可按组缓存复用"——多个租户如果装的是同一组 App(哈希相同),可以直接复用已经算好的 `AppGroupContainer`,不用重复跑一遍 `MetaBuilderSandbox`。
    - `AppMultiGroupSummary`(继承自 `AppGroupSummary`):代表"一个租户全部 App 的分组结果",`keyHash` 是全部 App 集合的哈希;内部 `groupsHash: Map<组哈希, AppGroupSummary>` 是拆出来的各个组。
    - `AppGroupSummary`:代表"一个组",`keyHash`/`contentHash` 分别是"App 名单哈希"和"App 内容哈希"(用于判断内容是否变化,见 `MultiGroupCompute.computeAppTagsHash` / `computeAppsContentHash`)。
- **`AppGroupContainer`**(`container/AppGroupContainer.java`,L40):一个"组"计算完之后的产物容器,持有该组下所有 App 的 `AppContainer`(含 Model/Property/Service 等 Meta)、`finalModelMap`(最终模型表)、`productLineMap`、`menuMetaMap` 等。
- **`TenantMetaGroupContainer`**(`container/TenantMetaGroupContainer.java`,L13/74):一个"租户"计算完之后的最终产物容器,聚合了该租户下所有涉及组的 `AppGroupContainer`(`groupContainers: Map<组哈希, AppGroupContainer>`)、合并后的 `modelMap`/`productLineMap`/`menuMetaMap`/`appMetaMap`/`uiViewMap`,以及可选的 `workflowAppGroupContainer`(工作流专属容器,惰性/条件创建)。
- **业务沙盒 `BUSSINESS_SANDBOX` / `BUSSINESS`**:`computeMeta(...)` 返回的多个 `AppGroupContainer` 里,标记为这两个 group 名之一的才是"业务容器"(可能还有 workflow 容器等其他产物),三个方法都用 `filter(e -> e.getGroup().equals(BUSSINESS_SANDBOX) || e.getGroup().equals(BUSSINESS)).findFirst()` 挑出业务容器。
- **m2m(多对多关系)**:`MANY_TO_MANY` 类型的 Meta 需要在组间计算阶段(`createMany2ManyPropertyBefore4PrepareBuild`)反向补齐对端模型的多对多属性,因为同一个多对多关系的两端可能分属不同 Group,各自独立计算时看不到对方。
- **ProductLine / MenuMeta / UiView**:租户级"产品线-菜单-视图"三层关系,由 `FrontMetaBuilder.finalBuildProductLine` / `finalBuildMenuMeta`(产品线、菜单的跨组合并与继承处理)与 `ViewMetaBuilder.buildViewMetaFromViews`(视图关系建立)在所有组计算完之后统一收口。

## 3. 三方法详细对比表

| 维度 | `computeMetaWithTenant`(L2788) | `buildMetaWithTenant`(L2911) | `buildMetaWithGroup`(L3123) |
| --- | --- | --- | --- |
| 入参 | `Map<租户ID, List<SaaSAppInfo>>` + `appTagJsonList` | 同左 | `virtualTenant`(String) + `List<SaaSAppInfo>` + `appTagJsonMap` |
| 返回 | `Map<租户ID, TenantMetaGroupContainer>`(多租户) | 同左 | `Map<virtualTenant, TenantMetaGroupContainer>`(单条目) |
| 参数校验 | `checkParams`(含 `checkTenantApps`) | 同左 | `checkParam`(单数,语义类似但独立实现,含 `checkTenantApps`) |
| 已安装 App 的初态获取时机 | **循环外**一次性批量拉取所有租户涉及的 appTag(`getAllOriginalMetasByAppTagList(allAppTagSet)`,L2800) | **循环内**每个租户单独拉取(L2925) | 单租户,直接拉取(L3130) |
| `AppGroupContainer` 复用策略 | `getAppGroupContainerFromBuildOrCache`:先查 `groupAppGroupContainers` 缓存(key=组哈希),命中则复用,否则调 `computeMeta` 计算并写回缓存 | 同左 | **不查缓存**,每个组都直接调用 `computeMeta(groupAppJsonList)` 重新计算(见风险①) |
| 组结果为空时的处理 | `if (!present) { log.warn; continue; }`,正确跳过 | 同左,正确跳过 | `if (!present) { log.warn; put(hash,null); }`(**没有 `continue`**),随后仍执行 `.get()`(见风险②,存在 `NoSuchElementException` 风险) |
| m2m 组间关系补齐 | `createMany2ManyPropertyBefore4PrepareBuild` | 同左 | 同左 |
| ProductLine / Menu 构建 | `FrontMetaBuilder.finalBuildProductLine/finalBuildMenuMeta` | 同左 | 同左 |
| 视图构建 | `ViewMetaBuilder.buildViewMetaFromViews` | 同左 | 同左 |
| 模型重复名校验 `repeatModelCheck` | **无** | **有**(落库前强制校验,重复则抛 `EngineException`,L3045) | **无** |
| 分布式锁 + 落库 `lockedMetaStorage` | **无**(纯计算,不落库) | **有**(`RedissonCache.lock(getSaaSBussLockKey())` 包裹 `metaStorage`) | **有** |
| 分组缓存清理 `MultiGroupCleanTask.runClean()` | 无 | 有 | 有 |
| 构建完成事件 `publishFinalBuildFinished` | 无 | 有 | 有 |
| 摘要事件 `SummaryManager.sendTenant*SummaryEvent` | 无 | 有(App 摘要 + 模型摘要两个事件) | **无** |
| m2m 变更通知 `new MetaM2MComputeUtil().accept(...)` | 无 | 无 | **有**(仅此方法独有,L3227) |
| Workflow 专属容器创建口径 | `appNames.stream().anyMatch(name -> name.contains("workflow"))`(**子串匹配**,L2892-2893) | `appNames.contains("workflow") || appNames.contains("workflow-extend")`(**精确匹配**,L3033) | **完全没有**这段逻辑 |
| 典型调用方 | 需要"预览/试算"结果但不想产生副作用的场景 | `SingleAppLoader`、`AppStoreServiceImpl`(App 市场安装/更新/上下架真正生效路径) | 面向虚拟租户/分组场景的构建入口(调用方需按名称/调用点自行确认) |

> 以上"无/有"均为静态读码结论;由于三者历史上大概率是彼此复制演化而来(大段逻辑逐字相同),差异点很可能并非全部有意为之,建议接手后与原作者确认哪些差异是设计意图、哪些是遗留不一致(详见第 8 节)。

## 4. 逐方法执行步骤详解

### 4.1 `computeMetaWithTenant`(L2788-2901)—— 只算不落库

```plain
1. 日志打印入参摘要(generateString 只打印每个 appJson 的长度,避免整段 JSON 进日志,L2781-2785)
2. checkParams 校验:租户不能为空;每个租户至少一个 appInfo;ADD/UPDATE 状态必须有对应 appJson
3. 【批量预取】收集所有租户涉及的 app.tag 全集 allAppTagSet,一次性从 Redis 批量拉取初态 JSON
   (getAllOriginalMetasByAppTagList 内部用 Redisson RBatch 批量 get,减少网络往返,L2796-2800)
4. 对每个租户 tenantId:
   a. 用 buildUpdatedAppTags 合并出 updatedAppJsonMap:
      ADD/UPDATE → 取本次传入的新 JSON;REMOVED → 从结果里剔除;UNCHANGED → 取 Redis 里的原始 JSON
   b. 找出"未变化(UNCHANGED)且已缓存过 MetaGraph"的 App,直接复用缓存的 MetaGraph(getInstalledBussAppMetaGraph)
      —— 避免对未变化的 App 重复跑一次 MetaGraphComputeUtil.computeMetaGraph
   c. 其余(新增/更新)的 App 现算 MetaGraph,与缓存的合并成 metaGraphMap
   d. MultiGroupCompute.computeGroups 对该租户的 App 集合按依赖关系分组
   e. 对分组结果里的每个组:getAppGroupContainerFromBuildOrCache 查缓存/现算 AppGroupContainer,
      合并进 tenantMetaGroupContainer 的 modelMap/productLineMap/menuMetaMap/appMetaMap,
      同时收集该组内的多对多(m2m) Meta 供组间关系补齐
   f. createMany2ManyPropertyBefore4PrepareBuild 补齐 m2m 关系
   g. FrontMetaBuilder 统一构建 ProductLine、Menu;ViewMetaBuilder 构建视图
   h. 若该租户 App 名称中含 "workflow" 子串(宽松匹配),初始化一个空的 workflowAppGroupContainer
5. 返回 Map<租户ID, TenantMetaGroupContainer>,全程无 Redis 写、无锁、无事件
```

**用途**:适合"先算出来看看结果对不对/会不会冲突"的场景(例如上架前预校验、模拟计算),因为没有副作用,可以反复调用而不影响系统状态。

### 4.2 `buildMetaWithTenant`(L2911-3039)—— 正式生效入口

与 4.1 计算主干完全一致(`updatedAppJsonMap`→分组→组容器合并→m2m→ProductLine/Menu→视图),额外多出的"生效"步骤:

```plain
9.  repeatModelCheck:落库前校验同一租户下是否有同名且无父模型(parents 为空)的模型重复,
    若重复则拼装详细报错信息(涉及哪些已装 App、哪些模型 ID)并抛 EngineException,
    整个租户的构建在此处中断(注意:checkParams 在最外层,repeatModelCheck 是循环体内、
    落库前的"业务级"校验,会导致当前租户构建失败,但不影响 Map 中已处理完的其他租户——
    因为异常会直接向上抛出中断整个方法,尚未处理的租户也不会再执行,需留意这是"部分成功"语义)
10. lockedMetaStorage:对 getSaaSBussLockKey() 加 Redisson 分布式锁后调用 metaStorage 落库,
    finally 里保证解锁(L3232-3247)
11. MultiGroupCleanTask.runClean():清理分组计算过程中产生的临时/过期缓存
12. publishFinalBuildFinished(appInfoList):发布"构建完成"事件(下游可能监听此事件做进一步处理)
13. sendTenantAppSummaryEvent / sendTenantModelSummaryEvent:
    通过 SummaryManager 发送两个摘要事件(tenantId + tenantHash + tenantHashContent),
    用于同步/通知其他关心"该租户 App 摘要"和"该租户模型摘要"的下游模块
14. workflow 容器创建改为精确匹配 appNames.contains("workflow") || appNames.contains("workflow-extend")
    (与 4.1 的子串匹配口径不同,见风险③)
```

**关键风险点**:第 9 步的 `repeatModelCheck` 是在**已经写入内存态 `tenantMetaGroupContainer`、准备落库之前**才做的校验;如果多租户 Map 中排在后面的租户触发了这个异常,排在它前面、已经成功落库的租户不会回滚——这是"循环中逐租户处理,异常会中断循环但不会撤销已完成的租户"的整体语义。

### 4.3 `buildMetaWithGroup`(L3123-3229)—— 虚拟租户/组变体

面向单个 `virtualTenant`(本质是一个"组标识",可以理解为多组场景下的一个虚拟租户单元,而非平台上真实的租户 ID),整体步骤与 4.2 类似,但有三处实质性差异:

1. **不复用 `getAppGroupContainerFromBuildOrCache`,而是内联了几乎一样但更简化的逻辑(L3159-3191)**:对每个组直接 `computeMeta(groupAppJsonList)` 重新计算,不检查 `groupAppGroupContainers` 是否已有缓存结果。
2. `Optional.get()` **没有在"未找到业务组"分支里 `continue`(L3163-3168)**:

```java
Optional<AppGroupContainer> appGroupContainerOptional = appGroupContainers.stream()
    .filter(e -> e.getGroup().equals(BUSSINESS_SANDBOX) || e.getGroup().equals(BUSSINESS)).findFirst();
if (!appGroupContainerOptional.isPresent()) {
    logger.warn("未找到业务组 bussinessGroupContainer");
    groupAppGroupContainers.put(appGroupsSummary.getKeyHash(), null);
}
AppGroupContainer appGroupContainer = appGroupContainerOptional.get(); // <-- 未 continue,Optional 为空时会抛 NoSuchElementException
```

3. **末尾多一步 `new MetaM2MComputeUtil().accept(tenantMetaGroupContainer)`(L3227,注释"通知更新")**,4.1/4.2 都没有这一步;同时**完全没有** `repeatModelCheck`、`SummaryManager` 摘要事件、workflow 容器创建这三段逻辑。

## 5. 关键依赖方法速查

| 方法 | 位置 | 作用 |
| --- | --- | --- |
| `checkParams(tenantsAppInfos, appTagJsonList)` | L3380 | 多租户批量入参校验 |
| `checkParam(group, appTagJsonMap, appInfo)` | L3430 | 单组/单虚拟租户入参校验 |
| `buildUpdatedAppTags(appTagJsonMap, originalMetasMap, appInfo)` | L3489 | 按 `SaaSAppStatus` 合并出"本次应参与计算"的 `app.tag → json` 全集 |
| `getAllOriginalMetasByAppTagList(appTagSet)` | `MetaModelFacade.java:367` | Redisson `RBatch` 批量从 Redis 拉取指定 app.tag 的初态 JSON |
| `getInstalledBussAppMetaGraph(appTags)` | L1936 | 已安装 buss 应用的 `MetaGraph`(Redis 缓存优先) |
| `MultiGroupCompute.computeGroups(tenantsApps)` | `MultiGroupCompute.java:206` | 按依赖关系把每个租户的 App 集合拆分成若干组 |
| `getAppGroupContainerFromBuildOrCache(...)` | L3083 | 组级缓存查找/计算 |
| `computeMeta(appJsonList)` | L458 | 真正触发 `MetaBuilderSandbox.buildMeta()` |
| `createMany2ManyPropertyBefore4PrepareBuild(...)` | L3544 | 组间多对多关系补齐 |
| `repeatModelCheck(container)` | L3045 | 落库前校验同名且无父模型的 Model |
| `lockedMetaStorage(container, tenantHash, summary)` / `metaStorage(...)` | L3232 / L3249 | 加 Redisson 分布式锁后落库 |
| `publishFinalBuildFinished(appInfoList)` | L3097 | 发布"构建完成"引擎事件 |
| `SummaryManager.sendTenantAppSummaryEvent/sendTenantModelSummaryEvent` | `facade/summary/SummaryManager.java` | 通知下游"该租户 App/模型摘要"已更新 |
| `MetaM2MComputeUtil().accept(container)` | `container/partialbuild/MetaM2MComputeUtil.java` | `buildMetaWithGroup` 独有的收尾通知 |

## 6. 关键数据结构

- `TenantMetaGroupContainer`(`container/TenantMetaGroupContainer.java`)
    - `tenantId`(final)
    - `groupContainers: Map<组哈希, AppGroupContainer>` —— 不同租户可能指向同一个 `AppGroupContainer` 实例
    - `workflowAppGroupContainer: volatile AppGroupContainer` —— 惰性/条件创建的工作流专属容器
    - `modelMap` / `productLineMap` / `menuMetaMap` / `appMetaMap` / `uiViewMap` / `tenantModelMap` / `m2mPropertyMetaMap` 等聚合字段
- `AppGroupContainer`(`container/AppGroupContainer.java`)
    - `group`(组名,如 `BUSSINESS_SANDBOX`/`BUSSINESS`/`WORKFLOW_GROUP`)
    - `appContainerMap`、`appDataInfoMap`
    - `finalModelMap`、`productLineMap`、`menuMetaMap`、`distributeModelMap`
- `AppMultiGroupSummary` / `AppGroupSummary`(`facade/summary/`)
    - `keyHash`:App 名单排序去重后 SHA-256
    - `contentHash`:App 内容整体哈希
    - `appsHash`、`metaGraphs`、`groupsHash`(仅 `AppMultiGroupSummary`)
- `SaaSAppInfo`:`app` + `tag` + `saaSAppStatus`,`getAppTag()` 拼成 `app.tag`

## 7. 调用方与使用场景

- `buildMetaWithTenant`:`AppStoreServiceImpl`(App 市场安装/更新/上下架服务实现,多处调用)、`SingleAppLoader`(单机/非分布式加载器)
- `computeMetaWithTenant`:`SaaSDistributedAppLoader`、`SingleAppLoader`
- `buildMetaWithGroup`:本次索引未在生产代码(非测试)中找到明确调用点,仅在 `ContainerFacadeManualTest` 中被 `testBuildVMetaGroup_meta` / `testBuildWithGroup` / `testBuildMetaVirtual_meta`(L573/674/716)覆盖。**待确认项**:`buildMetaWithGroup` 当前是否已在生产链路(如某个 RPC/接口)中被使用,还是仍处于"预留能力,尚未接入调用方"的状态。

## 8. 已知风险 / 待确认事项清单

1. **【中风险】`buildMetaWithGroup` 未做组级计算缓存**:每次都重新 `computeMeta`,理论上可能比另外两个方法更慢。
2. **【高风险,建议优先修复】`buildMetaWithGroup` 中 `Optional.get()` 缺少 `continue`(L3163-3168)**:当某个组找不到业务容器(`BUSSINESS_SANDBOX`/`BUSSINESS`)时,只打了 warn 日志、把 `null` 塞进缓存 map,但紧接着仍执行 `appGroupContainerOptional.get()`,会抛出 `NoSuchElementException` 而不是像另外两个方法一样优雅跳过。
3. **【中风险】三个方法对"是否含 workflow 相关 App"的判断口径不一致**:`computeMetaWithTenant` 子串宽松匹配、`buildMetaWithTenant` 精确匹配、`buildMetaWithGroup` 完全无。建议确认哪种口径是"正确"的业务预期,统一三处实现。
4. **【低中风险】`computeMetaWithTenant` 完全不做 `repeatModelCheck`**:理论上模型重复只会在真正 `buildMetaWithTenant` 时才暴露;但如果有调用方依赖 `computeMetaWithTenant` 的结果做"预检查"来提前给用户报错,目前不会检测出模型重复冲突。
5. **【需确认】`buildMetaWithTenant` 循环中途抛异常的"部分成功"语义**:多租户批量调用时,前面租户已落库成功,后面租户校验失败会中断整个方法且不回滚——如果调用方认为是"全部成功或全部失败"的事务操作,则存在语义不符风险。
6. **【需确认】`checkParams` / `checkParam` 存在大量重复实现**:两者校验逻辑几乎对称,后续可考虑抽取公共校验方法。
7. **【测试覆盖】** `buildUpdatedAppTags`、`getInstalledBussAppMetaGraph`、`lockedMetaStorage`、`getAppGroupContainerFromBuildOrCache` 等关键私有方法"⚠️ no covering tests found"。
8. **【设计问题,非 bug】`buildMetaWithGroup` 独有的 `MetaM2MComputeUtil().accept(...)` 收尾调用**(L3227)在另外两个方法中不存在——如果是 `buildMetaWithGroup` 场景特有的必要步骤,建议在代码注释中补充说明原因。

## 9. 排查 / 验证建议

- **日志关键字**:`computeMetaWithTenant` / `buildMetaWithTenant` / `buildMetaWithGroup` / `lockedMetaStorage` / `unlockedMetaStorage` / `未找到业务组 bussinessGroupContainer`(风险②的警告日志)
- **分布式锁 key**:`MetaKeyFacade.getSaaSBussLockKey()`
- **相关测试**:`sie-snest-engine/src/test/java/com/sie/snest/engine/facade/ContainerFacadeManualTest.java`
    - `testBuildVMetaGroup_meta`(L573)、`testBuildWithGroup`(L674)、`testBuildMetaVirtual_meta`(L716)→ 覆盖 `buildMetaWithGroup`
    - `testComputeMetaGroup_meta`(L546)→ 与租户/分组计算相关
- **快速复现风险②的思路**:构造一批 App JSON,使其在 `computeMeta` 阶段被过滤/解析失败导致返回的 `AppGroupContainer` 列表中不存在 `BUSSINESS_SANDBOX`/`BUSSINESS` 分组,再调用 `buildMetaWithGroup`,预期会抛 `NoSuchElementException`。

## 10. FAQ

**Q: 为什么明明看起来是同一段逻辑的方法,代码却要复制三份而不是抽公共方法?**
A: 从读码结果看,三者主干逻辑高度相似,大概率是历史上"复制一份改一改"演化出来的,目前存在的差异中,部分是有意为之(是否落库/发事件),部分疑似遗留不一致(风险③⑥)。

**Q: `computeMetaWithTenant` 和 `buildMetaWithTenant` 能不能互相替代?**
A: 不能直接替代。`computeMetaWithTenant` 无副作用,重复调用是安全的;`buildMetaWithTenant` 会加锁落库、发事件,重复调用会重复触发下游事件/摘要通知,且有 `repeatModelCheck` 这道额外校验闸门。如果只是想校验/预览结果,应使用前者。

**Q: `buildMetaWithGroup` 里的"虚拟租户"具体是什么?**
A: 从签名(`String virtualTenant`)和构造 `TenantMetaGroupContainer(virtualTenant)`、日志字样"按虚拟租户安装或更新非必装app-激活"来看,它把一个"组"当作一个可独立构建/落库的单元对待,`virtualTenant` 更像是这个组的标识符而非真实租户 ID。

## 11. 参考文件清单(file:line 索引)

| 符号 | 文件:行号 |
| --- | --- |
| `computeMetaWithTenant` | `ContainerFacade.java:2788` |
| `buildMetaWithTenant` | `ContainerFacade.java:2911` |
| `buildMetaWithGroup` | `ContainerFacade.java:3123` |
| `repeatModelCheck` | `ContainerFacade.java:3045` |
| `getAppGroupContainerFromBuildOrCache` | `ContainerFacade.java:3083` |
| `checkParams` / `checkParam` | `ContainerFacade.java:3380` / `:3430` |
| `buildUpdatedAppTags` | `ContainerFacade.java:3489` |
| `getInstalledBussAppMetaGraph` | `ContainerFacade.java:1936` |
| `lockedMetaStorage` / `metaStorage` | `ContainerFacade.java:3232` / `:3249` |
| `computeMeta` | `ContainerFacade.java:458` |
| `createMany2ManyPropertyBefore4PrepareBuild` | `ContainerFacade.java:3544` |
| `getAllOriginalMetasByAppTagList` | `MetaModelFacade.java:367` |
| `MultiGroupCompute.computeGroups` | `MultiGroupCompute.java:206` |
| `TenantMetaGroupContainer` / `AppGroupContainer` | `container/TenantMetaGroupContainer.java:13` / `AppGroupContainer.java:40` |
| `AppMultiGroupSummary` / `AppGroupSummary` | `AppMultiGroupSummary.java:17` / `AppGroupSummary.java:19` |
| 集成测试 | `ContainerFacadeManualTest.java`(L513/546/573/674/716 等) |

## 附:交接确认清单(接手人签署前逐项确认)

- [ ] 已理解三个方法的核心差异(是否落库/发事件/校验)
- [ ] 已与原作者/业务方确认第 8 节风险②(`buildMetaWithGroup` 的 `Optional.get()`)是否为已知问题
- [ ] 已确认第 8 节风险③(workflow 判断口径不一致)的业务预期
- [ ] 已确认 `buildMetaWithGroup` 当前生产环境的实际调用方(或确认其暂未接入)
- [ ] 已跑通 `ContainerFacadeManualTest` 中与本文档相关的用例
