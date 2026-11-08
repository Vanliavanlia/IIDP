# -*- coding: utf-8 -*-
"""按当前 docx 的正文标题重排静态目录（只改目录，不动正文）。

用法：
    python docs/fix_toc_inplace.py

流程：
    1. 备份到 runtime/backup/
    2. 转 PDF 量页码（目录页按“标题密度”识别，避免把目录页当成正文）
    3. 复用原目录条目的段落格式，克隆出与新标题一一对应的条目
    4. 保存后再量一次，页码有漂移就再写一遍（最多 4 轮）
"""
import copy
import os
import re
import shutil
import subprocess
import sys
import time

import docx
from docx.text.paragraph import Paragraph

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pdftext

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCX = os.path.join(ROOT, "交付文档", "快递驿站管理系统-项目开发文档.docx")
WORK = os.path.join(ROOT, "docs", ".tocfix")
NODE = r"D:\Program Files\DSH Desktop\DSH Desktop.exe"
CLI = r"D:\Program Files\DSH Desktop\resources\office-cli.mjs"

WS = re.compile(r"[\s\u3000]+")
ENTRY = re.compile(r"^(第\s*\d+\s*章|附录\s*[A-Z]|\d+\.\d+)")
HEADRE = re.compile(r"(第\s*\d+\s*章|附录\s*[A-Z]|\d+\.\d+)")
TOC_DENSITY = 6          # 一页里出现这么多个“X.Y / 第N章”就当它是目录页

PROTO = {}


def norm(s):
    return WS.sub("", s)


def to_pdf(docx_path, pdf_path):
    if os.path.exists(pdf_path):
        os.remove(pdf_path)
    env = dict(os.environ, ELECTRON_RUN_AS_NODE="1")
    run = subprocess.run(
        [NODE, CLI, "convert", "--input", docx_path, "--output", pdf_path],
        capture_output=True, text=True, env=env, encoding="utf-8", errors="replace",
    )
    if not os.path.exists(pdf_path):
        raise RuntimeError("转换失败：%s\n%s" % (run.stdout, run.stderr))
    return pdf_path


def toc_range(paras):
    """返回 (目录标题段序号, 最后一条目录条目序号, 正文第一段序号)。"""
    title = None
    for i, p in enumerate(paras):
        if norm(p.text) == "目录":
            title = i
            break
    if title is None:
        raise RuntimeError("没找到「目 录」标题段")
    body = None
    for i in range(title + 1, len(paras)):
        if paras[i].style.name.startswith("Heading"):
            body = i
            break
    if body is None:
        raise RuntimeError("没找到正文标题")
    last = None
    for i in range(body - 1, title, -1):
        if "\t" in paras[i].text and ENTRY.match(norm(paras[i].text)):
            last = i
            break
    if last is None:
        raise RuntimeError("没找到目录条目")
    return title, last, body


def headings(paras):
    out = []
    for p in paras:
        if p.style.name.startswith("Heading"):
            out.append((int(p.style.name.split()[-1]), p.text.strip()))
    return out


def capture_proto(paras, title, last):
    for i in range(title + 1, last + 1):
        t = norm(paras[i].text)
        if not ENTRY.match(t):
            continue
        if t.startswith("第") or t.startswith("附录"):
            lv = 1
        elif t.count(".") >= 2:
            lv = 3
        else:
            lv = 2
        PROTO.setdefault(lv, copy.deepcopy(paras[i]._p))


def page_map(pages, heads):
    dens = [len(HEADRE.findall(norm(pg))) for pg in pages]
    # 目录从“目 录”那一页开始，向后连续的高密度页都算目录页（正文里的表格也可能命中高密度）
    toc_pages = []
    anchor = next((i for i, pg in enumerate(pages) if "目录" in norm(pg)[:80]), None)
    if anchor is not None:
        b = anchor
        while b + 1 < len(pages) and dens[b + 1] >= TOC_DENSITY:
            b += 1
        toc_pages = list(range(anchor, b + 1))
        body_start = b + 1
    else:
        dense = [i for i, d in enumerate(dens) if d >= TOC_DENSITY]
        toc_pages = dense[:1]
        body_start = (dense[0] + 1) if dense else 0
    first = norm(heads[0][1])
    p0 = None
    for i in range(body_start, len(pages)):
        if first in norm(pages[i]):
            p0 = i
            break
    if p0 is None:
        raise RuntimeError("定位不到正文起始页")
    result, cur = {}, p0
    for lv, text in heads:
        key = norm(text)
        hit = None
        for i in range(cur, len(pages)):
            if key in norm(pages[i]):
                hit = i
                break
        if hit is None:
            raise RuntimeError("页码定位失败：%s" % text)
        result[text] = hit + 1
        cur = hit
    return result, p0, toc_pages


def rewrite(doc, title, last, heads, pmap):
    paras = doc.paragraphs
    parent = paras[title]._p.getparent()
    for i in range(last, title, -1):
        parent.remove(paras[i]._p)
    prev = paras[title]._p
    host = paras[0]._parent
    for lv, text in heads:
        proto = PROTO.get(lv)
        if proto is None:
            proto = PROTO.get(2) or PROTO[1]
        new_p = copy.deepcopy(proto)
        prev.addnext(new_p)
        prev = new_p
        p = Paragraph(new_p, host)
        runs = p.runs
        for r in runs[1:]:
            r._r.getparent().remove(r._r)
        if runs:
            runs[0].text = "%s\t%d" % (text, pmap[text])


def main():
    os.makedirs(WORK, exist_ok=True)
    bak = os.path.join(ROOT, "runtime", "backup")
    os.makedirs(bak, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    target = os.path.join(bak, "项目开发文档-%s.docx" % stamp)
    shutil.copy2(DOCX, target)
    print("已备份：runtime\\backup\\%s" % os.path.basename(target))

    doc = docx.Document(DOCX)
    paras = doc.paragraphs
    title, last, body = toc_range(paras)
    heads = headings(paras)
    capture_proto(paras, title, last)
    print("目录区 %d..%d（原 %d 条）→ 正文标题 %d 条；模板层级 %s"
          % (title, last, last - title, len(heads), sorted(PROTO)))

    pdf = os.path.join(WORK, "doc.pdf")
    pages = pdftext.extract(to_pdf(DOCX, pdf))
    pmap, p0, toc_pages = page_map(pages, heads)
    print("PDF %d 页；目录页 %s；正文起始打印页 %d（%d）"
          % (len(pages), [i + 1 for i in toc_pages], p0 + 1, pmap[heads[0][1]]))

    for attempt in range(1, 5):
        doc = docx.Document(DOCX)
        title, last, body = toc_range(doc.paragraphs)
        rewrite(doc, title, last, heads, pmap)
        doc.save(DOCX)
        pages = pdftext.extract(to_pdf(DOCX, pdf))
        pmap2, p0, toc_pages = page_map(pages, heads)
        bad = [(t, pmap.get(t), pmap2.get(t)) for _, t in heads if pmap.get(t) != pmap2.get(t)]
        print("第 %d 轮：PDF %d 页，偏差 %d 处" % (attempt, len(pages), len(bad)))
        if not bad:
            print("目录与正文页码一致。共 %d 条：%s → p%d，%s → p%d"
                  % (len(heads), heads[0][1], pmap[heads[0][1]],
                     heads[-1][1], pmap[heads[-1][1]]))
            return
        for t, a, b in bad[:5]:
            print("   %s：%s → %s" % (t, a, b))
        pmap = pmap2
    print("警告：多轮校准后仍有偏差，请人工检查。")


if __name__ == "__main__":
    main()
