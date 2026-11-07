# 数据权限 / 作用域（SG）体系 —— iam 侧业务模型与规则代入机制

> engine-runtime.md §7 讲的是引擎侧注入口（`flushSearch` → `AuthService.getDataAuth` → `MetaPolicy`）。本文接着往下讲：`getDataAuth` 背后 sie-iidp-iam 里的 SG 业务体系——策略从哪些表长出来、规则里的占位符怎么变成真实过滤值。回答"配了作用域为什么（不）过滤"类问题看这里。

## 1. 四方模型关系（一张图）

```
维度 ActionDimension（tenant_action_dimension，"轴"：取值模型+取值字段，可复用，本身无规则）
  │ tenant_action_scope_dimension (M:N，V2 代码按一域一维用)
  ▼
作用域 ActionScope（tenant_action_scope，策略实例：rule 波兰表达式 + type + 树形 path）
  ├─ tenant_role_action_scope ──→ rbac_role   （授权对象：只有角色！用户经角色间接获得）
  ├─ tenant_action_rule       ──→ 模型/字段   （规则范围：meta_id + property 决定管哪些模型的哪个字段）
  └─ (经维度间接) tenant_user_instance ──→ 用户×维度×实例值 （该用户取哪些值，如可见哪些工厂）
```

三张关联表各答一个问题：**谁**受约束（role_action_scope）、约束**什么**（action_rule）、取**什么值**（user_instance）。

关键设计：

- **授权对象只有角色**，没有 user↔scope 直连表；用户 → `getRoleIdsByUserId` → `Filter.in("roleList", roleIds)` 反查作用域。
- **用户实例值分配在维度上不在作用域上**（`tenant_user_instance.actionDimensionId`）——同一维度上的多个作用域共享用户的实例分配。
- `ActionScope.type`：`TENANT_SG`（实例=租户本身，不需用户实例）/ `USER_SG`（需按用户分配实例）。这是取值粒度，不是授权目标的区别。
- 作用域是树（`parentId`/`path`）：根必须是租户SG，子作用域挂具体规则；策略拼接时根 Filter AND 子作用域的 OR 组合。

## 2. 引擎 → iam 的委托：GlobalService GET_DATA_AUTH

`AuthService.getDataAuth`（引擎）不自己算策略，通过 `GlobalServiceManager.getGlobalService(GET_DATA_AUTH)` 委托给 iam 的 `AuthData.getDataAuth`（`sie-iidp-iam/.../model/auth/AuthData.java`）。要点：

- superuser 直接返回空列表（= 完全放行，engine/iam 两侧都短路）。
- 线程级缓存：`MetaPolicy:{modelMetaId}:{type}:{userId}`，同请求同模型同操作只算一次。
- 调用失败默认放行（除非 `IIDP_THROW_EXP_WHEN_AUTH_SERVICE_FAIL`）——排查"为什么没过滤"时留意 error 日志。

iam 侧取数 SQL（`AuthData.selectActionScopes`）两段 UNION：

1. `TENANT_SG` 作用域 JOIN `tenant_action_rule` 且 `r.meta_id = '#ALL_MODEL'`——**`#ALL_MODEL` 是特殊值，表示规则对所有模型生效**（租户过滤打底用）；
2. `USER_SG` 作用域 JOIN 规则表 + 角色关联表，`r.meta_id in (当前查询的模型)`——**规则管不管某模型，看 `meta_id` 匹配；过滤哪个字段，看 `property` 列**。

## 3. 规则占位符 → 实例值代入（核心机制）

规则里不写死值，写系统变量占位符；每次查询时从**用户 session** 代入：

```
规则模板（tenant_action_rule.rule，波兰表达式）:
  [["factory_id","in","${meta.user.CURSG_INSTANCES}"]]

写路径（登录/前端切换作用域）:
  transformDefaultActionScopesV2 等服务
    → tenant_user_instance 查该用户在维度下的实例值
    → SgInstance{sgId, values}（isInstanceSelectAll=1 全放，否则默认第一个）
    → meta.setSgInstances(map) 写入用户 session（CTX_SG_INSTANCES，UserSessionService 持久到缓存）

读路径（每次 flushSearch）:
  AuthData.formFilterByActionScope: Filter.parse(rule)
    → replaceFilterParams 发现 ${...} 公式
    → FormulaUtil.getValue → SystemVariableUtil.getCurUserSgInstances
    → meta.getSgInstances().get(作用域id).getValues()   ← 占位符变真实 id 列表
    → MetaPolicy.rowFilter → 引擎 filter.and(...) 进 SQL
```

