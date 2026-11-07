---
name: meta-model-view-productline-menu-auth
description: 元模型四件套(ProductLineMeta/MenuMeta/UiViewMeta/ViewMeta/ResourceMeta)的字段结构、存储方式、id 规则、转换器、关联关系。理解"视图"和"功能权限"两个易混概念的区别。覆盖 buildMetaPartial(type, metaJson) 系列方法所操作的实体。
---

# 元模型结构——视图 / 产品线 / 菜单 / 自定义权限

**模块**:`sie-snest-engine`
**涉及类**:
- `com.sie.snest.engine.model.ProductLineMeta`(产品线)
- `com.sie.snest.engine.model.MenuMeta`(菜单,含内部类 `MenuMeta.MenuConnectionMeta`)
- `com.sie.snest.engine.model.UiViewMeta`(视图 / UI 视图,**方法注释里"初态:视图"指的就是它**)
- `com.sie.snest.engine.model.view.ViewMeta`(功能权限对应的"视图"元模型,命名相近但用途不同,见第 3 节辨析)
- `com.sie.snest.engine.model.ResourceMeta`(自定义权限 CustomAuth)
- `com.sie.snest.engine.model.BaseMeta`(以上五者的公共基类)
- `com.sie.snest.engine.model.converters.{ResourceConverter, ViewConverter, MetaConverter}`(JSON → Meta 转换器)

## 1. 公共基类 `BaseMeta`

四个实体类均继承 `BaseMeta`,共享以下核心字段(字段名 → 含义):

| 字段 | 含义 |
| --- | --- |
| `id` | 元模型全局唯一 id,各子类有各自的 id 拼装规则(见下文各节) |
| `name` | 英文名/标识 |
| `displayName` | 展示名(中文名) |
| `description` | 描述信息 |
| `metaType` | 元模型类型标识(如 `Menu`/`ProductLine`/`View`/`Resource`),供 `MetaConverter.get(key)` 路由到对应转换器 |
| `relId` | 关联 id / 原始 id(不同实体语义略有差异,如 `UiViewMeta` 里 `relId` 就设为自身 `id`) |
| `parentPath` | 所属父级路径(通常是所属 App 的 id) |
| `point` | `src`/`dst`(外加 `ref`)连接关系,仅"ref"或"连接类"元模型使用,如 `MenuMeta.MenuConnectionMeta` |
| `appId` / `appName` / `version` | 所属 App 的 id / 名称 / 版本(`version` 与业务上说的 `tag` 是同一个概念) |
| `attrs` | 原始 JSON 里除公共字段外剩余的属性 Map(`Map<String,Object>`) |
| `sequence` | 排序号 |
| `getAppNameTag()` | **计算属性**(非持久化字段):`getAppName() + "." + (getVersion()为空则取"master")`,即业务里常说的 `app.tag` |

**转换入口**:`MetaConverter.get(metaType)` 是一张静态路由表(`APP_PRODUCT_LINE→ProductLineConverter`、`MENU→MenuConverter`、`VIEW→ViewConverter`、`RESOURCE→ResourceConverter` 等),`convert(JSONObject json)` 统一先调用 `loadAttrs(meta, json)`:
- 输入 `json` 必须是"标准元模型节点"格式:`{"id":..., "metaType":..., "parentPath":..., "relId":..., "points":{...}, "Attributes":{...}}`
- `loadAttrs` 把 `id`/`metaType`/`parentPath`/`relId`/`name`(从 `Attributes` 弹出)/`point` 这几个公共字段灌进 `meta`,剩余 `Attributes` 内容作为 `attrs` 返回,交给各子类 `Converter` 继续弹出私有字段
- **重要**:`buildMetaPartialCustomAuth`/`buildMetaPartialFunctionAuth` 里,Redis Hash 的 field 名(`metaId`,由调用方在外层 JSON 直接指定)与 `convert()` 产出的 `ResourceMeta`/`ViewMeta` 对象内部的 `id` 字段(来自内层 `json.id`)是**两个独立的值**,语义上应当一致,但代码层面并未做强制校验/断言,需要调用方(前端)自行保证一致性。

## 2. 产品线 `ProductLineMeta`

**额外字段**:
- `icon`:图标
- `menuMetaIds: List<String>`:挂在该产品线下的菜单 id 列表(**由菜单侧反向维护**,产品线自身不感知具体挂了哪些菜单,是"结果"而非"输入")
- `sequence` 有默认值 `DEFAULT_SEQ = 999.0`(`setSequence(null)` 时自动兜底为该值,公共基类 `BaseMeta.DEFAULT_SEQ` 是 `1.0`,产品线覆写了默认值)

**结构特点**:扁平,无父子层级(不像 Menu 有 `parentId`/`childrenIds`)。

