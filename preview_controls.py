# -*- coding: utf-8 -*-
"""按 InputController.layout 的真实参数画出控件布局，用来在电脑上直接看效果。
用法： python preview_controls.py [屏幕宽] [屏幕高] [px/dp]
"""
import sys
import os
from PIL import Image, ImageDraw, ImageFont

import check_layout as L

FONT_CANDIDATES = [
    'C:/Windows/Fonts/msyh.ttc', 'C:/Windows/Fonts/msyhbd.ttc',
    'C:/Windows/Fonts/simhei.ttf', 'C:/Windows/Fonts/DejaVuSans.ttf',
]


def font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def main():
    vw = int(sys.argv[1]) if len(sys.argv) > 1 else 2340
    vh = int(sys.argv[2]) if len(sys.argv) > 2 else 1080
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 2.625

    hudH, cell, bl, br = L.resize(vw, vh)
    c = L.layout(vw, vh, bl, br, hudH, scale)

    k = 0.55                      # 整体缩小以便查看
    W, H = int(vw * k), int(vh * k)
    img = Image.new('RGB', (W, H), (6, 6, 12))
    d = ImageDraw.Draw(img)

    # 关卡区
    board_h = cell * L.H
    oy = hudH + (vh - hudH - board_h) / 2.0
    d.rectangle([bl * k, oy * k, br * k, (oy + board_h) * k], fill=(16, 14, 28), outline=(58, 74, 122), width=max(2, int(3 * k)))
    # HUD
    d.rectangle([0, 0, W, hudH * k], fill=(14, 14, 24))
    d.text((14 * k, hudH * k * 0.35), '第 1 关   ♥♥♥   ● 7/10                     分数 004200',
           font=font(int(15 * (vh / 400.0) * k)), fill=(79, 232, 224))

    # ---------------- 方向键
    cx, cy = c['padCx'] * k, c['padCy'] * k
    h = c['padL'] * 0.5 * k
    a = c['padArm'] * 0.5 * k
    outer = [(cx - h, cy - h), (cx + h, cy + h)]
    d.rounded_rectangle(outer, radius=int(c['padL'] * 0.10 * k), fill=(28, 36, 54), outline=(90, 120, 165), width=2)
    quads = [
        ([(cx - h, cy - h), (cx + h, cy - h), (cx + a, cy - a), (cx - a, cy - a)], '▲', (0, -1), False),
        ([(cx + h, cy - h), (cx + h, cy + h), (cx + a, cy + a), (cx + a, cy - a)], '▶', (1, 0), True),
        ([(cx + h, cy + h), (cx - h, cy + h), (cx - a, cy + a), (cx + a, cy + a)], '▼', (0, 1), False),
        ([(cx - h, cy + h), (cx - h, cy - h), (cx - a, cy - a), (cx - a, cy + a)], '◀', (-1, 0), False),
    ]
    f = font(int(a * 1.15))
    for poly, arrow, (dx, dy), held in quads:
        d.polygon(poly, fill=(58, 74, 104) if held else (30, 38, 56),
                  outline=(200, 220, 255) if held else (95, 115, 155))
        dd = (h + a) * 0.5
        d.text((cx + dx * dd - a * 0.35, cy + dy * dd - a * 0.45), arrow, font=f,
               fill=(255, 255, 255) if held else (170, 190, 220))
    d.rounded_rectangle([(cx - a, cy - a), (cx + a, cy + a)], radius=int(a * 0.34),
                        fill=(18, 24, 40), outline=(110, 140, 180), width=2)

    # ---------------- 挖键
    r = c['digR'] * k
    for (bx, by), label, held in [((c['digLCx'], c['digLCy']), '◀ 挖左', True),
                                  ((c['digRCx'], c['digRCy']), '挖右 ▶', False)]:
        x, y = bx * k, by * k
        d.ellipse([x - r, y - r, x + r, y + r], fill=(150, 88, 32) if held else (74, 45, 18),
                  outline=(255, 214, 150), width=2)
        d.line([x - r * 0.34, y + r * 0.30, x + r * 0.26, y - r * 0.18], fill=(242, 242, 242), width=max(2, int(r * 0.15)))
        d.line([x - r * 0.06, y - r * 0.42, x + r * 0.42, y - r * 0.02], fill=(242, 242, 242), width=max(2, int(r * 0.15)))
        d.text((x - r * 0.55, y + r * 0.42), label, font=font(int(r * 0.30)), fill=(255, 245, 232))

    # 标注
    fs = font(max(12, int(16 * k)))
    info = [
        '方向键 %.0f dp 见方（每向有效区约 %.0f dp）' % (c['padL'] / scale, ((c['padL'] ** 2 - c['padArm'] ** 2) / 4) ** 0.5 / scale),
        '挖键直径 %.0f dp（%s），两键间隙 %.0f px' % (
            c['digR'] * 2 / scale, '上下排列' if c['stacked'] else '并排',
            (abs(c['digRCy'] - c['digLCy']) if c['stacked'] else abs(c['digRCx'] - c['digLCx'])) - 2 * c['digR']),
        '屏幕 %dx%d @ %.2fx   格子 %.0f px' % (vw, vh, scale, cell),
    ]
    y = hudH * k + 8
    for t in info:
        d.text((14 * k, y), t, font=fs, fill=(255, 210, 90))
        y += int(20 * k) + 6

    out = 'preview_controls_%dx%d.png' % (vw, vh)
    img.save(out)
    print('saved', out)


if __name__ == '__main__':
    main()
