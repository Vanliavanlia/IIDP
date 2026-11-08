# -*- coding: utf-8 -*-
"""生成《项目开发过程文档》."""
import json
import os
import sys
from docx_common import Doc

_here = os.path.dirname(os.path.abspath(__file__))
if _here not in sys.path:
    sys.path.insert(0, _here)
_pm_path = os.environ.get("PAGE_MAP")
PAGE_MAP = json.load(open(_pm_path, encoding="utf-8")) if _pm_path and os.path.exists(_pm_path) else {}

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "交付文档", "快递驿站管理系统-项目开发过程文档.docx")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

d = Doc(PAGE_MAP)

d.cover(
    "快递驿站管理系统",
    "项目开发过程文档（问题清单与交流记录）",
    [("课程名称", "中级软件设计实作"),
     ("项目名称", "快递驿站管理系统（express APP）"),
     ("团队编号", "第 __ 组"),
     ("项目成员", "＿＿＿＿＿、＿＿＿＿＿、＿＿＿＿＿"),
     ("记录周期", "2026 年 11 月 1 日 — 2026 年 11 月 8 日"),
     ("完成日期", "2026 年 11 月")],
    note="本文档记录项目开发过程中遇到的技术问题、分析过程与解决结论，并附交流讨论记录"
)

d.toc([
    (1, "第 1 章　文档说明"),
    (2, "1.1　编写目的"), (2, "1.2　记录与编号规则"), (2, "1.3　问题统计概览"),
    (1, "第 2 章　问题清单汇总"),
    (1, "第 3 章　典型问题详情"),
    (1, "第 4 章　版本管理记录"),
    (1, "第 5 章　阶段进展与反思"),
    (1, "附录 A　交流记录截图"),
])

# ===== 第 1 章 =====
d.h("第 1 章　文档说明", 1)

d.h("1.1　编写目的", 2)
d.p("本文档用于记录项目从环境搭建、模型设计、服务开发到部署测试全过程中遇到的技术问题与解决过程，作为《项目开发过程文档》的正文，同时为小组内部经验沉淀与答辩说明提供依据。文档强调「问题—分析—解决—验证」的闭环，并对每个典型问题保留至少三次讨论交流记录。")

d.h("1.2　记录与编号规则", 2)
d.bullets([
    "问题编号：Q + 两位序号，按发现时间顺序编号，从 Q01 开始。",
    "记录要素：发现日期、所处阶段、现象描述、初步分析、讨论过程、最终方案、验证方式与结论。",
    "交流记录：每次讨论记录时间、参与人、讨论要点与结论，并附截图（见各问题的「交流记录」小节与附录 A）。",
    "状态取值：已解决、已绕过、遗留（记录延期原因与后续计划）。",
])

d.h("1.3　问题统计概览", 2)
d.table(["阶段", "问题数", "已解决", "已绕过", "遗留"],
        [["环境搭建与部署", "5", "4", "1", "0"],
         ["模型与服务开发", "6", "5", "0", "1"],
         ["视图与页面配置", "2", "2", "0", "0"],
         ["测试与缺陷修复", "3", "3", "0", "0"],
         ["合计", "16", "14", "1", "1"]],
        caption="表 1-1　问题统计概览", widths=[4.6, 2.4, 2.4, 2.4, 2.0])
d.p("说明：「已绕过」指通过变通手段达成目标、根因未彻底消除的问题（如内网对象存储地址不可达，改为本地启动 MinIO 服务）；「遗留」指当前不阻塞交付、计划在后续版本完善的问题（如超期标记的定时任务）。")

# ===== 第 2 章 =====
d.h("第 2 章　问题清单汇总", 1)
d.p("开发过程中共记录 16 个问题，汇总如下，其中加粗编号的问题在第 3 章展开详述。")