**存储方式**:**全量 Map 存单个 String key**(`MetaKeyFacade.getBussProductKey()` → `{env:}iidp-eng:buss:product`),不是 Hash——即"所有产品线"整体序列化成一个 JSON 字符串存一个 key。这与 Menu/View/Resource(均为 Hash,按条目分 field)的存储方式不同,直接决定了:
- 产品线更新必须"整读-整改-整写",无法像 Hash 那样只改一个字段而不影响其他记录
- `buildMetaRemoved(type, ...)` 里产品线**不支持删除**

**关联关系**:产品线与菜单是"一对多"关系,但关联信息落在 `menuMetaIds` 这一侧,菜单本身不直接持有"我属于哪个产品线"的字段(在 `MenuMeta` 类定义里未见 `productLineId` 之类字段),推断产品线-菜单的挂载关系是在"租户维度批量构建"阶段(`FrontMetaBuilder.finalBuildMenuMeta`)通过遍历菜单所属 App 的 `ProductLineMeta` 反向收集出来的,而不是菜单元数据自带。

## 3. 视图:两个名字相近但用途不同的类

这是本主题里最容易混淆的一组概念,务必先分清楚:

|  | `UiViewMeta`(`model` 包) | `ViewMeta`(`model.view` 包) |
| --- | --- | --- |
| 对应 `buildMetaPartial` 的 `type` | `UiView` / `ViewSeed` | `FunctionAuth` |
| 定位 | **页面/UI 视图定义本身**——一个模型的一个 `key` 对应一整个页面视图(`body`/`originalBody` 是完整的前端视图 JSON 描述) | **功能/操作块级别的权限元模型**——细粒度到某个 CRUD 操作或服务引用,是"注入的功能权限" |
| 方法注释里的定位 | "初态"(要联动改回 App 原始 JSON) | "终态"(只改 Redis 最终元数据,与产品线/菜单同级) |
| 是否有父子/继承 | 有:`inheritIds`(`isExtentView()` 判断是否为扩展视图) | 有:`getParentPath()` 表达"继承自哪个功能权限",新增时会继承父级 `menuIds` |
| 是否直接关联菜单 | 无直接字段 | 有:`menuIds: Set<String>` |
| 存储 Hash | 全局:`getBussUiViewKey()` | 按 App 分桶:`getBussAppViewKey(appTag)` |

### 3.1 `UiViewMeta`

**关键字段**:
- `key`:视图在模型下的唯一标识
- `modelName`:所属模型
- `body` / `originalBody`:视图的 JSON 定义(`body` 可能是经过继承合并后的最终结构,`originalBody` 保留原始定义,`resetBody()` 可还原)
- `type`:视图类型
- `appName` / `appTag`:所属 App(注意这两个是 `UiViewMeta` 自己声明的字段,**遮蔽**了 `BaseMeta` 的 `appName`/`version`,`getAppName()`/`setAppName()` 被重写)
- `inheritIds`:继承自哪个(些)视图,`isExtentView()` 据此判断"扩展模式"
- `mode`:构建模式
- `authInfoMap: Map<String, JSONObject>`:权限信息注入结果
- `buildFromModel`:是否由模型自动生成(区别于 App JSON 里显式配置的视图)

**id 规则**:`uiViewMetaId(appName, appTag, modelName, key)` → `appName + "." + modelName + "." + key + "@UiView:" + appTag`;构造时 `relId` 直接设为自身 `id`。

**删除联动**:删除一个 `UiViewMeta` 会:
1. 从其所属 App 的**原始 JSON**(初态,`Attributes.Views`)里移除对应条目(按 `key`)
2. 级联删除该 App 下所有 `viewId` 等于该 `UiViewMeta.id` 的 `ViewMeta`(即由它派生出的功能权限视图一并清理)

### 3.2 `ViewMeta`(功能权限 / 视图权限元模型)

**关键字段**:
- `viewId` / `defaultViewId`:`getViewId()` 优先返回 `viewId`,为空则回退 `defaultViewId`
- `type`(`ViewMetaType`:`GRID`/`FORM`/`SEARCH`/`TREE` 等)
- `code`:附加编码(`buildViewMetaId` 的重载里可带 `code` 做进一步区分)
- `propModel`/`propSearch`/`propCount`/`propCreate`/`propUpdate`/`propDelete`/`propService`:对应到具体的服务方法名,是这个视图/功能块实际调用的后端服务标识
- `refServiceIds` / `serviceIds`:关联的服务 id 集合
- `menuIds: Set<String>`:该功能权限挂载在哪些菜单下(**新增时若能找到父级 `ViewMeta`,会直接继承父级的 `menuIds`**)

