---
name: container-facade-m2m-view-menu-productline
description: 多对多关系(m2m)组间补齐、产品线(ProductLine)跨组合并、菜单(Menu)继承+兼容+成环校验、视图(View)构建四个步骤的详细实现,是 computeMetaWithTenant / buildMetaWithTenant / buildMetaWithGroup 三个租户维度计算方法共享的"组间计算收口"阶段。
---

# 多对多关系(m2m) / 视图 / 菜单 / 产品线 终态计算逻辑

**模块**:`sie-snest-engine`

**关联文档**:本篇是 `container-facade-tenant-meta.md`(`ContainerFacade` 多租户元模型计算核心交接文档)的补充篇,专门展开该文档第 4 节"逐方法执行步骤"中,三个核心方法(`computeMetaWithTenant` / `buildMetaWithTenant` / `buildMetaWithGroup`)共享的"组间计算收口"阶段——即 **m2m 关系补齐 → 产品线(ProductLine)合并 → 菜单(Menu)合并 → 视图(View)构建** 这四步。阅读本篇前建议先读主文档第 1-4 节建立整体认知。

**涉及方法定位**(均在三个核心方法体内、按固定顺序调用,位置以 `buildMetaWithTenant` 为例):
- m2m:`ContainerFacade.createMany2ManyPropertyBefore4PrepareBuild`(L2988 调用处 / L3544 定义处)
- 产品线:`FrontMetaBuilder.finalBuildProductLine(TenantMetaGroupContainer)`(L2993 调用处 / `FrontMetaBuilder.java:58`)
- 菜单:`FrontMetaBuilder.finalBuildMenuMeta(TenantMetaGroupContainer)`(L2994 调用处 / `FrontMetaBuilder.java:97`)
- 视图:`ViewMetaBuilder.buildViewMetaFromViews(AppGroupContainer, List<JSONObject>)`(L3003 调用处 / `ViewMetaBuilder.java:84`)
- (`buildMetaWithGroup` 独有)跨虚拟租户 m2m 补齐:`MetaM2MComputeUtil.accept(TenantMetaGroupContainer)`(L3227 调用处 / `MetaM2MComputeUtil.java:33`)

## 1. 这四步在整体流程中的位置

三个核心方法在"每个组算完、汇总到 `tenantMetaGroupContainer`"之后,都会按**固定顺序**执行同一段收口逻辑(以 `buildMetaWithTenant` L2987-3005 为例):

```java
createMany2ManyPropertyBefore4PrepareBuild(m2mMetas, tenantMetaGroupContainer);      // ① m2m 组间关系补齐

FrontMetaBuilder frontMetaBuilder = new FrontMetaBuilder();
frontMetaBuilder.finalBuildProductLine(tenantMetaGroupContainer);                    // ② 产品线跨组合并
frontMetaBuilder.finalBuildMenuMeta(tenantMetaGroupContainer);                       // ③ 菜单继承 + 兼容旧版ID + 成环校验

tempAppGroupContainer.setProductLineMap(tenantMetaGroupContainer.getProductLineMap());
tempAppGroupContainer.setMenuMetaMap(tenantMetaGroupContainer.getMenuMetaMap());

List<JSONObject> jsonObjects = updatedAppJsonMap.values().stream()...;
ViewMetaBuilder.buildViewMetaFromViews(tempAppGroupContainer, jsonObjects);          // ④ 视图构建(依赖③已完成)

tenantMetaGroupContainer.setUiViewMap(tempAppGroupContainer.getUiViewMap());
```

