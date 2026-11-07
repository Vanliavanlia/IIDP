# 种子数据（Seed）机制

app 里 `src/main/java/.../data/*.json`、`views/*.json`、`menus`、`auth.json` 这些**初始记录**，就是「种子数据（seed）」。它对标 Odoo 的 XML data / `noupdate` 记录（外部 id + 初始数据）。要回答「某张表里本应有哪些记录」，先看对应 app 的 seed。

## 谁在什么时候加载 —— 不是引擎启动，是应用市场生命周期

**驱动类**：`com.sie.snest.engine.model.loaders.SeedLoader`（在 engine 里，但由**应用市场 app（sie-iidp-app-store）安装/更新/卸载/重置流程**调用，不是每次引擎启动或模型加载时跑）。

三个入口方法对应三种 `ChangeType`：

| SeedLoader 方法 | ChangeType | 场景 | 对已存在记录的行为 |
|---|---|---|---|
| `install(...)` | `CREATE` | 安装 app | 已存在则**跳过**，只认新增（insert-if-not-exists） |
| `update(...)` | `UPDTE` | 更新 app | 已存在的 data **跳过业务表**，只维护登记表；仅**新增的** seed 落业务表 |
| `reset(...)` | `RESET` | 种子数据管理里「重置」 | **先删后插**，恢复出厂 |

所有操作都是「攒 SQL → 批量执行」：`deleteSqlMap` / `insertSqlMap` / `updateSqlMap` 三个 map 按数据源分组，最后 `executeFinallySeedSql` + `meta.flush()`。

**卸载（uninstall）不走 SeedLoader，且只软删元数据、不删业务表数据**。app-store 的 `UninstallHandler` → `CloudIMataManager.updateAppOther` 只把 `meta_model_data`（seed 登记表）、`meta_id_map`、`meta_app_dependency` 按 `app_ids` 置 `is_delete=1`（软删），`meta_app` 置 `state=uninstalled`；`UninstallFinalModelHandler` 重算元数据终态、发布容器事件。**业务表里的 seed 记录（如 `rbac_user` 那几行）不会被删**。含义：卸载后重装同一 app，业务数据还在，靠 `buildUniqueInsertSql` 的幂等避免重复插；要彻底清业务数据需另行处理。

## 幂等性 —— 靠 `meta_model_data` 登记表

- 登记表 `meta_model_data`（`source='seed'`）记录「seed key → 业务记录 id」映射。
- 加载前 `listModelData()` 把已存在的 key 载入内存 `nameIdMap`；`handleRecordItem` 据此判断走「已存在」还是「新增」分支。
- **seed key 规则** = `appId.model.name`（`handleRecordItem`，key 不含 `.` 时自动补前缀），等价于 Odoo external id。json 里的顶层 key（如 `rbac_user_admin`）就是这个 name。
- 业务表插入用 `buildUniqueInsertSql*`（insert-if-not-exists by id / 唯一条件），所以**重复安装不会重复插**。
- 建议每条 seed 显式带 `id`；不带则拿 key 当 id（注意 id 列已放宽到 varchar(64)）。改 key 等于新增一条 seed，旧的要人工在「种子数据管理」里删。

## seed json 结构与占位符语法

顶层四类节点（`MainNodeType`）：`$.data`（业务模型记录）、`$.views`、`$.menus`、`auth.json`。`data` 每条形如：

```json
{
  "data": {
    "rbac_user_admin": {                 // ← seed key（external id）
      "model": "rbac_user",              // ← 目标模型
      "properties": {                    // ← 字段值
        "login": "admin",
        "role_ids": [ { "@eval": "[4, @ref(rbac_role_admin), 0]" } ]
      }
    }
  }
}
```

解析器 `getProperty` + `compileEvalText`（`SeedLoader` L1554-1602）支持的占位符：

| 语法 | 含义 | 处理 |
|---|---|---|
| `@ref(xxx)` | 引用另一条 seed 记录的 id | 正则 `@ref\((?<ref>\S+)\)`，替换成 `"id"`（`findRef` 取 `.` 后最后一段） |
| `{ "@eval": "表达式" }` | 求值表达式（数组元素里） | 先把内部 `@ref(...)` 替换成 id，再 `convertToObject` 解析成 JSON 值 |
| `[4, @ref(...), 0]` | **x2many link 命令三元组**（关联已存在记录） | 走 `@eval`；`value instanceof ArrayList` 分支写**中间表**（many2many）。命令语义同 Odoo `(4, id, 0)` |
| `@fileId(f)` / `@fileUrl(f)` / `@filePath(f)` | 附件 seed 占位符 | 从 `ui_file_seed` / `meta_attachment` 查真实附件 id/url/path 替换 |

## 落库注意点

- **加密字段**：`@Property(encrypt=@Encrypt)` 的字段（如 password、mobile），seed 落库时经 `EncryptStrategyFactory` 加密，**不是明文入库**。
- **审计字段**：`create_user` / `create_date` 由 SeedLoader 补，创建人通常是 `Meta.SUPERUSER`。
- **many2many**：三元组命令写 `@JoinTable` 声明的中间表（如 `rbac_role_user`），用唯一插入避免重复。
- **多数据源/分表**：`dealSeedSql` 会把非平台表的 seed 同步到所有租户策略数据源；分表模型（shard=tables）不支持更新 seed。
- **抽象模型**：抽象模型配 data seed 无效（会告警跳过）。

## 排查用法

- 「新装后某表应有哪些记录」→ 找 app 的 `data/<xxx>.json`（顶层 key = 记录 external id）。
- 关系怎么建的 → 看 `properties` 里的 `@eval [4, @ref(...), 0]`，落到 `@JoinTable` 指定的中间表。
- 运行中表里的记录 ≠ seed：还有管理员通过 `create` 服务后建的、以及多租户下各租户自动生成的。
