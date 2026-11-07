---
name: partial-build-redis-batch-update
description: Partial Build 流程中"写临时 key + Lua 原子生效"的 Redis 批量更新机制,包括 EMPTY_STR_TO_DELETE 约定、LUA_SCRIPT_RENAME 合并语义、diffMeta 九件套编排、metaPartialStorage 落库时序。理解 buildMetaPartialSDK 和 buildMetaPartial(type, metaJson) 大部分分支落库一致性的基础。
---

# 部分计算(Partial Build)批量更新 Redis 机制

**模块**:`sie-snest-engine`
**核心方法/类**:
- `ContainerFacade.metaPartialStorage`(`ContainerFacade.java:1484`)—— SDK 分支的落库总入口
- `ContainerFacade.diffMeta` / `ContainerFacade.DiffMetaDto`(`ContainerFacade.java:2391 / 2434`)—— 计算"哪些 key 需要写临时值、哪些 key 需要删除"
- `ContainerFacade.diffProductLine/diffApp/diffModel/diffViewMeta/diffResourceMeta/diffMenuMeta/diffManyToMany/diffSummary/diffUiViewMeta`(`ContainerFacade.java:2452-2685`)—— 九个具体元数据类型的 diff 实现
- `RedissonCache.setRawJsonStringCompressed` / `RedissonCache.batchUpdateRedis(Set<String>, String)` / `LUA_SCRIPT_RENAME`(`RedissonCache.java`)—— 写临时 key + 原子生效的底层机制
- `SummaryManager`(`facade/summary/SummaryManager.java`)—— 摘要更新与事件通知

## 1. 核心问题:为什么需要"临时 key + 原子生效"

部分计算(如 `buildMetaPartialSDK`)一次会触碰多种元数据(App 容器、模型、视图、资源、菜单、m2m 关系、摘要……),分布在几十甚至上百个 Redis key/Hash field 上。如果逐个 key 直接 `SET`/`HSET`,一旦中途失败(网络抖动、进程重启),会留下"部分新、部分旧"的不一致状态,且没有回滚手段。

本仓库的解决方案:**所有新值先写到"临时 key"(正式 key 加上一个本次操作唯一的 `suffix`),全部写完之后,用一段 Redis Lua 脚本一次性把临时 key `RENAME` 成正式 key(或合并进正式 Hash)**。Lua 脚本在 Redis 单线程模型下天然原子执行,因此"批量替换生效"这一步本身不会出现部分成功的中间态;真正可能失败的是"写临时 key"阶段(可以安全重试,因为正式 key 完全未被触碰)和"执行 Lua"阶段本身(不成功则正式 key 保持原状,`suffix` 相关的临时 key 会残留,需要人工/定时清理,见风险)。

## 2. 基础写入原语

- `RedissonCache.setRawJsonStringCompressed(String key, String value, Long expired)`:写单个 String key(LZ4 压缩,`RBucket`)。
- `RedissonCache.setRawJsonStringCompressed(Map<String,String> data, Long expired)`:批量写多个 String key(`RBuckets.set`,`expired=-1` 时走批量不过期路径;否则退化为 `RBatch` 逐个 `setAsync`)。
- `RedissonCache.hSetAll(String key, Map<String,String> value, Long expired)`:整体 `putAll` 进某个 Hash key(不是新建/覆盖整个 Hash,是"合并写入"这些 field)。
- 上述方法在部分计算流程里,**写的都是"正式 key + suffix"这样的临时 key**,不是直接写正式 key。

## 3. `EMPTY_STR_TO_DELETE`:Hash 内单字段"标记删除"的约定

`ContainerFacade.EMPTY_STR_TO_DELETE = ""`(`ContainerFacade.java:72`,就是空字符串常量)。

约定:当某条元数据(Hash 里的一个 field)需要被删除,但整个 Hash key 本身还要保留(其他 field 仍然有效),就在"临时 key"对应的 Map 里,把这个 field 的 value 设成 `""`,而不是调用 `HDEL`。真正的删除动作,延后到第 4 节的 Lua 脚本执行阶段统一处理(脚本会扫描临时 Hash 的所有 field,凡是 value 为空字符串的,在合并进正式 Hash 后执行 `HDEL`)。

**为什么不直接 `HDEL`**:因为"写临时 key"阶段本身要保持"完全不触碰正式 key"的隔离性——如果中途失败需要重试,不能让重试逻辑分不清"这次要删的 field 之前是否已经被删过"。用"空字符串占位 + 最终统一处理",把删除也变成一次幂等的写操作。

