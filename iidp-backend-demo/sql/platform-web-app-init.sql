-- ============================================================
-- 平台前端应用与数据库记录的关联 SQL（首次初始化数据库后必须执行一次）
--
-- 作用：
--   把平台侧四个前端应用关联到本地 umdComps 包名。前端只有拿到应用包，
--   才会注册这些应用里的自定义动作（弹窗、生成视图等）。
--
-- 分两组：
--   前三行 → 影响「多租户 → 租户管理 → 授权」弹窗能否打开
--   第四行 → 影响「开发者中心 → 模型管理 → 生成视图」能否弹出结果
--
-- 现象对照：
--   未执行时页面能打开、按钮能点，但点了没有任何反应，且控制台不报错
--   （动作由前端应用注册，包没加载就没有绑定，后端接口其实是正常的）。
--
-- 执行方式（数据库名以实际为准，本项目为 iidp_demo_v3）：
--   mysql -h127.0.0.1 -P3306 -uroot -p iidp_demo_v3 < sql/tenant-grant-init.sql
--   或进入 mysql 客户端后：USE iidp_demo_v3; SOURCE sql/tenant-grant-init.sql;
-- ============================================================

-- 一、租户授权依赖
UPDATE meta_app SET view_file = 'sie-iidp-app-store-web' WHERE name = 'sie-iidp-app-store';
UPDATE meta_app SET view_file = 'sie-iidp-iam-web'       WHERE name = 'sie-iidp-iam';
UPDATE meta_app SET view_file = 'sie-iidp-tenant-web'    WHERE name = 'sie-iidp-tenant';

-- 二、开发者中心（模型管理的「生成视图」依赖）
UPDATE meta_app SET view_file = 'sie-iidp-dev-center-web' WHERE name = 'sie-iidp-dev-center';

-- 三、国际化（界面语言切换入口依赖，需先安装 sie-iidp-i18n 后端应用）
UPDATE meta_app SET view_file = 'sie-iidp-i18n-web' WHERE name = 'sie-iidp-i18n';

-- 校验：应返回 5 行，view_file 均不为空
SELECT name, view_file
FROM meta_app
WHERE name IN ('sie-iidp-app-store', 'sie-iidp-iam', 'sie-iidp-tenant',
               'sie-iidp-dev-center', 'sie-iidp-i18n');