**顺序不可颠倒**,原因见各节详述,核心结论先列在这里:
- ①必须在②③之前:m2m 补齐会往 `modelMap`/`tenantModelMap` 里新增/替换模型,②③④都依赖这些模型的最终状态。
- ②必须在③之前:②会把每个产品线的 `menuMetaIds` **清空**成空列表,等待③重新收集填充;顺序反了会导致产品线关联的菜单ID被清空后无法恢复(见风险③)。
- ③必须在④之前:`ViewMetaBuilder.buildViewMetaFromViews` 的类注释明确写着"这一步需要先构建好 MenuMeta"(`ViewMetaBuilder.java:79-83`),因为视图构建要遍历 `menuMetaMap`,通过每个菜单的 `model+view` 组合去定位/生成对应的视图。
- ④ 操作的是 `tempAppGroupContainer`(一个临时构造的 `BUSSINESS_SANDBOX` 容器)而不是 `tenantMetaGroupContainer` 本身——因为 `ViewMetaBuilder.buildViewMetaFromViews` 方法签名要求 `AppGroupContainer`,构建完再把结果 `getUiViewMap()` 拷贝回 `tenantMetaGroupContainer`。

三个核心方法在这四步上的实现**完全一致**(逐字相同),差异只在于 `buildMetaWithGroup` 在最后额外多调用了一次 `MetaM2MComputeUtil().accept(...)`(第 4 节展开)。

## 2. m2m(多对多关系)处理详解

### 2.1 背景:为什么需要"组间补齐"

一个多对多(`MANY_TO_MANY`)关系的 Meta 定义在某个 App 里,`point.src` 是发起端模型(通常是模型 ID),`point.dst` 是对端模型(通常是模型名)。由于 App 会先被 `MultiGroupCompute.computeGroups` 按依赖关系拆成若干"组(Group)"分别计算(见主文档第 2 节),**关系两端的模型可能分属不同组**,各组独立计算(`computeMeta`/`MetaBuilderSandbox`)时互相看不到对方,因此多对多关系的"反向属性"(对端模型上应该自动生成的 inverse 属性)无法在组内完成,必须等所有组都算完、汇总到 `tenantMetaGroupContainer` 之后再统一补齐。

### 2.2 `createMany2ManyPropertyBefore4PrepareBuild`(三方法共用,`ContainerFacade.java:3544`)

输入 `m2mMetas`:三个核心方法在遍历每个组的 `AppGroupContainer` 时,都会把该组内所有 `MetaType == MANY_TO_MANY` 的 Meta 收集进这个列表(见主文档 L2855-2861 / L2976-2982 等处的 `m2mMetas.addAll(...)`)。

逐步骤:

1. **过滤**:非 `MANY_TO_MANY` 类型直接跳过。
2. **归属校验**:如果 `tenantMetaGroupContainer.getAppMetaMap()` 里找不到这个 meta 所属的 `appNameTag`,说明该 App 本次并未真正参与计算(例如状态是 `REMOVED` 被过滤掉),直接跳过。
3. **解析两端模型**:
    - `src`:`meta.getPoint().getSrc()` → 调用 `getModelFromFinalAndBaseThenReturnFinal(tenantMetaGroupContainer, src)` 解析源模型。
    - `dst`:`meta.getPoint().getDst()`(可能带 `app.model` 前缀,取 `.` 后半段)→ 直接从 `tenantMetaGroupContainer.getModelMap()` 查。
    - **任意一端解析不到就跳过**(`oriRelModel == null || oriModel == null`)——这种情况通常是"对端模型在当前租户内确实不存在",或者(对 `buildMetaWithGroup` 而言)"对端模型在别的虚拟租户/别的组里",后者由第 4 节的 `MetaM2MComputeUtil` 兜底处理。
4. **复制模型再改**:`oriModel.copyTo(model)` / `oriRelModel.copyTo(relModel)`——**不直接修改缓存里的原始模型对象**,避免污染其他持有同一模型引用的地方。
5. **默认值推导**(四个属性,均为"meta 上没配就按规则推导"):
    - `inverseName`:取 `meta.getAttr(INVERSE_NAME)`,为空则用 `relModel.getName().toLowerCase()`。
    - `tableName`:取 `meta.getAttr(TABLE_NAME)`,为空则用 `model.getTableName() + "_" + relModel.getTableName()` 并**回写**到 `meta.getAttrs()`(下次再遇到同一个 meta 就不用重新推导)。
    - `inverseJoinColumn`:为空则等于 `inverseName`。
    - `columnName`:为空则等于 `meta.getName()`。