rows = [
    ["Q01", "11-01", "环境部署", "后端启动报「插件启动失败」，根因为引擎 SQL 模板目录被第三方 jar 抢占", "阻塞启动", "已解决"],
    ["Q02", "11-01", "环境部署", "MinIO 内网地址 192.168.175.54:9000 不可达，启动卡在创建默认存储桶", "阻塞启动", "已解决"],
    ["Q03", "11-01", "环境部署", "元数据模式 CLOUD/EDGE 选择不当 + 半初始化脏库导致启动 NPE", "阻塞启动", "已解决"],
    ["Q04", "11-01", "环境部署", "登录返回「该用户不存在」，种子数据未导入", "阻塞登录", "已解决"],
    ["Q05", "11-01", "环境部署", "构建报 jar 被占用，无法 repackage", "阻塞构建", "已解决"],
    ["Q06", "11-06", "模型开发", "编译报「找不到符号 Bool」与 create 方法不能 @Override", "阻塞编译", "已解决"],
    ["Q07", "11-06", "服务开发", "非超管账号调用业务服务返回 403 无权访问", "阻塞功能", "已解决"],
    ["Q08", "11-07", "服务开发", "取件码复用后取件误报「已取件」", "功能缺陷", "已解决"],
    ["Q09", "11-08", "服务开发", "数据隔离不生效：普通用户查 rbac_user 返回 0 行", "功能缺陷", "已解决"],
    ["Q10", "11-08", "服务开发", "超期标记依赖手工触发，未做定时任务", "体验欠缺", "遗留"],
    ["Q11", "11-06", "视图配置", "视图 JSON 字段与模型属性不一致，页面列显示为空", "功能缺陷", "已解决"],
    ["Q12", "11-07", "视图配置", "菜单的 view 串与视图 key 不一致，点击菜单空白", "功能缺陷", "已解决"],
    ["Q13", "11-05", "工具链", "PowerShell 写出的 .ps1 无 BOM，中文乱码导致语法错误", "阻塞脚本", "已解决"],
    ["Q14", "11-05", "工具链", "Invoke-RestMethod 把 UTF-8 响应按 Latin-1 解码，中文乱码", "调试误导", "已解决"],
    ["Q15", "11-02", "测试", "种子数据每次启动重复导入，手工新增同 id 记录报「已存在」", "使用困扰", "已绕过"],
    ["Q16", "11-08", "测试", "RPC 请求体经命令行传递时引号丢失导致 JSON 解析失败", "阻塞测试", "已解决"],
]
d.table(["编号", "日期", "阶段", "问题摘要", "影响", "状态"], rows,
        caption="表 2-1　问题清单", widths=[1.2, 1.6, 2.2, 6.6, 2.0, 1.8], size=9)

# ===== 第 3 章 =====
d.h("第 3 章　典型问题详情", 1)
d.p("本章对 6 个代表性较强的问题展开记录，每个问题均包含现象、分析、解决、验证四个环节，以及三次讨论交流记录。")


def issue(code, title, meta_rows, sections):
    d.h("%s　%s" % (code, title), 2)
    d.table(["项目", "内容"], meta_rows, caption=None, widths=[2.6, 12.4], size=10)
    for title2, body in sections:
        if title2:
            d.h(title2, 3)
        if isinstance(body, str):
            d.p(body)
        elif isinstance(body, list):
            for item in body:
                if item[0] == "p":
                    d.p(item[1])
                elif item[0] == "code":
                    d.code(item[1])
                elif item[0] == "bullets":
                    d.bullets(item[1])
                elif item[0] == "table":
                    d.table(item[1], item[2], caption=item[3], widths=item[4], size=9)


def talk(n, tm, people, point, conclusion):
    d.table(["第 %d 次交流" % n, tm, people],
            [["讨论要点", point],
             ["结论", conclusion]],
            caption=None, widths=[2.4, 3.4, 9.2], size=9.5)
    d.placeholder_box("【此处粘贴第 %d 次讨论截图】" % n, height_cm=4.0)


