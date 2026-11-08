# IIDP 工业数字平台 · 快递驿站管理平台（工作区说明）

> 状态：**2026-11-08 工作区已清理**。只保留运行环境 + 编码规则 APP（`code`），业务 APP 待开发（计划 `sie-snest-express`）。
> 第 1–5 天的全部代码与文档（MES / CMS / QMS / WMS / BIN、19 节 README）已归档在 git 标签 **`archive/day5`**，需要时可 `git checkout archive/day5` 查看。

## 1. 访问方式

| 项目 | 地址 / 值 |
|---|---|
| 前端 | http://localhost:30666/iidp/ |
| 后端 | http://localhost:8061 （经 Nginx 前缀 `/api`） |
| 接口文档页 | http://localhost:30666/api/root/api/master |
| 授权服务 | http://localhost:8080 |
| MinIO 控制台 | http://localhost:9001 （账号 `snest` / `12345678`，桶 `apps`） |
| MySQL | `127.0.0.1:3307`，用户 `root` / `123456`，库 `iidp_demo_v3`（`utf8mb4 / utf8mb4_bin`） |
| Redis | `127.0.0.1:6379`，db 5，无密码 |

登录账号（租户 `root`，密码传 `MD5(明文)`）：

| 账号 | 密码 | 角色 |
|---|---|---|
| implementer | Implementer_2023 | 平台租户管理员 |
| admin | admin | 管理员 |
| superuser | superuser | 超级管理员（绕过权限） |

## 2. 目录结构

```
intermediate_software/
├─ iidp-backend-demo/                后端工程（Maven 多模块）
│  ├─ sie-iidp-demo-apps/            业务 APP（当前只有 sie-snest-code）
│  ├─ sie-iidp-demo-start/           启动模块（DemoServer / application-dev.properties / dbcp.properties）
│  ├─ sie-iidp-demo-common/
│  └─ apps/                          运行时应用目录：apps.json + modules/*.jar
├─ iidp-frontend-dist/               前端 dist + Nginx + grant 授权服务 + sql + README
├─ runtime/                          MySQL 数据目录、Redis/MinIO 二进制与数据、运行日志
├─ offline-repo/                     离线 Maven 仓库（构建用）
├─ docs/                             文档（snippets/ 里有待复用的代码片段）
├─ scripts/                          辅助脚本
├─ mysql-iidp.ini                    独立 MySQL 实例配置（3307）
├─ start-all.ps1 / stop-all.ps1      一键起停（6 个服务）
└─ settings-gu.xml                   Maven settings
```

## 3. 起停

```powershell
# 启动（按 MySQL → Redis → MinIO → grant → 后端 → Nginx 顺序等待端口）
.\start-all.ps1

# 停止（先优雅停 MySQL/Redis/Nginx，再按端口兜底）
.\stop-all.ps1
```

> 注意：后端必须在 grant(8080) 监听成功之后再启动，否则报 `请求引擎授权服务器异常`。
> 用 AI/脚本托管进程时，每个服务用独立后台作业，不要用 `Start-Process`（作业结束会连带回收子进程）。

## 4. 构建

```powershell
cd D:\Code\intermediate_software\iidp-backend-demo
mvn -B -o -s ..\settings-gu.xml "-Dmaven.repo.local=D:\Code\intermediate_software\offline-repo" clean package -DskipTests
```

产物：`sie-iidp-demo-start\target\sie-iidp-demo-start-1.0-SNAPSHOT.jar`（Spring Boot 可执行，约 150 MB），业务 APP jar 由插件自动拷到 `apps/modules/`。
构建前必须先停后端（jar 被占用会报 `Unable to rename ... .jar.original`）。

## 5. 重建数据库

```sql
DROP DATABASE IF EXISTS iidp_demo_v3;
CREATE DATABASE iidp_demo_v3 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

```powershell
D:\Code\intermediate_software\runtime\redis\redis-cli.exe -p 6379 -n 5 flushdb
```

然后重启后端（自动建表 + 导入平台种子），并执行首启 SQL：

```powershell
mysql --host=127.0.0.1 --port=3307 --user=root --password=123456 --database=iidp_demo_v3 `
  -e "source D:/Code/intermediate_software/iidp-frontend-dist/sql/platform-web-app-init.sql"
```

