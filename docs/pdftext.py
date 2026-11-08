# -*- coding: utf-8 -*-
"""极简 PDF 文本抽取：按页返回文本，用于统计标题所在页码。

只依赖标准库（zlib/re），足以处理 LibreOffice 导出的 PDF：
- 扫描 `N 0 obj ... endobj`，取字典与（可选）FlateDecode 流
- 用 /Type /Pages 的 /Kids 确定页序，取每页 /Contents 与 /Resources /Font
- 字体若带 /ToUnicode，则按 CMap 把 CID 解成 Unicode；简单字体按单字节解码
- 解析内容流里的 Tf / Tj / TJ / ' / " 指令
"""
import json
import re
import sys
import zlib

OBJ_RE = re.compile(rb"(?m)^\s*(\d+)\s+(\d+)\s+obj\b(.*?)endobj", re.S)


def _decode_stream(dic, raw):
    if raw is None:
        return None
    if b"/FlateDecode" in dic:
        for attempt in (raw, raw.lstrip(b"\r\n"), raw[1:]):
            try:
                return zlib.decompress(attempt)
            except Exception:
                continue
        try:
            return zlib.decompressobj().decompress(raw)
        except Exception:
            return None
    return raw


def load(path):
    data = open(path, "rb").read()
    objs = {}
    for m in OBJ_RE.finditer(data):
        num = int(m.group(1))
        body = m.group(3)
        s = body.find(b"stream")
        if s >= 0:
            dic = body[:s]
            rest = body[s + 6:]
            if rest.startswith(b"\r\n"):
                rest = rest[2:]
            elif rest[:1] in (b"\n", b"\r"):
                rest = rest[1:]
            e = rest.rfind(b"endstream")
            raw = rest[:e] if e >= 0 else rest
            objs[num] = (dic, _decode_stream(dic, raw))
        else:
            objs[num] = (body, None)
    return objs


def resolve(objs, token):
    m = re.search(rb"(\d+)\s+\d+\s+R", token)
    if not m:
        return None
    return objs.get(int(m.group(1)))


def page_list(objs):
    for num, (dic, _) in objs.items():
        if b"/Type" in dic and b"/Pages" in dic and b"/Kids" in dic:
            kids = re.search(rb"/Kids\s*\[(.*?)\]", dic, re.S)
            if kids:
                return [int(x) for x in re.findall(rb"(\d+)\s+\d+\s+R", kids.group(1))]
    return [n for n, (dic, _) in sorted(objs.items()) if re.search(rb"/Type\s*/Page\b", dic)]


def parse_cmap(data):
    """返回 {code:int -> str}"""
    out = {}
    if not data:
        return out
    text = data
    for block in re.findall(rb"beginbfchar(.*?)endbfchar", text, re.S):
        for src, dst in re.findall(rb"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", block):
            try:
                out[int(src, 16)] = bytes.fromhex(dst.decode()).decode("utf-16-be", "ignore")
            except Exception:
                pass
    for block in re.findall(rb"beginbfrange(.*?)endbfrange", text, re.S):
        for lo, hi, rest in re.findall(rb"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*(\[[^\]]*\]|<[0-9A-Fa-f]+>)", block):
            lo_i, hi_i = int(lo, 16), int(hi, 16)
            if rest.startswith(b"["):
                dsts = re.findall(rb"<([0-9A-Fa-f]+)>", rest)
                for i, d in enumerate(dsts):
                    if lo_i + i <= hi_i:
                        out[lo_i + i] = bytes.fromhex(d.decode()).decode("utf-16-be", "ignore")
            else:
                base = bytes.fromhex(rest[1:-1].decode()).decode("utf-16-be", "ignore")
                for i in range(min(hi_i - lo_i + 1, 512)):
                    if base:
                        out[lo_i + i] = base[:-1] + chr(ord(base[-1]) + i)
    return out


