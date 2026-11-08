# -*- coding: utf-8 -*-
"""两个课程文档共用的排版工具：中文字体、标题层级、表格、图注、代码块、页码。"""
import json
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

HEI = "黑体"
SONG = "宋体"
LATIN = "Times New Roman"
MONO = "Consolas"

ACCENT = RGBColor(0x1F, 0x38, 0x64)


def set_run_font(run, cn=SONG, latin=LATIN, size=12, bold=False, color=None):
    run.font.name = latin
    run.font.size = Pt(size)
    run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), latin)
    rfonts.set(qn("w:hAnsi"), latin)
    rfonts.set(qn("w:eastAsia"), cn)


def style_font(style, cn, latin, size, bold=False, color=None):
    style.font.name = latin
    style.font.size = Pt(size)
    style.font.bold = bold
    if color is not None:
        style.font.color.rgb = color
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), latin)
    rfonts.set(qn("w:hAnsi"), latin)
    rfonts.set(qn("w:eastAsia"), cn)


def shade(cell_or_par, hexcolor):
    el = cell_or_par._element
    pr = el.find(qn("w:tcPr")) if el.tag.endswith("tc") else el.get_or_add_pPr()
    if pr is None:
        pr = OxmlElement("w:tcPr")
        el.append(pr)
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    pr.append(shd)


def add_field(paragraph, instr):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = instr
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    txt = OxmlElement("w:t")
    txt.text = "1"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr_el)
    run._r.append(fld_sep)
    run._r.append(txt)
    run._r.append(fld_end)
    return run


