# -*- coding: utf-8 -*-
"""生成课程文档所需的示意图（用例图/分析类图/类图/活动图/时序图）。
只用 Pillow 手绘，避免额外依赖。所有坐标按逻辑单位书写，内部 2 倍超采样后缩小。
"""
import os
import math
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = r"C:\Windows\Fonts\msyh.ttc"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "img")
S = 2

INK = (31, 31, 31)
LINE = (47, 85, 151)
FILL = (232, 241, 251)
FILL2 = (255, 246, 229)
FILL3 = (235, 245, 235)
FILL4 = (255, 235, 235)
WHITE = (255, 255, 255)


def font(size, bold=False):
    try:
        return ImageFont.truetype(FONT_PATH, size * S, index=1 if bold else 0)
    except Exception:
        return ImageFont.truetype(FONT_PATH, size * S)


class Canvas(object):
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGB", (w * S, h * S), WHITE)
        self.d = ImageDraw.Draw(self.im)

    def text(self, x, y, t, size=20, anchor="mm", fill=INK, bold=False):
        self.d.text((x * S, y * S), t, font=font(size, bold), fill=fill, anchor=anchor)

    def measure(self, t, size, bold=False):
        return self.d.textlength(t, font=font(size, bold)) / S

    def wrap(self, x, y, t, size=20, maxw=200, lh=None, fill=INK):
        lh = lh or int(size * 1.45)
        lines, cur = [], ""
        for ch in t:
            if ch == "\n":
                lines.append(cur); cur = ""; continue
            if self.measure(cur + ch, size) > maxw and cur:
                lines.append(cur); cur = ch
            else:
                cur += ch
        if cur:
            lines.append(cur)
        y0 = y - (len(lines) - 1) * lh / 2
        for i, ln in enumerate(lines):
            self.text(x, y0 + i * lh, ln, size=size, fill=fill)
        return len(lines)

    def box(self, x1, y1, x2, y2, fill=FILL, outline=LINE, w=2, r=10):
        if r:
            self.d.rounded_rectangle([x1 * S, y1 * S, x2 * S, y2 * S], radius=r * S,
                                     fill=fill, outline=outline, width=w * S)
        else:
            self.d.rectangle([x1 * S, y1 * S, x2 * S, y2 * S], fill=fill,
                             outline=outline, width=w * S)

    def ellipse(self, x1, y1, x2, y2, fill=FILL, outline=LINE, w=2):
        self.d.ellipse([x1 * S, y1 * S, x2 * S, y2 * S], fill=fill, outline=outline, width=w * S)

    def line(self, pts, w=2, dash=False, fill=LINE):
        if dash:
            for i in range(len(pts) - 1):
                self._dash(pts[i], pts[i + 1], w, fill)
        else:
            for i in range(len(pts) - 1):
                self.d.line([pts[i][0] * S, pts[i][1] * S, pts[i + 1][0] * S, pts[i + 1][1] * S],
                            fill=fill, width=w * S)

    def _dash(self, p1, p2, w, fill, dash=9, gap=7):
        x1, y1 = p1; x2, y2 = p2
        dist = math.hypot(x2 - x1, y2 - y1)
        if dist == 0:
            return
        ux, uy = (x2 - x1) / dist, (y2 - y1) / dist
        t = 0.0
        while t < dist:
            e = min(t + dash, dist)
            self.d.line([(x1 + ux * t) * S, (y1 + uy * t) * S,
                         (x1 + ux * e) * S, (y1 + uy * e) * S], fill=fill, width=w * S)
            t = e + gap

    def _head(self, p1, p2, w, fill):
        ang = math.atan2(p2[1] - p1[1], p2[0] - p1[0])
        L, W = 13, 6
        x, y = p2
        pts = [(x, y),
               (x - L * math.cos(ang) + W * math.sin(ang), y - L * math.sin(ang) - W * math.cos(ang)),
               (x - L * math.cos(ang) - W * math.sin(ang), y - L * math.sin(ang) + W * math.cos(ang))]
        self.d.polygon([(px * S, py * S) for px, py in pts], fill=fill)

    def arrow(self, p1, p2, label=None, w=2, dash=False, head=True, fill=LINE,
              lsize=16, loff=(0, 0)):
        self.line([p1, p2], w=w, dash=dash, fill=fill)
        if head:
            self._head(p1, p2, w, fill)
        if label:
            self.label((p1[0] + p2[0]) / 2 + loff[0], (p1[1] + p2[1]) / 2 + loff[1], label, lsize)

    def label(self, x, y, t, size=16):
        tw = self.measure(t, size) + 12
        self.d.rectangle([(x - tw / 2) * S, (y - 12) * S, (x + tw / 2) * S, (y + 12) * S], fill=WHITE)
        self.text(x, y, t, size=size)

    def actor(self, x, y, name, size=18):
        r = 13
        self.d.ellipse([(x - r) * S, y * S, (x + r) * S, (y + 2 * r) * S], outline=LINE, width=2 * S)
        self.line([(x, y + 2 * r), (x, y + 2 * r + 34)], w=2)
        self.line([(x - 26, y + 2 * r + 12), (x + 26, y + 2 * r + 12)], w=2)
        self.line([(x, y + 2 * r + 34), (x - 22, y + 2 * r + 66)], w=2)
        self.line([(x, y + 2 * r + 34), (x + 22, y + 2 * r + 66)], w=2)
        self.wrap(x, y + 2 * r + 92, name, size=size, maxw=250)

    def save(self, name):
        os.makedirs(OUT_DIR, exist_ok=True)
        p = os.path.join(OUT_DIR, name)
        self.im.resize((self.w, self.h), Image.LANCZOS).save(p)
        print("saved", p, (self.w, self.h))


