# -*- coding: utf-8 -*-
"""素材放大图：把小偷 / 警察的各种姿态和地形块并排画出来，用来检查像素画质量。
用法：python preview_sprites.py [放大倍数]
"""
import os
import sys
from preview import build_char, flip, make_brick, make_solid, make_ladder, make_bar, \
    make_trap, make_gold, make_exit, Grid, save_png, C

SCALE = int(sys.argv[1]) if len(sys.argv) > 1 else 8
PAD = 2          # 格子间距（单位：像素格）
BG = (0x0B, 0x0A, 0x16)
LINE = (0x3A, 0x4A, 0x7A)


def pose_list(police):
    if police:
        return [('stand', 0), ('run', 0), ('run', 2), ('run', 4), ('run', 6),
                ('climb', 0), ('climb', 1), ('fall', 0)]
    return [('stand', 0), ('run', 0), ('run', 2), ('run', 4), ('run', 6),
            ('climb', 0), ('climb', 1), ('fall', 0), ('hang', 0), ('dig', 0)]


def main():
    thief = [build_char(False, p, f) for p, f in pose_list(False)]
    thief_flip = [flip(build_char(False, 'run', 2))]
    police = [build_char(True, p, f) for p, f in pose_list(True)]
    police_flip = [flip(build_char(True, 'run', 2))]
    tiles = [make_brick(), make_solid(), make_ladder(), make_bar(),
             make_trap(0), make_trap(3), make_trap(6),
             make_gold(0), make_gold(2), make_gold(4),
             make_exit(0, False), make_exit(0, True), make_exit(3, True)]

    rows = [thief + thief_flip, police + police_flip, tiles]
    labels = ['小偷', '警察', '地形/道具']
    ncols = max(len(r) for r in rows)
    cell = 16 + PAD
    gw, gh = ncols * cell, len(rows) * cell
    g = Grid(gw * SCALE, gh * SCALE)
    g.r(0, 0, gw * SCALE, gh * SCALE, BG)

    for ri, row in enumerate(rows):
        # 每行的底色条
        for ci in range(ncols):
            for y in range(16):
                for x in range(16):
                    c = (0x16, 0x14, 0x22) if ((x + y + ci) % 8 < 4) else (0x11, 0x0F, 0x1C)
                    for dy in range(SCALE):
                        for dx in range(SCALE):
                            g.px[(ri * cell + y) * SCALE + dy][(ci * cell + x) * SCALE + dx] = c + (255,)
        for ci, sp in enumerate(row):
            for y in range(16):
                for x in range(16):
                    r, gg, b, a = sp.px[y][x]
                    if a == 0:
                        continue
                    if a == 255:
                        col = (r, gg, b, 255)
                    else:
                        orr, og, ob, _ = g.px[(ri * cell + y) * SCALE][(ci * cell + x) * SCALE]
                        f = a / 255.0
                        col = (int(orr * (1 - f) + r * f), int(og * (1 - f) + gg * f),
                               int(ob * (1 - f) + b * f), 255)
                    for dy in range(SCALE):
                        for dx in range(SCALE):
                            g.px[(ri * cell + y) * SCALE + dy][(ci * cell + x) * SCALE + dx] = col
    # 分隔线
    for ri in range(1, len(rows)):
        g.r(0, ri * cell * SCALE - SCALE, gw * SCALE, SCALE, LINE)

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview_sprites.png')
    save_png(g, path)
    print('rows:', [(lab, len(r)) for lab, r in zip(labels, rows)])


if __name__ == '__main__':
    main()