## 6. 当前已安装的 APP（13 个）

`base-cache`、`code`（编码规则）、**`express`（快递驿站）**、`file`、`iidp-user-prefer`、`sie-iidp-app-store`、`sie-iidp-dev-center`、`sie-iidp-dict`、`sie-iidp-i18n`、`sie-iidp-iam`、`sie-iidp-log`、`sie-iidp-tenant`、`sie-snest-config`

## 7. 已验证（清理后）

- 构建：5 个 Maven 模块全部 BUILD SUCCESS（本项目自有模块），`code` APP jar 自动拷入 `apps/modules/`
- 启动：`Started DemoServer in 2.526 seconds`，7 个端口全部监听
- 登录：`implementer / Implementer_2023` 成功，token 480 位
- 菜单：42 条（36 条平台菜单 + 包裹/货架/驿站/操作记录四个业务菜单）
- 数据：`meta_app` 13 行，`state=installed`；`ex_station` 2、`ex_shelf` 6、`ex_parcel` 若干

## 8. 快递驿站管理（express APP）

### 8.1 业务模型（4 张表）

| 模型 | 表名 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| `station` 驿站 | `ex_station` | name、code、address、phone、manager、status | 一个驿站有多个货架（`@OneToMany`） |
| `shelf` 货架 | `ex_shelf` | code、station（`@ManyToOne station_id`）、area、capacity、used、status | status：FREE / FULL |
| `parcel` 包裹 | `ex_parcel` | parcelNo、waybillNo、station、shelf、receiverName、receiverPhone、phoneTail、courier、inboundTime、pickupCode、status、onShelfTime、outboundTime、remark | 运单号唯一（`@Validate.Unique`） |
| `parcel_log` 操作记录 | `ex_parcel_log` | parcel、action、code、operator、operatorName、happenTime、remark | 入库/上架/取件/退货/换码/超期都会留痕 |

包裹状态机：

```
IN_STOCK 在库待上架 --> ON_SHELF 已上架 --> PICKED 已取件
        ON_SHELF --> OVERDUE 超期未取 --> PICKED
IN_STOCK / ON_SHELF / OVERDUE --> RETURNED 已退货
```

### 8.2 取件码规则（题意核心）

- 格式：`MMDD` + 4 位当日流水，例如 `11080001`、`11080002`。
- 唯一性范围：**只保证"有效期"内不重复**——数据库唯一索引做不到这一点（旧码在包裹取件后要能被新包裹复用），所以放在服务里校验：`nextPickupCode()` 用 `pickupCode == code AND status IN (ON_SHELF, OVERDUE)` 计数，为 0 才发放。
- 因此 `pickup(code)` 也优先命中"在架/超期"的那一条，避免历史上已取件的同码记录把新包裹挡住（这条是实测踩出来的，见 8.5）。
- 重新生成取件码（`reissue`）会把旧码在 `parcel_log` 里记为作废。

### 8.3 服务清单

| 服务 | 入参 | 说明 |
| --- | --- | --- |
| `parcel.create` | `valuesList[]` | 入库：自动编号（`PK` + 6 位，走 `code` APP 的 `barcode_rule`）、置 IN_STOCK、记录入库时间与手机尾号 |
| `parcel.autoShelf` | `ids[]` | 自动上架：按驿站找有空位的货架、used+1（满则 FULL）、生成取件码 |
| `parcel.pickup` | `code` | 凭取件码取件；已取件/已退货/未上架都抛错 |
| `parcel.pickupByIds` | `ids[]` | 视图按钮用的按行取件 |
| `parcel.returnOut` | `ids[]` | 退货出库，释放货架 |
| `parcel.reissue` | `ids[]` | 重新生成取件码 |
| `parcel.markOverdue` | `days` | 上架超过 N 天置 OVERDUE，返回 `{days,changed}` |
| `parcel.markOverdueNow` | - | 视图按钮版，固定 3 天 |
| `parcel.overview` | - | 各状态数量 |
| `station.stats` | `id` | 某驿站的 inStock/onShelf/picked/overdue/货架容量与占用率 |
| `shelf.findAvailable` | `stationId` | 找第一个 `used < capacity` 且 FREE 的货架 |