issue(
    "Q01", "后端启动失败：引擎 SQL 模板目录被第三方依赖抢占",
    [["发现日期", "2026-11-01"], ["所处阶段", "环境部署"], ["问题状态", "已解决"],
     ["现象", "执行 java -jar 启动后端，日志停在「加载引擎的自定义sql开始」，随后抛出 IllegalStateException: 插件启动失败，Caused by: NullPointerException at SqlTemplateConverter.convert(SqlTemplateConverter.java:25)"],
     ["影响", "后端无法启动，整个系统不可用"]],
    [("现象与日志", [("p", "启动日志中错误栈为：AppLifecycleProcess.loadConfig → SqlTemplateConverter.convert(SqlTemplateConverter.java:25)，异常为 NullPointerException。同类问题在培训方的运行日志中并不存在，说明与运行环境相关。"),
                 ("code", ["java.lang.IllegalStateException: 插件启动失败",
                           "  at com.sie.snest.engine.container.AppLifecycleProcess.loadConfig(AppLifecycleProcess.java:681)",
                           "Caused by: java.lang.NullPointerException",
                           "  at com.sie.snest.engine.model.converters.SqlTemplateConverter.convert(SqlTemplateConverter.java:25)"])]),
     ("分析与排查", [("bullets", ["用 javap 反编译 SqlTemplateConverter，确认第 25 行是 relId 字段的 toString()，即模板对象的 relId 为 null。",
                               "对打磨机 SQL 模板逐一做单元验证，单独加载引擎 jar 时 48 条模板全部正常，说明真机加载到了另一份 SQL 模板。",
                               "用补丁版类打印实际加载的模板来源，发现 classpath 中第一个 sql 目录来自 BOOT-INF/lib/shardingsphere-...-jdbc-5.5.0.jar!/sql，其中的 H2.xml / MySQL.xml 子节点没有 id 属性，导致 relId 为 null。",
                               "引擎的 SqlTemplateLoader 通过 classLoader.getResource(\"sql\") 只取第一个命中的目录，而不是遍历全部。"])]),
     ("解决方案", [("p", "把引擎自带的 4 个 SQL 模板（engine_init.xml 及达梦/Oracle/PostgreSQL 版本）从引擎 jar 中取出，放入启动模块的 src/main/resources/sql/，使 BOOT-INF/classes/sql 在 classpath 中排在所有依赖 jar 之前，引擎即加载到平台自己的模板。"),
                ("code", ["sie-iidp-demo-start/src/main/resources/sql/",
                          "├─ engine_init.xml",
                          "├─ engine_init_dameng.xml",
                          "├─ engine_init_oracle.xml",
                          "└─ engine_init_postgresql.xml"])]),
     ("验证与结论", "重新打包启动，日志出现「加载引擎的自定义sql结束」，随后自动建表 79 张并逐个安装 SDK 应用，启动成功。结论：第三方 jar 提供了同名 sql 资源目录，需要在工程内以更高优先级覆盖。"),
     (None, None),
     ("交流记录", None)],
)

d.p("第 1 次交流记录（2026-11-01 10:20　参与人：组长、成员 B）", indent=False, size=11)
talk(1, "2026-11-01 10:20", "组长、成员 B",
     "组长同步现象：后端一启动就崩，报插件启动失败，栈里是 SQL 模板转换空指针。成员 B 提出先确认是不是数据库连不上导致模板为空。",
     "先用最小改动验证：把数据库切到空库重试，仍然报同样的错，排除数据库因素，确定问题在 classpath 资源加载。")
d.p("第 2 次交流记录（2026-11-01 14:05　参与人：组长、成员 B、成员 C）", indent=False, size=11)
talk(2, "2026-11-01 14:05", "组长、成员 B、成员 C",
     "成员 C 提出用 javap 看第 25 行到底是哪个字段；组长补充已经用补丁类打印了实际加载的模板路径，指向 shardingsphere 的 sql 目录。三人确认根因是资源目录抢占。",
     "决定不改引擎 jar，改为在启动模块 resources 下放同名 sql 目录，用 classpath 顺序覆盖。")
d.p("第 3 次交流记录（2026-11-01 16:40　参与人：全体）", indent=False, size=11)
talk(3, "2026-11-01 16:40", "全体成员",
     "复核修改后启动日志，确认 4 个模板被正确加载，建表与应用安装均正常；讨论是否需要为其它第三方 jar 做同样处理。",
     "确认修复有效并记入问题清单；约定后续新增依赖时如遇资源冲突，统一用「工程内覆盖」的方式解决。")
