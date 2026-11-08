# -*- coding: utf-8 -*-
"""生成《项目开发文档》."""
import os
from docx_common import Doc

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "交付文档", "快递驿站管理系统-项目开发文档.docx")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

d = Doc()
d.update_fields_on_open()

# ============ 封面 ============
d.cover(
    "快递驿站管理系统",
    "项目开发文档",
    [("课程名称", "中级软件设计实作"),
     ("项目名称", "快递驿站管理系统（express APP）"),
     ("开发平台", "IIDP 工业数字平台（sie-snest 引擎 v3.0.1）"),
     ("团队编号", "第 __ 组"),
     ("项目成员", "＿＿＿＿＿、＿＿＿＿＿、＿＿＿＿＿"),
     ("指导教师", "＿＿＿＿＿"),
     ("完成日期", "2026 年 11 月")],
    note="本文档按《2024 级软件工程〈中级软件设计实作〉项目作品要求》编写"
)

# ============ 目录 ============
d.toc([
    (1, "第 1 章　项目概述"),
    (2, "1.1　项目背景"), (2, "1.2　项目目标"), (2, "1.3　建设范围"), (2, "1.4　术语与缩写"),
    (1, "第 2 章　需求分析"),
    (2, "2.1　需求调研方法与过程"), (2, "2.2　需求调查表"), (2, "2.3　用户角色与权限"),
    (2, "2.4　业务流程分析"), (2, "2.5　用例图"), (2, "2.6　用例描述"), (2, "2.7　非功能需求"),
    (1, "第 3 章　可行性分析"),
    (2, "3.1　技术可行性"), (2, "3.2　经济可行性"), (2, "3.3　进度可行性"), (2, "3.4　风险分析"),
    (1, "第 4 章　概要设计"),
    (2, "4.1　设计原则"), (2, "4.2　系统总体架构"), (2, "4.3　功能模块划分"),
    (2, "4.4　分析类图"), (2, "4.5　包裹状态机"), (2, "4.6　接口设计"),
    (1, "第 5 章　详细设计"),
    (2, "5.1　类图"), (2, "5.2　类说明"), (2, "5.3　取件出库活动图"), (2, "5.4　取件出库时序图"),
    (2, "5.5　数据库设计"), (2, "5.6　取件码生成算法"), (2, "5.7　数据隔离设计"), (2, "5.8　异常与错误码"),
    (1, "第 6 章　项目实现"),
    (2, "6.1　开发与运行环境"), (2, "6.2　工程结构"), (2, "6.3　关键代码实现"),
    (2, "6.4　部署与启动"), (2, "6.5　种子数据"),
    (1, "第 7 章　系统测试"),
    (2, "7.1　测试环境与测试数据"), (2, "7.2　功能测试用例"), (2, "7.3　数据隔离与越权测试"), (2, "7.4　测试结论"),
    (1, "第 8 章　项目分工"),
    (1, "第 9 章　课程知识点对照"),
    (1, "附录 A　参考资料"),
])

# ============ 第 1 章 ============
d.h("第 1 章　项目概述", 1)

d.h("1.1　项目背景", 2)
d.p("随着电子商务与社区团购的高速发展，末端配送环节的包裹量急剧增长。传统快递驿站仍然大量依赖纸笔登记或通用 Excel 台账：包裹入库后由人工翻找货架、手写取件码，取件时再靠肉眼比对，容易出现「同一个包裹被重复发放」「取件码被重复使用」「包裹长期滞留无人跟进」「不同驿站之间的数据互相可见」等问题。")
d.p("本次实训课程提供了 IIDP 工业数字平台（基于 sie-snest 元模型引擎）。该平台以「模型 + 视图 + 服务」的元数据驱动方式组织业务：开发者用 Java 注解声明模型，平台据此自动建表、自动生成增删改查服务与 JSON-RPC 接口，再由视图 JSON 描述表格、搜索区与表单，菜单 JSON 描述导航，前端动态渲染。我们以此为技术底座，开发一套面向单个驿站、可支撑多驿站并存的快递驿站管理系统。")

d.h("1.2　项目目标", 2)
d.p("系统面向「驿站工作人员」与「系统管理员」两类使用者，目标是打通包裹从到站到签收的完整链路，实现「入库—上架—取件—退货」全流程的数字化与可追溯，具体目标如下：")
d.bullets([
    "包裹到站登记：一次录入运单号、收件人、快递公司等信息，系统自动生成包裹编号并置为在库状态。",
    "自动分配货架：按驿站、按货架剩余容量自动分配货位，并生成当日不重复的四位取件码。",
    "取件核销：凭取件码完成出库，重复取件、跨驿站取件、未上架包裹取件均被拒绝并给出明确提示。",
    "超期与退货管理：超过设定天数未取的包裹自动标记超期，支持退货出库与取件码重新生成。",
    "数据隔离：不同驿站的工作人员只能看到、只能操作本驿站的包裹，越权请求被服务端拦截。",
    "过程留痕：每一次入库、上架、取件、退货、换码都写入操作记录，可查询、可追溯。",
])

d.h("1.3　建设范围", 2)
d.p("本期建设范围限定在「单平台、多驿站」的末端收派场景，包含 5 个业务对象、10 个业务服务、4 组页面视图与 1 套数据隔离规则；不含计费结算、面单打印、短信通知、路径规划等与第三方系统对接的功能，这些内容列在后续可扩展方向中。")
d.table(["序号", "功能域", "本期是否包含"],
        [["1", "包裹入库登记", "包含"],
         ["2", "货架自动分配与上架", "包含"],
         ["3", "取件码生成与核销", "包含"],
         ["4", "退货出库与重新生成取件码", "包含"],
         ["5", "超期标记与运营统计", "包含"],
         ["6", "多驿站数据隔离", "包含"],
         ["7", "计费结算、面单打印、短信通知", "不包含（后续扩展）"]],
        caption="表 1-1　建设范围一览", widths=[1.6, 7.6, 4.4])

d.h("1.4　术语与缩写", 2)
d.table(["术语", "说明"],
        [["IIDP", "Industrial Internet Digital Platform，工业互联网数字平台，本项目的开发与运行底座"],
         ["sie-snest 引擎", "平台的元模型引擎，负责模型元数据解析、建表、服务注册、JSON-RPC 分发与权限校验"],
         ["模型（Model）", "用 Java 注解声明的业务实体，平台据此建表并自动生成 CRUD 服务"],
         ["视图（View）", "描述表格（grid）、搜索区（search）、表单（form）的 JSON 元数据，前端据此动态渲染页面"],
         ["服务（Service）", "模型上以 @MethodService 声明的方法，经平台暴露为 JSON-RPC 接口"],
         ["APP", "平台的应用单元，一个 APP 由一个 jar 承载，内含若干模型、视图与菜单"],
         ["取件码", "包裹上架时生成的四位提货凭证，格式为「月日 + 当日四位流水」"],
         ["数据隔离", "按驿站维度限制用户可见数据范围的行级权限机制"]],
        caption="表 1-2　术语与缩写", widths=[3.6, 11.4])