**使用该约定的方法**:`diffMenuMeta`、`diffUiViewMeta`、`diffResourceMeta`、`diffViewMeta`(均在"新旧 `MetaGraph` 对比,找出旧有新无的字段"或"整 App 卸载"场景下使用),以及单条元数据交接文档里 `buildMetaPartialCustomAuth`/`buildMetaPartialFunctionAuth` 的删除分支。

## 4. 原子生效:`batchUpdateRedis(Set<String> keys, String suffix)` 与 `LUA_SCRIPT_RENAME`

```java
public static void batchUpdateRedis(Set<String> keys, String suffix) {
    // 1. 把本次要生效的正式 key 集合(keys.stream().map(e -> e + suffix))记入 BATCH_UPDATE_KEYS 列表
    //    (用于故障恢复:如果进程在 Lua 执行前后崩溃,可以扫描这个列表知道"上次做到哪一步")
    // 2. inParams = [正式key1, 正式key2, ..., suffix],调用 RScript.eval(LUA_SCRIPT_RENAME, ...)
    // 3. 执行成功后清空 BATCH_UPDATE_KEYS
}
```

`LUA_SCRIPT_RENAME`(`RedissonCache.java:77-137`)逐个正式 key 处理,`bak_key = 正式key + suffix`(即"临时 key"):

| 情形 | 处理 |
| --- | --- |
| 临时 key 不存在、正式 key 存在、正式 key 是 Hash 类型 | **保留正式 key 不动**("map(hash)类型只做更新不整体删除,备份不存在时保留原key"——即"这次操作没有涉及这个 Hash,不应该清空它") |
| 临时 key 不存在、正式 key 存在、正式 key 不是 Hash | 删除正式 key(说明这次操作的意图是"移除这个 key",如整 App 卸载) |
| 临时 key 不存在、正式 key 也不存在 | 什么都不做 |
| 临时 key 存在,类型是 String | 直接 `RENAME` 覆盖正式 key(整体替换) |
| 临时 key 存在,类型是 Hash,**正式 key 不存在** | 先清理临时 Hash 里 value 为空字符串的 field(`HDEL`),再把清理后的临时 Hash `RENAME` 成正式 key |
| 临时 key 存在,类型是 Hash,**正式 key 已存在** | 遍历临时 Hash 每个 field:value 为空字符串则对**正式 key** `HDEL` 该 field;否则对正式 key `HSET` 该 field/value(即"逐字段合并"而非整体替换),处理完后删除临时 key |
| 临时 key 存在,但类型既不是 String 也不是 Hash | 直接删除临时 key(兜底分支) |

**关键结论**:
- **String 类型的 key(如单个模型 `ModelMeta`、产品线整表、模型摘要)是"整体覆盖"语义**——写临时 key 时必须带上完整内容,不能只写"增量"。
- **Hash 类型的 key(如菜单/视图/资源)是"按 field 增量合并"语义**——临时 key 只需要包含"本次变化的 field",未涉及的 field 会保持不变;配合 `EMPTY_STR_TO_DELETE` 约定,可以在一次合并里同时表达"新增/修改某些 field"和"删除某些 field"。
- 还有一个更老的 `LUA_SCRIPT`(`RedissonCache.java:56-75`,逻辑更简单:临时 key 存在就整体 `RENAME`,否则整体 `DEL` 正式 key)——**部分计算流程用的是 `LUA_SCRIPT_RENAME`(新版),不是这个**,两者并存是历史演进产物,接手后新增落库逻辑应使用 `batchUpdateRedis(Set, String)`(对应 `LUA_SCRIPT_RENAME`)而不是老脚本对应的入口。

`clearBatchUpdateRedisKeys()`:读出 `BATCH_UPDATE_KEYS` 列表并批量删除对应 key——用于清理"因为某次批量更新中途失败而残留下来的临时 key",是手动/定时的补偿手段,不在正常流程里自动触发。

## 5. `diffMeta` 编排的九个 diff 方法

`diffMeta(keySuffix, appGroupContainerList, oldMetaGraphList, newMetaGraphList, uninstallApps)` 依次调用九个方法(顺序固定,`ContainerFacade.java:2438-2446`),统一通过 `DiffMetaDto.addNewKey(key)`(返回 `key + ":" + keySuffix"`,同时把 `<临时key, 正式key>` 记入 `tmpKeys`,并从 `deletedKeys` 里移除该正式 key——即"这个 key 本次是写而不是删")和 `DiffMetaDto.deleteKey(key)`(记入 `deletedKeys`,最终会被并入 `dealKeys` 交给 `batchUpdateRedis`,对应上表"临时 key 不存在、正式 key 存在"的删除分支)两个方法协作登记。