# ----------------------------------------------------------------------------
# 图 2-1 用例图
# ----------------------------------------------------------------------------
def usecase():
    c = Canvas(1400, 1120)
    c.text(700, 40, "图 2-1  快递驿站管理用例图", size=24, bold=True)
    c.box(400, 80, 1060, 1080, fill=(252, 252, 252), outline=(120, 120, 120), r=14)
    c.text(730, 110, "快递驿站管理系统（express APP）", size=20, bold=True)

    cx = 730
    rows = [
        ("UC01 包裹入库", 200, "staff"),
        ("UC02 自动上架", 288, "staff"),
        ("UC03 取件出库", 376, "staff"),
        ("UC04 退货出库", 464, "staff"),
        ("UC05 重新生成取件码", 552, "staff"),
        ("UC06 运营统计", 640, "staff"),
        ("UC07 超期标记", 728, "admin"),
        ("UC08 驿站管理", 816, "admin"),
        ("UC09 货架管理", 904, "admin"),
        ("UC10 驿站人员与数据范围", 992, "admin"),
    ]
    left = 1000 - 140
    for name, y, who in rows:
        c.ellipse(cx - 150, y - 36, cx + 150, y + 36, fill=FILL)
        c.text(cx, y, name, size=17)
        if who == "staff":
            c.line([(232, 300), (cx - 150, y)], w=2)
            c._head((cx - 160, y), (cx - 150, y), 2, LINE)
        else:
            c.line([(232, 880), (cx - 150, y)], w=2)
            c._head((cx - 160, y), (cx - 150, y), 2, LINE)

    c.actor(150, 200, "驿站人员\nzhangsan / lisi")
    c.actor(150, 780, "系统管理员\nimplementer / superuser")
    c.actor(1230, 400, "取件客户")
    c.line([(1170, 470), (cx + 150, 380)], dash=True, w=2)
    c._head((cx + 160, 380), (cx + 150, 380), 2, LINE)
    c.label(1160, 430, "出示取件码", 15)
    c.text(730, 1052, "说明：驿站人员只能看到自己绑定驿站的包裹（UC03/UC04/UC05/UC06 均受此限制）",
           size=15)
    c.save("usecase.png")


