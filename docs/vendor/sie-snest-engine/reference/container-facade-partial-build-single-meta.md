---
name: container-facade-partial-build-single-meta
description: ContainerFacade.buildMetaPartial(String type, String metaJson) L1982 单条元数据部分更新方法,五个 type 分支(ProductLine/Menu/UiView/CustomAuth/FunctionAuth)详细业务逻辑,以及与姊妹方法 buildMetaRemoved 的关系。覆盖前端菜单管理/视图设计器/自定义权限与功能权限配置页面的增删改场景。
---

# ContainerFacade 单条元数据部分更新——buildMetaPartial(type, metaJson)

**模块**:`sie-snest-engine`
**文件**:`sie-snest-engine/src/main/java/com/sie/snest/engine/facade/ContainerFacade.java`
**核心方法**:`buildMetaPartial(String type, String metaJson)` — **L1982**(注意:与 `ContainerFacade.buildMetaPartial(List<String>, String)`【L967】是**同名不同签名**的两个独立方法,互不调用,请勿混淆)
**姊妹方法**:`buildMetaRemoved(String type, String metaJsonRemoved)` — L2174(卸载/删除场景,与本方法共用大部分私有分支实现,见第 5 节)

## 1. 整体定位

L967 的 `buildMetaPartial` 是"重新计算并落库一批完整 App 的终态";本文档的 `buildMetaPartial(type, metaJson)` 则是完全不同维度的能力——**不触发任何 `computeMeta` / `MetaBuilderSandbox` 计算**,只是把前端传来的、已经是"终态格式"的一条或多条元数据,直接按 `type` 分发到对应的私有方法,做 Redis 层面的增删改。

方法注释(L1974-1981)自述:

> 部分更新元模型
> 初态:视图
> 终态:产品线、菜单、自定义权限 CustomAuth、注入的功能权限 FunctionAuth

即:**视图(UiView/ViewSeed)在这里被当作"初态"处理**(要联动改回 App 原始 JSON 里的 `Attributes.Views`),而**产品线/菜单/自定义权限/功能权限只改终态**(Redis 里的最终元数据表,不动 App 原始 JSON)。这是理解本方法五个分支差异的关键前提。

## 2. 分发逻辑(L1982-2008)

```java
public static void buildMetaPartial(String type, String metaJson) {
    if (StringUtils.isAnyBlank(type, metaJson)) {
        throw new SnestException("type或metaJson参数不能为空");
    }
    switch (type) {
        case MetaConstant.APP_PRODUCT_LINE:        // "ProductLine"
            buildMetaPartialProductLine(metaJson); break;
        case MetaConstant.MENU:                    // "Menu"
            buildMetaPartialMenu(metaJson); break;
        case MetaConstant.UI_VIEW:                 // "UiView"
            buildMetaPartialView(metaJson); break;
        case MetaConstant.VIEW_SEED:                // "ViewSeed"
            buildMetaPartialView(metaJson); break;   // 与 UiView 复用同一实现
        case MetaConstant.AUTH_TYPE.CUSTOM_AUTH:    // "CustomAuth"
            buildMetaPartialCustomAuth(metaJson); break;
        case MetaConstant.AUTH_TYPE.FUNCTION_AUTH:  // "FunctionAuth"
            buildMetaPartialFunctionAuth(metaJson); break;
        default:
            throw new SnestException("不支持的元模型类型:%s", type);
    }
}
```

`type` 取值来自 `MetaConstant`:`ProductLine` / `Menu` / `UiView` / `ViewSeed`(与 `UiView` 走同一分支)/ `CustomAuth` / `FunctionAuth`。`metaJson` 统一是一个 **JSON 数组字符串**(每个分支内部都是先 `JSONArray.parseArray(metaJson)`),数组里每个元素代表"一条要新增/修改/删除的元数据",通用字段约定(各分支略有差异,见下):

- `metaId`:该条元数据在 Redis 里的唯一 key(field)
- `app` / `tag`:所属 App 名称与版本标签
- `json`:真正的元数据内容(JSONObject)
- `deleteFlag`(可选,`Boolean`):是否为删除操作