6. **双向补齐属性**:
    - 若 `model` 上还没有这个属性名,调用 `PropertyBuilder.createManyToMany(model, name, relModel, columnName, inverseJoinColumn, inverseName, relModel.getTableName(), meta, **null**)` 创建正向属性。
    - 若 `relModel` 上还没有 `inverseName` 这个属性,同样调用 `createManyToMany` 反向创建对端属性。
    - **注意最后一个参数(`AppContainer ctn`)传的是 `null`**——对比 `AppLifecycleProcess.java:1862` 里非 SaaS 路径下的同名私有方法,那边会传入真实的 `EngineContainer.getAppContainer(...)` 并因此调用 `ctn.register(prop)` 把属性注册进 `AppContainer`;**本方法(SaaS 租户路径)不会做这个注册动作**,新属性只挂在 `model`/`relModel` 对象本身上(见风险①)。
7. **写回容器**:把 `model`/`relModel` 都 `put` 进 `tenantMetaGroupContainer` 的 `tenantModelMap` 和 `modelMap`。
8. **异常隔离**:整段用 `try/catch` 包住,单个 meta 出错只打 `error` 日志(`"安装应用扩展给对方增加many2man属性异常,app:{}, name:{}"`),**不会中断其余 m2m meta 的处理**。

### 2.3 `PropertyBuilder.createManyToMany`(`model/builders/PropertyBuilder.java:439`)

真正创建 `PropertyMeta` 的地方,核心逻辑:
- **虚拟连接判断**:`point.src` 如果不等于当前 `model.getId()`,说明这次调用是在给"关系的另一端"补属性(即调用方是从对端视角在建反向属性),标记 `virtual = true`。
- **虚拟属性可被真实属性替换**:如果模型上已经存在一个"虚拟"的同名属性(`RELATE_VIRTUAL=true`),且这次要创建的不是虚拟的(`!virtual`),则 `replace = true`,用真实属性覆盖虚拟属性——这是为了解决"组间补齐顺序不确定,可能先创建了占位的虚拟属性,之后才轮到真实一端"的时序问题。
- 设置好 `RELATE_MODEL` / `RELATE_MODEL_APP` / `INVERSE_NAME` / `INVERSE_JOIN_COLUMN` / `COLUMN_NAME` / `RELATE_TABLE` 等属性后 `model.addProperty(prop)`;`ctn != null` 时才 `ctn.register(prop)`。
- 若属性带 `SELECTION_TYPE`(多选类型的原生 many2many),额外调用 `buildSelection` 补充选择器相关配置(`selectionMethod`/`selectionModel`/`selectionFields`/`widget`/`multiple`/`cascader`/`filter`/`orderBy`)。

## 3. 产品线(ProductLine)处理详解

`FrontMetaBuilder.finalBuildProductLine(TenantMetaGroupContainer)`(`FrontMetaBuilder.java:58-76`):

```java
for (AppGroupContainer appGroupContainer : tenantMetaGroupContainer.getGroupContainers().values()) {
    for (ProductLineMeta pLMeta : appGroupContainer.getProductLineMap().values()) {
        if (tenantMetaGroupContainer.getProductLineMap().containsKey(pLMeta.getName())) {
            // 已存在同名产品线:用已存在的(basePLMeta)补全空字段
            ProductLineMeta extendMeta = extendAttributes(pLMeta, basePLMeta);
            tenantMetaGroupContainer.getProductLineMap().put(extendMeta.getName(), extendMeta);
        } else {
            // 首次出现:自己扩展自己(等价于深拷贝一份)
            ProductLineMeta extendMeta = extendAttributes(pLMeta, pLMeta);
            tenantMetaGroupContainer.getProductLineMap().put(pLMeta.getName(), extendMeta);
        }
        extendMeta.setMenuMetaIds(new ArrayList<>());  // 清空,交给下一步 Menu 阶段重新填充
    }
}
```