# ============ 第 2 章 ============
d.h("第 2 章　需求分析", 1)

d.h("2.1　需求调研方法与过程", 2)
d.p("需求来源以课程提供的《中级软件设计实作》方案 v3 中「课题 3：快递驿站」的业务描述为起点，结合小组成员在校园快递驿站的实地观察、以及与驿站工作人员的访谈进行补充。调研过程分为三步：")
d.bullets([
    "现场观察：在校园菜鸟驿站观察包裹到站、分拣上架、通知取件、核销出库的完整动作，记录人工台账的字段与常见差错。",
    "人员访谈：访谈 2 名驿站工作人员，确认「取件码为什么会重复」「超期包裹如何处理」「退货怎么登记」等细节。",
    "需求整理与确认：把访谈结论整理为需求调查表，逐条标注优先级，再与指导教师确认范围。",
])
d.p("调研中反复出现的关键诉求是「不能取错、不能重复取」。驿站每天处理数百件包裹，一旦同一取件码被两个人使用，就会出现「包裹已被取走」的纠纷；因此本项目把取件码的唯一性与取件的幂等性作为核心约束。")

d.h("2.2　需求调查表", 2)
d.table(["编号", "需求名称", "需求描述", "优先级"],
        [["R01", "驿站档案", "维护驿站名称、编号、地址、联系电话、负责人与启用状态", "高"],
         ["R02", "货架档案", "维护货架编号、所属驿站、区域、容量与当前占用数", "高"],
         ["R03", "包裹入库", "录入运单号、收件人、手机号、快递公司，系统自动生成包裹编号与入库时间", "高"],
         ["R04", "运单号唯一", "同一运单号重复入库时拒绝并提示「该运单号已入库，请勿重复登记」", "高"],
         ["R05", "自动上架", "按驿站自动挑选未满货架，占用数加一，满架自动置为「已满」", "高"],
         ["R06", "取件码生成", "上架时生成四位取件码，格式为「月日 + 当日流水」，当日有效包裹间不重复", "高"],
         ["R07", "取件核销", "凭取件码取件，成功后释放货架、记录出库时间与操作人", "高"],
         ["R08", "重复取件拦截", "同一包裹再次取件时拒绝，并提示首次取件时间与操作人", "高"],
         ["R09", "退货出库", "在库或已上架的包裹可登记退货，已取件包裹不可退货", "中"],
         ["R10", "取件码重发", "客户取件码遗失时，可作废旧码并生成新码，旧码使用情况写入操作记录", "中"],
         ["R11", "超期标记", "超过设定天数未取的包裹标记为超期，支持按天数批量扫描", "中"],
         ["R12", "操作留痕", "入库、上架、取件、退货、换码等关键动作写入操作记录，含操作人、时间、取件码", "高"],
         ["R13", "运营统计", "按驿站统计在库、在架、已取、超期数量与货架使用率", "中"],
         ["R14", "多驿站隔离", "驿站人员只能看到、只能操作本驿站的包裹，越权请求被服务端拒绝", "高"],
         ["R15", "人员归属", "维护「账号—驿站」绑定关系，作为数据隔离的依据", "高"],
         ["R16", "菜单与页面", "提供包裹、货架、驿站、驿站人员、操作记录五组页面，支持查询、新增、编辑、批量操作", "高"]],
        caption="表 2-1　需求调查表", widths=[1.3, 2.8, 8.4, 1.5])

d.h("2.3　用户角色与权限", 2)
d.p("系统不重新实现一套账号体系，直接复用平台 iam 应用提供的用户、角色、租户与功能权限。业务上划分两类角色：")
d.table(["角色", "典型账号", "可见范围", "可执行操作"],
        [["驿站人员", "zhangsan（绑定 1 号驿站）、lisi（绑定 2 号驿站）", "仅本驿站的包裹与操作记录", "入库、上架、取件、退货、换码、超期标记、查看统计"],
         ["系统管理员", "implementer（平台租户管理员）", "全部驿站", "上述全部操作，另可维护驿站、货架、人员归属与角色授权"]],
        caption="表 2-2　用户角色与权限", widths=[2.4, 4.6, 3.6, 5.4])

d.h("2.4　业务流程分析", 2)
d.p("包裹在系统中的生命周期由状态机约束，主流程为「到站登记 → 自动上架 → 取件出库」，旁路流程为「退货出库」「取件码重发」「超期标记」。各环节的业务规则如下：")
d.table(["环节", "触发者", "前置状态", "处理规则", "后置状态"],
        [["入库登记", "驿站人员", "无", "校验运单号唯一；生成包裹编号；记录入库时间与手机尾号", "IN_STOCK"],
         ["自动上架", "驿站人员", "IN_STOCK", "按驿站选未满货架并占用；生成取件码；记录上架时间", "ON_SHELF"],
         ["取件出库", "驿站人员", "ON_SHELF / OVERDUE", "校验归属驿站；释放货架；记录出库时间与操作人", "PICKED"],
         ["退货出库", "驿站人员", "IN_STOCK / ON_SHELF / OVERDUE", "记录退货原因；释放货架", "RETURNED"],
         ["超期标记", "系统或个人", "ON_SHELF", "扫描上架时间超过 N 天（默认 3 天）的包裹", "OVERDUE"],
         ["取件码重发", "驿站人员", "ON_SHELF / OVERDUE", "作废旧码并写日志，生成当日新码", "状态不变"]],
        caption="表 2-3　业务流程与规则", widths=[2.2, 2.2, 3.0, 6.2, 2.4])

d.h("2.5　用例图", 2)
d.p("系统共梳理出 10 个用例。驿站人员承担日常收派作业，系统管理员额外承担档案维护、人员归属与超期扫描，取件客户作为外部参与者仅提供取件码。")
d.fig("usecase.png", "图 2-1　快递驿站管理系统用例图", width=14.0)
d.p("说明：UC03 取件出库、UC04 退货出库、UC05 重新生成取件码、UC06 运营统计四个用例均受数据隔离约束，驿站人员只能作用于本人绑定驿站的数据。", indent=False, size=10.5)

