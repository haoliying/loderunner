# -*- coding: utf-8 -*-
"""离线预览：用与 Sprites.kt / Renderer.kt 完全相同的像素画法，把关卡画成 PNG。
用来在手机上跑之前先确认画面效果。用法：python preview.py [关卡号] [放大倍数]
"""
import re
import sys
import zlib
import struct
import os
import random

W, H, TILE = 28, 16, 16

C = {
    'bg': (0x0B, 0x0A, 0x16), 'bg2': (0x14, 0x10, 0x26),
    'brick': (0x9C, 0x52, 0x30), 'brick_lt': (0xC0, 0x70, 0x48),
    'brick_dk': (0x6B, 0x33, 0x1C), 'mortar': (0x3E, 0x1E, 0x12),
    'solid': (0x5C, 0x66, 0x7E), 'solid_lt': (0x9A, 0xA6, 0xBE),
    'solid_dk': (0x2E, 0x34, 0x46), 'rivet': (0xB8, 0xC2, 0xD6),
    'ladder': (0xE8, 0xC8, 0x7A), 'ladder_dk': (0x8A, 0x6A, 0x2A),
    'ladder_lt': (0xF6, 0xE6, 0xB4),
    'bar': (0xC6, 0xCC, 0xDC), 'bar_dk': (0x6E, 0x76, 0x88), 'bar_lt': (0xEE, 0xF2, 0xFA),
    'trap': (0x07, 0x05, 0x0A), 'dirt': (0x8A, 0x57, 0x30), 'dirt_dk': (0x4A, 0x2C, 0x16),
    'gold': (0xFF, 0xD2, 0x4A), 'gold_lt': (0xFF, 0xF3, 0xB0), 'gold_dk': (0xB8, 0x7A, 0x14),
    'exit': (0x2B, 0xE0, 0x4A), 'exit_dk': (0x12, 0x7A, 0x28), 'exit_lt': (0xC8, 0xFF, 0xD4),
    'white': (0xF2, 0xF2, 0xF2), 'black': (0x12, 0x14, 0x1C), 'skin': (0xF5, 0xC9, 0xA0),
    'th_hat': (0x2A, 0x35, 0x50), 'th_mask': (0x14, 0x16, 0x1F),
    'th_stripe': (0x1B, 0x1F, 0x2E), 'th_pants': (0x3A, 0x4A, 0x78),
    'po_cap': (0x1E, 0x3A, 0x8A), 'po_cap_dk': (0x0E, 0x1B, 0x3A),
    'po_uni': (0x2F, 0x5B, 0xC0), 'po_uni_dk': (0x1B, 0x35, 0x70),
    'po_pants': (0x1E, 0x3A, 0x8A), 'badge': (0xFF, 0xD2, 0x3F),
    'frame': (0x3A, 0x4A, 0x7A), 'frame2': (0x14, 0x1A, 0x2C),
    'cave': (0x1A, 0x15, 0x26),
}
MOUTH = (0x8A, 0x4A, 0x3A)
BADGE_LT = (0xFF, 0xE9, 0x8A)


class Grid:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def r(self, x, y, w, h, c, a=255):
        for yy in range(max(0, y), min(self.h, y + h)):
            row = self.px[yy]
            for xx in range(max(0, x), min(self.w, x + w)):
                row[xx] = (c[0], c[1], c[2], a)


# --------------------------------------------------------------- 地形
def make_brick():
    g = Grid(16, 16)
    g.r(0, 0, 16, 16, C['brick'])
    for y in (5, 11):
        g.r(0, y, 16, 1, C['mortar'])
    for x, y, h in ((0, 1, 4), (8, 1, 4), (4, 7, 4), (12, 7, 4), (0, 13, 2), (8, 13, 2)):
        g.r(x, y, 1, h, C['mortar'])
    g.r(0, 0, 16, 1, C['brick_lt'])
    g.r(0, 15, 16, 1, C['brick_dk'])
    g.r(2, 2, 2, 1, C['brick_lt']); g.r(10, 3, 3, 1, C['brick_lt'])
    g.r(6, 8, 2, 1, C['brick_lt']); g.r(1, 9, 2, 1, C['brick_dk'])
    g.r(9, 14, 3, 1, C['brick_dk'])
    return g