- **输入来源**:每个 `AppGroupContainer` 的 `productLineMap` 早在更早的"组内计算"阶段(`MetaBuilderSandbox.buildMeta()`)就已经产出——本方法做的是**跨组合并**,不是从零构建。
- **合并规则 `extendAttributes(pLMeta, basePLMeta)`**(L78-88):`copyTo` 一份新对象,若 `displayName`/`icon` 为空,则回填 `basePLMeta` 的对应值——即"后出现的产品线定义,用先出现/已存在的产品线定义补全展示信息"。
- `menuMetaIds` **清空动作**:这是与下一步 Menu 阶段强耦合的关键点——见风险③。
- 另有一个重载 `finalBuildProductLine(AppGroupContainer)`(组内版本,L38-56)只在 `BUSSINESS_SANDBOX` 组或单机模式(`WebMode.SINGLE`)下生效,租户级版本没有这个限制,一定会执行跨组合并。

## 4. 菜单(Menu)处理详解

`FrontMetaBuilder.finalBuildMenuMeta(TenantMetaGroupContainer)`(`FrontMetaBuilder.java:97-102`)= `inheritMenu` + `wrapperMenus` + `GraphUtil.checkMenuDependency`。

### 4.1 `inheritMenu`(`FrontMetaBuilder.java:305-388`,租户级版本)

1. **按权重排序** App(`sortAppContainers` → `MultiGroupCompute.sortAppContainerByWeight`),保证"谁先谁后处理菜单"的顺序稳定可预期。
2. 遍历每个 App 的每个 `MenuMeta`:
    - `disableAppGroupMenuInSingleMode`:非多租户单机模式下,把内置的"应用市场分组菜单"(`meta_app_store_group_menu`)强制禁用。
    - `getInheritId`:判断该菜单是否是"扩展模式"(`mode == EXTENSION` 且配置了 `inherit_ids`/`InheritIds`)。是则暂存进 `inheritMenus`(key 是被扩展的目标 `relId`),否则视为普通菜单放入 `menus`,并且若该 App 挂了 `ProductLineMeta`,同时登记进 `productMenus`(`产品线名 → menuId → MenuMeta`)。
    - 顺带收集 `MenuMeta.MenuConnectionMeta`(一种通过 `point.src/dst` 表达"菜单间父子连接关系"的特殊 Meta)到 `menuPointMap`。
3. 把 `productMenus` 汇总的菜单 ID 灌回对应产品线的 `menuMetaIds`(**衔接第 3 步 ProductLine 阶段清空的列表**——这就是"清空-回填"配对的落地位置)。
4. **扩展菜单的"接管"机制**:对每个"扩展菜单",按 `relId` 找到被扩展的原始菜单 `inheritMenu`;创建一个新 `MenuMeta`,内容拷贝自扩展菜单,但 `id` / `appName` / `appId` / `relId` / `parentPath` / `parentId` / `childrenIds` / `viewMetaIds` 全部替换成被扩展菜单的值——也就是说扩展菜单不是"新增一个子菜单",而是**顶替/覆盖原菜单在树里的位置**,同时保留自己的其余属性(如 `view` 为空时才继承原菜单的 `view`)。
5. **菜单连接关系落地**:遍历 `menuPointMap`,对每对 `src→dst`:若 `src` 菜单还没有 `parentId`,设为 `dst` 的 `id`;若 `dst` 菜单还没有 `childrenIds`,初始化并把 `src` 加进去。**这段逻辑只在租户级版本存在,`AppGroupContainer` 版本(组内版)没有**——说明"跨 App 的菜单连接关系"只能等所有组都汇总到租户级之后才能建立。

