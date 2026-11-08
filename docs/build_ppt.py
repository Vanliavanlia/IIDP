# -*- coding: utf-8 -*-
"""生成汇报 PPT：快递驿站管理系统（IIDP 中级软件设计实作）"""
import os
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from PIL import Image

BASE = r"D:\Code\intermediate_software"
SHOTS = os.path.join(BASE, "docs", "shots")
IMG = os.path.join(BASE, "docs", "img")
OUT = os.path.join(BASE, "交付文档", "快递驿站管理系统-汇报PPT.pptx")

NAVY = RGBColor(0x12, 0x26, 0x3F)
BLUE = RGBColor(0x1F, 0x6F, 0xEB)
ORANGE = RGBColor(0xE8, 0x8B, 0x2E)
GRAY = RGBColor(0x5A, 0x6C, 0x7D)
LGRAY = RGBColor(0xE3, 0xE8, 0xEF)
BG = RGBColor(0xF7, 0xF9, 0xFC)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
FONT = "微软雅黑"

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]
PAGES = []


def rgb_rect(slide, l, t, w, h, color, line=None, shape=MSO_SHAPE.RECTANGLE):
    sp = slide.shapes.add_shape(shape, Inches(l), Inches(t), Inches(w), Inches(h))
    sp.fill.solid()
    sp.fill.fore_color.rgb = color
    if line is None:
        sp.line.fill.background()
    else:
        sp.line.color.rgb = line
        sp.line.width = Pt(1)
    sp.shadow.inherit = False
    if sp.has_text_frame:
        sp.text_frame.word_wrap = True
    return sp


def tb(slide, l, t, w, h, text, size=18, bold=False, color=NAVY, align=PP_ALIGN.LEFT,
       anchor=MSO_ANCHOR.TOP, space=6, line=1.35, italic=False):
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    lines = text.split("\n")
    for i, s in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.space_after = Pt(space)
        p.line_spacing = line
        r = p.add_run()
        r.text = s
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.italic = italic
        r.font.name = FONT
        r.font.color.rgb = color
    return box


def bullets(slide, l, t, w, items, size=17, gap=10, color=NAVY, bullet_color=BLUE, line=1.3):
    """items: list of (title, desc) or plain str"""
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(4.6))
    tf = box.text_frame
    tf.word_wrap = True
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    first = True
    for it in items:
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.space_after = Pt(gap)
        p.line_spacing = line
        r0 = p.add_run()
        r0.text = "▪ "
        r0.font.size = Pt(size)
        r0.font.bold = True
        r0.font.name = FONT
        r0.font.color.rgb = bullet_color
        if isinstance(it, tuple):
            head, desc = it
            r1 = p.add_run()
            r1.text = head + " "
            r1.font.size = Pt(size)
            r1.font.bold = True
            r1.font.name = FONT
            r1.font.color.rgb = color
            if desc:
                r2 = p.add_run()
                r2.text = desc
                r2.font.size = Pt(size - 1)
                r2.font.name = FONT
                r2.font.color.rgb = GRAY
        else:
            r1 = p.add_run()
            r1.text = it
            r1.font.size = Pt(size)
            r1.font.name = FONT
            r1.font.color.rgb = color
    return box


def head(slide, kicker, title, sub=None):
    rgb_rect(slide, 0, 0, 13.333, 7.5, BG)
    rgb_rect(slide, 0.72, 0.62, 0.09, 0.42, ORANGE)
    tb(slide, 0.95, 0.55, 9.6, 0.34, kicker, size=12, bold=True, color=BLUE)
    tb(slide, 0.95, 0.88, 11.2, 0.62, title, size=27, bold=True, color=NAVY)
    y = 1.62
    if sub:
        tb(slide, 0.95, 1.58, 11.4, 0.4, sub, size=14, color=GRAY)
        y = 2.05
    rgb_rect(slide, 0.95, y - 0.16, 11.45, 0.012, LGRAY)
    return y


def footer(slide, num):
    rgb_rect(slide, 0.95, 6.92, 11.45, 0.01, LGRAY)
    tb(slide, 0.95, 7.0, 8.0, 0.3, "快递驿站管理系统 ｜ IIDP《中级软件设计实作》", size=10, color=GRAY)
    tb(slide, 11.4, 7.0, 1.0, 0.3, "%02d" % num, size=10, bold=True, color=GRAY, align=PP_ALIGN.RIGHT)


