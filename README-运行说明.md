# IIDP 工业数字平台 —— 本地环境构建说明

本目录是在 Windows + JDK 1.8 下完整跑通 IIDP（v3.0.1）演示环境的工程副本。
后端已从源码构建成功，数据库、缓存、授权服务、前端代理均已就绪，可直接登录使用。

---

## 1. 访问方式

| 项目 | 值 |
| --- | --- |
| 前端地址 | http://localhost:30666/iidp/ |
| 后端端口 | 8061（经 Nginx `/api/` 反代） |
| 接口文档 | http://localhost:30666/api/root/api/master |
| 授权服务 | 8080（grant-dev） |
| MySQL | 127.0.0.1:3307，库 `iidp_demo_v3`（`utf8mb4 / utf8mb4_bin`），`root / 123456`（本目录独立实例，见 `mysql-iidp.ini`） |
| Redis | 127.0.0.1:6379，**无密码**，db 5（与手册一致） |
| 对象存储 | 127.0.0.1:9000（真实 MinIO，数据目录 `runtime\minio\data`，控制台 http://127.0.0.1:9001 ，`snest / 12345678`） |

登录账号（密码为明文种子密码，前端登录时会做 MD5 + 服务端 sha256 校验）：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| implementer | Implementer_2023 | 平台租户管理员 | MES 业务应用已授权，手册指定账号 |
| admin | admin | 平台租户管理员 | 同上 |
| superuser | superuser | 超级管理员 | 不受模型权限限制，排障用 |
| admin_000001 | Admin_000001 | 演示租户管理员 | 租户"演示租户"（编码 `000001`，最大层级 2 / 最大数量 99）的管理员，**由系统在创建租户时自动生成**（`admin_<租户编码>` / `Admin_<租户编码>`） |

> 手册的完整登录链路已可用：`implementer / Implementer_2023` 登录 → 建租户（层级 2、数量 99）→ 用租户管理员 `admin_000001 / Admin_000001` 登录。

---

## 2. 目录结构

```
D:\Code\intermediate_software
├─ iidp-backend-demo\            后端 Maven 多模块工程（父 pom + 3 模块）
│  ├─ sie-iidp-demo-start\       启动模块（DemoServer.java、application*.properties）
│  ├─ sie-iidp-demo-apps\        业务应用 sie-snest-mes（8 个模型 + 8 个视图 + menus.json）
│  ├─ sie-iidp-demo-common\      公共模块
│  └─ apps\                      apps.json + modules\（14 个 SDK 应用 jar）
├─ iidp-frontend-dist\           前端发布包（dist + nginx + grant + sql + README）
├─ mysql-data\                   本地 MySQL 8.0 实例数据目录（端口 3307）
├─ mysql-iidp.ini                该实例的配置文件
├─ runtime\                      redis、MinIO（minio\minio.exe + 数据目录）、启动日志
├─ offline-repo\                 Maven 本地仓库（由你提供的 repository.zip 解出并补全，**完全离线可构建**）
├─ .m2repo\                      旧的本地仓库（早期从私服下载，保留作备份，可删）
│                               （已另外整仓复制到 C:\Users\Vanlia\.m2\repository，IDEA 默认构建可直接用）
├─ start-all.ps1 / stop-all.ps1  一键启动 / 停止
└─ settings-gu.xml               Maven 配置（私服 + 本地仓库路径）
```

---

## 3. 常用命令

```powershell
# 启动全部服务（幂等，已在跑的服务会跳过）
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1

# 停止全部服务
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-all.ps1

# 重新构建后端（必须先停后端，否则 jar 被占用）
cd .\iidp-backend-demo
$env:JAVA_HOME="D:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
& "D:\Program Files\apache-maven-3.10.0\bin\mvn.cmd" -B -o -s ..\settings-gu.xml `
  "-Dmaven.repo.local=D:\Code\intermediate_software\offline-repo" clean package -DskipTests
# 产物：sie-iidp-demo-start\target\sie-iidp-demo-start-1.0-SNAPSHOT.jar
# 实测：-o 全离线 + 干净构建约 9 秒（5 个模块全 SUCCESS）

# 后端启动（必须在 iidp-backend-demo 目录下，apps/ 是相对路径）
java "-Dfile.encoding=UTF-8" "-Duser.timezone=Asia/Shanghai" ``
  -jar "sie-iidp-demo-start\target\sie-iidp-demo-start-1.0-SNAPSHOT.jar"
```

**重建数据库（清库重来）**：

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root --password=123456 `
  -e "DROP DATABASE IF EXISTS iidp_demo_v3; CREATE DATABASE iidp_demo_v3 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
& ".\runtime\redis\redis-cli.exe" -p 6379 -n 5 flushdb
# 然后重启后端（首次启动会建 79 张表并导入种子数据，约 1 分钟）
# 启动完成后必须再执行一次（否则前端按钮无反应）：
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root --password=123456 `
  --database=iidp_demo_v3 -e "source D:/Code/intermediate_software/iidp-frontend-dist/sql/platform-web-app-init.sql"
```

---

## 4. 为跑通所做的改动（相对厂商原始包）

1. **`sie-iidp-demo-start\src\main\resources\application-dev.properties`**
   - `dbcp.properties` 指向 3307 端口（本项目自建 MySQL 实例）。
   - `seeder.sync=true`：原始包中被注释/关闭时，视图、菜单、用户等种子数据不会导入，导致"库里没有任何用户、登录提示该用户不存在"。
   - `minio.endpoint=http://127.0.0.1:9000`：原值指向厂商内网 `192.168.175.54`，本机不可达会让启动卡在"创建默认存储桶"。