### 4.2 `wrapperMenus`(兼容旧版菜单 ID 格式,L144-172)

- 旧版本(多租户 App 注入格式)里,菜单 ID 可能不是标准的元模型 ID。这里为每个菜单重新计算一个标准 ID:`MenuMeta.menuMetaId(appName, version, relId)`,通过 `MapFunction` 建立"旧 ID → 新 ID"映射。
- 同步更新:产品线 `menuMetaIds` 列表里的旧 ID、菜单自身的 `id`/`parentId`/`childrenIds`(`parentPath` 同步设为新 `parentId`)、以及 `originId` 记录原始 `relId`。
- 最终用新 ID 重新构建一份 `menuMetaMap` 覆盖回容器。

### 4.3 `GraphUtil.checkMenuDependency`

对最终 `menuMetaMap` 做依赖/成环校验,本次交接**未展开其内部实现**,如需排查"菜单相互引用成环"一类的报错,需接手后单独跳转到 `GraphUtil` 确认。

## 5. 视图(View)处理详解

`ViewMetaBuilder.buildViewMetaFromViews(AppGroupContainer appGroupContainer, List<JSONObject> appJsons)`(`container/view/ViewMetaBuilder.java:84-134`)。

**前置条件**:`appGroupContainer.getFinalModelMap()` 不能为空,否则直接 `log.error` 并 return(不抛异常,静默放弃视图计算)。

### 5.1 `initView` 阶段(`ViewMetaBuilder.java:304+`)

1. **解析 App 自带视图**:遍历每个 `appJson` 的 `Attributes.Views`,逐个转成 `UiViewMeta` 存入 `dto.keyMap`(`convertToUiView`,单个转换失败通过 `handleError` 隔离,不影响其他视图)。
2. **按模型生成默认视图**:对 `finalModelMap` 里每个非抽象模型,如果没有对应视图,调用 `buildDefaultViews` 自动生成 `grid`/`search`/`form` 三种默认视图(`DEFAULT_VIEWS` 常量)。其中有个特殊分支:若当前是 `BUSSINESS_SANDBOX` 组,且模型属于"平台继承模型"(`getPlatformInheritModelNames()`),会去 `EngineContainer.getBussinessAppGroupContainer()`(全局业务容器)里找模型定义,而不是当前局部容器——即"沙盒计算时借用平台已有的模型定义"。
3. **视图扩展合并** `calViewExtend`:处理 `inherit_ids` 表达的视图继承/扩展链。
4. **清理中间态**:把已经被合并进扩展链的"中间视图"从 `keyMap` 中移除,只保留合并后的终态视图。
5. **注入权限信息** `LoadViewAuthInfoInjector.injectAuthToViewBody`。

### 5.2 主流程

1. 调 `initView` 拿到 `dto.keyMap`(全部 `UiViewMeta`,key 是 viewId)。
2. `dto.modelMetaMap = appGroupContainer.getFinalModelMap()`。
3. 收集所有 App 里"手写"的 `ViewMeta`(`customViewMetaMap`——区别于上面 JSON 配置驱动的 `UiViewMeta`,这是前端/App 直接定义的视图元模型)。
4. **核心步骤:先遍历所有 `MenuMeta`**,对每个菜单调用 `buildViewMetaByMenu`:通过菜单的 `model + view类型`,找到对应 `UiViewMeta` 并展开构建具体的 `ViewMeta`(`grid`/`search`/`form`/`tree` 四种类型分别有专门的 `build*ByJsonBody`,会产出带默认增删改查服务引用的 `ViewMeta`,例如 `grid` 会挂 `REF_GRID_SEARCH/COUNT/CREATE/DELETE/UPDATE` 等默认服务)。**这一步就是为什么必须先构建好 Menu**——没有 `menuMetaMap` 就无法按"菜单→模型→视图"的路径去驱动视图构建。
5. 处理完所有菜单后,清空 `dto.menuMeta` 上下文(避免最后一个菜单的上下文误关联到后续视图),再对 `keyMap` 里"没有被任何菜单引用到的孤立视图"补算一遍(`buildViewMetaByView`)。
6. 把算出来的所有 `ViewMeta` 挂回各自 App 的 `AppContainer.getMetas()`。
7. `appGroupContainer.setUiViewMap(dto.keyMap)` 保存视图配置终态。