**id 规则**:`buildViewMetaId(appName, appTag, modelName, viewId, viewMetaType[, code])` → `appName.modelName.viewId.{type对应的auth标识}[:code]:appTag`。

`getRefServiceMetaId(service)`:按 `type` 分支(GRID/FORM/SEARCH/TREE 走 propSearch/propCount/propCreate/propUpdate/propDelete 四选一,其余类型走 `propService`)解析出实际服务名,再拼成 `RefMeta.refServiceId(...)` 格式的服务 id——用于判断"某个前端请求调用的服务,是否是这个视图声明的引用服务之一",是权限校验链路上的关键方法。

**Converter**:`ViewConverter.convert` 只从 JSON 里灌入 `type`/`code`/`sequence`(继承 `loadAttrs` 的公共字段),**不设置** `menuIds`/`propXxx` 等——这些字段是在 `buildMetaPartialFunctionAuth` 业务逻辑里手动补充的,不是转换器职责。

## 4. 菜单 `MenuMeta`

**关键字段**:
- `parentId` / `childrenIds: List<String>`:父子菜单关系
- `viewMetaIds: Set<String>`:该菜单关联的功能权限视图集合,元素格式是 `appNameTag + "." + viewMeta.getId()`(跨对象引用,不是直接持有对象)
- `modelName`:菜单目标模型
- `view` / `click` / `url` / `css` / `filter`:菜单行为/展现相关的原始配置
- `active`:是否启用
- `isDelete`:软删除标记(与 Redis 层面的"物理删除某个 Hash field"是两回事,`isDelete` 更像是"App JSON 里声明的删除意图")
- `originId`:兼容旧版菜单 id 格式时记录的原始 `relId`
- 内部类 `MenuConnectionMeta extends BaseMeta`:**空壳类**,只是复用 `BaseMeta.point{src,dst}` 表达"菜单 A 应该挂到菜单 B 下面"这条连接关系,本身不落库为正式菜单记录,只在计算过程中被扫描读取(`ContainerFacade.dealMenuParentAndChildren` 遍历各 `AppContainer` 里的这类 Meta,收集 `point.src→dst` 反推父子关系)。

**id 规则**:`menuMetaId(appName, appTag, key)` → `appName + "." + key + "@Menu:" + appTag`(`key` 通常是业务侧的菜单唯一标识,即前端传入的 `json.id`)。`getKeyFromMetaId()` 可从 `id` 反解出 `key`。

**关联方法 `linkViewMeta(ViewMeta)`**:把 `viewMeta.getAppNameTag() + "." + viewMeta.getId()` 加入自身 `viewMetaIds`——这是 `buildMetaPartialFunctionAuth` 新增功能权限视图时,"反向登记到菜单"的落地方法。

**存储方式**:全局单一 Hash——`MetaKeyFacade.getBussMenuKey()` → `{env:}iidp-eng:buss:menu`,field = `metaId`,value = `MenuMeta` JSON(**不区分 App**,所有 App 的菜单存在同一个 Hash 里)。另有一个按 App 维度的 `getBussAppMenuKey(apptag)`(`...:buss:app:{apptag}:menu`),在 `diffMenuMeta` 里用于"整 App 卸载时按 apptag 整 key 删除"的场景,与全局 Hash 是两套并存的索引。

## 5. 自定义权限 `ResourceMeta`

**关键字段**:
- `ResourceType`(注意大写 `R` 开头,字段名不规范但 getter/setter 是标准的 `getResourceType`/`setResourceType`):资源类型
- `key`:资源标识
- `data: Map<String, Object>`:具体的权限资源配置内容(JSON 反序列化而来,结构由业务侧自定义,本类不做进一步建模)

**Converter**:`ResourceConverter.convert` 除公共字段外,只从 `attrs` 弹出 `resourceType`/`data`/`key`/`sequence` 四个字段,是纯 DTO 转换,无跨对象关联逻辑(不像 `MenuMeta`/`ViewMeta` 那样牵扯父子/菜单挂载)。

**存储方式**:按 App 分桶 Hash——`MetaKeyFacade.getBussAppResourceKey(apptag)` → `{env:}iidp-eng:buss:app:{apptag}:resource`,field = `metaId`。

**与 `ViewMeta`(功能权限)的定位区别**:CustomAuth(`ResourceMeta`)是"你能不能访问某个自定义资源/操作"的通用权限配置,字段是开放的 `data` Map,不感知菜单;FunctionAuth(`ViewMeta`)是"某个具体的 CRUD/服务功能"级别的权限,天然要回答"挂在哪个菜单下",因此多了 `menuIds` 与菜单双向登记逻辑。两者是同一产品概念("权限")下并列的两种粒度,代码上完全独立(不同的类、不同的转换器、不同的 Redis key 前缀)。