def pic_fit(slide, path, l, t, w, h, frame=True):
    """按比例在给定框内居中放置图片，返回实际矩形"""
    iw, ih = Image.open(path).size
    scale = min(w / iw, h / ih)
    pw, ph = iw * scale, ih * scale
    px, py = l + (w - pw) / 2, t + (h - ph) / 2
    if frame:
        pad = 0.06
        fr = rgb_rect(slide, px - pad, py - pad, pw + 2 * pad, ph + 2 * pad, WHITE, line=LGRAY)
    slide.shapes.add_picture(path, Inches(px), Inches(py), Inches(pw), Inches(ph))
    return px, py, pw, ph


def caption(slide, l, t, w, text):
    tb(slide, l, t, w, 0.3, text, size=11, color=GRAY, align=PP_ALIGN.CENTER, italic=True)


def table(slide, l, t, w, headers, rows, col_w=None, size=12, row_h=0.36, header_h=0.42):
    nrow, ncol = len(rows) + 1, len(headers)
    shape = slide.shapes.add_table(nrow, ncol, Inches(l), Inches(t), Inches(w), Inches(header_h + row_h * len(rows)))
    tbl = shape.table
    tbl.first_row = True
    if col_w:
        total = sum(col_w)
        for i, cw in enumerate(col_w):
            tbl.columns[i].width = Emu(int(Inches(w) * cw / total))
    tbl.rows[0].height = Inches(header_h)
    for i in range(1, nrow):
        tbl.rows[i].height = Inches(row_h)
    for c, htext in enumerate(headers):
        cell = tbl.cell(0, c)
        cell.fill.solid()
        cell.fill.fore_color.rgb = NAVY
        cell.margin_left = Inches(0.08)
        cell.margin_right = Inches(0.06)
        cell.vertical_anchor = MSO_ANCHOR.MIDDLE
        p = cell.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.LEFT
        r = p.add_run()
        r.text = htext
        r.font.size = Pt(size)
        r.font.bold = True
        r.font.name = FONT
        r.font.color.rgb = WHITE
    for ri, row in enumerate(rows):
        for c in range(ncol):
            cell = tbl.cell(ri + 1, c)
            cell.fill.solid()
            cell.fill.fore_color.rgb = WHITE if ri % 2 == 0 else RGBColor(0xEE, 0xF3, 0xF9)
            cell.margin_left = Inches(0.08)
            cell.margin_right = Inches(0.06)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            p = cell.text_frame.paragraphs[0]
            p.alignment = PP_ALIGN.LEFT
            r = p.add_run()
            r.text = str(row[c]) if c < len(row) else ""
            r.font.size = Pt(size - 1)
            r.font.name = FONT
            r.font.color.rgb = NAVY
    return shape


def new_slide():
    return prs.slides.add_slide(BLANK)


# ---------------------------------------------------------------- 1 封面
s = new_slide()
rgb_rect(s, 0, 0, 13.333, 7.5, NAVY)
rgb_rect(s, 0, 0, 13.333, 0.12, ORANGE)
rgb_rect(s, 8.6, 0, 4.733, 7.5, RGBColor(0x18, 0x30, 0x50))
tb(s, 1.0, 1.45, 7.4, 0.5, "IIDP 工业数字平台 · 中级软件设计实作", size=15, bold=True, color=RGBColor(0x7F, 0xA9, 0xE0))
tb(s, 1.0, 2.02, 7.4, 1.5, "快递驿站管理系统", size=44, bold=True, color=WHITE, line=1.15)
rgb_rect(s, 1.0, 3.28, 1.5, 0.06, ORANGE)
tb(s, 1.0, 3.58, 7.2, 0.9, "基于元模型驱动的低代码业务 APP 设计与实现", size=19, color=RGBColor(0xC9, 0xD6, 0xE6))
tb(s, 1.0, 5.55, 7.2, 1.0, "组号：＿＿＿＿　　成员：＿＿＿　＿＿＿　＿＿＿\n指导教师：＿＿＿　　日期：2026 年 11 月", size=13, color=RGBColor(0x9F, 0xB3, 0xC9), line=1.6)
tb(s, 9.0, 5.6, 3.9, 1.2, "express APP\n5 张数据表 · 12 个自定义服务", size=13, color=RGBColor(0x9F, 0xB3, 0xC9), line=1.6)

