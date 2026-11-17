# -*- coding: utf-8 -*-
"""警察抓小偷（Lode Runner）关卡生成器 —— 带严格可解性与安全性校验。

网格 28 x 16：
  '=' 钢块（不可挖/不可穿）  '#' 砖块（可挖）  'H' 梯子  '-' 横杆
  'G' 金块   'P' 小偷起点   'E' 警察起点   'X' 出口梯（收齐金块后出现）

结构：
  地板行  3, 6, 9, 12（砖）；最底行 15 是实心边界
  站立行  2, 5, 8, 11, 14（站在地板上一行）
  相邻两层的站立行之间用梯子连通，且相邻层梯子的 x 必须错开（逼玩家绕路）

安全性（修掉“第一关必死”）：
  1. 警察与小偷起点的路径距离必须 >= 阈值
  2. 若警察和小偷同层，必须存在一条「比警察更近」的上行梯子，保证能先爬走
  3. 每条走廊至少 3 条上行梯子，不存在只有一个出口的死胡同
"""
import random
import os
from collections import deque

W, H = 28, 16
FLOORS = [3, 6, 9, 12]
STANDS = [2, 5, 8, 11, 14]
PASSABLE = set(' HGPEX-')


# ------------------------------------------------------------------ 基础工具
def blank():
    g = [[' '] * W for _ in range(H)]
    for x in range(W):
        g[0][x] = '='
        g[H - 1][x] = '='
    for y in range(H):
        g[y][0] = '='
        g[y][W - 1] = '='
    return g


def passable(ch):
    return ch not in '#='


def support_below(ch):
    """脚下的格子能不能踩住"""
    return ch in '#=H-X'


def self_support(ch):
    """本格自身能不能站住（梯子 / 横杆可以挂）"""
    return ch in 'H-X'


def standing(g, x, y):
    if not (0 <= x < W and 0 <= y < H):
        return False
    return self_support(g[y][x]) or support_below(g[y + 1][x])


def settle(g, x, y):
    """从 (x, y) 进入后一路下落到站稳为止；返回站立坐标或 None"""
    if not (0 <= x < W and 0 <= y < H):
        return None
    if not passable(g[y][x]):
        return None
    while y < H - 1 and not standing(g, x, y):
        y += 1
        if not passable(g[y][x]):
            return None
    return (x, y) if standing(g, x, y) else None


def states_from(g, start):
    """严格可达性 BFS：走 / 爬梯 / 下落 / 挖穿地板下一层"""
    s0 = settle(g, start[0], start[1])
    if s0 is None:
        return {}
    dist = {s0: 0}
    q = deque([s0])
    while q:
        x, y = q.popleft()
        d = dist[(x, y)] + 1

        def push(nx, ny):
            st = settle(g, nx, ny)
            if st and st not in dist:
                dist[st] = d
                q.append(st)

        # 左右行走
        for nx in (x - 1, x + 1):
            if 0 <= nx < W and passable(g[y][nx]):
                push(nx, y)
        # 上爬（只能沿着梯子 / 出口梯）
        if y > 0 and g[y - 1][x] in 'HX':
            push(x, y - 1)
        # 下爬 / 掉落
        if y < H - 1:
            if g[y + 1][x] in 'HX':
                push(x, y + 1)
            elif passable(g[y + 1][x]) and not standing(g, x, y):
                push(x, y + 1)
        # 挖穿脚边的砖，然后掉下去
        for dx in (-1, 1):
            nx = x + dx
            if not (0 <= nx < W and y + 1 < H):
                continue
            if g[y + 1][nx] != '#' or not passable(g[y][nx]):
                continue
            g[y + 1][nx] = ' '          # 临时挖开
            push(nx, y)
            g[y + 1][nx] = '#'          # 还原
    return dist