系统变量还有 `${meta.userId}`、`${meta.user.tenantId}`、`${meta.user.orgId}`、`${meta.user.org.group/unit/dept}`（本级及以下）等，见 `SystemVariableUtil.VarType`。

**空实例的语义（重要）**：`${meta.user.CURSG_INSTANCES}` 解析出空列表时，该 FilterOp 被**整个移除**——"没分配/没选实例 = 这条规则不过滤"，而不是 `IN ()` 查无数据。这是排查"为什么没过滤"的第一嫌疑点。

**切换即时生效**：SgInstance 存 session 不存线程，前端切换实例（顶栏切工厂）后所有后续请求立即用新值。

## 4. 规则范围的双来源：DB 配置 ∪ 代码标记，DB 优先

"哪些模型受某作用域管"有两个来源，按 `modelMetaId` 合并、**DB 覆盖引擎**：

| 来源 | 存哪 | 怎么看 |
|---|---|---|
| 界面配置 | `tenant_action_rule`（meta_id/model_name/property/rule） | 直接查库，或服务 `queryActionScopeRule(scopeId)` / `searchActionRuleRange` |
| 代码标记 | 模型字段注解 `@Property(scopeModel="维度模型名")`，随 app 元数据进引擎内存 | 源码 grep `scopeModel\s*=`；运行时 ognl `StoreMetaUtil.getScopeInstanceMap("维度模型名")` |

不一致时以 `tenant_action_rule` 为准——授权 SQL 实际 join 的是它。

保存规则范围（`saveAuthRuleRange`）是**全删全增**：先 `deleteByFilter(relActionScope=...)` 再 create，之后 `refreshSgLimitModel` 刷引擎。所以"已选列表突然为空"可能是被一次保存清空的（`isAutoLog=True`，可查审计日志）。

## 5. 排查清单（"配了作用域为什么不生效/为什么全放行"）

按命中概率排序：

1. **superuser / implementer** —— 源头短路，空策略 = 全放行（含租户过滤、列权限一起失效）。
2. **用户实例为空** —— FilterOp 被移除，规则等于没配。查 `tenant_user_instance` 有没有该用户×该维度的行；session 里 `meta.getSgInstances()` 有没有值。
3. **规则范围没覆盖目标模型** —— `tenant_action_rule` 里没有该模型的 `meta_id` 行（且引擎侧也没 `scopeModel` 标记）。
4. **孤儿规则** —— 表里有规则但其 `meta_id` 不在租户的 `listPermission()` 里（app 被卸载/权限收回），配置界面静默不显示、运行时也可能不匹配。
5. **角色没绑作用域** —— `tenant_role_action_scope` 缺行；或用户没有该角色。
6. **走了逃逸口** —— `ignoreSg` / `executeIgnoreTenant` / `*ForIgnoreSg`（见 engine-runtime.md §7）。
7. **GET_DATA_AUTH 调用失败被默认放行** —— 看 error 日志。

运行时定位（arthas，详见 arthas-debug skill）：

```
watch com.sie.iidp.iam.model.auth.AuthData getActionScopeByRoles returnObj   # 命中了哪些作用域+规则+字段
watch com.sie.iidp.iam.model.auth.AuthData getDataAuth returnObj             # 最终 MetaPolicy（rowFilter 已代入实例值）
```

## 附：关键代码位置（以 sie-iidp-iam / sie-snest 仓库为准）

- 策略入口：`AuthData.getDataAuth` / `formMetaPolicies` / `formFilterByActionScope` / `replaceFilterParams`（`model/auth/AuthData.java`）
- 取数 SQL（含 `#ALL_MODEL`）：`AuthData.selectActionScopes`
- 占位符求值：引擎 `FormulaUtil.getValue` → `SystemVariableUtil.getCurUserSgInstances`
- 实例写 session：`ActionScope.transformDefaultActionScopesV2` 等（`model/sg/ActionScope.java`）；引擎 `Meta.setSgInstances/getSgInstances`
- 模型：`ActionScope`（tenant_action_scope）、`ActionDimension`（tenant_action_dimension）、`ActionRule`（tenant_action_rule）、`UserInstance`（tenant_user_instance），均在 `sie-iidp-iam/.../model/sg|ref/`
- 引擎侧标记读取：`NewActionScope.getSelectedActionRuleVosByEngine` → `StoreMetaUtil.getScopeInstanceMap`