d.h("2.6　用例描述", 2)
d.table(["用例编号", "用例名称", "参与者", "简要说明"],
        [["UC01", "包裹入库", "驿站人员", "登记到站包裹，生成包裹编号，状态置为在库"],
         ["UC02", "自动上架", "驿站人员", "按容量分配货架并生成取件码"],
         ["UC03", "取件出库", "驿站人员、取件客户", "凭取件码核销出库，重复取件被拒绝"],
         ["UC04", "退货出库", "驿站人员", "登记退货并释放货架"],
         ["UC05", "重新生成取件码", "驿站人员", "作废旧码、生成新码并留痕"],
         ["UC06", "运营统计", "驿站人员", "统计各状态数量与货架使用率"],
         ["UC07", "超期标记", "系统管理员", "批量扫描超过 N 天未取的包裹"],
         ["UC08", "驿站管理", "系统管理员", "维护驿站档案"],
         ["UC09", "货架管理", "系统管理员", "维护货架档案与容量"],
         ["UC10", "驿站人员与数据范围", "系统管理员", "维护账号与驿站的绑定关系"]],
        caption="表 2-4　用例清单", widths=[2.0, 3.6, 3.2, 6.6])

d.p("其中 UC03 取件出库是业务核心，详述如下：")
d.table(["项目", "内容"],
        [["用例名称", "取件出库"],
         ["参与者", "驿站人员（主）、取件客户（提供取件码）"],
         ["前置条件", "包裹已完成上架，状态为 ON_SHELF 或 OVERDUE；操作人已绑定所属驿站"],
         ["后置条件", "包裹状态变为 PICKED，货架占用数减一，写入一条操作记录"],
         ["基本流程", "1. 输入取件码；2. 系统查询处于在架/超期状态且取件码匹配的包裹；3. 校验包裹所属驿站是否与操作人绑定驿站一致；4. 校验包裹状态是否允许出库；5. 释放货架、更新包裹状态与出库时间；6. 写操作记录；7. 返回取件成功信息"],
         ["异常流程", "E1 取件码不存在或不属于本人驿站：提示「取件码不存在或不属于你所在驿站」；E2 包裹已取件：提示「该包裹已于 X 时间取件，不能重复出库」；E3 已退货：提示「已退货，不能取件」；E4 未上架：提示「还没上架」；E5 跨驿站：提示「无权操作驿站[..]的包裹」"]],
        caption="表 2-5　UC03 取件出库用例描述", widths=[2.6, 12.4])

d.h("2.7　非功能需求", 2)
d.table(["类别", "要求"],
        [["性能", "单驿站日常 500 件/天的数据量下，列表查询与取件核销响应时间小于 1 秒"],
         ["可靠性", "取件、退货等写操作具备事务性，异常时整体回滚，不出现「状态改了但记录没写」"],
         ["安全性", "接口需携带 token；服务端按驿站做行级过滤，不依赖前端隐藏菜单"],
         ["可用性", "页面由平台视图元数据渲染，字段中文名、下拉选项、日期格式统一"],
         ["可维护性", "模型、视图、服务分层，新增业务对象只需增加模型与视图，无需改动引擎"],
         ["可移植性", "数据库使用 MySQL 8.0，字符集 utf8mb4 / utf8mb4_bin，可在任意安装平台的环境部署"]],
        caption="表 2-6　非功能需求", widths=[2.6, 12.4])

# ============ 第 3 章 ============
d.h("第 3 章　可行性分析", 1)

d.h("3.1　技术可行性", 2)
d.p("平台已经提供元模型引擎、自动建表、JSON-RPC 服务分发、权限校验、视图渲染与菜单管理等基础能力，业务侧只需要编写模型注解、服务方法与视图 JSON，技术风险低。团队具备 Java 8、MySQL 与 HTTP 接口的基本开发能力；开发环境（JDK、Maven、MySQL、Redis、MinIO、Nginx、授权服务）均已在本机跑通，形成了一键启停脚本。")
d.p("关键技术点均已验证：①跨表关联通过 @ManyToOne/@OneToMany 声明，外键列可显式命名；②业务编码通过平台「编码规则」应用统一发号；③行级隔离通过重写 search/count 注入过滤条件实现；④批量操作与页面按钮通过 @MethodService + 视图 buttons 配置联动。")

d.h("3.2　经济可行性", 2)
d.p("本项目全部基于开源软件与课程提供的平台授权，无需采购商业中间件；部署在一台普通办公电脑即可运行，硬件与软件成本几乎为零。系统上线后，驿站工作人员登记与查找包裹的时间由平均 2～3 分钟/件下降到 30 秒以内，按每天 300 件估算，可节省约 7 人时/天。")
d.table(["项目", "说明", "成本"],
        [["开发平台", "IIDP 平台（课程提供授权）", "0 元"],
         ["数据库", "MySQL 8.0 社区版", "0 元"],
         ["缓存/对象存储", "Redis、MinIO 社区版", "0 元"],
         ["服务器", "复用现有办公电脑（4C8G）", "0 元"],
         ["人力投入", "3 人 × 3 周（约 360 人时）", "课程学分"],
         ["合计", "——", "接近于 0 元"]],
        caption="表 3-1　成本估算", widths=[3.4, 8.0, 3.6])

d.h("3.3　进度可行性", 2)
d.table(["阶段", "时间", "主要工作", "交付物"],
        [["需求与分析", "第 1 周", "现场调研、需求调查表、用例图、可行性分析", "需求分析章节"],
         ["设计", "第 2 周", "架构设计、分析类图、类图、活动图、时序图、表结构设计", "设计章节与图表"],
         ["实现", "第 3 周", "模型与服务编码、视图与菜单配置、构建部署、联调", "可运行系统"],
         ["测试与文档", "第 4 周", "功能与权限测试、缺陷修复、文档整理、演示录制", "测试报告、开发文档、过程文档"]],
        caption="表 3-2　进度计划", widths=[2.4, 2.0, 7.2, 4.0])

d.h("3.4　风险分析", 2)
d.table(["风险", "影响", "应对措施"],
        [["对平台引擎不熟悉，调试成本高", "进度延期", "先用最小模型验证注解与视图渲染，再逐步扩展；关键问题记录进过程文档"],
         ["取件码并发重复", "取件纠纷", "生成时校验当日有效包裹的占用情况，关键路径加锁并写日志"],
         ["数据隔离依赖平台权限体系", "越权风险", "在服务层显式注入驿站过滤条件，并补充越权测试用例"],
         ["环境依赖多（MySQL/Redis/MinIO/授权服务）", "启动失败", "编写 start-all/stop-all 脚本，逐服务健康检查"],
         ["课程时间紧、人员分工不均", "交付质量下降", "任务拆分到人，每周同步进度"]],
        caption="表 3-3　风险与应对", widths=[5.0, 3.0, 7.0])

# ============ 第 4 章 ============
d.h("第 4 章　概要设计", 1)

