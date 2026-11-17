# -*- coding: utf-8 -*-
"""把关卡画面 + 新控件按真实布局合成一张完整界面预览（纯 Python 手写 PNG，无第三方依赖）。
用法： python preview_ui.py [关卡号] [屏幕宽] [屏幕高] [px/dp] [缩放]
"""
import sys
import os
import struct
import zlib

import check_layout as L
import preview as P


# ---------------------------------------------------------------- 基础图元
def blend(g, x, y, c, a=255):
    if x < 0 or y < 0 or x >= g.w or y >= g.h or a <= 0:
        return
    r0, g0, b0, _ = g.px[y][x]
    f = a / 255.0
    g.px[y][x] = (int(r0 * (1 - f) + c[0] * f),
                  int(g0 * (1 - f) + c[1] * f),
                  int(b0 * (1 - f) + c[2] * f), 255)


def fill_poly(g, pts, c, a=255):
    ys = [p[1] for p in pts]
    y0, y1 = int(min(ys)), int(max(ys))
    n = len(pts)
    for y in range(max(0, y0), min(g.h, y1 + 1)):
        xs = []
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            if ay == by:
                continue
            if min(ay, by) <= y < max(ay, by):
                t = (y - ay) / float(by - ay)
                xs.append(ax + t * (bx - ax))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(max(0, int(round(xs[i]))), min(g.w, int(round(xs[i + 1])) + 1)):
                blend(g, x, y, c, a)


def stroke_poly(g, pts, c, w=1, a=255):
    n = len(pts)
    for i in range(n):
        line(g, pts[i][0], pts[i][1], pts[(i + 1) % n][0], pts[(i + 1) % n][1], c, w, a)


def fill_circle(g, cx, cy, r, c, a=255):
    r = int(r)
    for y in range(int(cy) - r - 1, int(cy) + r + 2):
        for x in range(int(cx) - r - 1, int(cx) + r + 2):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy <= r * r:
                blend(g, x, y, c, a)


def stroke_circle(g, cx, cy, r, c, w=2, a=255):
    steps = max(24, int(r * 6))
    import math
    for i in range(steps):
        t = 2 * math.pi * i / steps
        x = cx + r * math.cos(t)
        y = cy + r * math.sin(t)
        rr = w / 2.0
        for dy in range(int(-rr) - 1, int(rr) + 2):
            for dx in range(int(-rr) - 1, int(rr) + 2):
                if dx * dx + dy * dy <= rr * rr:
                    blend(g, int(x) + dx, int(y) + dy, c, a)


def line(g, x1, y1, x2, y2, c, w=2, a=255):
    import math
    n = int(max(abs(x2 - x1), abs(y2 - y1)) * 2) + 1
    rr = w / 2.0
    for i in range(n + 1):
        t = i / float(n)
        x = x1 + (x2 - x1) * t
        y = y1 + (y2 - y1) * t
        for dy in range(int(-rr) - 1, int(rr) + 2):
            for dx in range(int(-rr) - 1, int(rr) + 2):
                if dx * dx + dy * dy <= rr * rr:
                    blend(g, int(x) + dx, int(y) + dy, c, a)


def scale_grid(src, k):
    w, h = max(1, int(src.w * k)), max(1, int(src.h * k))
    out = P.Grid(w, h)
    for y in range(h):
        sy = min(src.h - 1, int(y / k))
        for x in range(w):
            sx = min(src.w - 1, int(x / k))
            out.px[y][x] = src.px[sy][sx]
    return out


def paste(dst, src, px, py):
    px, py = int(px), int(py)
    for y in range(src.h):
        ty = py + y
        if ty < 0 or ty >= dst.h:
            continue
        for x in range(src.w):
            tx = px + x
            if 0 <= tx < dst.w:
                dst.px[ty][tx] = src.px[y][x]


def save_png(g, path):
    rows = []
    for y in range(g.h):
        row = bytearray()
        for x in range(g.w):
            r, gg, b, a = g.px[y][x]
            row += bytes((r, gg, b, 255))
        rows.append(bytes(row))
    raw = b''.join(b'\x00' + r for r in rows)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', g.w, g.h, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(raw, 9))
    out += chunk(b'IEND', b'')
    open(path, 'wb').write(out)
    print('wrote', path, g.w, 'x', g.h)


