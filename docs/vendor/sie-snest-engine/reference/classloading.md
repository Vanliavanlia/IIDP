# 类加载体系 —— 每个 app 一个 ClassLoader 的隔离模型

> 包：`sie-snest-engine/.../engine/classload/`。整套是 **Spring Boot loader（spring-boot-loader 的 Archive/JarFile/LaunchedURLClassLoader）的 fork + 魔改**，改动点在"找不到类时的兜底委托"。理解它才能解释：app 之间依赖版本为什么不冲突、跨 app 强转为什么 CCE、arthas 为什么要 `--classLoaderClass`、卸载 app 为什么能释放 jar。

## 1. 三层加载器结构

```
Ext/Platform ClassLoader（JDK）
   ▲ parent（注意：不是引擎加载器！）
   │
MetaAppClassLoader（每个 app jar 一个；extends 魔改版 LaunchedURLClassLoader）
   │  URL = 该 app fat-jar 的 BOOT-INF/classes/ + BOOT-INF/lib/*.jar
   │
   ├─ 兜底① engineClassLoader（引擎自身的加载器）—— 仅限"引擎 API 导出白名单"包
   └─ 兜底② MetaLibClassLoader（公共库，静态单例）—— **代码存在但实际未启用**
                URL = apps/libs/*.jar（可配 app.install.jar.lib）；实际部署该目录不存在/无 jar，
                getLibsClassLoader 返回 null → 这条兜底从不生效，没有任何 jar 经它加载
```

- **app jar 是 Spring Boot fat-jar 布局**（`BOOT-INF/classes` + `BOOT-INF/lib`），由 `MetaAppArchive extends JarFileArchive` 解析；`app.json` 在 `BOOT-INF/classes/**/app.json`。
- 创建点：`AppLifecycleProcess`（应用市场安装/启动加载）`appInfo.setClassLoader(metaAppArchive.createClassLoader())`。
- **parent 是 Ext loader 而非引擎加载器**（`getExtClassLoader` 沿 getParent 到顶）——双亲委派只到 JDK，app↔引擎、app↔app 天然类隔离。

## 2. 查找顺序（MetaAppClassLoader.loadClass）

1. 双亲委派到 JDK（parent=Ext）；
2. 自己的 URL：app 自带的 `BOOT-INF/classes` + `BOOT-INF/lib` —— **app 优先用自己打包的依赖版本**，各 app 依赖互不冲突；
3. `ClassNotFoundException` 时进 `searchDependencyLoaders` 兜底：
   - `com.sie.snest.sdk.*` → **直接返回 null，不兜底**。SDK 类必须打进 app 自己的 fat-jar；每个 app 的 `BaseModel` 等 SDK 类是**独立的 Class 对象**（跨 app/引擎传值靠 `BaseModel extends LinkedHashMap` 的 JDK Map 接口 + 反射，不依赖共享类定义——这是 SDK"值存 map"设计的另一层原因）。
   - 命中 `engineProvidedGroupPrefix` 白名单 → 委托**引擎加载器**。白名单（`LaunchedURLClassLoader.java`）：`com.sie.snest.`（除 sdk）、`org.slf4j.`、`net.sf.cglib.`、`javax.`、`com.alibaba.fastjson.`、`com.fasterxml.jackson.`、`com.google.common.graph.`、`com.sie.iidp.fengine.`、`com.googlecode.aviator.`、`cn.tna.`。这就是"引擎只导出这些包作为 API"——`RecordSet`/`Meta`/`Filter` 等引擎类全 app 共享同一份。
   - 其它 → 委托 `MetaLibClassLoader`（公共库）——**实际部署中此加载器为 null，这步是空操作**，CNFE 直接向上抛。
4. `MetaLibClassLoader` 自己的逻辑是"白名单包先问引擎，再查 libs 目录"，但如上所述实际未启用。**有效的兜底只有引擎白名单一条**：app 用白名单外的任何依赖都必须自己打进 fat-jar。

## 3. 运行侧消费

- **`BaseMeta.getClassLoader()`**（所有元模型的入口）：按 `appName.tag` 从 `EngineContainer` 的三个 AppGroupContainer（base/business/workflow）找 `AppDataInfo` 拿该 app 的 loader；`loaderType=API` 的 app（引擎内部）直接用引擎加载器。
- `MethodMeta.getMethod()`：服务反射调用前用它 `loadClass(声明类).getDeclaredMethods()` —— 同时注意此处强制**方法名在类内唯一**（不支持重载，重名直接抛错）。
- `AppLoaderType`：`SDK`（普通业务 app，用 app 自己的 loader 加载 `SdkLoader` 解析 `@Model`）/ `API`（引擎内部）/ `JSON` / `DME`。
- `CustomerClassLoader`：另一个独立的简易加载器——把目录下散 `.class`/jar 预读进静态 `classMap`，遵守双亲委派，用于动态生成类的场景。

## 4. 卸载与热更新

`MetaAppClassLoader.close()` 会关掉 `rootArchive`（jar 文件句柄）→ 应用市场卸载/重装 app 时可释放并替换 jar。Windows 上 jar 句柄不关文件删不掉，这就是 close 链路存在的原因。卸载后旧 loader 及其所有类等 GC 回收。

## 5. 排查含义（坑）

1. **跨 app 同名类是不同 Class**：`instanceof` / 强转跨 app 对象会 `ClassCastException`；跨 app 协作永远走 `getMeta().get("模型名").call(...)`（字符串 + 反射），别 import 别的 app 的类。
2. **arthas 必须指定类加载器**：每个 app 一个 `MetaAppClassLoader` 实例，`ognl`/`watch` 用 `--classLoaderClass com.sie.snest.engine.classload.MetaAppClassLoader` 可能命中多个实例，需 `sc -d 类名` 查 hashcode 后用 `-c <hashcode>` 精确选择（引擎类则用 Spring Boot 的 LaunchedURLClassLoader）。详见 arthas-debug skill。
3. **NoClassDefFoundError 排查顺序**：类在不在 app 自己的 BOOT-INF/lib？→ 在不在引擎白名单前缀内？两条都不中就是没有（`apps/libs` 公共库实际未启用，别往那个方向查）。白名单外的引擎依赖 app 是看不见的（这是特性不是 bug），app 要用就自己打包。
4. **fastjson/jackson 在白名单里** → 全 app 共享引擎版本，app 自带的版本会被自己 URL 先命中……注意顺序：兜底只在**自己找不到**时才发生，所以 app 打包了 fastjson 就用自己的；没打包才用引擎的。序列化行为不一致时先确认实际加载源（`clazz.getClassLoader()`）。
5. **SDK 版本随 app 走**：app 编译时用什么版本的 sie-snest-sdk 就带什么版本运行，引擎升级不影响已安装 app 的 SDK 行为（兼容性由引擎白名单 API 保证）。

## 附：关键代码位置

- 加载器：`engine/classload/MetaAppClassLoader.java`、`LaunchedURLClassLoader.java`（白名单 `engineProvidedGroupPrefix` + `searchDependencyLoaders`）、`MetaLibClassLoader.java`、`CustomerClassLoader.java`
- fat-jar 解析：`engine/classload/MetaAppArchive.java`（`createClassLoader`、libs 目录 `getLibsClassLoader`）
- 创建点：`engine/container/AppLifecycleProcess.java`（`appInfo.setClassLoader(...)`）
- 消费点：`engine/model/BaseMeta.getClassLoader()`、`MethodMeta.getMethod()`、`ServiceMeta`、`AppDataInfo`（SdkLoader 加载）