class Doc(object):
    def __init__(self, page_map=None):
        self.doc = Document()
        self.page_map = dict(page_map or {})
        sec = self.doc.sections[0]
        sec.page_width = Cm(21.0)
        sec.page_height = Cm(29.7)
        sec.top_margin = Cm(2.54)
        sec.bottom_margin = Cm(2.54)
        sec.left_margin = Cm(2.8)
        sec.right_margin = Cm(2.6)
        self.sec = sec
        self._setup_styles()
        self._setup_footer()
        self.fig_no = {}
        self.tab_no = {}

    # ---------- 基础设置 ----------
    def _setup_styles(self):
        st = self.doc.styles["Normal"]
        style_font(st, SONG, LATIN, 12)
        st.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
        st.paragraph_format.space_before = Pt(0)
        st.paragraph_format.space_after = Pt(0)
        st.paragraph_format.first_line_indent = Pt(24)

        for name, size, before, after in (("Heading 1", 16, 18, 12),
                                          ("Heading 2", 14, 14, 8),
                                          ("Heading 3", 12, 10, 6)):
            s = self.doc.styles[name]
            style_font(s, HEI, HEI, size, bold=True, color=ACCENT)
            s.paragraph_format.space_before = Pt(before)
            s.paragraph_format.space_after = Pt(after)
            s.paragraph_format.first_line_indent = Pt(0)
            s.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
            s.paragraph_format.keep_with_next = True

    def _setup_footer(self):
        footer = self.sec.footer
        p = footer.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run("第 ")
        set_run_font(r, SONG, LATIN, 9)
        add_field(p, " PAGE ")
        r2 = p.add_run(" 页  共 ")
        set_run_font(r2, SONG, LATIN, 9)
        add_field(p, " NUMPAGES ")
        r3 = p.add_run(" 页")
        set_run_font(r3, SONG, LATIN, 9)

    def update_fields_on_open(self):
        settings = self.doc.settings.element
        el = OxmlElement("w:updateFields")
        el.set(qn("w:val"), "true")
        settings.append(el)

    # ---------- 内容 ----------
    def cover(self, title, subtitle, meta, note=None):
        for _ in range(4):
            self.doc.add_paragraph()
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Pt(0)
        r = p.add_run(title)
        set_run_font(r, HEI, HEI, 30, bold=True, color=ACCENT)

        p2 = self.doc.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p2.paragraph_format.first_line_indent = Pt(0)
        p2.paragraph_format.space_before = Pt(10)
        r2 = p2.add_run(subtitle)
        set_run_font(r2, HEI, HEI, 15, bold=False)

        for _ in range(3):
            self.doc.add_paragraph()

        t = self.doc.add_table(rows=0, cols=2)
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        t.autofit = False
        for k, v in meta:
            row = t.add_row().cells
            row[0].width = Cm(3.6)
            row[1].width = Cm(7.4)
            p0 = row[0].paragraphs[0]
            p0.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            p0.paragraph_format.first_line_indent = Pt(0)
            r0 = p0.add_run(k)
            set_run_font(r0, SONG, LATIN, 12, bold=True)
            p1 = row[1].paragraphs[0]
            p1.paragraph_format.first_line_indent = Pt(0)
            r1 = p1.add_run(v)
            set_run_font(r1, SONG, LATIN, 12)

        if note:
            for _ in range(2):
                self.doc.add_paragraph()
            pn = self.doc.add_paragraph()
            pn.alignment = WD_ALIGN_PARAGRAPH.CENTER
            pn.paragraph_format.first_line_indent = Pt(0)
            rn = pn.add_run(note)
            set_run_font(rn, SONG, LATIN, 10.5)
        self.doc.add_page_break()

    def toc(self, entries):
        """写死目录：标题 + 点线 + 页码（页码来自 page_map，缺省留空）。"""
        p = self.doc.add_paragraph()
        p.paragraph_format.first_line_indent = Pt(0)
        p.paragraph_format.space_after = Pt(12)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run("目　录")
        set_run_font(r, HEI, HEI, 18, bold=True, color=ACCENT)

        for level, text in entries:
            par = self.doc.add_paragraph()
            pf = par.paragraph_format
            pf.first_line_indent = Pt(0)
            pf.left_indent = Cm(0.0 if level == 1 else (0.75 if level == 2 else 1.5))
            pf.space_before = Pt(6 if level == 1 else 0)
            pf.space_after = Pt(2)
            pf.line_spacing_rule = WD_LINE_SPACING.SINGLE
            pf.tab_stops.add_tab_stop(Cm(15.3), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)

            r1 = par.add_run(text)
            set_run_font(r1, HEI if level == 1 else SONG, LATIN,
                         12 if level == 1 else 10.5, bold=(level == 1), color=ACCENT if level == 1 else None)
            r2 = par.add_run("\t")
            set_run_font(r2, SONG, LATIN, 10.5)
            page = self.page_map.get(text, "")
            r3 = par.add_run(str(page))
            set_run_font(r3, SONG, LATIN, 10.5, bold=(level == 1))
        self.toc_entries = list(entries)
        _out = os.environ.get("TOC_OUT")
        if _out:
            with open(_out, "w", encoding="utf-8") as fh:
                json.dump([[int(lv), tx] for lv, tx in entries], fh, ensure_ascii=False, indent=1)
        self.doc.add_page_break()
        # 正文起始哨兵：1pt 白字，肉眼不可见，用于在 PDF 里定位正文第一页
        if os.environ.get("NOMARK"):
            return
        mark = self.doc.add_paragraph()
        mf = mark.paragraph_format
        mf.first_line_indent = Pt(0)
        mf.space_before = Pt(0)
        mf.space_after = Pt(0)
        mf.line_spacing = Pt(1)
        mr = mark.add_run("BODYMARK")
        set_run_font(mr, SONG, LATIN, 1)
        mr.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)

    def h(self, text, level=1):
        p = self.doc.add_heading(level=level)
        r = p.add_run(text)
        size = {1: 16, 2: 14, 3: 12}[level]
        set_run_font(r, HEI, HEI, size, bold=True, color=ACCENT)
        return p

    def p(self, text, indent=True, size=12, bold=False, align=None, space_after=0):
        par = self.doc.add_paragraph()
        par.paragraph_format.first_line_indent = Pt(24) if indent else Pt(0)
        par.paragraph_format.space_after = Pt(space_after)
        if align == "center":
            par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = par.add_run(text)
        set_run_font(r, SONG, LATIN, size, bold=bold)
        return par

    def bullets(self, items, size=12):
        for it in items:
            par = self.doc.add_paragraph()
            par.paragraph_format.left_indent = Cm(0.9)
            par.paragraph_format.first_line_indent = Cm(-0.45)
            par.paragraph_format.space_after = Pt(2)
            r = par.add_run("· " + it)
            set_run_font(r, SONG, LATIN, size)

    def code(self, lines, size=8.5):
        par = self.doc.add_paragraph()
        par.paragraph_format.first_line_indent = Pt(0)
        par.paragraph_format.left_indent = Cm(0.4)
        par.paragraph_format.space_before = Pt(4)
        par.paragraph_format.space_after = Pt(6)
        par.paragraph_format.line_spacing = 1.0
        shade(par, "F5F6F8")
        for i, ln in enumerate(lines):
            if i:
                par.add_run().add_break()
            r = par.add_run(ln)
            set_run_font(r, SONG, MONO, size)

    def fig(self, filename, caption, width=15.0):
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "img", filename)
        par = self.doc.add_paragraph()
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        par.paragraph_format.first_line_indent = Pt(0)
        par.paragraph_format.space_before = Pt(6)
        run = par.add_run()
        run.add_picture(path, width=Cm(width))
        cap = self.doc.add_paragraph()
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cap.paragraph_format.first_line_indent = Pt(0)
        cap.paragraph_format.space_after = Pt(10)
        r = cap.add_run(caption)
        set_run_font(r, SONG, LATIN, 10.5)

    def table(self, headers, rows, caption=None, widths=None, size=10, align_center=True):
        if caption:
            cap = self.doc.add_paragraph()
            cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            cap.paragraph_format.first_line_indent = Pt(0)
            cap.paragraph_format.space_before = Pt(8)
            cap.paragraph_format.space_after = Pt(2)
            r = cap.add_run(caption)
            set_run_font(r, SONG, LATIN, 10.5)

        t = self.doc.add_table(rows=1, cols=len(headers))
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        hdr = t.rows[0].cells
        for i, h in enumerate(headers):
            cell = hdr[i]
            cell.text = ""
            par = cell.paragraphs[0]
            par.alignment = WD_ALIGN_PARAGRAPH.CENTER
            par.paragraph_format.first_line_indent = Pt(0)
            par.paragraph_format.space_after = Pt(0)
            r = par.add_run(str(h))
            set_run_font(r, HEI, LATIN, size, bold=True)
            shade(cell, "DCE6F2")

        for row in rows:
            cells = t.add_row().cells
            for i, v in enumerate(row):
                cell = cells[i]
                cell.text = ""
                par = cell.paragraphs[0]
                par.paragraph_format.first_line_indent = Pt(0)
                par.paragraph_format.space_after = Pt(0)
                par.paragraph_format.line_spacing = 1.15
                if align_center and i == 0:
                    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
                r = par.add_run(str(v))
                set_run_font(r, SONG, LATIN, size)

        for ri, row in enumerate(t.rows):
            trPr = row._tr.get_or_add_trPr()
            if trPr.find(qn("w:cantSplit")) is None:
                trPr.append(OxmlElement("w:cantSplit"))
            if ri == 0 and trPr.find(qn("w:tblHeader")) is None:
                trPr.append(OxmlElement("w:tblHeader"))

        if widths:
            total = sum(widths)
            limit = 15.3
            if total > limit:
                widths = [w * limit / total for w in widths]
            t.autofit = False
            for i, w in enumerate(widths):
                t.columns[i].width = Cm(w)
                for row in t.rows:
                    row.cells[i].width = Cm(w)
        self.doc.add_paragraph().paragraph_format.space_after = Pt(0)
        return t

    def placeholder_box(self, text, height_cm=6.0):
        """灰色占位框（用于待补截图）。"""
        t = self.doc.add_table(rows=1, cols=1)
        t.style = "Table Grid"
        trPr = t.rows[0]._tr.get_or_add_trPr()
        if trPr.find(qn("w:cantSplit")) is None:
            trPr.append(OxmlElement("w:cantSplit"))
        cell = t.rows[0].cells[0]
        cell.width = Cm(15.0)
        cell.text = ""
        shade(cell, "FAFAFA")
        par = cell.paragraphs[0]
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        par.paragraph_format.first_line_indent = Pt(0)
        r = par.add_run("\n" * 3 + text + "\n" * 3)
        set_run_font(r, SONG, LATIN, 10, color=RGBColor(0x80, 0x80, 0x80))

    def save(self, path):
        self.doc.save(path)
        print("saved", path, os.path.getsize(path), "bytes")