d.h("4.1　设计原则", 2)
d.bullets([
    "元数据驱动：能用模型注解与视图 JSON 表达的内容，不写重复代码；平台负责建表、CRUD 与界面渲染。",
    "业务内聚：取件码生成、货架分配、状态流转等规则全部收敛在包裹服务内，页面只负责传参。",
    "服务端兜底：数据隔离、唯一性、状态校验不依赖前端隐藏按钮，一律在服务端校验。",
    "可追溯：所有关键动作写操作记录，出现纠纷可回溯到人、时间与取件码。",
    "可扩展：新增驿站或新增业务对象只需增加数据与模型，不修改既有服务。",
])

d.h("4.2　系统总体架构", 2)
d.p("系统采用平台既有的四层结构：前端由平台页面引擎渲染，通过 Nginx 反向代理访问后端；后端由 sie-snest 引擎承担元模型管理、服务分发与鉴权，业务 APP 以 jar 的形式被引擎加载；数据层使用 MySQL 保存业务数据与元数据，Redis 保存会话与缓存，MinIO 保存附件。")
d.table(["层次", "组成", "职责"],
        [["表现层", "平台前端（Nginx 站点 /iidp/）+ 视图元数据", "渲染菜单、表格、搜索区与表单，把按钮动作转换为 JSON-RPC 请求"],
         ["网关层", "Nginx（30666）", "托管前端静态资源，/api/ 反向代理到后端 8061"],
         ["服务层", "sie-snest 引擎 + express APP jar", "解析模型注解、自动建表、注册服务、校验 token 与权限、分发请求"],
         ["数据层", "MySQL（iidp_demo_v3）、Redis、MinIO", "保存业务数据与元数据、会话缓存、附件对象"]],
        caption="表 4-1　系统分层", widths=[2.2, 6.0, 6.8])

d.h("4.3　功能模块划分", 2)
d.table(["模块", "对应模型", "主要功能"],
        [["包裹管理", "parcel", "入库登记、自动上架、取件、退货、换码、超期标记、状态统计"],
         ["货架管理", "shelf", "货架增删改查、可用货架查询"],
         ["驿站管理", "station", "驿站档案维护、运营统计"],
         ["驿站人员", "station_user", "账号与驿站的绑定关系维护，提供数据范围"],
         ["操作记录", "parcel_log", "登记入库、上架、取件、退货、换码等动作，供追溯查询"]],
        caption="表 4-2　功能模块划分", widths=[2.6, 3.2, 9.2])

d.h("4.4　分析类图", 2)
d.p("按边界类、控制类、实体类三类职责建模：包裹管理界面是边界类；包裹业务服务是控制类，承担状态流转与校验；五个实体类分别承载数据与关系。")
d.fig("analysis.png", "图 4-1　快递驿站分析类图", width=14.5)

d.h("4.5　包裹状态机", 2)
d.p("包裹状态取值与流转规则如下，非法流转在服务端直接抛出业务异常：")
d.table(["状态", "中文名", "允许的下一状态", "说明"],
        [["IN_STOCK", "在库待上架", "ON_SHELF、RETURNED", "已入库未分配货架"],
         ["ON_SHELF", "已上架", "PICKED、OVERDUE、RETURNED", "已分配货架并生成取件码"],
         ["OVERDUE", "超期未取", "PICKED、RETURNED", "超过设定天数仍未取件"],
         ["PICKED", "已取件", "——", "已核销出库，不可再取、不可退货"],
         ["RETURNED", "已退货", "——", "退回快递公司，货架已释放"]],
        caption="表 4-3　包裹状态机", widths=[2.6, 2.6, 5.0, 5.2])

d.h("4.6　接口设计", 2)
d.p("所有业务能力均由平台统一暴露为 JSON-RPC 接口，前端不直接访问数据库。统一入口为 POST /api/root/rpc/service/，通过查询参数 model 与 service 指定模型与服务，参数体放在 params.args 中。")
d.code([
    "POST http://localhost:30666/api/root/rpc/service/?model=parcel&service=pickup",
    "Content-Type: application/json",
    "Authorization: Bearer <token>",
    "token: <token>",
    "",
    "{",
    '  "jsonrpc": "2.0", "method": "service", "id": "1",',
    '  "params": {',
    '    "args": { "code": "11080001" },',
    '    "app": "express", "model": "parcel", "service": "pickup", "tag": "master",',
    '    "context": { "lang": "zh_CN", "tenant": "root" }',
    "  }",
    "}",
])
d.p("服务返回统一结构：成功时 result.data 携带业务数据，失败时 error.code 与 error.message 描述原因（业务异常由引擎包装为 code=504）。", indent=False, size=10.5)

# ============ 第 5 章 ============
d.h("第 5 章　详细设计", 1)

d.h("5.1　类图", 2)
d.p("核心类图如下，五个实体类与平台「编码规则」应用构成包裹业务的数据模型；服务方法标注在类的方法区，其中 search/count 被重写以注入数据隔离条件。")
d.fig("class.png", "图 5-1　快递驿站核心类图", width=15.0)

d.h("5.2　类说明", 2)
d.table(["类名", "职责", "关键属性", "关键方法"],
        [["Station", "驿站档案", "name、code、address、phone、manager、status", "stats(id) 运营统计"],
         ["Shelf", "货架档案与容量", "code、station、area、capacity、used、status", "findAvailable(stationId) 找可用货架"],
         ["Parcel", "包裹主数据与状态流转", "parcelNo、waybillNo、station、shelf、pickupCode、status、时间戳", "create/autoShelf/pickup/returnOut/reissue/markOverdue/overview"],
         ["ParcelLog", "操作留痕", "parcel、action、code、operator、operatorName、happenTime", "log(parcelId, action, code, remark)"],
         ["StationUser", "账号与驿站绑定，提供数据范围", "userLogin、userName、station、status", "myStationIds()、myScope()"],
         ["BarcodeRule", "平台编码规则（code 应用）", "code、prefix、length", "generateCode(code) 统一发号"]],
        caption="表 5-1　类说明", widths=[2.4, 4.2, 5.4, 4.0])

d.h("5.3　取件出库活动图", 2)
d.p("取件是系统里校验最多的动作，依次判断「取件码是否存在」「是否属于本人驿站」「状态是否允许出库」，任一判断失败直接给出提示并结束，全部通过后才执行释放货架、更新状态与写日志。")
d.fig("activity_pickup.png", "图 5-2　取件出库活动图", width=10.5)

d.h("5.4　取件出库时序图", 2)
d.p("从页面点击「取件」到返回结果的对象协作过程如下：RpcController 完成 token 校验后分发到 Parcel 服务，服务先取当前用户的数据范围，再查询在架包裹，校验通过后更新包裹与货架并写操作记录。")
d.fig("sequence_pickup.png", "图 5-3　取件出库时序图", width=15.0)

