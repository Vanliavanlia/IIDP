# sie-snest 引擎知识库（厂商资料归档）

本目录是**原样归档**的第三方文档，未做任何改动。

## 来源

- 文件：`iidp-skills-master.zip`（106 KB，解压 253 KB）
- 时间：2026-11-07 由培训方（赛意）在微信群发出
- 原始形态：一套 AI Agent 技能包（CodeBuddy / Claude Code 的 `SKILL.md` + `reference/` 规范），供智能体在写 sie-snest 应用时自动加载
- 我们这里**只当领域知识库阅读**，不作为本项目的运行依赖

## 文件清单与对本项目的相关度

| 文件 | 讲什么 | 对本项目 |
|---|---|---|
| [`SKILL.md`](./SKILL.md) | 引擎设计理念、Odoo 对照表、写业务最小套路、**12 条关键认知（踩坑点）**、手写 SQL 铁律 | ⭐⭐⭐ **必读**，先读这一篇 |
| [`reference/sdk-model.md`](./reference/sdk-model.md) | `@Model` / `@Property` / 关系注解 / `@Validate` / `@Rules` 全部属性，`BaseModel` 全部方法 | ⭐⭐⭐ 写模型必查 |
| [`reference/engine-runtime.md`](./reference/engine-runtime.md) | `Meta`(env) / `RecordSet` / `call` 与 `callSuper` 重写链 / JSON-RPC 责任链 / `Filter` DSL | ⭐⭐⭐ 排查服务调用必查 |
| [`reference/seed-data.md`](./reference/seed-data.md) | 种子数据：由**应用市场 install/update** 触发（不是每次启动）、`meta_model_data` 幂等、`@ref`/`@eval` 语法 | ⭐⭐⭐ 解释"为什么我改了 seed 没生效" |
| [`reference/data-scope.md`](./reference/data-scope.md) | 数据权限（SG）四方模型、`GET_DATA_AUTH` 委托、占位符代入、"配了作用域为什么全放行"排查清单 | ⭐⭐⭐ 权限/数据隔离类问题 |
| [`reference/classloading.md`](./reference/classloading.md) | 每个 app 一个 `MetaAppClassLoader`、SDK 类不共享、卸载热更、`NoClassDefFoundError` 排查 | ⭐⭐ 跨 app 强转报 CCE 时看 |
| [`reference/meta-model-view-productline-menu-auth.md`](./reference/meta-model-view-productline-menu-auth.md) | 产品线 / 菜单 / `UiViewMeta` vs `ViewMeta` / 自定义权限的字段结构与 id 规则 | ⭐⭐ 菜单不显示、权限码对不上时看 |
| `reference/container-facade-*.md`（6 篇） | 元模型多阶段构建（`buildMetaWithTenant` / `buildMetaPartial`）、m2m 补齐、菜单继承、partial build 落 Redis | ⭐ 单机 demo 用不上 |
| `reference/router-v2-distributed.md` | SaaS 多版本分布式路由、K8s Service→Pod→租户三层模型 | ✗ 跳过 |
| `reference/summary-manager-multinode.md` | 多节点 Redis 摘要同步、事件总线广播 | ✗ 跳过 |
| `reference/partial-build-redis-batch-update.md` | "临时 key + Lua 原子生效"的元数据批量更新 | ✗ 跳过 |
| `reference/container-facade-saas-architecture.md` | SaaS 化三模式对比、App 市场四态机、启动阶段差异 | ✗ 跳过 |
| `reference/metabuilders-sandbox-internal.md` | `MetaBuilderSandbox` 内部三阶段构建、ThreadLocal 沙箱 | ✗ 跳过 |

## 阅读时的三个注意点

1. **`file:line` 引用不可点**：文中大量出现 `ContainerFacade.java:2788`、`AppLifecycleProcess.java:460` 这类定位，指向引擎源码仓库。我们本地只有编译后的 jar（`offline-repo` 里也无 sources），所以这些行号只能当"设计说明"读，不能跳转核对。
2. **引用了未随包提供的内容**：`SKILL.md` 第 6 条提到 `../arthas-debug/SKILL.md`（Arthas 调试技能），本仓库没有这一篇。
3. **和我们的实测结论有出入时以实测为准**：文档描述的是完整商业版行为（含 SaaS 多版本），我们的环境是单机单版本 + 公司私服制品，个别路径（如 Redis key、租户容器）会不同。

## 我们用这份资料得出的结论（记录备查）

- `@Model(parent=...)` 是 `String[]`（多继承），我们 `OrderExt` 写的 `parent = "order"` 是单元素数组，合法。
- `@Property(unique=true)` 可直接实现"编码唯一"，不需要手写 `validate`。
- `superuser` 在 `AuthService.getDataAuth` 源头短路，**连租户过滤一起跳过**——解释了为什么只有 superuser 能调通所有模型服务。
- seed json 只在 app install/update 时加载，所以"改 seed → 重启"能生效的前提是 app 状态发生了变更；稳定在 installed 后改 json 不会自动重导。
- SDK 本地 `new X().search(...)` 与引擎 `rs.call("search", ...)` 是两条路，前者不进重写链——写"数据隔离"功能时必须用后者。