## 3. 五个分支详细业务逻辑

### 3.1 `buildMetaPartialProductLine`(L2010-2038)—— 产品线

- 先从 Redis `getBussProductKey()` 读出**整个** `Map<metaId, ProductLineMeta>`(产品线是全量 Map 结构,不是 Hash 按 field 存,见元模型文档)。
- 遍历传入数组,每条按 `metaId` 取出已有的 `ProductLineMeta`(不存在则 `new`),用传入 `json` 里的 `display_name`/`sequence`/`icon`/`description` **覆盖式更新**这几个字段(无删除语义,注意:产品线分支在 `buildMetaRemoved` 里是**不支持删除**的,见第 5 节)。
- 整个 Map 重新序列化,写到 `getBussProductKey() + suffix` 临时 key,`batchUpdateRedis` 生效——**产品线是整表覆盖式更新**,不是按 field 增量合并;如果并发场景下两次调用交叉执行,后写入的会整体覆盖先写入的(基于读到的 Map 做修改再整体写回,存在"读-改-写"竞态窗口,见风险)。

### 3.2 `buildMetaPartialMenu`(L2040-2062)—— 菜单

- 对数组每一项:先用 `MenuMeta.menuMetaId(app, tag, id)`(`id` 来自 `json.id`)重新计算标准 `metaId`(**忽略调用方传入的 `metaId` 字段**,以 `json.id` 重新拼装为准)。
- `deleteFlag=true`:直接 `RedissonCache.hDel(getBussMenuKey(), metaId)`——**注意这里是同步直接删除,不经过临时 key / `batchUpdateRedis` 机制**,与其余分支的"先写临时 key 再批量生效"模式不同(见风险,直接 `hDel` 意味着这一步没有"原子回滚"能力)。
- 否则:`AppJsonAssemblerFacade.buildMenuJsonWithoutBody(json, app)` 组装菜单 JSON(补 `appName`/`version` 字段),调用 `FrontMetaBuilder.handleFinalMenu(metaId, menuJson)` 处理。

### 3.3 `buildMetaPartialView`(L2064-2101)—— 视图(UiView/ViewSeed 共用)

这是五个分支里**唯一同时涉及"初态回写"**的分支,对应方法开头注释里"初态:视图":

- **删除**(`deleteFlag=true`):
    1. 从 `getBussUiViewKey()`(Hash)按 `metaId` 读出 `UiViewMeta`,为空则直接 `return`(幂等,重复删除不报错)。
    2. `removeViewFromAppJson(uiViewMeta)`:反查该 App 的**原始(初态)JSON**(`getOriginalMetas(appNameTag, "buss")`),从 `Attributes.Views` 里按 `uiViewMeta.getKey()` 移除对应条目,再 `updateRedisAppJson` 整体写回——**这一步直接同步写库,不经过临时 key 机制**(见风险)。
    3. `RedissonCache.hDel(getBussUiViewKey(), metaId)` 删除终态视图记录。
    4. 级联清理:扫描该 App 下所有 `ViewMeta`(`getBussAppViewKey(appNameTag)`),找出 `viewId` 等于当前被删 `metaId` 的那些(即由这个 UiView 派生出的功能权限视图),批量 `hDelKeys` 一并删除——**这是"删 UiView 联动删其派生 ViewMeta(功能权限)"的唯一入口**。
- **新增/修改**(`deleteFlag` 为空或 `false`):直接委托 `ViewMetaBuilder.buildViewMetaFromPartial(app, tag, metaId, json)`。

### 3.4 `buildMetaPartialCustomAuth`(L2125-2164)—— 自定义权限