# ----------------------------------------------------------------------------
# 图 4-1 分析类图
# ----------------------------------------------------------------------------
def analysis():
    c = Canvas(1500, 860)
    c.text(750, 36, "图 4-1  快递驿站分析类图", size=24, bold=True)

    def cls(x1, y1, x2, y2, name, stereo, fill):
        c.box(x1, y1, x2, y2, fill=fill)
        c.text((x1 + x2) / 2, y1 + 26, "«%s»" % stereo, size=15)
        c.text((x1 + x2) / 2, y1 + 62, name, size=19, bold=True)

    cls(90, 570, 350, 700, "包裹管理界面", "boundary", FILL2)
    cls(430, 520, 720, 760, "包裹业务服务", "control", FILL3)
    cls(430, 90, 720, 220, "驿站人员 StationUser", "entity", FILL)
    cls(810, 90, 1090, 220, "包裹 Parcel", "entity", FILL)
    cls(1130, 90, 1410, 220, "货架 Shelf", "entity", FILL)
    cls(810, 300, 1090, 430, "操作记录 ParcelLog", "entity", FILL)
    cls(1130, 300, 1410, 430, "驿站 Station", "entity", FILL)
    cls(90, 300, 350, 430, "编码规则 BarcodeRule", "entity", FILL)

    # 控制类 → 实体类
    c.arrow((520, 520), (520, 220), label="取绑定", lsize=15, loff=(0, 0))
    c.arrow((700, 545), (810, 170), label="查询/写入", lsize=15, loff=(-30, 0))
    c.arrow((720, 650), (810, 400), label="写日志", lsize=15, loff=(-20, 10))
    c.arrow((430, 600), (350, 430), label="取号", lsize=15, loff=(0, 0))
    c.arrow((350, 635), (430, 635), label="请求", lsize=15, loff=(0, -24))

    # 实体之间的关系
    c.line([(1090, 155), (1130, 155)], w=2)
    c.label(1110, 130, "0..1", 14)
    c.line([(1270, 220), (1270, 300)], w=2)
    c.label(1290, 260, "1..*", 14)
    c.line([(950, 300), (950, 220)], w=2)
    c.label(970, 260, "1..*", 14)

    c.text(750, 815, "边界类只负责展示与交互，控制类承担业务流程，实体类承载数据与行级隔离规则",
           size=15)
    c.save("analysis.png")


# ----------------------------------------------------------------------------
# 图 5-1 类图
# ----------------------------------------------------------------------------
def classdiag():
    c = Canvas(1520, 1080)
    c.text(760, 34, "图 5-1  快递驿站核心类图", size=24, bold=True)

    def cls(x1, y1, w, name, attrs, methods, fill=FILL):
        x2 = x1 + w
        h = 46 + 14 + len(attrs) * 24 + 10 + 1 + 14 + len(methods) * 24 + 14
        y2 = y1 + h
        c.box(x1, y1, x2, y2, fill=WHITE)
        c.d.rectangle([x1 * S, y1 * S, x2 * S, (y1 + 46) * S], fill=fill, outline=LINE, width=2 * S)
        c.text((x1 + x2) / 2, y1 + 23, name, size=18, bold=True)
        y = y1 + 46 + 16
        for a in attrs:
            c.text(x1 + 14, y, a, size=15, anchor="lm")
            y += 24
        y += 8
        c.line([(x1, y), (x2, y)], w=1)
        y += 16
        for m in methods:
            c.text(x1 + 14, y, m, size=15, anchor="lm")
            y += 24
        c.box(x1, y1, x2, y2, fill=None, outline=LINE, r=0)
        return y2

    s2 = cls(50, 70, 360, "Station 驿站",
             ["- name : String", "- code : String", "- address : String", "- phone : String",
              "- manager : String", "- status : String"],
             ["+ stats(id) : Map"])
    sh2 = cls(510, 70, 360, "Shelf 货架",
              ["- code : String", "- area : String", "- capacity : Integer",
               "- used : Integer", "- status : String"],
              ["+ findAvailable(stationId) : String"])
    su2 = cls(950, 70, 360, "StationUser 驿站人员",
              ["- userLogin : String", "- userName : String", "- station : Station",
               "- status : String"],
              ["+ myStationIds() : List<String>", "+ myScope() : Map"])

    p2 = cls(50, 380, 560, "Parcel 包裹",
             ["- parcelNo : String", "- waybillNo : String", "- station : Station",
              "- shelf : Shelf", "- receiverName : String", "- receiverPhone : String",
              "- phoneTail : String", "- courier : String", "- pickupCode : String",
              "- status : String", "- inboundTime : Date", "- onShelfTime : Date"],
             ["+ create(rs, valuesList)", "+ autoShelf(ids)", "+ pickup(code)",
              "+ returnOut(ids)", "+ reissue(ids)", "+ markOverdue(days)",
              "+ overview()", "+ search(rs, filter, ...)", "+ count(rs, filter)",
              "- assertInScope(parcel)", "- nextPickupCode()", "- releaseShelf(p)"])
    lg2 = cls(670, 380, 340, "ParcelLog 操作记录",
              ["- parcel : Parcel", "- action : String", "- code : String",
               "- operator : String", "- operatorName : String", "- happenTime : Date"],
              ["+ log(parcelId, action, ...)"])
    bc2 = cls(1060, 740, 410, "BarcodeRule 编码规则",
              ["- code : String", "- prefix : String", "- length : Integer"],
              ["+ generateCode(code) : String"])

    # 关联
    c.line([(410, 170), (510, 170)], w=2)
    c.label(432, 146, "1", 15); c.label(486, 146, "1..*", 15)
    c.line([(230, s2), (230, 380)], w=2)
    c.label(258, 362, "1..*", 15)
    c.line([(690, sh2), (430, 380)], w=2)
    c.label(520, 352, "0..1", 15)
    c.line([(610, 470), (670, 470)], w=2)
    c.label(640, 446, "1..*", 15)
    c.text(1130, 352, "（一个账号绑定一个驿站）", size=14)

    # 依赖：编码规则
    c.line([(610, 880), (1060, 880)], dash=True, w=2)
    c._head((1050, 880), (1060, 880), 2, LINE)
    c.label(800, 902, "«use» nextParcelNo()", 15)

    # 状态机注释
    c.box(1060, 380, 1470, 700, fill=(255, 255, 240), outline=(150, 150, 120))
    c.text(1265, 402, "包裹状态机", size=17, bold=True)
    for i, t in enumerate(["IN_STOCK 在库待上架", "ON_SHELF 已上架", "OVERDUE 超期未取",
                           "PICKED 已取件", "RETURNED 已退货"]):
        c.text(1265, 436 + i * 28, t, size=15)
    c.text(1265, 596, "IN_STOCK → ON_SHELF → PICKED", size=14)
    c.text(1265, 622, "ON_SHELF → OVERDUE → PICKED", size=14)
    c.text(1265, 648, "IN_STOCK/ON_SHELF/OVERDUE → RETURNED", size=13)
    c.text(1265, 676, "（PICKED 不可再退货）", size=13)
    c.save("class.png")


