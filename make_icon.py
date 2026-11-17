# -*- coding: utf-8 -*-
"""生成启动图标：砖墙背景 + 戴面罩的小偷 + 金块（纯 Python 手写 PNG，无第三方依赖）"""
import os, zlib, struct

# 16x16 网格
ICON = [
    "BBBBBBBBBBBBBBBB",
    "BBBBBBHHHHBBBBBB",
    "BBBBBHHHHHHBBBBB",
    "BBBBHHHHHHHHBBBB",
    "BBBBKKKKKKKKBBBB",
    "BBBBKWWKKWWKBBBB",
    "BBBBSSSSSSSSBBBB",
    "BBBBBSSKKSSBBBBB",
    "BBSSWWWWWWWWSSBB",
    "BBSSKKKKKKKKSSBB",
    "BBSSWWWWWWWWSSBB",
    "BBSSKKKKKKKKSSBB",
    "BBBBWWWWWWWWBBBB",
    "BBBBPPPBBPPPYYBB",
    "BBBBPPPBBPPPYYBB",
    "BBBKKKKBBKKKKYBB",
]
PAL = {
    'B': (0x9C, 0x52, 0x30),   # 砖
    'H': (0x2A, 0x35, 0x50),   # 帽子
    'K': (0x14, 0x16, 0x1F),   # 面罩 / 黑条 / 鞋
    'S': (0xF5, 0xC9, 0xA0),   # 皮肤
    'W': (0xF2, 0xF2, 0xF2),   # 白条 / 眼
    'P': (0x3A, 0x4A, 0x78),   # 裤子
    'Y': (0xFF, 0xD2, 0x3F),   # 金
}


def png(path, size):
    px = size / 16.0
    rows = []
    for y in range(size):
        row = bytearray()
        gy = min(15, int(y // px))
        for x in range(size):
            gx = min(15, int(x // px))
            ch = ICON[gy][gx]
            r, g, b = PAL.get(ch, (0x06, 0x06, 0x0C))
            d = min(x, y, size - 1 - x, size - 1 - y)
            edge = max(1, size // 40)
            if d < edge:
                r, g, b = (0xC0, 0x70, 0x48)          # 圆角描边
            row += bytes((r, g, b, 0xFF))
        rows.append(bytes(row))
    raw = b''.join(b'\x00' + r for r in rows)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(raw, 9))
    out += chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(out)
    print('wrote', path, size)


base = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'app', 'src', 'main', 'res')
for folder, size in [('mipmap-mdpi', 48), ('mipmap-hdpi', 72),
                     ('mipmap-xhdpi', 96), ('mipmap-xxhdpi', 144),
                     ('mipmap-xxxhdpi', 192)]:
    png(os.path.join(base, folder, 'ic_launcher.png'), size)