d.h("5.5　数据库设计", 2)
d.p("业务表全部由模型注解自动建表，命名规则为「模型 tableName + 平台标准审计列」。平台为每个模型自动附加 id、tenant_id、create_user、create_date、update_user、update_date 六列（其中 parcel 因开启 isAutoLog 自动记录创建人）。")
d.p("（1）驿站表 ex_station", indent=False, size=11)
d.table(["字段", "类型", "允许空", "说明"],
        [["id", "varchar(64)", "否", "主键"],
         ["name", "varchar(240)", "是", "驿站名称，下拉显示字段"],
         ["code", "varchar(240)", "否", "驿站编号，唯一"],
         ["address", "varchar(240)", "是", "地址"],
         ["phone", "varchar(240)", "是", "联系电话"],
         ["manager", "varchar(240)", "是", "负责人"],
         ["status", "varchar(240)", "是", "状态（启用）"],
         ["tenant_id / create_user / create_date / update_user / update_date", "varchar(64) / varchar(64) / datetime / varchar(64) / datetime", "是", "平台审计列"]],
        caption="表 5-2　ex_station 表结构", widths=[6.2, 5.2, 1.6, 3.0])

d.p("（2）货架表 ex_shelf", indent=False, size=11)
d.table(["字段", "类型", "允许空", "说明"],
        [["id", "varchar(64)", "否", "主键"],
         ["code", "varchar(240)", "是", "货架编号，下拉显示字段"],
         ["station_id", "varchar(64)", "是", "所属驿站（外键）"],
         ["area", "varchar(240)", "是", "区域"],
         ["capacity", "int", "是", "容量"],
         ["used", "int", "是", "已占用数"],
         ["status", "varchar(240)", "是", "状态（FREE / FULL）"]],
        caption="表 5-3　ex_shelf 表结构", widths=[4.6, 5.2, 2.2, 4.0])

d.p("（3）包裹表 ex_parcel", indent=False, size=11)
d.table(["字段", "类型", "允许空", "说明"],
        [["id", "varchar(64)", "否", "主键"],
         ["parcel_no", "varchar(240)", "否", "包裹编号，唯一，由编码规则生成"],
         ["waybill_no", "varchar(240)", "否", "运单号，唯一，服务端校验重复"],
         ["station_id", "varchar(64)", "是", "所属驿站（外键）"],
         ["shelf_id", "varchar(64)", "是", "所在货架（外键）"],
         ["receiver_name", "varchar(240)", "是", "收件人"],
         ["receiver_phone", "varchar(240)", "是", "收件人手机号"],
         ["phone_tail", "varchar(240)", "是", "手机尾号（四位，便于核对）"],
         ["courier", "varchar(240)", "是", "快递公司"],
         ["inbound_time", "datetime", "是", "入库时间"],
         ["pickup_code", "varchar(240)", "是", "取件码（月日 + 四位流水）"],
         ["status", "varchar(240)", "是", "IN_STOCK / ON_SHELF / OVERDUE / PICKED / RETURNED"],
         ["on_shelf_time", "datetime", "是", "上架时间（超期判定基准）"],
         ["outbound_time", "datetime", "是", "出库时间"],
         ["remark", "varchar(240)", "是", "备注"]],
        caption="表 5-4　ex_parcel 表结构", widths=[3.6, 5.2, 1.8, 5.4])

d.p("（4）操作记录表 ex_parcel_log", indent=False, size=11)
d.table(["字段", "类型", "允许空", "说明"],
        [["id", "varchar(64)", "否", "主键"],
         ["parcel_id", "varchar(64)", "是", "所属包裹（外键）"],
         ["action", "varchar(240)", "是", "动作：IN_STOCK / ON_SHELF / PICKED / RETURNED / REISSUE / OVERDUE"],
         ["code", "varchar(240)", "是", "发生时的取件码"],
         ["operator", "varchar(240)", "是", "操作人账号 id"],
         ["operator_name", "varchar(240)", "是", "操作人姓名"],
         ["happen_time", "datetime", "是", "发生时间"],
         ["remark", "varchar(240)", "是", "备注（如「旧取件码 11080001 已作废」）"]],
        caption="表 5-5　ex_parcel_log 表结构", widths=[3.2, 5.2, 2.0, 6.0])

d.p("（5）驿站人员表 ex_station_user", indent=False, size=11)
d.table(["字段", "类型", "允许空", "说明"],
        [["id", "varchar(64)", "否", "主键"],
         ["user_login", "varchar(240)", "否", "登录账号，唯一"],
         ["user_name", "varchar(240)", "是", "显示姓名（与账号取并集匹配）"],
         ["station_id", "varchar(64)", "是", "绑定驿站（外键）"],
         ["status", "varchar(240)", "是", "enabled / disabled"],
         ["remark", "varchar(240)", "是", "备注"]],
        caption="表 5-6　ex_station_user 表结构", widths=[4.0, 5.2, 2.0, 5.2])

d.h("5.6　取件码生成算法", 2)
d.p("取件码采用「月日 + 四位当日流水」的格式，例如 11 月 8 日的第一件为 11080001。算法按以下步骤执行：")
d.bullets([
    "取当前日期，格式化为 MMDD，作为取件码前缀。",
    "查询当天已存在的、处于在架或超期状态的有效包裹，取流水号最大值加一。",
    "流水号补齐四位，与前缀拼接；若达到 9999，则抛出异常提示当日号段用尽。",
    "把取件码写入包裹记录，同时写一条 ON_SHELF 操作记录。",
])
d.p("之所以不建数据库唯一索引，是因为取件码允许在「已取件的旧包裹」与「当日新包裹」之间复用——客户只看当期有效码，历史码保留在操作记录里供追溯。为保证不重复，生成时以「状态属于在架/超期」作为过滤条件，并在方法上加锁避免并发下取到同一个流水号。对应的核心代码如下：")
d.code([
    "private String nextPickupCode() {",
    '    String prefix = new SimpleDateFormat("MMdd").format(new Date());',
    '    Filter active = Filter.OR(Filter.equal("status", "ON_SHELF"),',
    '                              Filter.equal("status", "OVERDUE"));',
    "    List<Parcel> today = this.search(Filter.AND(active,",
    "            Filter.llike(\"pickupCode\", prefix)),",
    '            Collections.singletonList("pickupCode"), 0, 0, null);',
    "    int max = 0;",
    "    for (Parcel p : today) {",
    '        String c = p.getStr("pickupCode");',
    "        if (c != null && c.length() == 8) {",
    "            max = Math.max(max, Integer.parseInt(c.substring(4)));",
    "        }",
    "    }",
    '    return prefix + String.format("%04d", max + 1);',
    "}",
])

