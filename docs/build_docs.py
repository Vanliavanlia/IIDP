# -*- coding: utf-8 -*-
"""两遍构建：先生成文档 → 转 PDF 统计各标题实际页码 → 回填静态目录页码 → 复核。"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PY = sys.executable
NODE = r"D:\Program Files\DSH Desktop\DSH Desktop.exe"
CLI = r"D:\Program Files\DSH Desktop\resources\office-cli.mjs"

sys.path.insert(0, HERE)
import pdftext  # noqa: E402

DOCS = [
    ("build_dev_doc.py", "快递驿站管理系统-项目开发文档"),
    ("build_process_doc.py", "快递驿站管理系统-项目开发过程文档"),
]

WS = re.compile(r"[\s\u3000]+")


def norm(s):
    return WS.sub("", s)


def run_build(script, toc_out=None, page_map=None, nomark=False):
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    if toc_out:
        env["TOC_OUT"] = toc_out
    else:
        env.pop("TOC_OUT", None)
    if page_map:
        env["PAGE_MAP"] = page_map
    else:
        env.pop("PAGE_MAP", None)
    if nomark:
        env["NOMARK"] = "1"
    else:
        env.pop("NOMARK", None)
    r = subprocess.run([PY, os.path.join(HERE, script)], env=env, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit("build failed: " + script)
    return r.stdout.strip()


def to_pdf(docx_path, workdir, tag):
    out = os.path.join(workdir, tag + ".pdf")
    if os.path.exists(out):
        os.remove(out)
    env = dict(os.environ)
    env["ELECTRON_RUN_AS_NODE"] = "1"
    r = subprocess.run([NODE, CLI, "convert", "--input", docx_path, "--output", out],
                       env=env, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0 or not os.path.exists(out):
        print(r.stdout, r.stderr)
        raise SystemExit("pdf convert failed: " + docx_path)
    return out


def page_of(pages, entry, start):
    key = norm(entry)
    for i in range(start, len(pages)):
        if key and key in norm(pages[i]):
            return i + 1
    return None


def body_start_index(pages):
    """优先用正文哨兵 BODYMARK 定位；找不到时退回「目录」标题页的下页。"""
    for i, p in enumerate(pages):
        if "BODYMARK" in p:
            return i
    last = -1
    for i, p in enumerate(pages):
        if "目录" in norm(p)[:30]:
            last = i
    return last + 1


def build_map(pages, entries, body_start):
    mp = {}
    missing = []
    cursor = body_start
    for level, text in entries:
        p = page_of(pages, text, cursor)
        if p is None:
            missing.append(text)
            continue
        mp[text] = p
        cursor = p - 1
    return mp, missing


def patch_numbers(docx_path, page_map):
    """把目录里空着的页码补上（保持段落数不变）。"""
    return docx_path


def main():
    workdir = os.path.join(HERE, ".pages")
    os.makedirs(workdir, exist_ok=True)
    target = sys.argv[1] if len(sys.argv) > 1 else None
    for script, name in DOCS:
        if target and target not in script:
            continue
        docx_path = os.environ.get("DOCX_OVERRIDE") or os.path.join(ROOT, "交付文档", name + ".docx")
        entries_json = os.path.join(workdir, name + ".toc.json")
        run_build(script, toc_out=entries_json)
        entries = [tuple(x) for x in json.load(open(entries_json, encoding="utf-8"))]
        pdf = to_pdf(docx_path, workdir, name + "-p1")
        pages = pdftext.extract(pdf)
        body_start = body_start_index(pages)
        mp1, missing = build_map(pages, entries, body_start)
        print(f"[{name}] pass1: {len(pages)} 页，标题 {len(entries)} 个，缺 {len(missing)} 个 {missing[:3]}")

        mp_path = os.path.join(workdir, name + ".map.json")
        json.dump(mp1, open(mp_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        run_build(script, page_map=mp_path)

        pdf2 = to_pdf(docx_path, workdir, name + "-p2")
        pages2 = pdftext.extract(pdf2)
        body2 = body_start_index(pages2)
        mp2, missing2 = build_map(pages2, entries, body2)
        drifted = {k: (mp1.get(k), mp2.get(k)) for k in mp2 if mp1.get(k) != mp2.get(k)}
        if drifted:
            print(f"[{name}] pass2 页码漂移 {len(drifted)} 处，回填后重建：{list(drifted.items())[:4]}")
            json.dump(mp2, open(mp_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
            run_build(script, page_map=mp_path)
            pdf3 = to_pdf(docx_path, workdir, name + "-p3")
            pages3 = pdftext.extract(pdf3)
            body3 = body_start_index(pages3)
            mp3, _ = build_map(pages3, entries, body3)
            still = {k: (mp2.get(k), mp3.get(k)) for k in mp3 if mp2.get(k) != mp3.get(k)}
            print(f"[{name}] pass3 复核：漂移 {len(still)} 处")
            final_pages = pages3
            final_map = mp3
        else:
            final_pages = pages2
            final_map = mp2
        # 去掉哨兵再复核一次：若页码不变则交付干净版本，否则保留哨兵
        run_build(script, page_map=mp_path, nomark=True)
        pdf4 = to_pdf(docx_path, workdir, name + "-clean")
        pages4 = pdftext.extract(pdf4)
        start4 = 0
        for i, pg in enumerate(pages4):
            if "目录" in norm(pg)[:30]:
                start4 = i + 1
        mp4, _ = build_map(pages4, entries, start4)
        diff = [k for k in mp4 if mp4.get(k) != final_map.get(k)]
        if diff:
            print(f"[{name}] 去哨兵后页码变动 {len(diff)} 处，保留哨兵版本")
            run_build(script, page_map=mp_path)
            final_pages = pdftext.extract(to_pdf(docx_path, workdir, name + "-final"))
            final_map = mp4
        else:
            print(f"[{name}] 去哨兵后页码一致，交付无哨兵版本（{len(pages4)} 页）")
            final_pages = pages4
        print(f"[{name}] 完成：{len(final_pages)} 页；目录页码样例 "
              f"{list(final_map.items())[:3]} … {list(final_map.items())[-2:]}")
    print("workdir:", workdir)


if __name__ == "__main__":
    main()