def fonts_of(objs, page_dic):
    fonts = {}
    m = re.search(rb"/Resources\s*(\d+\s+\d+\s+R|<<.*?>>)", page_dic, re.S)
    res = page_dic
    if m:
        token = m.group(1)
        if token.endswith(b"R"):
            r = resolve(objs, token)
            res = r[0] if r else page_dic
        else:
            res = token
    fm = re.search(rb"/Font\s*(<<.*?>>|\d+\s+\d+\s+R)", res, re.S)
    if not fm:
        return fonts
    ftok = fm.group(1)
    if ftok.endswith(b"R"):
        r = resolve(objs, ftok)
        fdict = r[0] if r else b""
    else:
        fdict = ftok
    for name, ref in re.findall(rb"/([A-Za-z0-9#+.\-]+)\s+(\d+)\s+\d+\s+R", fdict):
        fo = objs.get(int(ref))
        if not fo:
            continue
        fdic = fo[0]
        two_byte = b"/Type0" in fdic
        cmap = {}
        tu = re.search(rb"/ToUnicode\s+(\d+)\s+\d+\s+R", fdic)
        if tu:
            to = objs.get(int(tu.group(1)))
            if to and to[1]:
                cmap = parse_cmap(to[1])
        fonts[name.decode("latin-1")] = (two_byte, cmap)
    return fonts


def unescape(s):
    out = bytearray()
    i = 0
    while i < len(s):
        c = s[i]
        if c == 0x5C and i + 1 < len(s):
            n = s[i + 1]
            mapping = {0x6E: 10, 0x72: 13, 0x74: 9, 0x62: 8, 0x66: 12,
                       0x28: 0x28, 0x29: 0x29, 0x5C: 0x5C}
            if n in mapping:
                out.append(mapping[n])
                i += 2
                continue
            if 0x30 <= n <= 0x37:
                oct_digits = s[i + 1:i + 4]
                k = 0
                while k < len(oct_digits) and 0x30 <= oct_digits[k] <= 0x37:
                    k += 1
                out.append(int(oct_digits[:k], 8) & 0xFF)
                i += 1 + k
                continue
            out.append(n)
            i += 2
            continue
        out.append(c)
        i += 1
    return bytes(out)


def decode_text(raw, two_byte, cmap):
    if two_byte:
        codes = [int.from_bytes(raw[i:i + 2], "big") for i in range(0, len(raw) - 1, 2)]
        return "".join(cmap.get(c, "") for c in codes)
    if cmap:
        return "".join(cmap.get(b, chr(b) if 32 <= b < 127 else "") for b in raw)
    return "".join(chr(b) if 32 <= b < 127 else (" " if b in (9, 10, 13) else "") for b in raw)


TOKEN_RE = re.compile(rb"/([A-Za-z0-9#+.\-]+)\s+[\d.]+\s+Tf|\((?:[^()\\]|\\.)*\)|<([0-9A-Fa-f\s]+)>|\bTJ\b|\bTj\b|\bTd\b|\bTD\b|\bT\*\b|\bET\b", re.S)


def page_text(objs, pdic, content):
    fonts = fonts_of(objs, pdic)
    cur = None
    out = []
    for m in TOKEN_RE.finditer(content or b""):
        tok = m.group(0)
        if tok.endswith(b"Tf"):
            cur = fonts.get(m.group(1).decode("latin-1"))
            if cur is None:
                cur = (False, {})
        elif tok.startswith(b"("):
            raw = unescape(tok[1:-1])
            out.append(decode_text(raw, *(cur or (False, {}))))
        elif tok.startswith(b"<") and m.group(2) is not None:
            hexs = re.sub(rb"\s", b"", m.group(2))
            if len(hexs) % 2:
                hexs += b"0"
            try:
                raw = bytes.fromhex(hexs.decode())
            except Exception:
                continue
            out.append(decode_text(raw, *(cur or (False, {}))))
        elif tok in (b"Td", b"TD", b"T*", b"ET"):
            out.append("\n")
    return "".join(out)


def extract(path):
    objs = load(path)
    pages = []
    for num in page_list(objs):
        po = objs.get(num)
        if not po:
            continue
        dic, _ = po
        cs = re.search(rb"/Contents\s*(\[[^\]]*\]|\d+\s+\d+\s+R)", dic)
        chunks = []
        if cs:
            for ref in re.findall(rb"(\d+)\s+\d+\s+R", cs.group(1)):
                so = objs.get(int(ref))
                if so and so[1]:
                    chunks.append(so[1])
        pages.append(page_text(objs, dic, b"\n".join(chunks)))
    return pages


if __name__ == "__main__":
    pgs = extract(sys.argv[1])
    print(json.dumps({"pages": len(pgs), "text": [re.sub(r"\s+", " ", p) for p in pgs]}, ensure_ascii=False))