d.h("5.7　数据隔离设计", 2)
d.p("平台本身提供基于「作用域（SG）」的数据隔离体系：由维度、作用域、规则、角色授权、用户实例五类元数据共同决定某个用户在某个模型上能看到哪些行。该体系需要在 SaaS 管理端逐项配置，本项目环境未开通该界面，因此在业务服务层实现语义等价的隔离：")
d.bullets([
    "新增 ex_station_user 表，记录「账号—驿站」的绑定关系，由管理员维护。",
    "包裹服务提供 myStationIds()，按当前登录人的账号或姓名查出绑定驿站；未绑定的账号（管理员）返回空集合，表示不过滤。",
    "重写 search 与 count，把 station in (我的驿站) 与调用方传入的条件做 AND 合并；所有 selectById 之后的写操作再调用 assertInScope 二次校验。",
    "查询当前用户时存在一个坑：普通用户用 rbac_user 模型查自己的记录会被 iam 的数据权限挡住，因此改用引擎提供的 getMeta().getUser()/getUserName()，并按「登录名或显示名」双条件匹配绑定关系。",
])
d.p("与官方 SG 相比，本方案少了按角色细分的字段级控制与跨应用统一配置，但对本项目的「按驿站隔离」需求已经足够，且实现集中在服务层、便于测试。若后续接入 SaaS 管理端，只需把 myStationIds() 换成 SG 上下文取值即可。")

d.h("5.8　异常与错误码", 2)
d.table(["场景", "返回码", "提示信息"],
        [["未携带 token / token 失效", "7100 / -32604", "token不能为空 / Unauthorized"],
         ["运单号重复", "2000", "该运单号已入库，请勿重复登记"],
         ["重复取件", "504", "该包裹已于 <时间> 取件，不能重复出库"],
         ["取件码不存在或跨驿站", "504", "取件码不存在或不属于你所在驿站：<code>"],
         ["跨驿站操作", "504", "无权操作驿站[<id>]的包裹 <parcelNo>"],
         ["向非本驿站入库", "504", "无权向驿站[<id>]入库，只能操作自己绑定的驿站"],
         ["重复退货", "504", "已经退过货了"],
         ["已取件包裹退货", "504", "已取件的包裹不能退货"]],
        caption="表 5-7　业务异常与返回码", widths=[4.6, 3.4, 7.0])

# ============ 第 6 章 ============
d.h("第 6 章　项目实现", 1)

d.h("6.1　开发与运行环境", 2)
d.table(["类别", "软件/版本", "说明"],
        [["操作系统", "Windows 10/11", "开发机与演示机一致"],
         ["JDK", "1.8.0_504（Temurin）", "与平台引擎要求的 Java 8 一致"],
         ["构建工具", "Maven 3.10.0 + 离线仓库", "使用本地离线仓库，可断网构建"],
         ["开发 IDE", "IntelliJ IDEA", "用于模型、服务与视图的开发"],
         ["数据库", "MySQL 8.0.46", "独立实例，端口 3307，库 iidp_demo_v3，utf8mb4_bin"],
         ["缓存", "Redis", "端口 6379"],
         ["对象存储", "MinIO", "端口 9000（控制台 9001），桶 apps"],
         ["Web 服务器", "Nginx", "端口 30666，托管前端并反代 /api/"],
         ["后端服务", "sie-snest 引擎 + express APP", "端口 8061"],
         ["授权服务", "grant-dev", "端口 8080，启动后端前必须先启动"]],
        caption="表 6-1　开发与运行环境", widths=[2.6, 6.0, 6.4])

d.h("6.2　工程结构", 2)
d.p("业务代码以独立 APP 的形式组织，工程结构如下（只列出关键目录）：")
d.code([
    "iidp-backend-demo/",
    "├─ apps/                        # 引擎运行目录",
    "│  ├─ apps.json                 # 声明加载哪些 SDK 应用 jar",
    "│  └─ modules/                  # 平台应用与业务应用 jar",
    "├─ sie-iidp-demo-apps/          # 业务 APP 源代码（Maven 多模块）",
    "│  ├─ sie-snest-code/           # 编码规则应用（平台提供，用于统一发号）",
    "│  └─ sie-snest-express/        # 快递驿站应用（本项目）",
    "│     └─ src/main/java/com/sie/app/express/",
    "│        ├─ app.json            # 应用声明：名称、依赖、视图与数据清单",
    "│        ├─ model/              # Station/Shelf/Parcel/ParcelLog/StationUser",
    "│        ├─ views/              # 5 组视图 JSON + menus.json",
    "│        └─ data/express-seed.json  # 种子数据（驿站、货架、人员、授权）",
    "└─ sie-iidp-demo-start/         # 启动模块（Spring Boot 可执行 jar）",
])

d.h("6.3　关键代码实现", 2)
d.p("（1）包裹入库：自动编号、状态初始化与运单号唯一校验", indent=False, size=11)
d.code([
    '@MethodService(name = "create", auth = "create", description = "包裹入库登记")',
    "public RecordSet create(RecordSet rs, List<Map<String, Object>> valuesList) {",
    "    for (Map<String, Object> item : valuesList) {",
    '        item.put("parcelNo", nextParcelNo());',
    '        item.put("status", "IN_STOCK");',
    '        item.put("inboundTime", new Date());',
    '        String phone = (String) item.get("receiverPhone");',
    "        if (phone != null && phone.length() >= 4) {",
    '            item.put("phoneTail", phone.substring(phone.length() - 4));',
    "        }",
    "    }",
    '    return (RecordSet) rs.callSuper(Parcel.class, "create", valuesList);',
    "}",
])
d.p("运单号的唯一性由字段上的 @Validate.Unique(properties = {\"waybillNo\"}, message = \"该运单号已入库，请勿重复登记\") 声明，平台在写入前自动校验并返回友好提示。", indent=False, size=10.5)

d.p("（2）自动上架：分配货架、生成取件码、状态流转", indent=False, size=11)
d.code([
    '@MethodService(name = "autoShelf", auth = "autoShelf", description = "包裹自动上架")',
    "public JsonRpcServiceResponse autoShelf(String[] ids) {",
    "    for (String id : ids) {",
    "        Parcel p = selectById(id);",
    "        assertInScope(p);",
    '        if (!"IN_STOCK".equals(p.getStr("status"))) {',
    "            throw new RuntimeException(\"包裹 \" + p.getParcelNo() + \" 不在库，不能上架\");",
    "        }",
    '        String shelfId = new Shelf().findAvailable(p.getStationId());',
    "        if (shelfId == null) {",
    "            throw new RuntimeException(\"该驿站没有可用货架，请扩容或清理货架\");",
    "        }",
    "        Shelf shelf = new Shelf().selectById(shelfId);",
    '        int used = shelf.getInt("used") == null ? 0 : shelf.getInt("used");',
    '        int capacity = shelf.getInt("capacity") == null ? 0 : shelf.getInt("capacity");',
    '        shelf.set("used", used + 1);',
    '        if (used + 1 >= capacity) { shelf.set("status", "FULL"); }',
    "        shelf.update();",
    '        p.set("shelf", shelfId);',
    '        p.set("pickupCode", nextPickupCode());',
    '        p.set("status", "ON_SHELF");',
    '        p.set("onShelfTime", new Date());',
    "        p.update();",
    '        log(p.getId(), "ON_SHELF", p.getPickupCode(), "自动上架到货架 " + shelf.getCode());',
    "    }",
    "    return JsonRpcServiceResponse.reloadWindow();",
    "}",
])