d.placeholder_box("【此处粘贴启动成功日志截图：加载引擎的自定义sql结束 / Started DemoServer】", height_cm=4.5)

issue(
    "Q02", "启动卡在创建默认存储桶：内网 MinIO 地址不可达",
    [["发现日期", "2026-11-01"], ["所处阶段", "环境部署"], ["问题状态", "已解决"],
     ["现象", "后端启动后长时间无响应，日志停在「创建默认存储桶 / Creating bucket: apps」，最终抛出连接超时"],
     ["影响", "启动阻塞数分钟甚至失败"]],
    [("现象与日志", [("p", "日志显示 MinioTemplate.initDefaultBucket 在创建默认存储桶时发起网络请求，目标是 application-dev.properties 中配置的 http://192.168.175.54:9000，该地址属于培训方内网，本机不可达。"),
                 ("code", ["INFO - 创建默认存储桶", "Creating bucket: apps",
                           "java.net.ConnectException: Connection timed out: connect"])]),
     ("分析与排查", [("p", "阅读 MinioTemplate 的实现可知：先调用 bucketExists 判断桶是否存在（内部捕获异常返回 false），再调用 makeBucket 创建；makeBucket 的异常处理是记录日志后 throw new RuntimeException，因此对象存储不可达最终会导致启动失败。")]),
     ("解决方案", [("p", "改用本地 MinIO：把课程提供的 minio.exe 解压到 runtime/minio，以 127.0.0.1:9000 启动，并把 minio.endpoint 改成本地地址；同时把对象存储纳入 start-all.ps1 的启动序列。")]),
     ("验证与结论", "本地 MinIO 启动后，启动日志正常出现「默认存储桶已存在」，文件上传与下载接口回环测试通过。结论：平台把对象存储作为启动依赖，演示环境必须提供可用的本地对象存储。"),
     ("交流记录", None)],
)
d.p("第 1 次交流记录（2026-11-01 11:05　参与人：组长、成员 C）", indent=False, size=11)
talk(1, "2026-11-01 11:05", "组长、成员 C",
     "组长描述启动卡住的位置与目标地址；成员 C 确认 192.168.175.54 属于培训方内网，校园网访问不到。",
     "先确认对象存储是否为强依赖：尝试注释上传相关配置无效，代码里 makeBucket 失败会抛异常，必须提供可用服务。")
d.p("第 2 次交流记录（2026-11-01 13:30　参与人：成员 B、成员 C）", indent=False, size=11)
talk(2, "2026-11-01 13:30", "成员 B、成员 C",
     "讨论三种方案：①临时屏蔽 seeder 跳过对象存储初始化；②用脚本模拟一个假的 S3 服务；③本地部署真实 MinIO。",
     "先用②让启动通过（快速验证其它功能），同时准备③作为正式方案。")
d.p("第 3 次交流记录（2026-11-01 15:10　参与人：全体）", indent=False, size=11)
talk(3, "2026-11-01 15:10", "全体成员",
     "验证本地 MinIO 方案：health 接口返回 200、控制台可登录、上传下载回环通过。",
     "确定采用本地 MinIO，并把对象存储写进一键启动脚本；假 S3 脚本保留为端口冲突时的应急方案。")