| 方法 | 处理对象 | 存储结构 | 核心逻辑 |
| --- | --- | --- | --- |
| `diffProductLine` | 产品线全表 | String(整表) | 读现有 Redis 产品线 Map,遍历本次容器产出的每个 `ProductLineMeta`:若已存在同名产品线,`menuMetaIds` **取并集合并**(不是覆盖,`mergeProductLineMenuIds`);否则直接放入。整个 Map 写一个临时 key。 |
| `diffApp` | 每个 App 的 `AppContainer` | String(`getBussAppContainerKey(appTag)`,按 App) | 遍历所有容器的 `appContainerMap`,逐个序列化写临时 key;`uninstallApps` 对应的 key 直接 `deleteKey`(整体删除,无 field 级删除概念)。 |
| `diffModel` | 每个模型 `ModelMeta` | String(`getBussModelKey(name)`,按模型) | 卸载 App 对应旧图里的全部模型 `deleteKey`;正常情况遍历 `finalModelMap` 逐个模型写临时 key;再对比新旧图的模型名集合,"旧有新无"(未卸载但模型被移除,如某模型改名/删除)的也 `deleteKey`。 |
| `diffViewMeta` | 功能权限 `ViewMeta` | Hash(`getBussAppViewKey(apptag)`,按 App) | 卸载 App 整 key `deleteKey`;正常情况把 `AppContainer.getMetas()` 里 `metaType==VIEW` 的对象整体写入 field;新旧图 diff 出"旧有新无"的 field 置 `EMPTY_STR_TO_DELETE`;整个 Map 通过 `hSetAll` 写临时 key(**Hash 类型走"整体 hSetAll 到临时 key",不是"只写变化的几个 field"**——但因为 Lua 脚本合并阶段是按 field 处理,效果上不影响未出现在这个 Map 里的、Redis 正式 key 上已有的其它 field)。 |
| `diffResourceMeta` | 自定义权限 `ResourceMeta` | Hash(`getBussAppResourceKey(apptag)`,按 App) | 逻辑与 `diffViewMeta` 对称:卸载整 key 删除,正常情况写入 `AppContainer.getResourceMetaMap()` 全部内容 + 旧有新无的置空删除。 |
| `diffMenuMeta` | 菜单 `MenuMeta` | Hash(全局 `getBussMenuKey()` + 按 App `getBussAppMenuKey(apptag)`,**两层同时维护**) | 卸载 App:全局 Map 里该 App 名下的菜单 id(用 `MenuMeta.menuMetaId` 重新拼装)置空删除,同时按 App 的 Hash **整 key** `deleteKey`;正常情况:全局 Map 汇总所有容器 `menuMetaMap`;每个 `AppContainer` 单独再收集一份自己的 `menuMetaMap` 写入按 App 的 Hash,同样做"旧有新无"置空删除。 |
| `diffManyToMany` | 多对多关系 | 由 `MetaStorage.putMany2ManyMeta` 内部决定(未展开) | 委托 `MetaStorage.putMany2ManyMeta(container, appNameTag -> dto.addNewKey(getBussMany2ManyKey(appNameTag)))`,把"生成临时 key"的能力通过函数式接口传入,具体写入内容由 `MetaStorage` 内部实现。 |
| `diffSummary` | 应用摘要 + 模型摘要 | String(`getBussSummaryAppKey()` / `getBussSummaryModelKey()`,均整表) | 拆成 `diffSummaryApp`(读全量 App 摘要 Map,移除卸载 App,`putAll` 本次新增/更新的,整体写回)+ `diffSummaryModel`(先算出应移除的模型名集合——卸载 App 的全部模型,或新旧图 diff 出的被删模型——再从 `finalModelMap` 里"取消移除"仍存在的模型,最终从 Map 里 `remove` 该模型名后整体写回,用 `SnestLZ4CompressCodeC` 压缩编解码)。 |
| `diffUiViewMeta` | UI 视图 `UiViewMeta` | Hash(全局 `getBussUiViewKey()` + 类型索引 `getBussUiViewByTypeKey()`) | 汇总所有容器 `uiViewMap` 写入主表,同时维护一份 `modelName_type → viewId` 的反向索引 `uiTypeKeyMap`;卸载/被移除的 view 在主表里置空删除(**类型索引表没有对应的清理逻辑**,代码里两处都标了 `// todo type-view 关系`,见风险)。 |