# ----------------------------------------------------------------------------
# 图 5-2 活动图（取件）
# ----------------------------------------------------------------------------
def activity_pickup():
    c = Canvas(1220, 1140)
    c.text(560, 34, "图 5-2  取件出库活动图", size=24, bold=True)

    cx = 300
    w, h = 420, 58
    bx1 = 640          # 分支框左边界
    bw = 360
    y = 90

    def step(text, y, fill=FILL, hh=h):
        c.box(cx - w / 2, y, cx + w / 2, y + hh, fill=fill)
        c.wrap(cx, y + hh / 2, text, size=16, maxw=w - 30)
        return y + hh

    def down(y1, y2, label=None):
        c.arrow((cx, y1), (cx, y2), label=label, lsize=15, loff=(26, 0))

    def diamond(text, y, hh=110):
        hw = 150
        cy = y + hh / 2
        pts = [(cx, y), (cx + hw, cy), (cx, cy + hh), (cx - hw, cy)]
        c.d.polygon([(px * S, py * S) for px, py in pts], fill=FILL2, outline=LINE, width=2 * S)
        c.wrap(cx, cy, text, size=15, maxw=190)
        return y + hh, cy

    def branch(cy, text):
        c.arrow((cx + 150, cy), (bx1, cy), label="否", lsize=15, loff=(0, -18))
        c.box(bx1, cy - 34, bx1 + bw, cy + 34, fill=FILL4)
        c.wrap(bx1 + bw / 2, cy, text, size=15, maxw=bw - 26)
        ex = bx1 + bw + 40
        c.arrow((bx1 + bw, cy), (ex - 18, cy))
        c.d.ellipse([(ex - 16) * S, (cy - 16) * S, (ex + 16) * S, (cy + 16) * S],
                    outline=INK, width=3 * S)
        c.d.ellipse([(ex - 8) * S, (cy - 8) * S, (ex + 8) * S, (cy + 8) * S], fill=INK)

    # 开始
    c.d.ellipse([(cx - 16) * S, y * S, (cx + 16) * S, (y + 32) * S], fill=INK)
    y += 32
    down(y, y + 24); y += 24
    y = step("驿站人员输入取件码 / 选中包裹", y)
    down(y, y + 22); y += 22
    y = step("按 pickupCode 查询状态为 ON_SHELF / OVERDUE 的包裹", y, hh=62)
    down(y, y + 22); y += 22

    y2, cy = diamond("存在有效包裹？", y)
    branch(cy, "提示：取件码不存在或不属于你所在驿站")
    down(y2, y2 + 26, "是"); y = y2 + 26

    y2, cy = diamond("是否属于本人绑定驿站？", y)
    branch(cy, "提示：无权操作驿站[...]的包裹")
    down(y2, y2 + 26, "是"); y = y2 + 26

    y2, cy = diamond("包裹状态是否可出库？", y)
    branch(cy, "提示：该包裹已于 … 取件，不能重复出库")
    down(y2, y2 + 26, "在架/超期"); y = y2 + 26

    y = step("释放货架：used - 1，FULL 恢复为 FREE", y)
    down(y, y + 22); y += 22
    y = step("更新包裹：status = PICKED，写 outboundTime", y)
    down(y, y + 22); y += 22
    y = step("写操作记录 parcel_log（操作人 / 时间 / 取件码）", y)
    down(y, y + 22); y += 22
    y = step("返回：取件成功，欢迎下次光临", y, fill=FILL3)
    down(y, y + 28); y += 28
    c.d.ellipse([(cx - 20) * S, y * S, (cx + 20) * S, (y + 40) * S], outline=INK, width=3 * S)
    c.d.ellipse([(cx - 10) * S, (y + 10) * S, (cx + 10) * S, (y + 30) * S], fill=INK)
    c.save("activity_pickup.png")