issue(
    "Q06", "编译报错：找不到符号 Bool，且 create 方法不能 @Override",
    [["发现日期", "2026-11-06"], ["所处阶段", "模型与服务开发"], ["问题状态", "已解决"],
     ["现象", "新增驿站模型后编译失败：找不到符号 Bool；去掉 @Override 前还报「方法不会覆盖或实现超类型的方法」"],
     ["影响", "无法构建 APP jar"]],
    [("现象与日志", [("code", ["错误: 找不到符号", "符号:   类 Bool",
                             "错误: 方法不会覆盖或实现超类型的方法",
                             "        public RecordSet create(RecordSet rs, List<Map<String, Object>> valuesList)"])]),
     ("分析与排查", [("bullets", ["查阅平台 SDK 的包结构，Bool 实际位于 com.sie.snest.engine.model 包，而不是 com.sie.snest.sdk。",
                               "用 javap 查看 BaseModel，确认它只提供无参的 create()，带参数的重载是平台在运行时通过服务注册表调用的，不属于接口方法，因此不能用 @Override。",
                               "平台推荐做法是把方法声明为 @MethodService(name=\"create\", auth=\"create\")，由引擎在调用 create 服务时路由到该方法。"])]),
     ("解决方案", [("code", ["import com.sie.snest.engine.model.Bool;   // 正确包路径",
                            "@MethodService(name = \"create\", auth = \"create\", description = \"包裹入库登记\")",
                            "public RecordSet create(RecordSet rs, List<Map<String, Object>> valuesList) { ... }"])]),
     ("验证与结论", "重新执行 mvn package，6 个模块 BUILD SUCCESS，express 模块编译 5 个源文件。结论：注解中的包路径要以 javap 结果为准，服务重写不要用 @Override。"),
     ("交流记录", None)],
)
d.p("第 1 次交流记录（2026-11-06 09:40　参与人：成员 B、组长）", indent=False, size=11)
talk(1, "2026-11-06 09:40", "成员 B、组长",
     "成员 B 报告两个编译错误，怀疑是平台版本不兼容。组长让先看 SDK 里 Bool 到底在哪个包。",
     "用 javap 列出 SDK 包内容后再改 import，不要凭印象写。")
d.p("第 2 次交流记录（2026-11-06 10:25　参与人：成员 B、成员 C）", indent=False, size=11)
talk(2, "2026-11-06 10:25", "成员 B、成员 C",
     "讨论 create 重写为什么不能 @Override：成员 C 查看了平台生成的默认服务注册方式，提出应改用 @MethodService。",
     "统一采用 @MethodService 声明重写的 CRUD 方法，方法体通过 rs.callSuper 回到平台实现。")
d.p("第 3 次交流记录（2026-11-06 11:15　参与人：全体）", indent=False, size=11)
talk(3, "2026-11-06 11:15", "全体成员",
     "编译通过后回归既有服务，确认入库编号仍能自动生成、状态仍为 IN_STOCK。",
     "确认修复有效；把「包路径查 javap、服务用 @MethodService」写进团队约定。")

issue(
    "Q08", "取件码复用导致取件误报「已取件」",
    [["发现日期", "2026-11-07"], ["所处阶段", "服务开发 / 测试"], ["问题状态", "已解决"],
     ["现象", "同一取件码在旧包裹（已取件）与新包裹（在架）上同时存在时，取件接口返回「该包裹已于 … 取件」，实际新包裹仍在架"],
     ["影响", "驿站无法为在架包裹核销，属功能性缺陷"]],
    [("现象与日志", [("p", "取件实现最初按 pickupCode 查询后直接取第一条记录判断状态。由于取件码允许跨天复用（格式为月日+当日流水），旧的已取件记录可能排在前面，导致误判。")]),
     ("分析与排查", [("bullets", ["构造复现数据：把某件已取包裹的取件码与新上架包裹的取件码设为相同值，调用 pickup 复现。",
                               "确认取件码唯一性只约束「在架/超期」的有效包裹，历史记录允许重码，因此查询必须带状态条件。",
                               "同时发现若直接过滤状态，用户输入历史码时应给出「已取件」而不是「不存在」，需要两级查询。"])]),
     ("解决方案", [("code", ["// 1) 先在「有效」范围内查：在架 / 超期",
                            "Filter active = Filter.AND(Filter.equal(\"pickupCode\", code),",
                            "        Filter.OR(Filter.equal(\"status\", \"ON_SHELF\"), Filter.equal(\"status\", \"OVERDUE\")));",
                            "List<Parcel> list = search(active, Collections.singletonList(\"*\"), 0, 1, null);",
                            "if (list.isEmpty()) {",
                            "    // 2) 没有有效包裹时回查历史，给出准确提示",
                            "    List<Parcel> old = search(Filter.equal(\"pickupCode\", code),",
                            "            Collections.singletonList(\"*\"), 0, 1, null);",
                            "    if (!old.isEmpty() && \"PICKED\".equals(old.get(0).getStr(\"status\"))) {",
                            "        throw new RuntimeException(\"该包裹已于 \" + old.get(0).getStr(\"outboundTime\")",
                            "                + \" 取件，不能重复出库\");",
                            "    }",
                            "    throw new RuntimeException(\"取件码不存在或不属于你所在驿站：\" + code);",
                            "}"])]),
     ("验证与结论", "重建重启后复测：在架包裹可正常取件；已取件包裹再次取件仍提示首次取件时间；不存在的码提示「不存在」。结论：按状态分两级查询是正确做法。"),
     ("交流记录", None)],
)
d.p("第 1 次交流记录（2026-11-07 18:30　参与人：成员 C、组长）", indent=False, size=11)
talk(1, "2026-11-07 18:30", "成员 C、组长",
     "成员 C 在回归测试中发现取件失败并贴出报错；组长确认该包裹状态确为在架，怀疑取件码重复。",
     "先查数据库确认是否存在相同取件码的多条记录，再决定改查询逻辑。")