### 5.3 为什么操作的是 `tempAppGroupContainer` 而不是 `tenantMetaGroupContainer`

三个核心方法在调用视图构建前,都会先构造一个临时的 `AppGroupContainer tempAppGroupContainer = new AppGroupContainer(BUSSINESS_SANDBOX)`,并在遍历每个组时不断把 `finalModelMap`/`appContainerMap` 累加进去(主文档 L2848-2850 等处)。这是因为 `ViewMetaBuilder.buildViewMetaFromViews` 方法签名要求的是单个 `AppGroupContainer`,而 `tenantMetaGroupContainer` 聚合的是"多个组",两者数据结构不兼容,所以需要先拼出一个"汇总视角"的临时容器,构建完再把结果(`uiViewMap`)拷贝回真正的 `tenantMetaGroupContainer`。

## 6. `buildMetaWithGroup` 独有:跨虚拟租户 m2m 补齐(`MetaM2MComputeUtil`)

**仅 `buildMetaWithGroup` 在收尾处(L3227)额外调用**:`new MetaM2MComputeUtil().accept(tenantMetaGroupContainer)`,另外两个方法都没有这一步。类注释明确写着"计算跨虚拟租户的 ManyToMany"(`MetaM2MComputeUtil.java:24-29`)。

### 6.1 `accept`(L33-40)

```java
List<BaseMeta> baseMetas = findM2MMeta(tenantMetaGroupContainer);  // 找出"未配对"的m2m
if (!baseMetas.isEmpty()) {
    Map<String, ModelSummary> modelInfoMap = searchAll();          // 跨虚拟租户拉取模型摘要索引
    buildM2MMeta(tenantMetaGroupContainer, baseMetas, modelInfoMap); // 尝试补齐
}
```

### 6.2 `findM2MMeta`(L48-68)

遍历当前租户所有组、所有 App 的 `MANY_TO_MANY` Meta:src 端能在租户内解析到模型(`resolveModelInTenant`),但 dst 端在 `tenantMetaGroupContainer.getModelMap()` 里找不到——即第 2 节 `createMany2ManyPropertyBefore4PrepareBuild` 因为"对端模型不在当前租户/虚拟租户内"而被跳过的那些关系,在这里被重新收集,尝试"跨虚拟租户"补齐。

### 6.3 `searchAll`(L75-104)——跨虚拟租户模型索引怎么来的

1. 调用 `meta_app_store_group` 服务的 `getAllVirtualTenants` 方法拿到系统里所有虚拟租户 ID。
2. 从 Redis(`MetaKeyFacade.getSaaSBussTenantHashKey()`)读出"虚拟租户 → 分组哈希"的映射(即每个虚拟租户当前对应的 App 组合哈希,与主文档提到的 `AppMultiGroupSummary.keyHash` 是同一体系)。
3. 去重后,批量读取每个分组哈希对应的**模型摘要**(`MetaKeyFacade.getSaaSBussGroupsSummaryModelKey`,`RedissonCache.getRawJsonStringCompressed` 批量读),反序列化成 `Map<模型标识, ModelSummary>` 并全部汇总。

这样就构建出了一份"全局(跨所有虚拟租户/分组)模型摘要索引",用于查找当前租户内找不到、但可能存在于其他虚拟租户/分组里的目标模型。

### 6.4 `buildM2MMeta`(L114-196)——尝试补齐