- 遍历数组,按 `deleteFlag` 分两组收集:`deletedCustomAuths: Map<appTag, Set<metaId>>` 和 `newCustomAuths: Map<appTag, Map<metaId, json>>`(`appTag = app + "." + tag`,`app`/`tag` 缺失直接抛异常)。
- 新增/修改项:`new ResourceConverter().convert(json)` 把前端 JSON 转成 `ResourceMeta`(`BaseMeta` 子类),序列化后放进 `newCustomAuths`。
- 落库:
    - 新增/修改:按 `appTag` 分组,整体 `hSetAll(getBussAppResourceKey(appTag) + suffix, resourceMetas, -1L)` 写临时 key。
    - 删除:把要删的 `metaId` 对应字段值置为 `EMPTY_STR_TO_DELETE`(空字符串),同样写到 `getBussAppResourceKey(appTag) + suffix` 临时 key——**新增和删除可能落在同一个临时 key 上**(`newCustomAuths`/`deletedCustomAuths` 若命中同一个 `appTag`,`deletedCustomAuths.forEach` 是在 `newCustomAuths.forEach` 之后执行,都是往同一个临时 key 追加字段,`hSetAll` 是 `putAll` 语义,不会互相覆盖,可以共存),最终统一 `batchUpdateRedis(updatedKeys, suffix)` 生效。

### 3.5 `buildMetaPartialFunctionAuth`(L2203-2293)—— 功能权限

五个分支里**业务逻辑最复杂**的一支,本质是"修改/新增一个功能权限对应的 `ViewMeta`,并按需联动更新其挂载的菜单":

- 同样先按 `deleteFlag` 分两组:`deletedAuth`、`newAuths`;`app`/`tag`/`metaId` 任一为空直接抛异常,`json` 为空也抛异常。
- **新增/修改的核心分支逻辑**(对每条非删除记录):
    1. `new ViewConverter().convert(json)` 转出一个"临时" `ViewMeta`(只包含 `type`/`code`/`sequence` 等基础字段)。
    2. **先查 Redis 终态是否已存在同 `metaId` 的 `ViewMeta`**(`getBussAppViewKey(appTag)` 按 `metaId` 取):
        * **存在**:说明这是"修改"——**只允许改 `name` / `description` 两个字段**,直接在已有的 `redisViewMeta` 上覆盖这两个字段后写回,**完全丢弃本次传入 json 转换出的 `viewMeta` 对象**(不能通过这个接口修改已存在功能权限的其它属性,如 `type`/`propXxx` 等)。
        * **不存在**:说明这是"新增"——尝试用 `viewMeta.getParentPath()` 找父级 `ViewMeta`(同一个 `appTag` 下):
            + 找到父级:把父级的 `menuIds` **直接赋给**新 `viewMeta`(继承父级挂载的菜单集合),然后对这些菜单 id 批量读出 `MenuMeta`(`hMGet`),逐个调用 `menuMeta.linkViewMeta(viewMeta)`——即把新建的功能权限视图反向登记进菜单的 `viewMetaIds` 集合里;用 `updatedMenus` 本地 Map 做去重缓存。
            + 找不到父级(或没有 `parentPath`):`viewMeta` 直接用(`menuIds` 为空,没有挂到任何菜单上)。
    3. 最终写入 `newAuths`(`appTag -> metaId -> ViewMeta json`)。
- 落库:`newAuths` → `getBussAppViewKey(appTag) + suffix`(`hSetAll`);`deletedAuth` → 同一 key 下对应 `metaId` 置 `EMPTY_STR_TO_DELETE`;`updatedMenus` → `getBussMenuKey() + suffix`(逐个 `hSet`)。三类 key 汇总进 `updatedKeys`,统一 `batchUpdateRedis(updatedKeys, suffix)`。

**与自定义权限分支(3.4)的本质区别**:CustomAuth 是纯粹的资源权限配置(`ResourceMeta`),没有"挂载到菜单"的概念;FunctionAuth(`ViewMeta`)天然要回答"这个功能权限出现在哪个菜单下",所以多了一整套"继承父级 `menuIds` + 反向登记菜单 `viewMetaIds`"的联动逻辑。

## 4. 五个分支要点速查表