**规律总结**:
- 凡是"一个 App 对应一整个 key"的资源(`AppContainer`、`ResourceMeta`/`ViewMeta` 按 App 分桶的 Hash 整体、`Menu` 按 App 分桶的 Hash 整体),**卸载该 App 时是删 key**。
- 凡是"全局共享一个 Hash、内部按 metaId 分 field"的资源(全局菜单表、全局 UiView 表),**局部消失(非卸载,如某条记录被移除)时是把该 field 置空字符串**,交给 Lua 脚本在合并阶段 `HDEL`。
- 产品线、模型摘要走"整表 String,读-改-写"模式,天然是覆盖语义,不需要 field 级删除标记。

## 6. `metaPartialStorage`:SDK 分支的完整落库编排

`ContainerFacade.metaPartialStorage(afterRelatedMetaGraph, beforeAllMetaGraph, appGroupContainers)`(`ContainerFacade.java:1484-1511`)是 `buildMetaPartialSDK` 落库阶段的入口,做三件事:

1. `diffMeta`:调用第 5 节的九件套,拿到 `DiffMetaDto`,把 `tmpKeys`(临时 key → 正式 key 的映射,只需要正式 key 集合)和 `deletedKeys` 合并进 `dealKeys`。
2. **`MetaGraph` 快照回写**(独立于 `diffMeta`,`diffMeta` 不涉及 `MetaGraph` 本身的存储):
    - `appendMetaGraph(afterRelatedMetaGraph, appGroupContainers)`:把本次计算过程中"顺带产生"的元数据 id(`UiView`/`View`/`ResourceMeta`)回填进对应 App 的 `MetaGraph` 对象(`graph.getUiViews()/getViews()/getResourceMetas()` 这几个 Map,只存 key,value 置 `null`——纯粹是"存在性索引",不存内容,供下次 `diffMenuMeta`/`diffUiViewMeta`/`diffResourceMeta` 里"新旧图对比找差异"时使用)。
    - 遍历 `afterMetaGraph`,按 App 类型(`APP_TYPE_BUSS`/`APP_TYPE_API`)生成 `MetaModelFacade.getAppMetaGraphKey(nameTag, type)`,**直接**(不经过 `DiffMetaDto.addNewKey`,但会手动拼 `":" + suffix` 且把生成的 key 加入 `dealKeys`)调用 `RedissonCache.setRawJsonStringCompressed(key + ":" + suffix, json, -1L)` 写临时 key。
3. **统一生效**:`RedissonCache.batchUpdateRedis(dealKeys, ":" + suffix)` 触发第 4 节的 Lua 脚本,把本次涉及的所有 key(业务元数据 + `MetaGraph` 快照)一次性原子生效;随后调用 `SummaryManager.getInstance().sendAppSummaryEvent()` + `sendModelSummaryEvent()` 发布两个通知事件。

`buildMetaPartialOrchestration` / `buildMetaPartialWorkflow` **不调用 `metaPartialStorage`**:这两条分支只更新模型摘要(`SummaryManager.updateModelSummary(map, suffix)`,见下)和各自模型上的服务列表,落库方式更简化,直接自己拼 `changeKeySet` 调 `batchUpdateRedis`,不涉及 `diffMeta` 九件套(详见 `container-facade-partial-build-overview.md` 第 3、5 节)。

## 7. `SummaryManager` 在批量更新中的角色

- `updateModelSummary(Map<String,ModelMeta> map, String suffix)`(区别于不带 `suffix` 的重载):读全量模型摘要、`putAll` 合并本次变更,转 JSON(`SnestLZ4CompressCodeC` 压缩)**写到 `getBussSummaryModelKey() + suffix` 这个临时 key**——不发事件、不解锁之外没有其它动作,是专为"批量部分更新"设计的重载,配合外层 `batchUpdateRedis(keys, suffix)` 统一生效。整个过程加 `changeBussModelSummary` 分布式锁防止并发写冲突。不带 `suffix` 的重载会直接写正式 key 并立即发 `MODEL_SUMMARY_TYPE` 事件,是非批量场景(如 `updateModelSummary(map)`,未在部分计算路径中使用,本文档不展开)的接口。
- `sendAppSummaryEvent()` / `sendModelSummaryEvent()`:只发引擎事件总线消息(`APP_SUMMARY_TYPE`/`MODEL_SUMMARY_TYPE`),通知下游(网关/前端等)"应用摘要"或"模型摘要"已更新、应重新拉取。不做任何数据写入,是 `batchUpdateRedis` 完成原子生效**之后**的收尾通知步骤——三条分支(SDK/Workflow/Orchestration)都在各自落库流程末尾调用这两个方法。