### 8.4 页面操作路径

1. 登录 `implementer / Implementer_2023` → 左侧「快递驿站」。
2. 「包裹管理」→ 新增（填运单号、收件人、手机号、快递公司、驿站）→ 保存后状态为"在库待上架"。
3. 勾选若干行 → 「自动上架」→ 状态变"已上架"、货架与取件码自动填好。
4. 勾选行 → 「取件」（或直接调 `parcel.pickup` 传取件码）→ 状态"已取件"，货架占用减一。
5. 对同一包裹再取件会被拦下：`该包裹已于 … 取件，不能重复出库`。
6. 「重新生成取件码」「退货出库」「标记超期」分别在行按钮/工具条上。

### 8.5 实测结果

| 场景 | 结果 |
| --- | --- |
| 入库 2 件（顺丰/圆通） | `PK000001`、`PK000002`，状态 IN_STOCK，手机尾号自动截取 |
| 自动上架 | 取件码 `11080001`/`11080002`，货架 `A-01`，used=2 |
| 取件 `11080001` | 成功返回 `parcelNo/waybillNo/receiverName/code/status=PICKED`，货架 used 降为 1 |
| 再次取件同一码 | `该包裹已于 … 取件，不能重复出库`（HTTP 200 + error.code=504） |
| 不存在的码 | `取件码不存在：99999999` |
| 换码 | 旧码记入 `parcel_log`（`REISSUE`，备注"旧取件码 … 已作废"） |
| 超期 | 手工把 `on_shelf_time` 改成 5 天前，`markOverdue(3)` 返回 `{days:3,changed:1}`，状态变 OVERDUE |
| 退货 | `RETURNED`；重复退货报 `已经退过货了` |
| 货架容量 | 把 C-01 容量改为 1 后连续上架 2 件：第一件占满 C-01（status=FULL），第二件自动落到 D-01 |
| 权限 | implementer 调驿站服务正常；未授权应用会 403（本次已用 `rbac_role_auth` 补 express 授权） |

### 8.6 踩坑记录

1. `@Model(isAutoLog = Bool.True)` 的 `Bool` 在 **`com.sie.snest.engine.model.Bool`**，不是 `com.sie.snest.sdk.Bool`（后者不存在，编译报"找不到符号 Bool"）。
2. `create` 不是覆写父类方法（`BaseModel` 只有无参 `create()`），要写成 `@MethodService(name="create") public RecordSet create(RecordSet rs, List<Map<String,Object>> valuesList)`，否则报"方法不会覆盖或实现超类型的方法"。
3. 跨表过滤要用**被查模型自己的属性名**：`station.stats` 最初写 `this.count(Filter.equal("station", id))`，报 `无效查询条件,模型:station, 找不到属性:station`——正确写法是 `new Parcel().count(...)`。
4. `rbac_role_auth` 的字段是 camelCase（`appName`/`appTag`/`appAll`/`tenantId`），写 snake_case 会插入空行、菜单不生效。
5. PowerShell 里调 RPC：`curl.exe --data-raw "$json"` 会被吞引号导致 400，应 `$body | curl.exe --data-binary "@-"`（stdin 传 body）。
6. `@Validate.Unique` 与 `@Property(unique=true)` 建议都加：前者给友好提示，后者兜底建唯一索引。

### 8.7 后续可扩展

- 取件人身份校验（手机尾号 + 取件码双因子）
- 按驿站做数据隔离（`@Model(isScope)` 或 create_user/tenant 过滤）
- 超期自动任务（定时调 `markOverdue`）、短信通知
- 包裹轨迹对接快递公司接口、驿站结算与提成

## 9. 下一步

- 课程截止 **2026-11-29**，需交付：演示视频、汇报 PPT、项目开发文档（需求分析/可行性/概要设计/详细设计/实现/分工）、项目开发过程文档（问题清单 + 每个问题 ≥3 次交流截图）、源代码。
- 开发文档里的图可以基于本 README 的模型表 + 状态机画用例图/类图/时序图。