def make_solid():
    g = Grid(16, 16)
    g.r(0, 0, 16, 16, C['solid'])
    g.r(0, 0, 16, 2, C['solid_lt']); g.r(0, 0, 2, 16, C['solid_lt'])
    g.r(0, 14, 16, 2, C['solid_dk']); g.r(14, 0, 2, 16, C['solid_dk'])
    g.r(3, 3, 10, 10, (0x4A, 0x54, 0x70))
    g.r(3, 3, 10, 1, C['solid_dk'])
    for x, y in ((4, 4), (10, 4), (4, 10), (10, 10)):
        g.r(x, y, 2, 2, C['rivet'])
    g.r(7, 6, 2, 1, C['solid_lt'])
    return g


def make_ladder():
    g = Grid(16, 16)
    g.r(3, 0, 2, 16, C['ladder_dk']); g.r(11, 0, 2, 16, C['ladder_dk'])
    g.r(3, 0, 1, 16, C['ladder_lt']); g.r(11, 0, 1, 16, C['ladder_lt'])
    for y in (1, 6, 11):
        g.r(2, y, 12, 2, C['ladder_dk'])
        g.r(3, y, 10, 1, C['ladder_lt'])
    return g


def make_bar():
    g = Grid(16, 16)
    g.r(0, 0, 16, 3, C['bar_dk'])
    g.r(0, 0, 16, 1, C['bar_lt'])
    g.r(0, 1, 16, 2, C['bar'])
    g.r(1, 3, 2, 2, C['bar_dk']); g.r(13, 3, 2, 2, C['bar_dk'])
    return g


def make_trap(stage):
    g = Grid(16, 16)
    g.r(0, 0, 16, 16, C['trap'])
    g.r(0, 0, 16, 2, C['dirt_dk']); g.r(0, 0, 16, 1, C['dirt'])
    g.r(0, 1, 3, 1, C['dirt']); g.r(11, 1, 4, 1, C['dirt'])
    g.r(0, 2, 1, 12, (0x2A, 0x20, 0x36)); g.r(15, 2, 1, 12, (0x2A, 0x20, 0x36))
    h = stage * 16 // 7
    if h > 0:
        g.r(0, 16 - h, 16, h, C['dirt_dk'])
        g.r(0, 16 - h, 16, 1, C['dirt'])
        g.r(2, 16 - h + 2, 3, 1, C['dirt']); g.r(9, 16 - h + 3, 4, 1, C['dirt'])
    return g