## 8. 端到端时序小结(以 SDK 分支为例)

```plain
buildMetaPartialSDK
  └─ computeMeta(...)  →  appGroupContainers
  └─ metaPartialStorage(afterRelatedMetaGraph, beforeAllMetaGraph, appGroupContainers)
        ├─ diffMeta(suffix, appGroupContainers, before, after, uninstallApps={})
        │     ├─ diffProductLine   → 临时 key: buss:product:suffix                     (整表覆盖)
        │     ├─ diffApp           → 临时 key: buss:app:{tag}:ctr:suffix × N            (整 key 覆盖)
        │     ├─ diffModel         → 临时 key: buss:model:{name}:suffix × N             (整 key 覆盖)
        │     ├─ diffViewMeta      → 临时 key: buss:app:{tag}:view:suffix × N           (Hash 合并+置空删除)
        │     ├─ diffResourceMeta  → 临时 key: buss:app:{tag}:resource:suffix × N        (Hash 合并+置空删除)
        │     ├─ diffMenuMeta      → 临时 key: buss:menu:suffix, buss:app:{tag}:menu:suffix × N (Hash 合并+置空删除)
        │     ├─ diffManyToMany    → 临时 key: buss:app:{tag}:m2m:suffix × N            (委托 MetaStorage)
        │     ├─ diffSummary       → 临时 key: buss:summ:app:suffix, buss:summ:model:suffix     (整表覆盖)
        │     └─ diffUiViewMeta    → 临时 key: buss:uiview:suffix, buss:uiviewByType:suffix     (Hash 合并+置空删除)
        │   汇总 dealKeys = tmpKeys(正式key集合) ∪ deletedKeys
        ├─ appendMetaGraph(...)  回填 UiView/View/ResourceMeta 索引进 MetaGraph 对象
        ├─ 遍历 afterMetaGraph → 写 MetaGraph 快照临时 key: {api|buss}graph:{tag}:suffix × N,加入 dealKeys
        └─ RedissonCache.batchUpdateRedis(dealKeys, ":" + suffix)
              → LUA_SCRIPT_RENAME 原子地把上面所有临时 key RENAME/合并进正式 key
        └─ SummaryManager.sendAppSummaryEvent() + sendModelSummaryEvent()
```

## 9. 已知风险 / 待确认事项

1. **【中风险,明确标注的 TODO】`diffUiViewMeta` 的类型索引表 `getBussUiViewByTypeKey()` 没有清理逻辑**:源码里两处 `// todo type-view 关系` 注释(`ContainerFacade.java:2621`、`2631`)表明,当某个 `UiViewMeta` 被卸载/移除时,主表 `getBussUiViewKey()` 会正确置空删除,但 `modelName_type → viewId` 的反向索引表**不会同步清理**,可能导致该索引长期指向一个已经不存在的 `viewId`,读取方如果直接信任索引表拿到的 `viewId` 去查主表,需要自行判空容错。建议接手后确认下游读取逻辑是否已经处理了这种"索引存在但目标已删除"的情况。
2. **【需确认】`diffApp` / `diffModel` / `diffProductLine` / `diffSummary` 均是"整 key 级别"的覆盖或删除,没有 Hash 级别的 field 精细化**:与"按 field 增量合并"的菜单/视图/资源/UI视图四类相比,App 容器/模型/产品线/摘要都是"整个 key 一次性覆盖",这意味着并发的两次部分计算如果恰好都涉及同一个模型/同一个 App 容器,后完成 `batchUpdateRedis` 的一方会完全覆盖先完成一方的内容(没有合并语义)。产品线在 `diffProductLine` 里对 `menuMetaIds` 做了特殊的并集合并处理,但 `displayName`/`icon`/`description` 等字段仍是新对象直接覆盖旧对象(`mergeProductLineMenuIds` 只处理 `menuMetaIds`)。需要确认这种"整体覆盖"语义在当前系统的实际并发场景下是否会出现问题。
3. **【需确认】Lua 脚本执行失败后的残留清理依赖 `BATCH_UPDATE_KEYS` + `clearBatchUpdateRedisKeys`,但后者未见被自动定时调用**:本次检索未发现有定时任务或启动流程调用 `clearBatchUpdateRedisKeys()`,如果 `batchUpdateRedis` 在 Lua 执行前进程崩溃,临时 key 会残留在 Redis 里且不会自动被清理(不影响正确性,因为正式 key 未被触碰,但长期积累可能造成 Redis 内存膨胀)。建议确认是否有独立的定时清理机制。
4. **【低风险,代码可读性】`LUA_SCRIPT`(老版本)与 `LUA_SCRIPT_RENAME`(新版本)并存**:两者语义有明显差异,如果后续有代码不小心使用了老版本对应的调用入口,可能引入"Hash 被整体覆盖而非合并"的回归问题,建议接手后确认 `LUA_SCRIPT` 是否已完全废弃、能否安全移除。