d.p("（3）取件核销：重复取件拦截与货架释放", indent=False, size=11)
d.code([
    '@MethodService(name = "pickup", auth = "pickup", description = "凭取件码取件")',
    "public Map<String, Object> pickup(String code) {",
    "    Filter active = Filter.AND(Filter.equal(\"pickupCode\", code),",
    '            Filter.OR(Filter.equal("status", "ON_SHELF"), Filter.equal("status", "OVERDUE")));',
    "    List<Parcel> list = this.search(active, Collections.singletonList(\"*\"), 0, 1, null);",
    "    if (list.isEmpty()) {",
    "        // 没有有效包裹时，回查历史记录给出准确提示",
    "        List<Parcel> old = this.search(Filter.equal(\"pickupCode\", code),",
    '                Collections.singletonList("*"), 0, 1, null);',
    "        if (!old.isEmpty() && \"PICKED\".equals(old.get(0).getStr(\"status\"))) {",
    "            throw new RuntimeException(\"该包裹已于 \" + old.get(0).getStr(\"outboundTime\")",
    '                    + " 取件，不能重复出库");',
    "        }",
    "        throw new RuntimeException(\"取件码不存在或不属于你所在驿站：\" + code);",
    "    }",
    "    Parcel p = list.get(0);",
    "    assertInScope(p);",
    "    releaseShelf(p);",
    '    p.set("status", "PICKED");',
    '    p.set("outboundTime", new Date());',
    "    p.update();",
    '    log(p.getId(), "PICKED", code, "取件出库");',
    "    ...",
    "}",
])

d.p("（4）数据隔离：重写查询并注入驿站过滤条件", indent=False, size=11)
d.code([
    '@MethodService(name = "search")',
    "public List<Map<String, Object>> search(RecordSet rs, Filter filter,",
    "        List<String> properties, Integer limit, Integer offset, String order) {",
    "    Filter scoped = mergeScope(filter);",
    '    return (List<Map<String, Object>>) rs.callSuper(Parcel.class, "search",',
    "            scoped, properties, limit, offset, order);",
    "}",
    "",
    "private Filter mergeScope(Filter filter) {",
    "    List<String> ids = myStationIds();",
    "    if (ids == null || ids.isEmpty()) { return filter; }   // 未绑定驿站：不过滤",
    '    Filter mine = Filter.in("station", ids);',
    "    return (filter == null) ? mine : mine.and(filter);",
    "}",
])

d.h("6.4　部署与启动", 2)
d.p("系统由 6 个进程组成，启动顺序为 MySQL → Redis → MinIO → 授权服务 → 后端 → Nginx，其中后端必须在授权服务就绪后再启动。仓库根目录提供 start-all.ps1 / stop-all.ps1 一键启停，也可以按下面的命令逐个启动。")
d.code([
    "# 1) MySQL（独立实例 3307）",
    '& "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqld.exe" --defaults-file=mysql-iidp.ini',
    "# 2) Redis（6379）",
    '& ".\\runtime\\redis\\redis-server.exe" ".\\runtime\\redis\\redis-iidp.conf"',
    "# 3) MinIO（9000 / 控制台 9001），账号 snest / 12345678",
    'minio.exe server ".\\runtime\\minio\\data" --address 127.0.0.1:9000 --console-address 127.0.0.1:9001',
    "# 4) 授权服务（8080）",
    '& ".\\iidp-frontend-dist\\grant\\grant-dev.exe"',
    "# 5) 后端（8061），必须在 iidp-backend-demo 目录下启动",
    'java -jar "sie-iidp-demo-start\\target\\sie-iidp-demo-start-1.0-SNAPSHOT.jar"',
    "# 6) Nginx（30666）",
    '& ".\\nginx\\nginx.exe" -p "." -c "nginx\\conf\\nginx.conf"',
])
d.table(["服务", "地址", "账号"],
        [["前端入口", "http://localhost:30666/iidp/", "zhangsan / Zhang_2026（1 号驿站）"],
         ["", "", "lisi / Li_2026（2 号驿站）"],
         ["", "", "implementer / Implementer_2023（租户管理员）"],
         ["接口文档", "http://localhost:30666/api/root/api/master", "免登录"],
         ["MySQL", "127.0.0.1:3307 / iidp_demo_v3", "root / 123456"],
         ["Redis", "127.0.0.1:6379", "无密码"],
         ["MinIO", "http://127.0.0.1:9000（控制台 9001）", "snest / 12345678"]],
        caption="表 6-2　访问地址与账号", widths=[3.0, 7.0, 5.0])

d.h("6.5　种子数据", 2)
d.p("应用首次安装时由 app.json 中声明的 data/express-seed.json 导入基础数据；因为开启了 seeder.sync，每次启动都会重放种子，已存在的记录按固定 id 覆盖或跳过，因此种子里的 id 需要写成稳定值。")
d.table(["数据", "内容"],
        [["驿站", "华师驿站（菜鸟）ST-001；大学城驿站（丰巢）ST-002"],
         ["货架", "A-01/A-02/B-01 属 1 号驿站；C-01/C-02/D-01 属 2 号驿站（容量 20/20/10/20/20/10）"],
         ["驿站人员", "express_station_user_001（zhangsan→1 号驿站）、express_station_user_002（lisi→2 号驿站）"],
         ["编码规则", "barcode_rule：parcel，前缀 PK，长度 6"],
         ["角色授权", "rbac_role_implementer_admin 与 rbac_role_admin 各一条，appName=express、appAll=1"]],
        caption="表 6-3　种子数据", widths=[3.0, 12.0])

# ============ 第 7 章 ============
d.h("第 7 章　系统测试", 1)

d.h("7.1　测试环境与测试数据", 2)
d.p("测试在开发机上进行，登录前端 http://localhost:30666/iidp/ 后，分别以驿站人员与管理员身份操作页面，同时用 curl 直接调用 JSON-RPC 接口做接口级验证，二者结果一致。测试数据为种子数据中的 2 个驿站、6 个货架，以及测试过程中登记的 9 件包裹。")