2. **新增 `sie-iidp-demo-start\src\main\resources\sql\`**（4 个 `engine_init*.xml`，从引擎 jar 中取出）
   - 修复引擎 `SqlTemplateLoader` 只扫描 classpath 上**第一个** `sql` 目录的问题：真机先命中了 `shardingsphere-standalone-mode-repository-jdbc` 里的 `sql/H2.xml`、`MySQL.xml`（其节点无 `id` 属性），导致 `SqlTemplateConverter.convert` 抛 `NullPointerException`，启动报"插件启动失败"。
3. **MES 应用新增种子数据 `sie-snest-mes\src\main\java\com\sie\app\mes\data\mes-seed.json`**（并在 `app.json` 的 `data` 中登记）
   - 物料字典 `material_type`（原材料/半成品/成品/辅料）与 `material_unit`（千克/个/米/升/套）：缺失时新增物料会被拦："字典配置有误,请检查material.type[raw]"。
   - 给 `rbac_role_implementer_admin`、`rbac_role_admin` 授予 `mes` 应用 `appAll=1`：IAM 自带种子只授权平台类应用，业务应用 mes 未授权时，非超级管理员调用 MES 模型服务一律返回 403"无权访问"。
4. **`runtime\minio\`（真实 MinIO RELEASE.2024-08-17）**：从你提供的 `minio(1).zip` 解出，监听 9000（API）/9001（控制台），数据目录 `runtime\minio\data`，根凭据 `snest / 12345678`（与 `application-dev.properties` 的 `minio.accessKey/secretKey` 一致）。桶 `apps` 已存在，文件上传/下载已实测可用。
   - 早期为绕开不可达的厂商 MinIO 曾用过 `runtime\fake-s3.ps1`（只回 200、不存文件），**现已不再需要**，`start-all.ps1` 已改为直接拉真实 MinIO；若 9000 被别的程序占用，该脚本仍可作为应急保底。

5. **`offline-repo\`（由你提供的 `repository.zip` 解包而来）**
   - 内容：6621 个文件 / 537MB 的离线 Maven 仓库，`com/sie/meta` 下 32 个 artifact 齐全（`sie-snest-engine:v3.0.1-HOTFIX.002`、`sie-snest-sdk:v3.0.1-UAT.003`、`sie-iidp-iam/tenant/file/log/dict/i18n/cache/dev-center/app-store` 等），另带大量第三方依赖的 `-sources.jar`（便于读源码）。
   - 已补全：该 zip 缺 3 个本机 Maven 3.10 需要的插件版本（如 `maven-clean-plugin:3.5.0`），已从旧 `.m2repo` 合并进来（合并后 7101 个文件 / 569MB）。
   - 实测：`mvn -o -Dmaven.repo.local=...\offline-repo clean package` 全离线构建成功（9 秒）。**从此构建不再依赖公司私服 `iidp.chinasie.com:9999`，断网也能重建。**

6. **按手册对齐 Redis 端口**：`runtime\redis\redis-iidp.conf` 由 51746 改为 **6379 且去掉 `requirepass`**，`application-dev.properties` 的 `redis.port=6379`、`redis.password=`（空）。`start-all.ps1` / `stop-all.ps1` 里的端口探测、优雅停止命令同步改为 `6379`（无 `-a`）。
7. **按手册对齐数据库排序规则**：库 `iidp_demo_v3` 以 `DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin` 重建（原为 `utf8mb4_general_ci`），79 张表随首次启动重建，种子数据自动重新导入。
8. **Maven 本地仓库放到手册要求的位置**：把 `offline-repo\` 整仓复制到 `C:\Users\Vanlia\.m2\repository`（13020 个文件 / 约 1.0GB，`com\sie\meta` 下 32 个 artifact 齐全），这样 IDEA 用默认 `~/.m2` 也能直接构建，不必再传 `-Dmaven.repo.local`（命令行仍可用 `offline-repo`，两者内容一致）。
9. **创建演示租户、租户管理员并完成租户授权**（手册步骤 10、11）：通过 RPC `rbac_tenant.create` 建成租户 **演示租户**（编码 `000001`，`maxHierarchy=2`、`maxChildren=99`、level=1），系统自动生成租户角色 `rbac_role_tenant_admin` 与用户 `admin_000001 / Admin_000001`；随后按前端"授权"弹窗的同一套服务补做授权——`rbac_tenant.listPermission` 取全部 14 项权限（2 条产品线 + 12 个应用），`rbac_tenant.grant({id:租户id, permissionIds:[...]})` 写入 14 条 `rbac_role_auth`（`appAll=1`）。**不做授权时租户管理员能登录但左侧菜单为空**，这是手册流程里容易漏掉的一步；授权后 `ui_menu.loadMenu` 返回 38 条菜单（含 MES 的物料/订单/产品/BOM/工序/工艺路线/供应商）。

> 备注：`apps.json` / `apps/modules/*.jar` 未改动；构建时用 `-Dmaven.repo.local=D:\Code\intermediate_software\offline-repo`，该参数在 PowerShell 里**必须加引号**，否则 Maven 会去写不可写的 `maven-repository` 目录而报 `AccessDenied / .locks`。旧的 `.m2repo` 可保留作备份，不需要时整个目录删掉即可（不影响构建与运行）。

---

## 5. 还缺什么（需要你确认/补充的部分）

**① 对象存储（MinIO）—— 已用你提供的 minio.exe 补齐**
- 现状：`127.0.0.1:9000` 跑的是真实 MinIO（数据落在 `runtime\minio\data`，桶 `apps`），上传接口 `POST /api/file/upload` 实测成功，返回 `{"bucket":"apps","key":"2026-10-30/xxx.txt","size":60,...}`，对象确实写进了 `runtime\minio\data\apps\...`。
- 控制台：http://127.0.0.1:9001 ，账号 `snest` / 密码 `12345678`。
- 若以后要换成别的 MinIO/S3：改 `application-dev.properties` 的 `minio.endpoint / accessKey / secretKey / bucketName`，并保证启动 MinIO 的 `MINIO_ROOT_USER / MINIO_ROOT_PASSWORD` 与之匹配，重打包即可。

**② MySQL —— 按你的要求继续用 3307**
- 现状：你机器上原有的 MySQL80 服务在 3306，root 密码未知（试过空、123456、1145、0721、1234 等均被拒），因此本项目另起了一个 8.0 实例在 3307（数据目录 `mysql-data`，root/123456），与系统实例互不影响。
- 已确认：**继续使用 3307 这套**，`dbcp.properties` 不动；3306 的系统实例保持原样，不读不写。
- 若哪天你想救回系统实例密码：用管理员权限运行本目录下的 `fix-mysql-root-password.bat`（把 3306 的 root 重置为 123456），再把 `dbcp.properties` 的端口改回 3306 重打包即可。

**③ Redis / 授权服务 / Nginx 都是本机进程**
- 现在用 `start-all.ps1` 拉起，**关机后需重新执行**；没有注册成 Windows 服务。
- 如果你希望开机自启，我可以再加计划任务或注册服务。

**④ 手册中的实验内容还没写代码**
- 现有 `mes` 业务应用只有 8 个模型：Material、Supplier、Product、ProductBom、ProcessRoute、Process、ProcessBom、Order。
- 手册要求的 **编码规则 CodeRule / CodeRuleDetail（前缀+日期+流水号、服务生成递增编码，并重写 Material.create 自动取号）目前不存在**；Material 现在用的是应用自带的 `CodeGenerator.generateCode("M")`（生成形如 `M2026103022262019900001`）。
- 四个实训任务（产品 BOM `@Unique` 校验、编码规则 `@Selection`、数据隔离 `isAutoLog/create_user`、APP2 服务扩展）也都还没实现。
- 需要你确认：要不要我按手册把 CodeRule/CodeRuleDetail 及实训任务直接写进 `sie-snest-mes`（或新建 `sie-snest-lab` 应用）。

**⑤ 账号体系 —— 已按手册补齐**
- 手册账号 `admin_000001 / Admin_000001` 已随"演示租户"创建而自动生成（系统规则：`admin_<租户编码>` / `Admin_<租户编码>`），实测两个租户路径都能登录，角色为 `rbac_role_tenant_admin`。
- 平台侧另有 `implementer / Implementer_2023`、`admin / admin`、`superuser / superuser`。

---

## 6. 已验证的功能

- 后端从源码 `mvn clean package` 构建成功（Reactor 5 模块全 SUCCESS），Spring Boot 可执行 jar 正常启动：`Started DemoServer`，建表 79 张，12 个应用 `state=installed`。
- 前端经 Nginx 访问 200（`/iidp/`、`/config/apps.json`），接口文档页可打开。
- RPC 登录（`POST /api/root/rpc/login`）返回 token；`ui_menu.loadMenu`、`rbac_user.profile` 正常。
- 以 `implementer` 身份调用 MES 服务：`material.create` 成功（自动生成编码 `M2026103022262019900001`、写入 `create_user`）、`material.search` 返回数据。
- 真实 MinIO 文件上传：`POST /api/file/upload`（需 `Authorization: Bearer <token>`）返回 `bucket=apps`、`key=2026-10-30/...txt`、`md5`，对象已落在 `runtime\minio\data\apps\` 下，可经 `/api/file/download` 取回。
- 5 个 `-web` 应用的 `view_file` 已按 `platform-web-app-init.sql` 更新。
- 租户链路：`implementer` 登录 → RPC 建租户"演示租户"（`code=000001`、`level=1`、`max_hierarchy=2`、`max_children=99`）→ 授权 14 项权限 → `admin_000001 / Admin_000001` 登录成功（`rbac_role_tenant_admin`），`ui_menu.loadMenu` 返回 38 条菜单（MES 7 条 + 平台应用 31 条）。
- 对齐后的冒烟：`implementer` 建物料 → 编码 `M2026103109430324700001`（`create_user=rbac_user_implementer`）；search 返回该行；库排序规则为 `utf8mb4_bin`；Redis 6379 无密码、db5 有 220 个键。

---

## 7. 与手册搭建步骤的对照

| # | 手册步骤 | 现状 |
| --- | --- | --- |
| 1 | 建库 `utf8mb4` / `utf8mb4_bin` | ✅ 已按此建库（`iidp_demo_v3`） |
| 2 | 启动 `redis-server`（6379） | ✅ 6379，无密码，db 5 |
| 3 | 启动 `grant-dev`（8080） | ✅ `iidp-frontend-dist\grant\grant-dev.exe` |
| 4 | 把 `maven/repository` 拷到 `~/.m2` | ✅ 已拷贝到 `C:\Users\Vanlia\.m2\repository`（13020 文件 / 1.0GB，含 `com\sie\meta` 32 个 artifact） |
| 5 | 后端（8061） | ✅ Spring Boot jar，`Started DemoServer`，79 张表 |
| 6 | Nginx（30666）+ 前端 dist | ✅ `http://localhost:30666/iidp/` |
| 7 | 执行 `iidp-backend-demo/sql` 里的脚本 | ✅ `platform-web-app-init.sql` 已执行（5 个 `-web` 应用 `view_file` 生效） |
| 8 | 访问 `http://localhost:30666/iidp/` | ✅ 200 |
| 9 | `implementer / Implementer_2023` 登录 | ✅ 实测通过 |
| 10 | 建租户：最大层级 2、最大数量 99 | ✅ 已建"演示租户"（`000001`，level 1，层级 2 / 数量 99），并完成授权（14 条 `rbac_role_auth`） |
| 11 | `admin_000001 / Admin_000001` 登录 | ✅ 实测通过（角色 `rbac_role_tenant_admin`，登录后菜单 38 项） |

> 说明：手册第 1 步的排序规则、第 2 步的 Redis 端口、第 4 步的 `~/.m2`、第 10–11 步的租户与账号，都已在 `2026-10-31` 按手册对齐；其余步骤原本就满足。

---

## 8. 教程新增示例：工厂 / 区域 / 设备（含"检查设备状态"按钮服务）

对应教程里"新建区域 + 设备模型 → 写 `@MethodService check` → 在视图按钮里调服务 → 返回 `JsonRpcServiceResponse.reloadWindow()` → 对 SKJ-0001 故意抛异常"这一整套操作，已写进 `sie-snest-mes` 应用。

### 8.1 模型

目录：`iidp-backend-demo\sie-iidp-demo-apps\sie-snest-mes\src\main\java\com\sie\app\mes\model\`

| 文件 | `@Model` | 表名 | 主要字段 |
| --- | --- | --- | --- |
| `Factory.java` | `factory` / 工厂 | `ems_factory` | name、code、address、description |
| `Area.java` | `area` / 区域 | `ems_area` | name（`displayForModel=true`）、code、description；另有 `@OneToMany List<Device> devices`（设备列表） |
| `Device.java` | `device` / 设备 | `ems_device` | name、code、status（`@Selection` 在线/离线）、`@ManyToOne Area area`、purchaseDate（`DataType.DATE`） |

关键写法（与教程一致）：

```java
@Property(displayName = "设备状态")
@Selection(values = {
        @Option(value = "online", label = "在线"),
        @Option(value = "offline", label = "离线")
})
private String status;

@Property(displayName = "区域")
@ManyToOne
@JoinColumn
private Area area;
```

> 驼峰属性会自动映射下划线列：`purchaseDate` → `purchase_date`，写 SQL 查库时要用列名。

### 8.2 服务：`Device.check`

```java
@MethodService(name = "check", auth = "check", description = "检查设备状态")
public JsonRpcServiceResponse check(String[] ids) {
    Device device = selectById(ids[0]);
    String curStatus = device.getStatus();
    if ("offline".equals(curStatus)) {
        device.setStatus("online");
    } else {
        device.setStatus("offline");
    }
    device.update();
    if ("SKJ-0001".equals(device.getCode())) {
        throw new RuntimeException("Device SKJ-0001 is not allowed to check");
    }
    return JsonRpcServiceResponse.reloadWindow();
}
```

- `JsonRpcServiceResponse` 常用静态方法：`reloadWindow()`（整页刷新，返回 `{"action":"js","js":"window.location.reload()"}`）、`reloadTable()`、`refresh(...)`、`message(...)`、`openView(...)`。
- 服务返回的 `action: js` 会被前端执行，所以点按钮后页面会自动刷新、状态列更新。
- **SKJ-0001 会抛异常，整个 RPC 事务回滚**：`update()` 虽然执行了但不会落库，所以该设备状态保持原样，接口返回 `error.code = 504`。

### 8.3 视图与菜单

- `views\mes-device-view.json`：`device_grid.body.buttons` 增加一个按钮（与"编辑""删除"并列）：

```json
{
  "auth": "check",
  "name": "检查设备状态",
  "model": "device",
  "service": "check"
}
```

  `columns` 为 purchaseDate / name（`width: 80px`、`sortable: true`）/ code / area / status。
- `views\mes-factory-view.json`：factory_grid / factory_form / factory_search。
- `views\menus.json`：`factory_menu`（sequence 7）→ `area_menu`（8）→ `device_menu`（9），都挂在 `mes_root_menu` 下，`view` 顺序统一为 `search,grid,form`。
- `app.json` 的 `"view"` 数组已追加 `mes-factory-view.json`、`mes-device-view.json`。

### 8.4 复现命令（PowerShell，经 Nginx）

```powershell
$login = Invoke-RestMethod -Uri "http://127.0.0.1:30666/api/root/rpc/login?model=&service=" -Method Post -ContentType "application/json" -Body '{"jsonrpc":"2.0","method":"login","id":"1","params":{"login":"implementer","password":"059b7e90b851797c848e610a212cb915","app":"sie-iidp-iam","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
$token = $login.result.data.token
$H = @{ "Authorization" = "Bearer $token"; "token" = $token; "Content-Type" = "application/json" }

# 调用 check：ids 传设备 id 数组
$body = '{"jsonrpc":"2.0","method":"service","id":"2","params":{"args":{"ids":["<设备id>"]},"app":"mes","service":"check","model":"device","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
Invoke-RestMethod -Uri "http://127.0.0.1:30666/api/root/rpc/service/?model=device&service=check" -Method Post -Headers $H -Body $body
```

### 8.5 实测结果

- `area.create` → `06buo2ryc2hst`（一号车间 / A01）；`factory.create` → `06buo9suhza8n`（苏州一厂 / FAC-001）。
- `device.create` 两台：`SKJ-0001`（offline）、`SKJ-0002`（offline）。
- `device.check` SKJ-0002 → 返回 `{"action":"js","js":"window.location.reload()"}`，状态 offline → online；再调一次变回 offline。
- `device.check` SKJ-0001 → 返回 `{"error":{"code":504,"message":"Device SKJ-0001 is not allowed to check",...}}`，**该设备状态仍为 offline**（异常回滚）。
- UI 路径：用 `admin_000001 / Admin_000001`（或 `implementer`）登录 → 左侧 MES → 设备管理 → 勾选一行点"检查设备状态" → 页面刷新、状态列变化；SKJ-0001 会弹错误提示。

> 说明：`Factory` 的字段（name/code/address/description）是**按你截图里的 `factory_menu` 推断补的**，教程没给具体字段；如果你的工厂模型字段不一样（比如需要"所属公司""负责人"），告诉我改一版即可。

---

## 9. 模型扩展 / 继承 / 批量服务（对齐培训方 `sie-snest-mes.zip`）

培训方给的 `sie-snest-mes.zip` 里除了物料等基础模型，还演示了"模型扩展、模型继承、Filter 批量查询"。这三块已按同样写法补进本工程：

### 9.1 扩展：`AreaExt`

`model\AreaExt.java`：

```java
@Model(name = "area", parent = "area")   // name == parent → 扩展
public class AreaExt extends BaseModel<AreaExt> {
    @Property(displayName = "区域负责人")
    private String areaManager;
}
```

- 效果：**不新建表**，`ems_area` 直接多出一列 `area_manager`；`area` 模型的 search/create/update 都能读写它。
- 也可以在这个类里写与 `Area` 同名的 `@MethodService` 来覆盖父模型服务。

### 9.2 继承：`BookDevice`

`model\BookDevice.java`：

```java
@Model(name = "book_device", parent = "device", displayName = "预约设备")  // name != parent → 继承
public class BookDevice extends BaseModel<BookDevice> { }
```

- 效果：新建表 `book_device`，**完整继承** device 的字段与服务。实测该模型的权限里自动出现了 `check`、`modifyStatus`、`create`、`search`、`count`、`delete` 等全部服务（`mes.master.book_device.*@Service`），说明服务也被继承了。
- 实测 `book_device.create` 成功（返回 id `06bwd04nwo1gj`）。

### 9.3 批量服务：`Device.modifyStatus`

```java
@MethodService(name = "modifyStatus", auth = "modifyStatus", description = "批量将离线设备置为在线")
public void modifyStatus() {
    List<Device> offlineDevices = search(Filter.equal("status", "offline"),
            Collections.singletonList("*"), 0, 0, null);
    List<Area> areas = new Area().search(Filter.EMPTY_FILTER,
            Collections.singletonList("*"), 0, 0, null);
    for (Device device : offlineDevices) {
        device.setStatus("online");
        device.update();
    }
}
```

- 演示要点：`Collections.singletonList("*")` 查全部字段、`limit=0` 不分页、`Filter.EMPTY_FILTER` 不过滤、`Filter.equal(...)` 精确过滤。
- 实测：调用前 SKJ-0001 为 offline，调用后变 online（`update_date=2026-10-31 23:03:43`）。

### 9.4 设备表格新增「详情」按钮

`views\mes-device-view.json` 的 `device_grid.body.buttons` 现在是三段：详情（`auth=read` / `action=preview`）、编辑（`auth=update` / `action=edit`）、检查设备状态（`auth=check` / `model=device` / `service=check`），与培训方一致。权限码自动生成：`device_grid.grid.btn.detail@View`、`.btn.update@View`、`.btn.check@View`。

### 9.5 与培训方原包的差异（有意保留的）

| 项 | 培训方 | 本工程 | 原因 |
| --- | --- | --- | --- |
| `Device.create` 重写 | 把所有新建设备的 code 固定写成 `0001` | 未重写，用默认 create | 固定 code 会导致多台设备编码重复；本工程物料/设备用 `CodeGenerator` 或手工编码，更贴近真实用法 |
| `Factory` 字段 | 只有 name、code | name、code、address、description | 字段多了不影响演示；如要与培训方完全一致，删掉两个字段即可 |
| `Material.supplier` | `@ManyToOne Supplier supplier` | 与培训方一致 | — |
| `app.json` 的 `data` | 空数组 | 带 `mes-seed.json`（字典 + mes 角色授权） | 没有字典数据时新增物料会报"字典配置有误"；没有角色授权时非超管调用 403 |
| 视图文件名 | `device-view.json`、`area-view.json` | `mes-device-view.json`、`mes-factory-view.json`… | 只是命名风格差异，`app.json` 里登记正确即可 |
---

## 10. 教程新增：编码规则（CodeRule / CodeRuleDetail）

第二天课件 §3.5 的「编码规则」，已按「编码规则 + 编码规则明细 + 查询流水号服务」落地，并让物料新建自动取号。

### 10.1 新增/改动的文件

| 文件 | 说明 |
| --- | --- |
| `model/CodeRule.java` | 编码规则模型（表 `ems_code_rule`） |
| `model/CodeRuleDetail.java` | 编码规则明细（表 `ems_code_rule_detail`），记录某编码当前流水号 |
| `util/CodeRuleGenerator.java` | 生成器：前缀 + 日期段 + 补零流水号，流水号按「规则 + 日期段」累计 |
| `views/mes-code-rule-view.json` | 编码规则 表格/表单/搜索 三个视图（表单带「规则明细」一对多页签） |
| `app.json` | `view` 追加 `mes-code-rule-view.json` |
| `views/menus.json` | 追加 `code_rule_menu`（编码规则，sequence 10） |
| `data/mes-seed.json` | 追加种子 `mes_code_rule_material`（物料编码：M + yyyyMMdd + 4 位） |
| `model/Material.java` | `create` 改为 `CodeRuleGenerator.generate("material")` 自动取号 |

### 10.2 模型字段

**编码规则（code_rule）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | String | 规则编码，业务唯一标识（`material` / `device` …），`displayForModel=true` |
| `name` | String | 规则名称 |
| `prefix` | String | 编码前缀，如 `M` |
| `serialLength` | Integer | 流水号长度（不足补 0），默认 4 |
| `dateFormat` | String | 日期格式，`@Selection` 下拉：`none` / `yyyy` / `yyyyMM` / `yyyyMMdd` |
| `details` | List | `@OneToMany` 规则明细，表单单据体页签 |

**编码规则明细（code_rule_detail）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | String | 对应规则编码 |
| `dateKey` | String | 日期段，如 `20261031`；不带日期时为空串 |
| `currentSerial` | Integer | 当前已用流水号 |
| `rule` | ManyToOne | 所属规则（外键列 `rule` 存规则主键 id） |

### 10.3 服务

| 模型 | 服务 | 入参 | 返回 |
| --- | --- | --- | --- |
| `code_rule` | `nextCode` | `{"code":"material"}` | 下一个编码字符串，同时流水号 +1 落库 |
| `code_rule_detail` | `currentSerial` | `{"code":"material"}` | 当前流水号（只读，不递增） |

表格页签「试算下一个编码」按钮就是调 `code_rule.nextCode`（权限码 `code_rule_grid.grid.btn.nextCode@View`）。

### 10.4 复现命令

```powershell
# 生成下一个编码
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=code_rule&service=nextCode" `
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -H "token: <token>" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"2","params":{"args":{"code":"material"},"app":"mes","service":"nextCode","model":"code_rule","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'

# 查询当前流水号
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=code_rule_detail&service=currentSerial" `
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -H "token: <token>" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"3","params":{"args":{"code":"material"},"app":"mes","service":"currentSerial","model":"code_rule_detail","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
```

### 10.5 实测结果

| 操作 | 结果 |
| --- | --- |
| `code_rule.nextCode` 第 1 次 | `M202610310001` |
| `code_rule.nextCode` 第 2 次 | `M202610310002` |
| `code_rule_detail.currentSerial` | `2` |
| `material.create`（1 条） | 落库 code = `M202610310003`，`create_user=rbac_user_implementer` |
| `ems_code_rule_detail` | 1 行：`code=material`、`date_key=20261031`、`current_serial=3` |
| `ui_menu.loadMenu` | 48 条，含 `code_rule_menu`（编码规则，seq 10） |

### 10.6 踩坑记录：外键列不能直接塞模型对象

第一版 `nextSerial` 里写 `detail.setRule(rule)`，引擎把模型对象当参数绑到 SQL，报：

```
Cannot convert class com.sie.app.mes.model.CodeRule to SQL type requested
due to com.mysql.cj.exceptions.WrongArgumentException - Invalid argument value: java.io.NotSerializableException
```

外键列存的是**被关联记录的主键**，应写 `detail.set("rule", rule.getId());`。改完即通过。

### 10.7 并发说明

`CodeRuleGenerator.generate` 用 `synchronized` 保证单实例内串行；若后端多实例部署，需要改成数据库层原子自增（`UPDATE ... SET current_serial = current_serial + 1` 后回读）或行锁，否则可能重号。
---

## 11. 第三天：新建第二个业务 APP（WMS 仓储管理系统）

第三天课件（`Sie-培训-华师-第三天.pdf`）的 12 步是「新建一个 APP」的完整流程，本文档按同样顺序落地。

> 与课件的一处差异：课件第 1 步是「先删除 mes 下昨天新加的模型和视图」，本次**按你的选择不删**，mes 里的 device / area / factory / code_rule 全部保留，新 APP 独立新增。

### 11.1 12 步对照

| 课件步骤 | 本次落地 |
| --- | --- |
| 1. 删除 mes 下昨天新加的模型和视图 | 跳过（保留 mes 现有功能） |
| 2. 在 `sie-iidp-demo-apps` 下创建新 module | 新建 `sie-iidp-demo-apps\sie-snest-wms` |
| 3. 选 module 的 parent 为 `sie-iidp-demo-apps` | `pom.xml` 里 `<parent>` 指 `com.sie.iidp:sie-iidp-demo-apps:${revision}` |
| 4. 参考 mes 的 pom 修改 | 复制 mes 的 `build/resources`（`src/main/java` 下非 `.java` 文件一并打包）、插件继承父 pom 的 `sie-snest-maven-plugin` |
| 5. 创建 app.json | `src/main/java/com/sie/app/wms/app.json`，`name=wms`、`displayName=WMS-APP`、`category/product=WMS` |
| 6. 声明模型 | `model\Warehouse.java`、`model\Location.java` |
| 7. 声明菜单 | `views\menus.json`：`wms_root_menu`（仓储管理系统）+ `warehouse_menu` + `location_menu` |
| 8. 配置文件加内置 app 声明 | `application-dev.properties` 的 `image.minimal.requiredApps` 末尾加 `,wms` |
| 9. `apps/apps.json` 加 jar 包声明 | `"sie-snest-wms-1.0-SNAPSHOT.jar"` |
| 10. 重启服务 | `mvn clean package -DskipTests` 后 `java -jar` 重启，日志出现 `加载SDK应用:[wms]`、`meta_app` 插入 `wms / WMS-APP / installed` |
| 11. 开发者中心生成视图并保存到本地 | 本次**手写**视图 JSON（`wms-warehouse-view.json`、`wms-location-view.json`）放在 `views\`，与开发者中心生成的结果等价 |
| 12. 重启服务，对租户授权新 app | `rbac_tenant.listPermission` 取到 `WMS-APP(wms)`，再 `rbac_tenant.grant` 授权给「演示租户」，`admin_000001` 的 `loadMenu` 由 38 条变 46 条（含仓储管理系统） |

另外为「装到全新库也能用」，补了一份种子 `data\wms-seed.json`：给 `rbac_role_implementer_admin`、`rbac_role_admin` 两个角色补 `appName=wms` 的 `rbac_role_auth`（`appAll=1`），否则非超管调用 wms 模型服务会 403。

### 11.2 新增文件

```
sie-iidp-demo-apps\sie-snest-wms\
├── pom.xml
└── src\main\java\com\sie\app\wms\
    ├── app.json
    ├── data\wms-seed.json
    ├── model\Warehouse.java      仓库：name/code/address/manager/description + @OneToMany locations
    ├── model\Location.java       库位：name/code/description + @ManyToOne warehouse
    └── views\
        ├── menus.json
        ├── wms-warehouse-view.json    warehouse_grid / warehouse_form（库位 tab）/ warehouse_search
        └── wms-location-view.json     location_grid / location_form / location_search
```

### 11.3 模型字段

`warehouse`（表 `wms_warehouse`）：`name` 仓库名称（`displayForModel=true`）、`code` 仓库编码、`address` 仓库地址、`manager` 负责人、`description` 描述、`locations` 库位列表（`@OneToMany`）。

`location`（表 `wms_location`）：`name` 库位名称（`displayForModel=true`）、`code` 库位编码、`warehouse` 所属仓库（`@ManyToOne` + `@JoinColumn`）、`description` 描述。

### 11.4 实测结果

| 操作 | 结果 |
| --- | --- |
| 启动日志 | `加载SDK应用:[wms]`、`更新表 wms_warehouse / wms_location`、`Started DemoServer in 5.562 seconds` |
| `meta_app` | 13 行，新增 `wms / WMS-APP / installed` |
| `warehouse.create`（2 条） | `WH-002 苏州二号仓`、`WH-001 上海一号仓` |
| `location.create`（3 条） | `LOC-A0101`、`LOC-A0102`（一号仓）、`LOC-B0201`（二号仓） |
| `warehouse.search` / `location.search` | 分别返回 2 条 / 3 条 |
| `tenant_permission` | 新增 27 条 `wms.*` 服务权限 + `warehouse@Model`、`location@Model`、`warehouse_grid.grid@View`、`warehouse_form.form.prop.tab.0@View`、`wms_root_menu@Menu` 等 |
| `ui_menu.loadMenu`（implementer） | 52 条，含 `仓储管理系统 / 仓库管理 / 库位管理` |
| `ui_menu.loadMenu`（admin_000001，租户 000001） | 46 条，同样含三个 wms 菜单 |

### 11.5 RPC 复现

```powershell
# 仓库
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=warehouse&service=create" `
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -H "token: <token>" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"1","params":{"args":{"valuesList":[{"name":"上海一号仓","code":"WH-001","address":"上海市浦东新区","manager":"张三"}]},"app":"wms","service":"create","model":"warehouse","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'

# 库位（warehouse 传仓库 id）
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=location&service=create" `
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -H "token: <token>" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"2","params":{"args":{"valuesList":[{"name":"A-01-01","code":"LOC-A0101","warehouse":"<warehouse id>"}]},"app":"wms","service":"create","model":"location","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
```

### 11.6 踩坑记录

1. **非超管 403**：新 app 装好后 `rbac_role_auth` 里没有它的记录，implementer 调 `warehouse.create` 直接 403 `无权访问：模型[warehouse] 服务[create]`。补 `appName=wms / appAll=1` 的角色授权即可（已写进 `wms-seed.json`，下次全新库自动生效）。
2. **租户看不到菜单**：只建 app 不授权租户，`admin_000001` 的 `loadMenu` 里没有仓储管理系统；必须走 `rbac_tenant.grant`（课件第 12 步）。
3. **视图目录命名**：menu 里的 `view` 串要和视图 JSON 里的 key 完全一致（`warehouse_search,warehouse_grid,warehouse_form`），否则菜单点开是空白。
4. **密码要传 MD5，不是库里那串哈希**：`rbac_user.password` 存的是 `sha256(md5(明文)+salt)`，而登录接口的 `password` 参数要传 **`md5(明文)`**。用库里的哈希去登录一定「用户名或密码错误」。四个账号的 MD5 值：`admin`=`21232f297a57a5a743894a0e4a801fc3`、`Implementer_2023`=`059b7e90b851797c848e610a212cb915`、`superuser`=`0baea2f0ae20150db78f58cddac442a9`、`Admin_000001`=`e1a853c9c2b9960305b90c79a12f1b33`（四个账号均已实测登录成功）。
## 12. 第三天第二批：编码规则（BarcodeRule）与服务扩展（OrderExt）

这一批来自培训方发来的 `sie-iidp-demo-apps.zip`（含完整的 `sie-snest-mes` 与 `sie-snest-wms` 两个 APP 源码）。核心两件事：**把编码规则做成通用服务**，以及**用模型扩展改写另一个 APP 的服务**。

### 12.1 与培训方参考实现的对照

| 项 | 培训方 `sie-snest-wms` | 本项目 `sie-snest-wms` | 说明 |
| --- | --- | --- | --- |
| app.json `name` | `sie-snest-wms` | `wms` | 应用名只是标识；改名的代价是要重新授权、`meta_app` 里会残留旧行，因此保持 `wms` |
| Java 包名 | `com.sie.iidp.wms` | `com.sie.app.wms` | 同上，与 mes 的 `com.sie.app.mes` 保持一致 |
| 编码规则模型 | `barcode_rule`（code / prefix / length）+ `generateCode` | 同左，逐字对齐 | ✅ |
| 编码明细模型 | `barcode_rule_detail`（code / seq） | 同左 | ✅ |
| 服务扩展 | `OrderExt`（`@Model(name="order", parent="order")`） | 同左 | ✅ |
| 设备模型 | 在 wms 里重建一个精简版 `Device`（name / code） | 未重建 | mes 里已有完整的 `device`（含区域、状态、check 服务），同名模型再声明会冲突 |
| 菜单 | `wms_root_menu` + `device_menu` + `barcode_rule_menu` | `wms_root_menu` + `warehouse_menu` + `location_menu` + `barcode_rule_menu` | 保留上一批的仓库/库位 |
| 视图 | `barcode-rule-view.json`、`device-view.json` | `barcode-rule-view.json`（+ 仓库/库位视图） | ✅ |

### 12.2 新增/改动的文件

```
sie-iidp-demo-apps/sie-snest-wms/src/main/java/com/sie/app/wms/
├── model/BarcodeRule.java          # 新增：编码规则 + generateCode 服务
├── model/BarcodeRuleDetail.java    # 新增：编码规则明细（记录当前流水号）
├── model/OrderExt.java             # 新增：扩展 mes 的 order，重写 create
├── views/barcode-rule-view.json    # 新增：编码规则的 grid/search/form
├── views/menus.json                # 改：追加 barcode_rule_menu（sequence 3）
├── data/wms-seed.json              # 改：追加 order/material/device 三条编码规则
└── app.json                        # 改：view 加 barcode-rule-view.json，data 指向 wms-seed.json

sie-iidp-demo-apps/sie-snest-mes/src/main/java/com/sie/app/mes/model/Order.java
└── 改：增加 @Property(displayName = "编码") private String code;
```

> `Order` 原本没有 `code` 字段，扩展服务的 `map.put("code", ...)` 会没有落点，所以先给 APP1 的订单模型加了 `code` 列（引擎会自动 ALTER TABLE 加列）。

### 12.3 编码规则是怎么工作的

`BarcodeRule.generateCode(String code)`：

1. 用 `Filter.equal("code", code)` 查出规则（前缀 + 流水号长度），查不到直接 `throw new RuntimeException("编码规则不存在")`；
2. 去 `barcode_rule_detail` 找这个编码对应的明细行；
3. 没有明细 → 新建一行 `seq = 1`；有明细 → `seq = 当前值 + 1` 并 `update()`；
4. 返回 `前缀 + 补零到指定长度的流水号`，例如 `PO` + `%06d` → `PO000001`。

种子数据里预置了三条规则：

| code | prefix | length | 用途 |
| --- | --- | --- | --- |
| `order` | `PO` | 6 | 生产订单编号 |
| `material` | `MT` | 4 | 物料编号 |
| `device` | `SKJ-` | 4 | 设备编号 |

### 12.4 服务扩展：在 WMS 里改 MES 的订单服务

```java
@Model(name = "order", parent = "order")   // name 与 parent 相同 = 扩展
public class OrderExt extends BaseModel<OrderExt> {

    @MethodService
    public RecordSet create(List<Map<String, Object>> valuesList) {
        for (Map<String, Object> map : valuesList) {
            String orderCode = new BarcodeRule().generateCode("order");
            map.put("code", orderCode);
        }
        return (RecordSet) getMeta().get("order")
                .callSuper(OrderExt.class, "create", valuesList);
    }
}
```

要点：

- `name == parent` 表示**扩展**（同一模型，跨 APP 追加/改写服务）；`name != parent` 才是**继承**（新模型，继承父模型全部属性与服务）。
- 改写后必须用 `getMeta().get("order").callSuper(...)` 把请求交回父模型（MES）的 `create`，否则订单不会真正落库。
- 调用时 `params.app` 必须是 `wms`，引擎才会路由到扩展后的服务；`app=mes` 走的仍是原始 `Order`。

### 12.5 实测结果（implementer，经 nginx 30666）

| 调用 | 结果 |
| --- | --- |
| `barcode_rule.generateCode {"code":"order"}` | `PO000001` |
| 再调一次 | `PO000002` |
| `app=wms, model=order, service=create {"valuesList":[{"quantity":10,"status":"1"}]}` | 返回 id `06byl37crjqvc` |
| `app=mes, model=order, service=search` | `{"code":"PO000003","quantity":10,"status":"1"}` |
| `ui_menu.loadMenu`（implementer） | 53 条，含「仓储管理系统 / 仓库管理 / 库位管理 / 编码规则」 |

`barcode_rule_detail` 里 `code=order` 的记录 `seq=3`，说明流水号在「直接调服务」和「订单扩展调用」之间是连续递增的。

### 12.6 复现命令

```powershell
# 1. 生成一个编码（会自增流水号）
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=barcode_rule&service=generateCode" `
  -H "Content-Type: application/json" -H "Authorization: Bearer $token" -H "token: $token" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"1","params":{"args":{"code":"order"},"app":"wms","model":"barcode_rule","service":"generateCode","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'

# 2. 用 WMS 的扩展服务建订单（自动取号）
curl.exe -s -X POST "http://127.0.0.1:30666/api/root/rpc/service/?model=order&service=create" `
  -H "Content-Type: application/json" -H "Authorization: Bearer $token" -H "token: $token" `
  --data-binary '{"jsonrpc":"2.0","method":"service","id":"1","params":{"args":{"valuesList":[{"quantity":10,"status":"1"}]},"app":"wms","model":"order","service":"create","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
```

> Windows PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，必须写全 `curl.exe`。

### 12.7 新增模型后要重启并检查的三件事

1. **重启后端**（`stop-all.ps1` → `start-all.ps1`，或直接重启 backend 进程），模型/视图/菜单才会重新解析；
2. **租户授权**：wms 应用已经整应用授权（`rbac_role_auth` 里 `appAll=1`），新增服务会自动纳入；如果是新建的应用，要去「租户管理 → 授权」里勾上新应用，否则菜单看不到、调用 403；
3. **种子数据**：`wms-seed.json` 里给编码规则写了固定 `id`（`wms_barcode_rule_order` 等），重启不会重复插入；`app.json` 的 `data` 必须引用这个文件才会被加载。