## 10. 排查 / 验证建议

- **日志关键字**:`"开始批量更新Redis操作"` / `"批量更新Redis操作失败"`(`batchUpdateRedis(Map,...)` 系列重载的日志,注意本文档主用的 `batchUpdateRedis(Set<String>, String)` 重载本身只有失败时的 `logger.error`,没有开始/成功的 info 日志);`diffMeta tmpKeys` / `diffMeta deletedKeys`(`ContainerFacade.diffMeta` 末尾 `logger.debug`,排查"为什么某个 key 没有被更新/删除"时的第一手信息,注意默认是 `debug` 级别)
- **Redis 层排查**:如果怀疑某次部分计算"卡在中途",可以用 `RedissonCache.exists`/`scanKeys` 检查是否存在大量 `xxx:数字suffix` 形式的残留 key(`IdGenerator.nextId()` 生成的 suffix 通常是纯数字或雪花 id),配合 `BATCH_UPDATE_KEYS`(`iidp-eng:batch-update:keys`)列表确认上一次批量更新是否完整执行
- **验证 Hash 合并 vs 覆盖语义**:可以直接读 `RedissonCache.LUA_SCRIPT_RENAME` 源码(`RedissonCache.java:77-137`)逐分支核对,或者在测试环境构造"正式 key 已有 3 个 field,临时 key 只写 1 个 field"的场景,验证生效后正式 key 是否仍保留另外 2 个未涉及的 field(预期:保留,因为是合并语义)

## 11. 参考文件索引(file:line)

| 符号 | 文件:行号 |
| --- | --- |
| `ContainerFacade.EMPTY_STR_TO_DELETE` | `ContainerFacade.java:72` |
| `ContainerFacade.metaPartialStorage` | `ContainerFacade.java:1484` |
| `ContainerFacade.DiffMetaDto` | `ContainerFacade.java:2391` |
| `ContainerFacade.diffMeta` | `ContainerFacade.java:2434` |
| `diffApp` / `diffModel` / `diffProductLine` | `ContainerFacade.java:2452` / `:2463` / `:2489` |
| `diffSummary` / `diffSummaryApp` / `diffSummaryModel` | `ContainerFacade.java:2518` / `:2523` / `:2540` |
| `diffManyToMany` / `diffMenuMeta` / `diffUiViewMeta` | `ContainerFacade.java:2569` / `:2575` / `:2608` |
| `diffResourceMeta` / `diffViewMeta` | `ContainerFacade.java:2639` / `:2663` |
| `ContainerFacade.appendMetaGraph` | `ContainerFacade.java:2363` |
| `RedissonCache.LUA_SCRIPT` / `LUA_SCRIPT_RENAME` | `RedissonCache.java:56` / `:77` |
| `RedissonCache.batchUpdateRedis(Set<String>, String)` | `RedissonCache.java:978` |
| `RedissonCache.setRawJsonStringCompressed`(单个/批量) | `RedissonCache.java:922` / `:937` |
| `RedissonCache.hSetAll` | `RedissonCache.java:614` |
| `RedissonCache.clearBatchUpdateRedisKeys` / `BATCH_UPDATE_KEYS` | `RedissonCache.java:952` / `:53` |
| `MetaGraphComputeUtil.computeCanUnInstall` | `MetaGraphComputeUtil.java:616`(供 Orchestration 分支使用,非本机制核心,另见 `container-facade-partial-build-overview.md`) |
| `SummaryManager.updateModelSummary(Map, String)` | `SummaryManager.java:292` |
| `SummaryManager.sendAppSummaryEvent` / `sendModelSummaryEvent` | `SummaryManager.java:469` / `:329` |