# ---------------------------------------------------------------- 2 目录
s = new_slide()
y = head(s, "CONTENTS", "汇报路线")
items = [
    ("01", "背景与选题", "为什么是「快递驿站」，与课程课题的对应关系"),
    ("02", "需求与设计", "角色与用例、总体架构、数据模型、状态机"),
    ("03", "实现与演示", "入库上架、取件核销、数据隔离与界面"),
    ("04", "验证与总结", "测试结果、工程实践、不足与展望"),
]
for i, (no, t1, t2) in enumerate(items):
    yy = y + 0.15 + i * 1.22
    rgb_rect(s, 0.95, yy, 11.45, 1.05, WHITE, line=LGRAY)
    rgb_rect(s, 0.95, yy, 0.07, 1.05, BLUE if i % 2 == 0 else ORANGE)
    tb(s, 1.28, yy + 0.30, 1.1, 0.45, no, size=24, bold=True, color=RGBColor(0xC7, 0xD3, 0xE2))
    tb(s, 2.35, yy + 0.16, 3.0, 0.4, t1, size=19, bold=True)
    tb(s, 2.35, yy + 0.6, 9.6, 0.35, t2, size=13, color=GRAY)
footer(s, 2)

# ---------------------------------------------------------------- 3 背景
s = new_slide()
y = head(s, "01 背景与选题", "从「一个真实的小场景」切入", "课程课题三：快递驿站的入库、上架、取件与货架管理")
bullets(s, 0.95, y + 0.05, 5.55, [
    ("痛点一", "包裹靠纸质登记，取件码手写，重码/漏码常有"),
    ("痛点二", "取件只认人，重复取件、错取无法追溯"),
    ("痛点三", "多驿站、多货架时，谁能看哪些数据说不清"),
    ("课题要求", "入库 / 出库 / 货架 / 取件码；取件不可重复、有效取件码不可重复"),
], size=16, gap=14)
rgb_rect(s, 6.95, y + 0.05, 5.4, 4.05, WHITE, line=LGRAY)
rgb_rect(s, 6.95, y + 0.05, 5.4, 0.55, BLUE)
tb(s, 7.2, y + 0.14, 5.0, 0.4, "为什么选它", size=15, bold=True, color=WHITE)
tb(s, 7.2, y + 0.78, 4.95, 3.1,
   "模型少而统一（驿站 / 货架 / 包裹 / 日志）\n"
   "业务是一条完整主线：入库 → 上架 → 取件 → 退货\n"
   "「有效取件码唯一」是可讲的实现难点\n"
   "界面以列表 + 表单为主，正好把低代码平台的能力用满",
   size=14, color=NAVY, line=1.7)
footer(s, 3)

# ---------------------------------------------------------------- 4 需求与用例
s = new_slide()
y = head(s, "02 需求与设计", "三类角色、十个用例", "需求调查 16 条（R01–R16），收敛为 10 个用例")
pic_fit(s, os.path.join(IMG, "usecase.png"), 0.95, y + 0.02, 7.7, 4.5)
x2 = 8.85
rgb_rect(s, x2, y + 0.02, 3.55, 4.5, WHITE, line=LGRAY)
tb(s, x2 + 0.25, y + 0.18, 3.1, 0.35, "角色与可见范围", size=14, bold=True, color=BLUE)
tb(s, x2 + 0.25, y + 0.62, 3.05, 3.7,
   "驿站人员（zhangsan / lisi）\n"
   "　只能看到并操作自己绑定驿站的包裹\n\n"
   "系统管理员（implementer）\n"
   "　跨驿站管理、配置货架与驿站人员\n\n"
   "取件客户（外部）\n"
   "　出示取件码，由驿站人员核销\n\n"
   "→ 规则在服务层落地\n　 不依赖人工约束",
   size=11, color=NAVY, line=1.45)
footer(s, 4)