## 6. Redis Key 一览(本文档涉及的四类元模型)

| 元模型 | Key 方法 | Key 模式 | Redis 结构 | 是否按 App 分桶 |
| --- | --- | --- | --- | --- |
| ProductLine | `getBussProductKey()` | `{env:}iidp-eng:buss:product` | String(整表) | 否(全局唯一) |
| Menu(全局) | `getBussMenuKey()` | `{env:}iidp-eng:buss:menu` | Hash | 否(全局唯一,field=metaId) |
| Menu(按 App,diff 用) | `getBussAppMenuKey(apptag)` | `{env:}iidp-eng:buss:app:{apptag}:menu` | Hash | 是 |
| UiView(全局) | `getBussUiViewKey()` | `{env:}iidp-eng:buss:uiview` | Hash | 否 |
| UiView 按类型索引 | `getBussUiViewByTypeKey()` | `{env:}iidp-eng:buss:uiviewByType` | Hash(`modelName_type → viewId` 索引) | 否 |
| ViewMeta(功能权限) | `getBussAppViewKey(apptag)` | `{env:}iidp-eng:buss:app:{apptag}:view` | Hash | 是 |
| ResourceMeta(自定义权限) | `getBussAppResourceKey(apptag)` | `{env:}iidp-eng:buss:app:{apptag}:resource` | Hash | 是 |

> 前缀 `{env:}iidp-eng` 由 `MetaKeyFacade.getIIDP()` 统一生成(含环境隔离前缀)。SaaS 多租户场景下还存在一套 `saas:buss:g:{groupHash}:...` / `saas:buss:m:{groupsHash}:...` 前缀的并行 key(按"组/多组哈希"维度隔离),本文档只覆盖非租户维度(`buildMetaPartial` 系列)用到的 key,租户维度 key 详见 `container-facade-tenant-meta.md`。

## 7. 已知风险 / 待确认事项

1. **【需确认】`ResourceMeta` / `ViewMeta` 的 Redis field 名(外层 `metaId`)与转换后对象内部 `id` 字段可能不一致**:`MetaConverter.loadAttrs` 从内层 `json.id` 设置 `meta.id`,而 Redis Hash 的 field 是调用方外层单独传入的 `metaId`,两者没有强制校验一致性(第 1 节)。如果前端传参时两者不一致,可能导致"用 metaId 查到的记录,其内部 id 字段却指向别的东西"的隐性数据错位,建议接手后确认是否需要加校验。
2. **【需确认】产品线-菜单的挂载关系存储位置**:`menuMetaIds` 存在 `ProductLineMeta` 上,但目前看到的写入路径(`FrontMetaBuilder.finalBuildMenuMeta`)属于租户维度体系;本文档聚焦的非租户维度 `buildMetaPartialProductLine`/`buildMetaPartialMenu` 两个分支都**没有**维护 `menuMetaIds` 的代码。需要确认非租户维度场景下,产品线的 `menuMetaIds` 是否本来就不需要维护,还是存在功能缺口。
3. **【低风险,命名】`ResourceMeta.ResourceType` 字段以大写字母开头**,不符合 Java 命名规范(可能是历史遗留或 fastjson 序列化兼容需要),重构时注意不要误改导致序列化字段名变化(会影响 Redis 里已存量的历史数据反序列化)。

## 8. 参考文件索引

| 类/方法 | 路径 |
| --- | --- |
| `BaseMeta` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/BaseMeta.java` |
| `ProductLineMeta` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/ProductLineMeta.java` |
| `MenuMeta` / `MenuMeta.MenuConnectionMeta` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/MenuMeta.java` |
| `UiViewMeta` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/UiViewMeta.java` |
| `ViewMeta`(功能权限) | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/view/ViewMeta.java` |
| `ResourceMeta` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/ResourceMeta.java` |
| `MetaConverter` / `ResourceConverter` / `ViewConverter` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/converters/` |
| `MetaKeyFacade` | `sie-snest-engine/src/main/java/com/sie/snest/engine/facade/MetaKeyFacade.java` |

## 附:交接确认清单(接手人签署前逐项确认)

- [ ] 已理解 `UiViewMeta`(页面视图,初态)与 `ViewMeta`(功能权限,终态)是两个不同的类,命名相近但职责不同
- [ ] 已理解四类元模型各自的 Redis 存储结构(整表 String / 全局 Hash / 按 App 分桶 Hash,第 6 节表格)
- [ ] 已与原作者确认第 7 节风险①(metaId 与内部 id 一致性)是否需要补充校验
- [ ] 已与原作者确认第 7 节风险②(非租户维度下产品线 `menuMetaIds` 是否需要维护)