# ---------------------------------------------------------------- 主流程
def main():
    lv = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    vw = int(sys.argv[2]) if len(sys.argv) > 2 else 2340
    vh = int(sys.argv[3]) if len(sys.argv) > 3 else 1080
    dpi = float(sys.argv[4]) if len(sys.argv) > 4 else 2.625
    k = float(sys.argv[5]) if len(sys.argv) > 5 else 0.5

    hudH, cell, bl, br = L.resize(vw, vh)
    c = L.layout(vw, vh, bl, br, hudH, dpi)

    scr = P.Grid(int(vw * k), int(vh * k))
    scr.r(0, 0, scr.w, scr.h, (6, 6, 12))

    # 关卡画面
    levels = P.load_levels()
    rows = levels[(lv - 1) % len(levels)]
    board_img = P.render(rows, scale=3)
    board_img = scale_grid(board_img, k * cell / (16.0 * 3.0))
    board_h = cell * L.H
    oy = hudH + (vh - hudH - board_h) / 2.0
    paste(scr, board_img, bl * k, oy * k)

    # HUD
    scr.r(0, 0, scr.w, int(hudH * k), (14, 14, 24))

    # ---------------- 方向键
    cx, cy = c['padCx'] * k, c['padCy'] * k
    h = c['padL'] * 0.5 * k
    a = c['padArm'] * 0.5 * k
    fill_poly(scr, [(cx - h, cy - h), (cx + h, cy - h), (cx + h, cy + h), (cx - h, cy + h)], (26, 34, 52), 120)
    quads = [
        ([(cx - h, cy - h), (cx + h, cy - h), (cx + a, cy - a), (cx - a, cy - a)], (0, -1), False),
        ([(cx + h, cy - h), (cx + h, cy + h), (cx + a, cy + a), (cx + a, cy - a)], (1, 0), False),
        ([(cx + h, cy + h), (cx - h, cy + h), (cx - a, cy + a), (cx + a, cy + a)], (0, 1), False),
        ([(cx - h, cy + h), (cx - h, cy - h), (cx - a, cy - a), (cx - a, cy + a)], (-1, 0), False),
    ]
    for poly, (dx, dy), held in quads:
        fill_poly(scr, poly, (150, 180, 225) if held else (38, 48, 72), 175 if held else 46)
        stroke_poly(scr, poly, (245, 250, 255) if held else (95, 115, 155), max(1.0, 2.2 * k),
                    245 if held else 95)
        # 箭头（小三角）
        d = (h + a) * 0.5
        ax, ay = cx + dx * d, cy + dy * d
        s = a * 0.62
        tri = [(ax + dx * s, ay + dy * s), (ax - dy * s * 0.8 - dx * s * 0.3, ay + dx * s * 0.8 - dy * s * 0.3),
               (ax + dy * s * 0.8 - dx * s * 0.3, ay - dx * s * 0.8 - dy * s * 0.3)]
        fill_poly(scr, tri, (255, 255, 255) if held else (170, 190, 220), 255 if held else 200)
    # 中心死区
    fill_poly(scr, [(cx - a, cy - a), (cx + a, cy - a), (cx + a, cy + a), (cx - a, cy + a)], (16, 22, 38), 190)
    stroke_poly(scr, [(cx - a, cy - a), (cx + a, cy - a), (cx + a, cy + a), (cx - a, cy + a)],
                (120, 150, 195), max(1.0, 1.8 * k), 110)

    # ---------------- 挖键
    r = c['digR'] * k
    for (bx, by), held, dirx in [((c['digLCx'], c['digLCy']), False, -1),
                                 ((c['digRCx'], c['digRCy']), False, 1)]:
        x, y = bx * k, by * k
        fill_circle(scr, x, y + r * 0.09, r * 1.04, (0, 0, 0), 70)
        fill_circle(scr, x, y, r, (232, 132, 40), 215 if held else 105)
        stroke_circle(scr, x, y, r, (255, 214, 150), max(1.2, 3.4 * k if held else 2.4 * k), 255 if held else 200)
        line(scr, x - r * 0.34 * dirx, y + r * 0.30, x + r * 0.26 * dirx, y - r * 0.18,
             (242, 242, 242), max(1.2, r * 0.15))
        line(scr, x - r * 0.06 * dirx, y - r * 0.42, x + r * 0.42 * dirx, y - r * 0.02,
             (242, 242, 242), max(1.2, r * 0.15))

    out = 'preview_ui_%dx%d.png' % (vw, vh)
    save_png(scr, out)
    print('  方向键 %.0f dp 见方，挖键直径 %.0f dp（%s）'
          % (c['padL'] / dpi, c['digR'] * 2 / dpi, '上下排列' if c['stacked'] else '并排'))


if __name__ == '__main__':
    main()