对每个未配对的 m2m Meta:
1. 先按 `resolveModelInTenant` 在当前租户内再确认一次 `src` 模型。
2. `dst` 模型解析顺序:当前租户 `modelMap` → 全局模型摘要索引按 name 查 → 按 id 查。任意一步命中即用 `ModelSummary.convert2ModelMeta()` 转成 `ModelMeta`。
3. 两端都解析到后,`copyTo` 复制一份(同样避免污染原始对象),推导 `inverseName`/`tableName`/`inverseJoinColumn`/`columnName`(逻辑与第 2.2 节一致;`tableName` 若两端表名都拿不到会直接抛 `MetaException`,比第 2.2 节更严格——那边推导不出来只是留空,这里是硬性要求)。
4. **只补齐了 `src` 端的正向属性**(`PropertyBuilder.createManyToMany(src, ..., null)`),写回 `tenantMetaGroupContainer.getModelMap()`。**没有像第 2.2 节那样再对 `dst` 端做一次反向 `inverse` 属性补齐**——即跨虚拟租户补出来的这段关系目前是单向的(详见风险②)。

## 7. 三方法在这四步上的差异总表

| 步骤 | `computeMetaWithTenant` | `buildMetaWithTenant` | `buildMetaWithGroup` |
| --- | --- | --- | --- |
| ① m2m 组内/组间补齐(`createMany2ManyPropertyBefore4PrepareBuild`) | 有 | 有 | 有 |
| ② 产品线合并(`finalBuildProductLine`) | 有 | 有 | 有 |
| ③ 菜单继承/兼容/成环校验(`finalBuildMenuMeta`) | 有 | 有 | 有 |
| ④ 视图构建(`buildViewMetaFromViews`) | 有 | 有 | 有 |
| ⑤ 跨虚拟租户 m2m 补齐(`MetaM2MComputeUtil`) | 无 | 无 | **有(独有)** |

①-④ 四步在三个方法里代码逐字相同,是共享的"计算收口"主干;真正的行为差异集中在这四步**之外**的落库/加锁/事件/校验部分(详见主文档第 3 节对比表),以及 `buildMetaWithGroup` 独有的第⑤步。

## 8. 已知风险 / 待确认事项

1. **【需确认】SaaS 租户路径下新建的 m2m 属性不会注册进 `AppContainer`**:对比 `AppLifecycleProcess.java:1862` 非 SaaS 路径的同名方法会传真实 `ctn` 并注册。如果下游有代码依赖"从 `AppContainer` 里能查到这个 m2m 属性",在 SaaS 租户路径下可能查不到,需要确认这是有意的设计差异还是遗漏。
2. **【需确认,中风险】`MetaM2MComputeUtil` 跨虚拟租户补齐的 m2m 关系可能是单向的**:`buildM2MMeta`(第 6.4 节)只对 `src` 端调用了 `createManyToMany`,没有像组内补齐逻辑那样再对 `dst` 端补一次 `inverse` 反向属性。如果业务上要求这类跨虚拟租户的多对多关系也能双向访问,这里存在缺口。
3. **【高风险,隐式顺序依赖】ProductLine 阶段"清空 `menuMetaIds`" 与 Menu 阶段"回填 `menuMetaIds`" 是强耦合的隐式契约**:`finalBuildProductLine` 必须先于 `finalBuildMenuMeta` 执行,且两者的调用顺序目前**只靠三个核心方法里手写的固定调用顺序保证,没有任何断言或注释以外的约束**。若后续重构时不慎调换顺序(例如把 ProductLine 移到 Menu 之后),会导致产品线的菜单 ID 被清空后再也没有机会被重新填充,属于"运行不报错、结果悄悄错误"的沉默故障。建议后续给这两个方法加上顺序性断言或至少补充更醒目的代码注释。
4. **【中风险,同类隐式依赖】View 依赖 Menu 先构建好**:`ViewMetaBuilder.buildViewMetaFromViews` 的类注释已说明这一约束,但同样没有运行时校验——如果调用顺序被打乱,视图构建时 `menuMetaMap` 为空或不完整,只会导致"部分视图没有关联上菜单",不会抛异常,排查成本较高。
5. **【待确认】`GraphUtil.checkMenuDependency` 与 `calViewExtend` 内部实现本次未展开**:如果后续需要排查"菜单成环"或"视图继承链异常"的问题,需要接手后单独深入这两个方法。
6. **【待确认】`MetaM2MComputeUtil.searchAll` 依赖 `meta_app_store_group` 服务的 `getAllVirtualTenants` 动态方法调用**(`storeGroupRs.tryCall("getAllVirtualTenants")`),属于运行时反射式调用,如果该服务的方法签名/返回类型发生变化,这里只会静默拿到空集合,不会报错。

