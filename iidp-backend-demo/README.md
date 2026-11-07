# IIDP

# 目录说明

1. apps: 引擎启动所需基础应用目录
2. sie-iidp-demo-apps:  项目自行开发的 Demo APP。可以创建多个模块，一个模型对应一个 APP，例如我们当前有3个demo模块：DEMO、图书管理、练习
3. sie-iidp-demo-start: 引擎服务。通常只需要改动配置文件和升级引擎版本。

# 启动项目

## 设置 Maven

参考项目下的 `settings.xml` 文件，配置 maven 仓库。

## 修改数据库连接
 
修改 `sie-iidp-demo-start/src/main/resources/dbcp.properties` 里面的数据库连接

## 修改 APP 元数据来源

修改 `apps/apps.json` 文件中的 apps.API 节点，修改对应 APP 的 resolved 字段。对应获取 APP 元数据的地址。

# 接口
启动后可以访问 http://localhost:8060/root/api/master 调试接口

# 文档
IIDP开发手册： http://iidp.chinasie.com:9999/iidpdoc/