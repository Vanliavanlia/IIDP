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

## 6. 当前已安装的 APP（12 个）

`base-cache`、`code`（编码规则）、`file`、`iidp-user-prefer`、`sie-iidp-app-store`、`sie-iidp-dev-center`、`sie-iidp-dict`、`sie-iidp-i18n`、`sie-iidp-iam`、`sie-iidp-log`、`sie-iidp-tenant`、`sie-snest-config`

## 7. 已验证（清理后）

- 构建：5 个 Maven 模块全部 BUILD SUCCESS（本项目自有模块），`code` APP jar 自动拷入 `apps/modules/`
- 启动：`Started DemoServer in 2.526 seconds`，7 个端口全部监听
- 登录：`implementer / Implementer_2023` 成功，token 480 位
- 菜单：36 条，全部为平台管理菜单（用户/角色/租户/字典/视图/菜单/应用市场等）
- 数据：`meta_app` 12 行，`state=installed`

## 8. 下一步

开发快递驿站管理平台业务 APP（`sie-snest-express`）：包裹入库/上架/取件码/取件/退货、货架管理、驿站隔离与统计。编码规则 APP（`code`）保留复用。