| 分支 | 元模型类型 | 存储结构 | 是否走"临时 key + batchUpdateRedis" | 是否有级联影响其它元数据 |
| --- | --- | --- | --- | --- |
| ProductLine | `ProductLineMeta` | 单 key 整表 String | 是 | 无 |
| Menu | `MenuMeta` | 全局 Hash | **删除时否(直接 hDel)**,新增/改动委托 `FrontMetaBuilder.handleFinalMenu` | 无(本分支内) |
| UiView/ViewSeed | `UiViewMeta` | 全局 Hash + App 原始 JSON | **删除时否(直接 hDel + 直接改写 App JSON)**,新增委托 `ViewMetaBuilder` | 是:删除时级联清理该 App 下 `viewId` 匹配的功能权限 `ViewMeta` |
| CustomAuth | `ResourceMeta` | 按 App 分桶 Hash | 是 | 无 |
| FunctionAuth | `ViewMeta` | 按 App 分桶 Hash + 全局菜单 Hash | 是 | 是:新增时可能反向修改父级视图关联的菜单(`linkViewMeta`) |

## 5. 与 `buildMetaRemoved(type, metaJsonRemoved)`(L2174)的关系

`buildMetaRemoved(type, metaJsonRemoved)` 是同一套"单条元数据"体系里的删除入口,`switch` 分支复用了本方法的部分私有实现:

```java
case APP_PRODUCT_LINE: // 产品线不支持删除(空分支,什么都不做)
case MENU:              buildMetaRemovedMenu(metaJsonRemoved);        // 独立实现(非 buildMetaPartialMenu 的 deleteFlag 分支)
case CUSTOM_AUTH:       buildMetaPartialCustomAuth(metaJsonRemoved);  // 复用 3.4,依赖数组里每项自带 deleteFlag=true
case FUNCTION_AUTH:     buildMetaPartialFunctionAuth(metaJsonRemoved);// 复用 3.5,同上
case VIEW:              buildMetaPartialView(metaJsonRemoved);        // 复用 3.3,同上
case VIEW_SEED:         buildMetaPartialView(metaJsonRemoved);        // 同上
```

**需要特别注意的不一致**:
- `buildMetaRemoved` 的 `switch` 用的是 `MetaConstant.VIEW`(值 `"View"`),而 `buildMetaPartial` 的 `switch` 用的是 `MetaConstant.UI_VIEW`(值 `"UiView"`)——**两个方法对"视图类型"传入的 `type` 字符串约定不一样**(一个传 `"View"`,一个传 `"UiView"`),调用方如果照搬同一个 `type` 常量在两个方法间复用,会因为 `default` 分支直接抛 `"不支持的元模型类型"` 而失败。
- `buildMetaRemovedMenu`(L2295-2305)是独立实现,只是简单遍历数组按 `metaId` 逐条 `hDel(getBussMenuKey(), metaId)`,**不做任何子菜单/父菜单关系清理**——与 `ContainerFacade.dealMenuParentAndChildrenRemoved`(在 App 级卸载流程中调用的方法)是两套不同粒度的菜单清理逻辑,不要混淆。
- 产品线在这里被显式设计为"不支持删除"(`case APP_PRODUCT_LINE:` 空分支),与 `buildMetaPartialProductLine` 只支持"新增/覆盖更新"是一致的产品设计。

## 6. 已知风险 / 待确认事项清单