def make_gold(frame):
    g = Grid(16, 16)
    for i in range(5):
        g.r(8 - (i + 1), 4 + i, 2 * (i + 1), 1, C['gold'])
    for i, wd in enumerate((8, 6, 4, 2)):
        g.r(8 - wd // 2, 9 + i, wd, 1, C['gold'])
    g.r(6, 5, 3, 2, C['gold_lt']); g.r(5, 6, 2, 1, C['gold_lt'])
    g.r(4, 7, 3, 2, C['gold_lt'])
    g.r(5, 10, 6, 1, C['gold_dk']); g.r(6, 11, 4, 1, C['gold_dk'])
    g.r(3, 8, 10, 1, C['gold_dk'])
    sx = (11, 2, 12, 3, 10, 4)[frame]
    sy = (3, 10, 7, 4, 12, 6)[frame]
    g.r(sx, sy - 1, 1, 3, C['gold_lt']); g.r(sx - 1, sy, 3, 1, C['gold_lt'])
    if frame % 2 == 0:
        g.r(sx, sy, 1, 1, C['white'])
    return g


def make_exit(frame, on=True):
    g = Grid(16, 16)
    a = 255 if on else 42
    rail = C['exit_lt'] if (on and frame % 2 == 0) else C['exit']
    g.r(4, 0, 2, 16, rail, a); g.r(10, 0, 2, 16, rail, a)
    for i in range(3):
        y = 2 + i * 5
        lit = on and ((frame + i) % 6 < 2)
        g.r(4, y, 8, 2, C['white'] if lit else rail, a)
    if on:
        g.r(7, 0, 2, 3, C['exit_lt'], 200); g.r(6, 1, 4, 1, C['exit_lt'], 160)
    return g


# --------------------------------------------------------------- 角色
def build_char(police, pose, frame=0):
    g = Grid(16, 16)
    arm = C['po_uni'] if police else C['skin']
    pant = C['po_pants'] if police else C['th_pants']

    def head(oy):
        if police:
            g.r(5, oy, 6, 1, C['po_cap']); g.r(4, oy + 1, 8, 1, C['po_cap'])
            g.r(7, oy + 1, 2, 1, C['badge']); g.r(4, oy + 2, 8, 1, C['po_cap_dk'])
            g.r(4, oy + 3, 8, 4, C['skin'])
            g.r(5, oy + 4, 2, 1, C['black']); g.r(9, oy + 4, 2, 1, C['black'])
            g.r(7, oy + 6, 2, 1, MOUTH); g.r(6, oy + 7, 4, 1, C['skin'])
        else:
            g.r(5, oy, 6, 1, C['th_hat']); g.r(4, oy + 1, 8, 1, C['th_hat'])
            g.r(4, oy + 2, 8, 1, C['th_hat']); g.r(4, oy + 3, 8, 3, C['th_mask'])
            g.r(5, oy + 4, 2, 1, C['white']); g.r(9, oy + 4, 2, 1, C['white'])
            g.r(4, oy + 6, 8, 1, C['skin']); g.r(7, oy + 6, 2, 1, C['th_mask'])
            g.r(6, oy + 7, 4, 1, C['skin'])

    def torso(oy, rows=5):
        if police:
            g.r(4, oy + 8, 8, rows, C['po_uni'])
            g.r(6, oy + 9, 2, 2, C['badge']); g.r(6, oy + 9, 2, 1, BADGE_LT)
            g.r(4, oy + 8 + rows - 1, 8, 1, C['black'])
            g.r(7, oy + 8 + rows - 1, 2, 1, C['badge'])
        else:
            for i in range(rows):
                g.r(4, oy + 8 + i, 8, 1, C['white'] if i % 2 == 0 else C['th_stripe'])

    def legs(py, lx, rx):
        g.r(lx, py, 2, 2, pant); g.r(rx, py, 2, 2, pant)
        g.r(lx - 1, py + 2, 3, 1, C['black']); g.r(rx - 1, py + 2, 3, 1, C['black'])

    def arm_at(x, y, h, hand_top):
        g.r(x, y, 2, h, arm)
        g.r(x, y - 1 if hand_top else y + h, 2, 1, C['skin'])

    if pose == 'stand':
        head(0); torso(0); arm_at(2, 8, 3, False); arm_at(12, 8, 3, False); legs(13, 5, 9)
    elif pose == 'run':
        import math
        s = max(-1, min(1, int(round(2 * math.sin(2 * math.pi * frame / 8)))))
        head(0); torso(0); arm_at(2, 8 - s, 3, False); arm_at(12, 8 + s, 3, False)
        legs(13, 5 + s, 9 - s)
    elif pose == 'climb':
        head(0); torso(0)
        if frame == 0:
            arm_at(2, 6, 3, True); arm_at(12, 9, 3, False); legs(13, 6, 9)
        else:
            arm_at(2, 9, 3, False); arm_at(12, 6, 3, True); legs(13, 5, 10)
    elif pose == 'fall':
        head(0); torso(0); arm_at(2, 5, 4, True); arm_at(12, 5, 4, True); legs(13, 4, 10)
    elif pose == 'hang':
        head(2); torso(1, 4); arm_at(2, 2, 8, True); arm_at(12, 2, 8, True)
        g.r(5, 14, 2, 1, pant); g.r(9, 14, 2, 1, pant)
        g.r(4, 15, 3, 1, C['black']); g.r(8, 15, 3, 1, C['black'])
    elif pose == 'dig':
        head(1); torso(1, 4); arm_at(2, 10, 3, False); arm_at(12, 10, 3, False); legs(13, 5, 9)
    return g


def flip(g):
    out = Grid(16, 16)
    for y in range(16):
        for x in range(16):
            out.px[y][15 - x] = g.px[y][x]
    return out


# --------------------------------------------------------------- 合成
def load_levels():
    src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            'app', 'src', 'main', 'java', 'com', 'haoly',
                            'loderunner', 'Levels.kt'), encoding='utf-8').read()
    rows = re.findall(r'"([=#HGPEX\- ]{28})"', src)
    return [rows[i * 16:(i + 1) * 16] for i in range(len(rows) // 16)]


def render(level_rows, scale=3, exit_active=False):
    bw, bh = W * TILE, H * TILE
    out = Grid(bw, bh)
    rng = random.Random(20261002)
    for y in range(bh):
        t = y / (bh - 1)
        c = tuple(int(lo + (hi - lo) * (1 - t)) for lo, hi in
                  ((0x0B, 0x14), (0x0A, 0x10), (0x16, 0x26)))
        out.r(0, y, bw, 1, c)
    for _ in range(900):
        x, y = rng.randrange(bw), rng.randrange(bh)
        out.r(x, y, 1, 1, C['cave'], 90)

    def blit(g, tx, ty):
        for y in range(16):
            for x in range(16):
                r, gg, b, a = g.px[y][x]
                if a == 0:
                    continue
                if a == 255:
                    out.px[ty + y][tx + x] = (r, gg, b, 255)
                else:
                    orr, og, ob, _ = out.px[ty + y][tx + x]
                    f = a / 255.0
                    out.px[ty + y][tx + x] = (int(orr * (1 - f) + r * f),
                                              int(og * (1 - f) + gg * f),
                                              int(ob * (1 - f) + b * f), 255)

    brick, solid, ladder, bar = make_brick(), make_solid(), make_ladder(), make_bar()
    for y in range(H):
        row = level_rows[y]
        for x in range(W):
            ch = row[x]
            if ch == '#':
                blit(brick, x * 16, y * 16)
            elif ch == '=':
                blit(solid, x * 16, y * 16)
            elif ch == 'H':
                blit(ladder, x * 16, y * 16)
            elif ch == '-':
                blit(bar, x * 16, y * 16)
            elif ch == 'X':
                blit(make_exit(0, exit_active), x * 16, y * 16)

    # 金块
    for y in range(H):
        for x in range(W):
            if level_rows[y][x] == 'G':
                blit(make_gold((x + y) % 6), x * 16, y * 16)

    # 角色
    for y in range(H):
        for x in range(W):
            ch = level_rows[y][x]
            if ch == 'P':
                blit(build_char(False, 'stand'), x * 16, y * 16)
            elif ch == 'E':
                blit(flip(build_char(True, 'run', 2)), x * 16, y * 16)
            elif ch == 'T':
                blit(build_char(False, 'climb', 0), x * 16, y * 16)
            elif ch == 'D':
                blit(build_char(False, 'dig'), x * 16, y * 16)
            elif ch == 'N':
                blit(build_char(False, 'hang'), x * 16, y * 16)

    # 边框 + 放大
    sw, sh = bw * scale + 8 * scale, bh * scale + 8 * scale
    scr = Grid(sw, sh)
    scr.r(0, 0, sw, sh, (0x06, 0x06, 0x0C))
    for y in range(bh):
        row = out.px[y]
        for x in range(bw):
            r, g, b, _ = row[x]
            for dy in range(scale):
                for dx in range(scale):
                    scr.px[4 * scale + y * scale + dy][4 * scale + x * scale + dx] = (r, g, b, 255)
    bw2 = 2 * scale
    scr.r(0, 0, sw, bw2, C['frame'])
    scr.r(0, sh - bw2, sw, bw2, C['frame'])
    scr.r(0, 0, bw2, sh, C['frame'])
    scr.r(sw - bw2, 0, bw2, sh, C['frame'])
    return scr


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
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(out)
    print('wrote', path, g.w, 'x', g.h)


if __name__ == '__main__':
    lv = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    sc = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    levels = load_levels()
    rows = [list(r) for r in levels[(lv - 1) % len(levels)]]
    # 在梯子上/横杆上摆几个示范姿态
    rows[13][8] = 'T'
    save_png(render(rows, sc), os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview_lv%d.png' % lv))