# ---------------------------------------------------------------- 5 架构
s = new_slide()
y = head(s, "02 需求与设计", "总体架构：平台 + 业务 APP 分层", "业务代码只写模型、视图与服务，运行时由引擎接管")
layers = [
    ("展示层", "IIDP 前端（30666）／开发者中心", BLUE),
    ("接口层", "JSON-RPC：/root/rpc/login、/root/rpc/service/", RGBColor(0x2E, 0x86, 0xAB)),
    ("业务层", "express APP：5 个模型 + 12 个自定义服务", RGBColor(0x35, 0x9B, 0x8A)),
    ("能力层", "code APP 编码规则 · iam 用户角色 · tenant 租户授权", ORANGE),
    ("数据层", "MySQL 8（3307）· Redis · MinIO 对象存储", GRAY),
]
for i, (name, desc, color) in enumerate(layers):
    yy = y + 0.05 + i * 0.79
    rgb_rect(s, 0.95, yy, 2.0, 0.64, color)
    tb(s, 0.95, yy + 0.16, 2.0, 0.35, name, size=15, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    rgb_rect(s, 3.05, yy, 8.35, 0.64, WHITE, line=LGRAY)
    tb(s, 3.3, yy + 0.18, 7.9, 0.35, desc, size=14, color=NAVY)
tb(s, 0.95, y + 4.15, 11.45, 0.6, "新增一个业务 APP = 建模块 + 写 app.json + 声明模型 + 配视图菜单 + 三处注册，全程无需改平台代码。",
   size=13, color=GRAY)
footer(s, 5)

# ---------------------------------------------------------------- 6 数据模型
s = new_slide()
y = head(s, "02 需求与设计", "数据模型：五张表撑起一条业务链", "模型即元数据，建表、接口、权限码由引擎自动生成")
rows = [
    ["ex_station", "驿站", "名称、编码、地址、电话、负责人、状态", "1 : N 货架 / 包裹"],
    ["ex_shelf", "货架", "编码、区域、容量、已用、状态（FREE/FULL）", "N : 1 驿站"],
    ["ex_parcel", "包裹", "运单号、收件人、手机后四位、取件码、状态", "N : 1 驿站 / 货架"],
    ["ex_parcel_log", "操作记录", "动作、取件码、操作人、时间、备注", "N : 1 包裹"],
    ["ex_station_user", "驿站人员", "登录名、姓名、所属驿站、状态", "N : 1 驿站"],
]
table(s, 0.95, y + 0.05, 7.9, ["表名", "含义", "核心字段", "关系"], rows,
      col_w=[1.35, 0.9, 3.1, 1.5], size=12, row_h=0.62)
rgb_rect(s, 9.15, y + 0.05, 3.25, 4.05, WHITE, line=LGRAY)
tb(s, 9.4, y + 0.22, 2.8, 0.35, "两处设计取舍", size=14, bold=True, color=BLUE)
tb(s, 9.4, y + 0.66, 2.85, 3.3,
   "运单号做唯一索引\n→ 同一运单不能重复入库\n\n"
   "取件码不做唯一索引\n→ 旧码回收后可复用\n　 唯一性由服务层保证\n\n"
   "包裹与日志分离\n→ 操作留痕，可审计",
   size=11, color=NAVY, line=1.45)
footer(s, 6)

# ---------------------------------------------------------------- 7 状态机
s = new_slide()
y = head(s, "02 需求与设计", "包裹状态机：把「不能重复」写进状态", "状态非法即拒绝，是防重复取件的第二道闸")
states = [("IN_STOCK", "在库待上架", BLUE), ("ON_SHELF", "已上架", RGBColor(0x35, 0x9B, 0x8A)),
          ("OVERDUE", "超期未取", ORANGE), ("PICKED", "已取件", RGBColor(0x6C, 0x77, 0x8C)),
          ("RETURNED", "已退货", RGBColor(0x8E, 0x44, 0xAD))]
for i, (code, name, color) in enumerate(states):
    xx = 0.95 + i * 2.32
    yy = y + 0.08
    rgb_rect(s, xx, yy, 2.16, 1.0, WHITE, line=LGRAY)
    rgb_rect(s, xx, yy, 0.07, 1.0, color)
    tb(s, xx + 0.22, yy + 0.12, 1.9, 0.32, code, size=13, bold=True, color=color)
    tb(s, xx + 0.22, yy + 0.52, 1.9, 0.32, name, size=12, color=NAVY)
tb(s, 0.95, y + 1.42, 11.45, 0.36, "允许的迁移", size=13.5, bold=True, color=BLUE)
table(s, 0.95, y + 1.80, 11.45, ["来源状态", "动作", "目标状态", "约束"], [
    ["IN_STOCK", "autoShelf 自动上架", "ON_SHELF", "分配货架、used+1、生成取件码"],
    ["ON_SHELF / OVERDUE", "pickup 取件核销", "PICKED", "释放货架、写 outboundTime 与日志"],
    ["ON_SHELF", "markOverdue 标记超期", "OVERDUE", "默认超 3 天，可传天数"],
    ["IN_STOCK / ON_SHELF / OVERDUE", "returnOut 退货", "RETURNED", "已取件不可退，避免绕过取件"],
], col_w=[2.5, 2.4, 1.6, 4.6], size=12, row_h=0.44, header_h=0.42)
footer(s, 7)

# ---------------------------------------------------------------- 8 入库上架
s = new_slide()
y = head(s, "03 实现与演示", "功能一：入库登记 + 自动上架", "表单录入后一键上架，货架容量与取件码自动分配")
pic_fit(s, os.path.join(SHOTS, "crop", "list.png"), 0.95, y + 0.05, 11.45, 3.15)
caption(s, 0.95, y + 3.25, 11.45, "包裹列表（zhangsan 登录，仅可见本驿站 5 条）")
bullets(s, 0.95, y + 3.62, 11.45, [
    ("入库", "自动补 parcelNo（编码中心 PK+6 位）、inboundTime、手机后四位与 create_user"),
    ("上架", "按 used < capacity 取第一个空闲货架，满架置 FULL 并自动落到下一个；取件码 = MMDD + 4 位流水"),
], size=13, gap=8)
footer(s, 8)

# ---------------------------------------------------------------- 9 取件核销
s = new_slide()
y = head(s, "03 实现与演示", "功能二：取件核销，重复取件被拦住", "先按「有效状态」查，再校验归属与状态，最后落日志")
pic_fit(s, os.path.join(IMG, "activity_pickup.png"), 0.95, y + 0.02, 4.4, 4.45)
x2 = 5.75
bullets(s, x2, y + 0.12, 6.65, [
    ("查", "pickupCode 匹配且状态为 ON_SHELF / OVERDUE 才算命中"),
    ("校验", "不属于本人驿站 → 提示取件码不存在或不属于你所在驿站"),
    ("核销", "释放货架（used-1，FULL 回 FREE）、置 PICKED、写 outboundTime"),
    ("留痕", "写 parcel_log：动作、取件码、操作人、时间"),
    ("防重", "再次取件返回 code=504「已于 … 取件，不能重复出库」"),
], size=15, gap=15)
rgb_rect(s, x2, y + 3.55, 6.65, 0.85, WHITE, line=ORANGE)
tb(s, x2 + 0.25, y + 3.66, 6.2, 0.65, "「有效取件码唯一」＝ 只在在架/超期包裹范围内唯一，旧码可回收复用。",
   size=12, color=NAVY)
footer(s, 9)

# ---------------------------------------------------------------- 10 数据隔离
s = new_slide()
y = head(s, "03 实现与演示", "功能三：按驿站的数据隔离", "平台 SG 太重，改为在服务层做等价的行级隔离")
pic_fit(s, os.path.join(IMG, "sequence_pickup.png"), 0.95, y + 0.02, 6.5, 2.85)
table(s, 7.75, y + 0.05, 4.65, ["账号", "绑定驿站", "可见包裹"], [
    ["implementer", "无（管理员）", "9 条"],
    ["zhangsan", "华师驿站 ST-001", "5 条"],
    ["lisi", "大学城驿站 ST-002", "4 条"],
], col_w=[1.5, 2.1, 1.05], size=12, row_h=0.42)
rgb_rect(s, 7.75, y + 2.05, 4.65, 2.55, WHITE, line=LGRAY)
tb(s, 8.0, y + 2.18, 4.25, 2.35,
   "实现：重写 parcel.search / count\n"
   "　　　合并 station in (我的驿站)\n"
   "写入同理校验归属\n\n"
   "越权实测：\n"
   "往 ST-002 入库 → 504 无权入库\n"
   "取 ST-002 包裹 → 504 无权操作",
   size=10.5, color=NAVY, line=1.45)
tb(s, 0.95, y + 3.0, 6.4, 1.5,
   "一个坑：普通用户查 rbac_user 返回 0 行（带组织/数据权限过滤）。\n"
   "兜底顺序：getMeta().getUser() → rbac_user → getMeta().getUserName()（显示名）。",
   size=12, color=GRAY, line=1.6)
footer(s, 10)

# ---------------------------------------------------------------- 11 界面一览
s = new_slide()
y = head(s, "03 实现与演示", "界面一览：全部由视图 JSON 渲染", "开发者中心生成视图后拷入工程，配 menus.json 即可出菜单")
grid = [
    ("crop/form.png", "入库登记表单", 0.95, y + 0.02),
    ("crop/shelf.png", "货架管理（容量 / 已用 / 状态）", 6.72, y + 0.02),
    ("crop/station.png", "驿站管理（含运营统计）", 0.95, y + 2.10),
    ("crop/log.png", "操作记录（动作 / 取件码 / 操作人）", 6.72, y + 2.10),
]
for path, cap, xx, yy in grid:
    pic_fit(s, os.path.join(SHOTS, path), xx, yy, 5.55, 1.55)
    caption(s, xx, yy + 1.62, 5.55, cap)
tb(s, 0.95, y + 4.25, 11.45, 0.8,
   "五个视图文件（station / shelf / parcel / parcel-log / station-user）＋ 一个 menus.json；grid / search / form 三种类型覆盖列表、筛选与表单，"
   "按钮以 {\"model\": \"parcel\", \"service\": \"autoShelf\"} 直接调用模型服务。",
   size=12, color=GRAY, line=1.5)
footer(s, 11)

# ---------------------------------------------------------------- 12 技术要点
s = new_slide()
y = head(s, "03 实现与演示", "技术要点：服务即接口", "12 个自定义服务，前端按钮与外部调用走同一条 JSON-RPC")
rows = [
    ["parcel.create", "入库登记：补编号、状态、时间、创建人", "重写内置 create"],
    ["parcel.autoShelf", "批量自动上架并分配取件码", "内置 + 自定义"],
    ["parcel.pickup", "按取件码核销，防重复", "自定义"],
    ["parcel.returnOut / reissue", "退货、重新生成取件码（旧码作废）", "自定义"],
    ["parcel.markOverdue", "按天数标记超期", "自定义"],
    ["parcel.search / count", "注入驿站范围（数据隔离）", "重写内置"],
    ["station.stats", "驿站运营统计（库存/在架/已取/超期/货架利用率）", "自定义"],
    ["shelf.findAvailable", "按容量找可用货架", "自定义"],
]
table(s, 0.95, y + 0.05, 11.45, ["服务", "职责", "类型"], rows,
      col_w=[2.6, 6.3, 2.0], size=12, row_h=0.42)
tb(s, 0.95, y + 4.0, 11.45, 0.5,
   "统一入口：POST /root/rpc/service/?model=<模型>&service=<服务>，两个鉴权头 Authorization: Bearer <token> 与 token: <token>。",
   size=12, color=GRAY)
footer(s, 12)

# ---------------------------------------------------------------- 13 测试
s = new_slide()
y = head(s, "04 验证与总结", "测试与验证：12 条功能 + 7 条隔离", "接口级实测，全部通过")
table(s, 0.95, y + 0.05, 6.5, ["编号", "用例", "结果"], [
    ["T01–T03", "入库 / 唯一运单号 / 手机尾号", "通过"],
    ["T04–T05", "自动上架 / 货架满则换架", "通过"],
    ["T06–T08", "取件 / 重复取件拦截 / 错误码提示", "通过"],
    ["T09–T10", "退货 / 重复退货拦截", "通过"],
    ["T11–T12", "重新生成取件码 / 超期标记", "通过"],
], col_w=[1.3, 3.7, 1.1], size=12, row_h=0.42)
table(s, 7.75, y + 0.05, 4.65, ["编号", "场景", "结果"], [
    ["S01–S02", "驿站人员仅见本驿站", "通过"],
    ["S03–S04", "越权入库/取件被拒", "通过"],
    ["S05", "管理员不受限", "通过"],
    ["S06–S07", "未绑定账号不过滤 / 换码", "通过"],
], col_w=[1.2, 2.5, 0.9], size=12, row_h=0.42)
rows = [
    ["包裹入库 → 上架 → 取件", "PK000001 → 11080001 → PICKED", "货架 used 2→1"],
    ["重复取件", "code=504，提示已于 2026-11-08 取件", "状态不变"],
    ["货架容量 1 的边界", "第 2 件自动落到 D-01", "FULL/FREE 正确"],
    ["数据隔离", "zhangsan 5 / lisi 4 / implementer 9", "越权 504"],
]
table(s, 0.95, y + 2.72, 11.45, ["场景", "观察结果", "结论"], rows,
      col_w=[3.3, 6.0, 2.15], size=11.5, row_h=0.36)
footer(s, 13)

# ---------------------------------------------------------------- 14 工程实践
s = new_slide()
y = head(s, "04 验证与总结", "工程实践：踩坑与沉淀", "16 条问题记录，其中 5 条写成了过程文档的典型案例")
cards = [
    ("引擎 SQL 模板加载顺序", "shardingsphere 自带 sql 目录被先加载，缺 id 属性导致启动 NPE；把引擎 4 个模板前置到 classes/sql 解决。"),
    ("MinIO 内网地址不可达", "内网 endpoint 连不上导致启动卡死；改为本地 MinIO + 关闭 file seed。"),
    ("Bool 的包路径", "在 engine.model 不在 sdk；create 不能 @Override，要用 @MethodService 声明。"),
    ("取件码复用误报", "旧码与在架新码重复时命中了旧记录；改为先查有效状态再回退。"),
]
for i, (t1, t2) in enumerate(cards):
    xx = 0.95 + (i % 2) * 5.85
    yy = y + 0.05 + (i // 2) * 2.15
    rgb_rect(s, xx, yy, 5.6, 1.95, WHITE, line=LGRAY)
    rgb_rect(s, xx, yy, 5.6, 0.06, BLUE if i % 2 == 0 else ORANGE)
    tb(s, xx + 0.25, yy + 0.22, 5.1, 0.4, t1, size=14, bold=True, color=NAVY)
    tb(s, xx + 0.25, yy + 0.68, 5.1, 1.15, t2, size=11.5, color=GRAY, line=1.45)
tb(s, 0.95, y + 4.35, 11.45, 0.5,
   "Git 分阶段提交 12 次；交付《项目开发文档》约 25 万字级排版（29 表 5 图）与《项目开发过程文档》（16 条问题清单）。",
   size=12, color=GRAY)
footer(s, 14)

# ---------------------------------------------------------------- 15 不足与展望
s = new_slide()
y = head(s, "04 验证与总结", "不足与下一步", "先把主线做扎实，再谈平台化")
bullets(s, 0.95, y + 0.1, 5.6, [
    ("定时任务", "超期目前靠手动/接口触发，可加调度器每日跑一次"),
    ("取件码规则", "现在是 MMDD+4 位，可按驿站分前缀、支持短信通知"),
    ("权限粒度", "隔离落在服务层，后续可切回平台 SG 五张表配置"),
], size=15, gap=16)
bullets(s, 6.9, y + 0.1, 5.5, [
    ("统计看板", "已有 station.stats，可做成图表页"),
    ("扫码作业", "接扫码枪/手机扫码，减少手工录入"),
    ("多租户", "驿站群按租户拆分，配组织与角色模板"),
], size=15, gap=16)
rgb_rect(s, 0.95, y + 3.55, 11.45, 1.0, WHITE, line=BLUE)
tb(s, 1.25, y + 3.72, 10.9, 0.7,
   "课程要求已覆盖：需求分析、可行性、概要/详细设计（类图、活动图、时序图）、实现、测试、分工，以及过程文档的问题清单与交流记录。",
   size=12.5, color=NAVY)
footer(s, 15)

# ---------------------------------------------------------------- 16 结束
s = new_slide()
rgb_rect(s, 0, 0, 13.333, 7.5, NAVY)
rgb_rect(s, 0, 7.38, 13.333, 0.12, ORANGE)
tb(s, 1.0, 2.35, 11.3, 1.0, "谢谢，请老师批评指正", size=40, bold=True, color=WHITE)
tb(s, 1.0, 3.55, 11.3, 0.5, "现场演示：登录 → 入库登记 → 自动上架 → 取件核销 → 越权拦截", size=16,
   color=RGBColor(0x9F, 0xB3, 0xC9))
rgb_rect(s, 1.0, 4.35, 1.6, 0.06, ORANGE)
tb(s, 1.0, 4.65, 11.3, 0.9, "http://localhost:30666/iidp/　｜　zhangsan / Zhang_2026　｜　implementer / Implementer_2023",
   size=13, color=RGBColor(0x7F, 0x97, 0xB3))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
prs.save(OUT)
print("saved", OUT, os.path.getsize(OUT), "bytes,", len(prs.slides.__iter__.__self__._sldIdLst), "slides")