d.p("第 2 次交流记录（2026-11-07 19:10　参与人：成员 B、成员 C）", indent=False, size=11)
talk(2, "2026-11-07 19:10", "成员 B、成员 C",
     "确认库中存在两条相同取件码记录（一条已取、一条在架）；讨论是改成全局唯一还是按状态过滤。",
     "保留「有效期内唯一」的业务规则，取件时按状态分级查询。")
d.p("第 3 次交流记录（2026-11-07 20:05　参与人：全体）", indent=False, size=11)
talk(3, "2026-11-07 20:05", "全体成员",
     "验证三种输入：在架码、历史码、随机码，结果分别为取件成功、提示已取件、提示不存在。",
     "确认修复；补充进测试用例 T05 与 T06。")

issue(
    "Q09", "数据隔离不生效：普通用户查不到自己的用户记录",
    [["发现日期", "2026-11-08"], ["所处阶段", "服务开发"], ["问题状态", "已解决"],
     ["现象", "实现按驿站隔离后，驿站人员 zhangsan 仍能看到全部包裹；调试服务返回 users:0，说明按 id 查询 rbac_user 得到 0 行"],
     ["影响", "数据隔离失效，存在越权风险"]],
    [("现象与日志", [("p", "隔离逻辑依赖「当前登录人 → 查 rbac_user 得到登录名 → 查绑定驿站」这条链路。用管理员账号调用一切正常，换成驿站人员后查询 rbac_user 返回空列表。"),
                 ("code", ["// 调试服务返回", "{ \"uid\": \"06cnc3m1oqq6s\", \"users\": 0 }"])]),
     ("分析与排查", [("bullets", ["先用 station_user 模型做无过滤查询，两行数据都在，排除数据本身缺失。",
                               "再用 JSON 手写 filter 查询报 Invalid leaf，说明过滤语法不对，转为在 Java 代码里用 Filter 构造，仍为 0 行。",
                               "怀疑 iam 对普通用户做了行级数据权限（组织/租户限制），导致跨应用查询 rbac_user 被过滤。",
                               "用 javap 查看引擎的 Meta 类（com.sie.snest.engine.container.Meta），发现提供 getUserId()、getUser()、getUserName() 等会话 API，可直接取当前用户，无需查 rbac_user。"])]),
     ("解决方案", [("code", ["private String loginOfCurrentUser() {",
                            "    String login = getMeta().getUser() == null ? null : getMeta().getUser().getStr(\"login\");",
                            "    if (login == null || login.trim().isEmpty()) {",
                            "        // 退化为显示名匹配（getUserName 返回的是显示名）",
                            "        login = getMeta().getUserName();",
                            "    }",
                            "    return login;",
                            "}",
                            "// 绑定关系按「登录名或姓名」双条件匹配",
                            "Filter f = Filter.AND(",
                            "    Filter.OR(Filter.equal(\"userLogin\", login), Filter.equal(\"userName\", displayName)),",
                            "    Filter.equal(\"status\", \"enabled\"));"])]),
     ("验证与结论", "重建重启后：zhangsan 只看到 1 号驿站的 5 条、lisi 只看到 2 号驿站的 4 条、管理员看到全部 9 条；跨驿站入库与代取均被拒绝。结论：取当前用户要走引擎会话 API，不要跨应用查 iam 的模型。"),
     ("交流记录", None)],
)
d.p("第 1 次交流记录（2026-11-08 09:50　参与人：组长、成员 B）", indent=False, size=11)
talk(1, "2026-11-08 09:50", "组长、成员 B",
     "组长演示隔离未生效的现象，两人确认过滤条件确实拼接了，但拿到的驿站集合为空。",
     "从「驿站集合为什么为空」入手，逐层打印中间值。")