1. **【中风险】Menu / UiView 删除路径直接同步写 Redis,不经过临时 key 机制**:`buildMetaPartialMenu` 的 `deleteFlag=true` 分支直接 `hDel`,`buildMetaPartialView` 的删除分支里 `removeViewFromAppJson`/`hDel`/`hDelKeys` 均直接同步执行,没有像其余分支那样"先写临时 key,最后统一 `batchUpdateRedis` 原子生效"。这意味着:如果一次请求里数组包含多条记录、处理到中途抛异常,**已经执行的直接删除操作不会回滚**,与同一批次里"新增/改动"部分(如果失败会整体不生效)的原子性语义不一致。
2. **【需确认】`buildMetaPartial` 与 `buildMetaRemoved` 对"视图"类型使用不同的 `type` 字符串常量**(`UiView` vs `View`,见第 5 节)——需要与前端/调用方核实这是两套独立约定还是不一致的 bug。
3. **【需确认】产品线更新是"整表读-改-写",存在并发覆盖窗口**:`buildMetaPartialProductLine` 先整体读出 Map,本地修改后整体写回临时 key。如果两个请求并发调用(都读到修改前的旧 Map),后完成 `batchUpdateRedis` 的一方会覆盖掉先完成一方的变更(并非按字段合并)。
4. **【需确认】`FunctionAuth` 修改已存在记录时,只允许改 `name` / `description`**:如果前端页面允许用户修改功能权限的其它字段(如 `type`),提交后会被静默忽略而不是报错,建议确认这是产品预期行为还是接手后需要补充校验/报错提示。
5. **【待确认】`FrontMetaBuilder.handleFinalMenu` / `ViewMetaBuilder.buildViewMetaFromPartial` 内部实现本次未展开**:如需修改菜单/视图新增-修改路径的具体落库细节,需要接手后单独跳转确认。
6. **【测试覆盖】** `ContainerFacadeManualTest` 中有针对本方法五个分支的手工测试(Menu/UiView/CustomAuth/ProductLine),但**未见针对 `FunctionAuth` 分支的测试用例**。

## 7. 排查 / 验证建议

- **异常信息**:`"type或metaJson参数不能为空"`、`"不支持的元模型类型:%s"`(分发入口);`"修改自定义权限失败,app 或 tag 不能为空"`;`"修改功能权限失败,app 或 tag 不能为空"` / `"...json 不能为空"`
- **相关测试**(`ContainerFacadeManualTest.java`):
    - `buildMetaPartial("Menu", ...)`:L186/210/237/250
    - `buildMetaPartial("UiView", ...)`:L286(新增)/298(删除,用 `partialjson/viewremove.json`)/305/324(视图继承/扩展,`partialjson/viewextend.json`)
    - `buildMetaPartial("CustomAuth", ...)`:L346(`partialjson/customAuth.json`)
    - `buildMetaPartial("ProductLine", ...)`:L360(`partialjson/productLine.json`)
    - 测试资源文件目录:`src/test/resources/partialjson/`

## 8. 参考文件索引(file:line)

| 符号 | 文件:行号 |
| --- | --- |
| `buildMetaPartial(String, String)` | `ContainerFacade.java:1982` |
| `buildMetaPartialProductLine` | `ContainerFacade.java:2010` |
| `buildMetaPartialMenu` | `ContainerFacade.java:2040` |
| `buildMetaPartialView` | `ContainerFacade.java:2068` |
| `removeViewFromAppJson` | `ContainerFacade.java:2103` |
| `buildMetaPartialCustomAuth` | `ContainerFacade.java:2125` |
| `buildMetaRemoved(String, String)` | `ContainerFacade.java:2174` |
| `buildMetaPartialFunctionAuth` | `ContainerFacade.java:2203` |
| `buildMetaRemovedMenu` | `ContainerFacade.java:2295` |
| `MenuMeta.menuMetaId` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/MenuMeta.java` |
| `MenuMeta.linkViewMeta` | 同上 |
| `ResourceConverter` / `ViewConverter` | `sie-snest-engine/src/main/java/com/sie/snest/engine/model/converter/` |
| 测试用例与样例 JSON | `ContainerFacadeManualTest.java`(L186-360 区间)、`src/test/resources/partialjson/*.json` |

## 附:交接确认清单(接手人签署前逐项确认)

- [ ] 已理解本方法与 `buildMetaPartial(List<String>, String)`(L967)是完全独立的两个重载
- [ ] 已理解五个分支各自的存储结构与是否走批量原子生效机制(第 4 节对比表)
- [ ] 已与前端/调用方确认第 6 节风险②(视图类型 `type` 常量在 `buildMetaPartial`/`buildMetaRemoved` 间不一致)
- [ ] 已确认第 6 节风险④(FunctionAuth 修改已有记录只认 name/description)是否符合产品预期
- [ ] 已跑通 `ContainerFacadeManualTest` 中 L186-360 区间与本文档相关的用例