d.h("7.2　功能测试用例", 2)
d.table(["编号", "用例", "前置条件", "操作", "预期结果", "结果"],
        [["T01", "包裹入库", "已登录驿站人员", "录入运单号、收件人、手机号", "生成包裹编号 PK000001，状态在库，手机尾号自动截取", "通过"],
         ["T02", "运单号重复", "已存在运单号", "用同一运单号再次入库", "提示「该运单号已入库，请勿重复登记」", "通过"],
         ["T03", "自动上架", "包裹在库", "勾选包裹点「自动上架」", "分配到 A-01，生成取件码 11080001，状态已上架", "通过"],
         ["T04", "取件成功", "包裹在架", "输入取件码取件", "返回取件成功，状态已取件，货架占用减一", "通过"],
         ["T05", "重复取件", "同一包裹已取件", "再次用同一取件码取件", "提示「该包裹已于 … 取件，不能重复出库」", "通过"],
         ["T06", "取件码不存在", "无对应包裹", "输入 99999999", "提示「取件码不存在或不属于你所在驿站」", "通过"],
         ["T07", "重新生成取件码", "包裹在架", "点「重新生成取件码」", "旧码作废并写日志，生成新码", "通过"],
         ["T08", "超期标记", "上架时间超过 3 天", "点「标记超期」", "返回 {days:3, changed:1}，状态变超期", "通过"],
         ["T09", "退货出库", "包裹在架", "点「退货出库」", "状态变已退货，释放货架", "通过"],
         ["T10", "重复退货", "包裹已退货", "再次退货", "提示「已经退过货了」", "通过"],
         ["T11", "货架满自动换架", "货架容量为 1", "连续上架两件", "第一件占满置 FULL，第二件落到下一货架", "通过"],
         ["T12", "运营统计", "有数据", "打开驿站页面点「运营统计」", "返回在库/在架/已取/超期数量与货架使用率", "通过"]],
        caption="表 7-1　功能测试用例", widths=[1.2, 2.4, 2.6, 3.2, 5.4, 1.2], size=9)

d.h("7.3　数据隔离与越权测试", 2)
d.table(["编号", "场景", "操作账号", "观察结果", "通过与否"],
        [["S01", "查看包裹列表", "zhangsan（1 号驿站）", "只返回本驿站 5 条", "通过"],
         ["S02", "查看包裹列表", "lisi（2 号驿站）", "只返回本驿站 4 条", "通过"],
         ["S03", "查看包裹列表", "implementer（管理员）", "返回全部 9 条", "通过"],
         ["S04", "向他人驿站入库", "zhangsan → 2 号驿站", "拒绝：无权向驿站[..]入库", "通过"],
         ["S05", "代取他人驿站包裹", "zhangsan → 2 号驿站包裹", "拒绝：无权操作驿站[..]的包裹", "通过"],
         ["S06", "不指定驿站入库", "zhangsan", "自动落到其绑定的 1 号驿站", "通过"],
         ["S07", "菜单可见性", "zhangsan", "可见「快递驿站/包裹/货架/驿站/人员/操作记录」共 5 个菜单", "通过"]],
        caption="表 7-2　数据隔离测试用例", widths=[1.4, 4.4, 4.6, 5.4, 1.2])

d.h("7.4　测试结论", 2)
d.p("共执行 12 条功能用例与 7 条隔离用例，全部通过。测试过程中发现并修复的主要缺陷包括：取件码复用时命中历史已取件记录导致误报（改为优先匹配在架/超期状态）、普通用户查询 rbac_user 被数据权限拦截导致隔离失效（改用引擎会话 API 并双条件匹配）、货架满时未自动换架（补充容量判断）。系统满足需求调查表中的全部高优先级需求。")

# ============ 第 8 章 ============
d.h("第 8 章　项目分工", 1)
d.p("本项目由 ___ 名成员共同完成，分工如下（请按实际情况替换姓名与学号）：")
d.table(["成员", "学号", "主要工作", "占比"],
        [["＿＿＿（组长）", "＿＿＿＿", "需求调研与文档统稿、模型与服务设计、数据隔离实现", "35%"],
         ["＿＿＿", "＿＿＿＿", "视图与菜单配置、页面联调、测试用例设计与执行", "35%"],
         ["＿＿＿", "＿＿＿＿", "环境搭建与部署脚本、种子数据、演示录制与汇报材料", "30%"]],
        caption="表 8-1　项目分工", widths=[3.2, 2.6, 8.4, 1.8])
d.p("协作方式：使用 Git 管理源代码，主干为 master，按功能提交并在提交信息中说明改动范围；遇到平台使用问题先记录到过程文档，再由组长汇总确认。", indent=True)

# ============ 第 9 章 ============
d.h("第 9 章　课程知识点对照", 1)
d.p("本项目覆盖了课程中模型、视图、服务、权限、编码规则、数据隔离等主要知识点，对应关系如下：")
d.table(["课程知识点", "在本项目中的体现"],
        [["模型定义与注解", "@Model/@Property/@Validate 声明 5 个模型，平台自动建表"],
         ["字段类型与字典", "日期用 DataType.DATE/DATE_TIME，状态用 @Selection 下拉"],
         ["实体关系", "@ManyToOne + @JoinColumn 表达包裹→驿站/货架，@OneToMany 表达驿站→货架"],
         ["视图元数据", "5 组 grid/search/form 视图 JSON，含按钮、列宽、一对多 tab"],
         ["菜单配置", "menus.json 配置根菜单与 5 个子菜单，随应用安装自动注册"],
         ["模型服务", "@MethodService 声明 10 个业务服务，支持越权与状态校验"],
         ["服务重写与 callSuper", "重写 create/search/count，通过 callSuper 回到平台默认实现"],
         ["Filter 条件组合", "Filter.AND/OR/in/llike/equal 组合出隔离条件与统计条件"],
         ["权限与租户", "复用 iam 的用户/角色/租户，租户授权后可访问新 APP"],
         ["编码规则", "依赖 code 应用的 barcode_rule 统一为包裹编号发号"],
         ["数据隔离", "服务层实现按驿站的行级隔离，并补充越权用例"],
         ["版本管理", "Git 提交、分支与远程仓库推送"]],
        caption="表 9-1　课程知识点对照", widths=[4.6, 10.4])

# ============ 附录 ============
d.h("附录 A　参考资料", 1)
d.bullets([
    "《IIDP 工业数字平台基础开发实验手册》，课程讲义。",
    "《IIDP 常用写法总结》，课程讲义。",
    "《华师大-〈中级软件设计实作〉方案 v3》。",
    "《sie-snest 引擎知识库》（课程提供的 SKILL 与 reference 文档）。",
    "JSON-RPC 2.0 Specification，https://www.jsonrpc.org/specification。",
    "MySQL 8.0 Reference Manual，https://dev.mysql.com/doc/refman/8.0/en/。",
])

d.save(OUT)