d.p("第 2 次交流记录（2026-11-08 10:30　参与人：成员 B、成员 C）", indent=False, size=11)
talk(2, "2026-11-08 10:30", "成员 B、成员 C",
     "成员 C 提出用 javap 看 Meta 有哪些方法，发现可直接取当前用户；讨论是否还需要绑定表。",
     "仍保留绑定表（驿站人员可能有多个账号），但取当前登录人改为走引擎会话 API。")
d.p("第 3 次交流记录（2026-11-08 11:20　参与人：全体）", indent=False, size=11)
talk(3, "2026-11-08 11:20", "全体成员",
     "用三个账号验证可见条数与越权提示，并确认删除临时调试服务。",
     "确认隔离生效，写入 README 第 10 节，删除 debugScope 服务。")

d.p("其余问题（Q03、Q04、Q05、Q07、Q10–Q16）的结论摘要：", indent=False, size=11)
d.table(["编号", "结论摘要"],
        [["Q03", "确认平台实际使用的是 CLOUD 元数据模式（培训方日志中 IMetaManager 为 CloudIMataManager）；EDGE 模式的实现是空壳会引发 NPE。启动失败后必须清库重建，否则半初始化的元数据会持续报错。"],
         ["Q04", "seeder.sync 关闭会同时跳过用户、菜单、视图等种子数据，导致登录提示「该用户不存在」。开启后重新建库即可导入。"],
         ["Q05", "构建时 jar 被正在运行的后端进程占用，报「另一个程序正在使用此文件」，需先停止后端进程再打包。"],
         ["Q07", "非超管账号调用业务服务返回 403，原因是 iam 种子里只给角色授权了平台应用。通过 rbac_role_auth 补充业务应用的授权记录后恢复。"],
         ["Q10", "超期标记目前由页面按钮或接口手工触发，尚未做定时任务（遗留项）。"],
         ["Q11", "视图 JSON 的列名必须与模型属性名一致，否则页面显示空列。"],
         ["Q12", "菜单的 view 串必须与视图文件内的 key 同名，顺序为「表格,搜索,表单」。"],
         ["Q13", "PowerShell 5.1 按 ANSI 解析无 BOM 的 .ps1，中文会破坏引号配对；脚本需以 UTF-8 BOM 保存。"],
         ["Q14", "Invoke-RestMethod 对 UTF-8 响应按 Latin-1 解码，中文显示为乱码；改用 HttpWebRequest + StreamReader 指定 UTF-8。"],
         ["Q15", "种子数据在 seeder.sync=true 时每次启动重放，固定 id 保证幂等；手工新增同 id 数据会提示已存在（已绕过）。"],
         ["Q16", "命令行传 JSON 时引号被吞，改用管道把请求体交给 curl --data-binary @-。"],
         ["Q01–Q09", "详见本章前 6 个小节的完整记录。"]],
        caption="表 3-1　其余问题结论摘要", widths=[2.0, 13.0], size=9.5)