## 9. 排查建议

- **m2m 单条 meta 出错日志**:`"安装应用扩展给对方增加many2man属性异常,app:{}, name:{}"`(`createMany2ManyPropertyBefore4PrepareBuild` 内 catch 块)
- **视图计算耗时日志**:`TimeCounter` 打的 `"计算视图元模型"` / `"计算视图元模型结束"`,可用于定位视图构建阶段的性能问题
- **视图构建失败但不中断**:`ViewMetaBuilder.handleError` 默认只是 `log.warn`,除非配置 `iidp.throw.exp.when.build.view.meta.fail=true` 才会真正抛异常中断——排查"某个视图明明配置了但终态里没有"的问题时,优先查 warn 级别日志而不是等异常
- **跨虚拟租户 m2m 排查**:关注 Redis key `MetaKeyFacade.getSaaSBussTenantHashKey()`(虚拟租户→分组哈希)和 `MetaKeyFacade.getSaaSBussGroupsSummaryModelKey(hash)`(分组模型摘要),如果 `buildMetaWithGroup` 跨租户 m2m 补不齐,先确认这两个 key 的数据是否符合预期

## 10. 参考文件索引(file:line)

| 符号 | 文件:行号 |
| --- | --- |
| `createMany2ManyPropertyBefore4PrepareBuild` | `ContainerFacade.java:3544` |
| `PropertyBuilder.createManyToMany` | `PropertyBuilder.java:439` |
| `FrontMetaBuilder.finalBuildProductLine(TenantMetaGroupContainer)` | `FrontMetaBuilder.java:58` |
| `FrontMetaBuilder.extendAttributes` | `FrontMetaBuilder.java:78` |
| `FrontMetaBuilder.finalBuildMenuMeta(TenantMetaGroupContainer)` | `FrontMetaBuilder.java:98` |
| `FrontMetaBuilder.inheritMenu(TenantMetaGroupContainer)` | `FrontMetaBuilder.java:305` |
| `FrontMetaBuilder.wrapperMenus(TenantMetaGroupContainer)` | `FrontMetaBuilder.java:144` |
| `ViewMetaBuilder.buildViewMetaFromViews` | `ViewMetaBuilder.java:84` |
| `ViewMetaBuilder.initView` | `ViewMetaBuilder.java:304` |
| `MetaM2MComputeUtil.accept` | `MetaM2MComputeUtil.java:33` |
| `MetaM2MComputeUtil.findM2MMeta` | `MetaM2MComputeUtil.java:48` |
| `MetaM2MComputeUtil.searchAll` | `MetaM2MComputeUtil.java:75` |
| `MetaM2MComputeUtil.buildM2MMeta` | `MetaM2MComputeUtil.java:114` |
| 三方法调用处(m2m→产品线→菜单→视图收口段) | `ContainerFacade.java:2865-2884`(`computeMetaWithTenant`)/ `:2987-3005`(`buildMetaWithTenant`)/ `:3195-3210`(`buildMetaWithGroup`) |