# ----------------------------------------------------------------------------
# 图 5-3 时序图（取件）
# ----------------------------------------------------------------------------
def sequence():
    c = Canvas(1640, 1040)
    c.text(820, 30, "图 5-3  取件出库时序图", size=24, bold=True)
    names = ["前端页面", "RpcController", "Parcel 服务", "StationUser", "数据库", "ParcelLog"]
    xs = [150, 400, 700, 1000, 1280, 1520]
    top = 100
    for i, (n, x) in enumerate(zip(names, xs)):
        c.box(x - 90, top, x + 90, top + 52, fill=FILL if i < 3 else FILL3)
        c.text(x, top + 26, n, size=17, bold=True)
        c.line([(x, top + 52), (x, 990)], dash=True, w=2)

    def msg(y, i, j, label, dash=False):
        x1, x2 = xs[i], xs[j]
        if x2 > x1:
            c.arrow((x1, y), (x2 - 6, y), label=label, dash=dash, lsize=15, loff=(0, -18))
        else:
            c.arrow((x1, y), (x2 + 6, y), label=label, dash=dash, lsize=15, loff=(0, -18))

    def bar(i, y1, y2):
        x = xs[i]
        c.d.rectangle([(x - 7) * S, y1 * S, (x + 7) * S, y2 * S], fill=(210, 226, 245), outline=LINE)

    def self_msg(y, i, label):
        x = xs[i]
        c.line([(x + 7, y), (x + 96, y)], w=2)
        c.line([(x + 96, y), (x + 96, y + 26)], w=2)
        c.arrow((x + 96, y + 26), (x + 9, y + 26), label=None)
        c.label(x + 150, y + 13, label, 15)

    y = 190
    msg(y, 0, 1, "POST /root/rpc/service/?model=parcel&service=pickup  {code}")
    bar(1, y, y + 700)
    y += 62
    msg(y, 1, 2, "pickup(code)")
    bar(2, y, y + 600)
    y += 58
    msg(y, 2, 3, "myStationIds()")
    bar(3, y, y + 86)
    y += 52
    msg(y, 3, 4, "select station_user where userLogin / userName")
    y += 40
    msg(y, 3, 2, "返回 [stationId]", dash=True)
    y += 56
    msg(y, 2, 4, "select parcel where pickupCode = ? and status in (ON_SHELF, OVERDUE)")
    y += 48
    self_msg(y, 2, "校验收件码 / 归属驿站 / 状态")
    y += 70
    msg(y, 2, 4, "update parcel set status = PICKED, outbound_time = now")
    y += 50
    msg(y, 2, 4, "update shelf set used = used - 1, status = FREE")
    y += 50
    msg(y, 2, 5, "insert parcel_log(action = PICKED)")
    y += 52
    msg(y, 2, 1, "返回 {parcelNo, code, status, message}", dash=True)
    y += 52
    msg(y, 1, 0, "JSON-RPC result", dash=True)
    c.text(820, 1012, "异常分支：重复取件 / 已退货 / 未上架 / 非本驿站，均在 Parcel 服务内抛 RuntimeException，"
                      "由引擎包装为 code=504", size=15)
    c.save("sequence_pickup.png")


if __name__ == "__main__":
    usecase()
    analysis()
    classdiag()
    activity_pickup()
    sequence()