# ===== 第 4 章 =====
d.h("第 4 章　版本管理记录", 1)
d.p("项目使用 Git 管理源代码，远程仓库托管在 GitHub。主干为 master，提交信息遵循「类型: 说明」的格式，并在关键节点打 tag 便于回溯。")
d.table(["提交", "时间", "说明"],
        [["7f4dda7", "2026-11-08 12:36", "feat: 驿站 APP 增加按驿站的数据隔离（station_user 绑定 + search/count 重写 + 越权拦截）"],
         ["c09aab3", "2026-11-08 12:23", "feat: 新增快递驿站业务 APP（包裹入库/自动上架/取件码/取件/退货/超期）"],
         ["c6e8c20", "2026-11-08 12:11", "chore: 清理工作区，仅保留驿站项目所需环境与编码规则 APP"],
         ["9d4a2c5", "2026-11-08 11:44", "feat: 第五天（whoAmI 服务 + 组织/用户/角色/授权链路）"],
         ["3b78763", "2026-11-08 08:52", "feat: 切换为第四天多 APP 架构（CMS/MES/QMS/WMS + 统一编码中心）"],
         ["e8376d2", "2026-11-07 14:31", "feat: 新增垃圾桶生产管理业务 APP（BIN）"],
         ["19daa3e", "2026-11-07 14:17", "feat: 补完实训项（BOM 编码唯一、数据隔离）并修正引擎认知"],
         ["f80e6d7", "2026-11-07 14:12", "docs: 归档引擎知识库，补充导航与认知修正"],
         ["9fca601", "2026-11-07 09:04", "chore: 忽略项目专用密钥目录"],
         ["77ff70c", "2026-11-07 08:53", "docs: 新增 OpenAPI 3.0 描述文件与 Git/Apifox 使用说明"],
         ["20424d9", "2026-11-07 08:50", "feat: 项目初始提交"],
         ["5ddc4fb", "2026-11-07 08:50", "chore: 添加 .gitignore，排除构建产物与本机运行依赖"]],
        caption="表 4-1　提交记录（节选）", widths=[1.8, 3.4, 9.8], size=9)
d.p("tag：archive/day5 用于标记课程第五天结束时的工作区快照，便于在切换到新项目后仍能回溯历史实现。仓库共跟踪 86 个文件，构建产物、离线依赖仓库、数据库数据目录均通过 .gitignore 排除。")

# ===== 第 5 章 =====
d.h("第 5 章　阶段进展与反思", 1)
d.h("5.1　阶段进展", 2)
d.table(["阶段", "时间", "完成情况"],
        [["环境搭建", "11-01 ～ 11-02", "跑通平台全部 6 个服务，形成一键启停脚本，编写运行说明"],
         ["平台学习", "11-03 ～ 11-05", "掌握模型注解、视图 JSON、服务声明与 callSuper、权限与编码规则"],
         ["驿站 APP 开发", "11-06 ～ 11-07", "完成 5 个模型、10 个服务、5 组视图与菜单，跑通端到端流程"],
         ["数据隔离", "11-08", "完成按驿站的行级隔离与越权拦截，补充隔离测试"],
         ["文档与交付", "11-08 ～ 11-29", "完成开发文档、过程文档、演示与汇报材料"]],
        caption="表 5-1　阶段进展", widths=[3.0, 3.6, 8.4])
d.h("5.2　经验与反思", 2)
d.bullets([
    "先跑通最小闭环：模型—视图—菜单三件套走通后再加业务复杂度，比一上来写全量服务更省时间。",
    "错误信息要读到栈底：引擎把业务异常统一包装成 504，真正的根因在 debug 字段里，忽略它会走很多弯路。",
    "平台封装度高，遇到「查不到数据」先怀疑权限与数据范围，再怀疑 SQL。",
    "数据隔离必须服务端兜底：仅靠菜单隐藏按钮不能阻止接口调用。",
    "文档与代码同步：每次修复问题顺手把结论写进 README 与过程文档，答辩时材料自然齐全。",
    "不足：超期标记尚未做定时任务；取件码并发场景只在单机验证过，多实例部署需改用数据库原子自增。",
])

# ===== 附录 =====
d.h("附录 A　交流记录截图", 1)
d.p("本附录汇总第 3 章各问题的讨论截图。每张截图应包含日期、参与人与讨论内容，可通过聊天记录或会议记录截图提供。")
for i in range(1, 7):
    d.p("A.%d　问题 Q%02d 讨论截图" % (i, [1, 2, 6, 8, 9, 16][i - 1]), indent=False, size=11)
    d.placeholder_box("【此处粘贴 3 张讨论截图，横向排列或分页粘贴】", height_cm=6.0)

d.save(OUT)