# ------------------------------------------------------------------ 生成
def gen(seed, level):
    rng = random.Random(seed)
    g = blank()

    # ---- 地板 + 缺口（部分缺口架横杆，可悬挂通过；其余是直通下层的洞）
    gap_count = min(1 + level // 3, 3)
    gaps = []                                   # [(floor_row, x, width)]
    for fy in FLOORS:
        for x in range(1, W - 1):
            g[fy][x] = '#'
        used = []
        for _ in range(gap_count):
            for _try in range(30):
                gx = rng.randint(4, W - 6)
                width = rng.choice([2, 2, 3])
                if any(abs(gx - u) < 6 for u in used):
                    continue
                used.append(gx)
                gaps.append((fy, gx, width))
                break

    # ---- 梯子：相邻两层连通，且相邻层的梯子 x 必须错开
    ladders = {}                                # layer_index -> [x]
    prev_xs = []
    for li in range(len(STANDS) - 1):
        top, bottom = STANDS[li], STANDS[li + 1]
        n = 4 if level <= 2 else 3
        xs = []
        for _ in range(120):
            if len(xs) >= n:
                break
            cx = rng.randint(3, W - 5)
            if any(abs(cx - u) < 5 for u in xs):
                continue
            if any(abs(cx - u) < 3 for u in prev_xs):
                continue                        # 与上一层错开，避免一条直梯通天
            xs.append(cx)
        if len(xs) < 3:                         # 至少 3 条，死胡同会必死
            return None
        ladders[li] = xs
        prev_xs = xs
        for cx in xs:
            for y in range(top, bottom + 1):
                if 1 <= cx <= W - 2:
                    g[y][cx] = 'H'

    # ---- 缺口成型：横杆放在站立行，直通洞留在地板行
    for (fy, gx, width) in gaps:
        # 整段缺口统一决定：要么架横杆（可悬挂通过），要么做成直通下层的洞
        bar = rng.random() < 0.6
        for k in range(width):
            x = gx + k
            if not (1 <= x <= W - 2):
                continue
            if bar:
                g[fy - 1][x] = '-'              # 站立行架横杆，可以挂着走过去
            g[fy][x] = ' '                      # 地板缺口

    # ---- 不可挖的钢块（不在梯子列、不在缺口旁）
    solid_n = min(1 + level // 2, 5)
    all_ladder_x = set()
    for xs in ladders.values():
        all_ladder_x.update(xs)
    placed = 0
    for _ in range(solid_n * 30):
        if placed >= solid_n:
            break
        fy = rng.choice(FLOORS)
        x = rng.randint(2, W - 3)
        if g[fy][x] != '#' or x in all_ladder_x:
            continue
        if any(g[fy][nx] != '#' for nx in (x - 1, x + 1)):
            continue
        g[fy][x] = '='
        placed += 1

    # ---- 金块：按层分配，低关卡偏下层，高关卡铺满
    gold_total = min(10 + (level - 1) * 2, 22)
    weights = [3, 3, 3, 3, 4]                   # 站立行 2,5,8,11,14
    if level <= 2:
        weights = [1, 2, 3, 4, 5]               # 前期主要在下层
    total_w = float(sum(weights))
    per = [int(round(gold_total * w / total_w)) for w in weights]
    for li, sy in enumerate(STANDS):
        spots = [x for x in range(1, W - 1)
                 if g[sy][x] == ' ' and g[sy + 1][x] in '#=']
        rng.shuffle(spots)
        for x in spots[:per[li]]:
            g[sy][x] = 'G'

    # ---- 小偷起点：底层左侧，脚下必须是实心
    spots = [x for x in range(2, 7)
             if g[14][x] == ' ' and g[15][x] == '=']
    if not spots:
        return None
    px = rng.choice(spots)
    g[14][px] = 'P'

    # ---- 出口梯：顶层某个落脚点往上
    spots = [x for x in range(2, W - 2) if g[2][x] == ' ']
    if not spots:
        return None
    ex = rng.choice(spots)
    g[1][ex] = 'X'
    g[2][ex] = 'X'

    # ---- 警察
    guards = min(2 + (level - 1) // 2, 5)
    return place_guards(g, rng, level, px, guards, ladders)


def place_guards(g, rng, level, px, guards, ladders):
    dist = states_from(g, (px, 14))
    min_d = 18 if level == 1 else (14 if level <= 3 else 11)
    allowed_layers = [14, 11, 8, 5, 2]
    if level == 1:
        allowed_layers = [11, 8]                # 第一关警察不出现在小偷所在层
    elif level == 2:
        allowed_layers = [11, 8, 5]
    elif level <= 4:
        allowed_layers = [11, 8, 5, 2]

    # 每层的上行梯子（用于判断能不能先爬走）
    up_ladders = {STANDS[i]: ladders.get(i - 1, []) for i in range(1, len(STANDS))}
    up_ladders[STANDS[0]] = []

    cands = []
    for sy in allowed_layers:
        for x in range(1, W - 1):
            if g[sy][x] != ' ' or g[sy + 1][x] not in '#=':
                continue
            st = (x, sy)
            if st not in dist:
                continue
            if dist[st] < min_d:
                continue
            if sy == 14:
                # 同层：必须有一条比警察更近的上行梯子，否则会被堵死
                esc = min((abs(lx - px) for lx in up_ladders[14]), default=999)
                if esc + 3 >= abs(x - px):
                    continue
            cands.append(st)
    if len(cands) < guards:
        return None
    rng.shuffle(cands)

    chosen = []
    for st in cands:
        if len(chosen) >= guards:
            break
        if any(abs(st[0] - c[0]) + abs(st[1] - c[1]) * 2 < 8 for c in chosen):
            continue
        chosen.append(st)
    if len(chosen) < guards:
        return None

    for (x, y) in chosen:
        g[y][x] = 'E'
    return g


# ------------------------------------------------------------------ 校验
def check(g, level):
    start = None
    for y in range(H):
        for x in range(W):
            if g[y][x] == 'P':
                start = (x, y)
    if not start:
        return 'no start'
    dist = states_from(g, start)

    bad = []
    gold = 0
    for y in range(H):
        for x in range(W):
            if g[y][x] == 'G':
                gold += 1
                if (x, y) not in dist:
                    bad.append((x, y))
    if bad:
        return 'gold unreachable %s' % bad[:4]
    if gold < 8:
        return 'too few gold %d' % gold
    if not any(g[y][x] == 'X' and (x, y) in dist for y in range(H) for x in range(W)):
        return 'exit unreachable'

    nguard = sum(r.count('E') for r in g)
    if nguard < 2:
        return 'too few guards'

    # 死胡同检测：底层必须至少 2 条上行梯子，且都能走到
    bottom_up = [x for x in range(1, W - 1) if g[11][x] == 'H' and (x, 11) in dist]
    if len(bottom_up) < 2:
        return 'bottom layer only %d way up' % len(bottom_up)
    return None


def to_rows(g):
    return [''.join(r) for r in g]


# ------------------------------------------------------------------ 主流程
def main():
    LEVELS = 12
    rows_out = []
    info = []
    for level in range(1, LEVELS + 1):
        ok = False
        for attempt in range(4000):
            g = gen(level * 1013904223 + attempt * 7919, level)
            if g is None:
                continue
            err = check(g, level)
            if err:
                continue
            rows = to_rows(g)
            assert all(len(r) == W for r in rows) and len(rows) == H
            rows_out.append(rows)
            gold = sum(r.count('G') for r in rows)
            guards = sum(r.count('E') for r in rows)
            info.append((level, gold, guards))
            ok = True
            break
        if not ok:
            raise SystemExit('第 %d 关生成失败' % level)

    out = ['package com.haoly.loderunner', '', '/**',
           ' * 关卡数据（28 x 16 网格）。',
           ' *',
           ' * 字符含义：',
           ' *   = 钢块（不可挖）  # 砖块（可挖）  H 梯子  - 横杆',
           ' *   G 金块  P 小偷起点  E 警察起点  X 出口梯',
           ' *',
           ' * 由 gen_levels.py 生成，全部通过校验：',
           ' *   1) 每块金子与出口梯在「走 / 爬梯 / 下落 / 挖穿地板」规则下可达',
           ' *   2) 警察与小偷起点的路径距离不低于阈值',
           ' *   3) 同层的警察不会堵死唯一上行梯子（修掉第一关必死）',
           ' */',
           'object Levels {',
           '    val DATA: Array<Array<String>> = arrayOf(']
    for rows in rows_out:
        out.append('        arrayOf(')
        for i, r in enumerate(rows):
            out.append('            "%s"%s' % (r, ',' if i < len(rows) - 1 else ''))
        out.append('        ),')
    out.append('    )')
    out.append('}')
    out.append('')

    base = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(base, 'app', 'src', 'main', 'java', 'com', 'haoly',
                        'loderunner', 'Levels.kt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out))

    for level, gold, guards in info:
        print('第 %2d 关：金块 %2d，警察 %d' % (level, gold, guards))
    print('written ->', path)


if __name__ == '__main__':
    main()
