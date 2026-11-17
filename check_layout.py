# -*- coding: utf-8 -*-
"""复刻 Kotlin 里 Renderer.resize + InputController.layout 的算法，
在各种屏幕尺寸下自检：控件够不够大、挖键间隙够不够、会不会压到关卡区。"""
import sys

W, H = 28, 16
TILE = 16

MIN_TOUCH_DP = 48.0          # Android 建议的最小触摸目标


def resize(vw, vh):
    """返回 (hudH, cell, 关卡左边界, 关卡右边界)"""
    u = min(vw, vh) / 400.0
    hudH = max(30.0 * u, 26.0)
    ctrlW = min(max(min(vw, vh) * 0.34, 130.0), 330.0)
    availW = max(vw - ctrlW * 2.0, 120.0)
    availH = max(vh - hudH - 6.0 * u, 120.0)
    raw = min(availW / W, availH / H)
    cell = max(int(raw / TILE), 2) * TILE
    ox = (vw - cell * W) / 2.0
    oy = hudH + (vh - hudH - cell * H) / 2.0
    return hudH, cell, ox, ox + cell * W


def clamp(v, lo, hi):
    return max(min(v, hi), lo)


def layout(vw, vh, boardL, boardR, hudH, dpi_scale):
    m = min(vw, vh)
    margin = max(m * 0.020, 6.0)
    top = hudH + margin
    availH = max(vh - top - margin, 80.0)

    panelL = max(boardL, 80.0)
    padCy = top + availH * 0.60
    l = min(panelL - margin * 2.0, availH * 0.66)
    l = min(l, (vh - margin - padCy) * 2.0, (padCy - top) * 2.0)
    padL = clamp(l, 120.0, 460.0)
    padArm = padL * 0.38
    padCx = panelL * 0.5
    if padCx + padL / 2.0 > panelL - 4.0:
        padCx = max(panelL - 4.0 - padL / 2.0, padL / 2.0 + 4.0)

    panelR = max(vw - boardR, 80.0)
    r = clamp(panelR * 0.215, 40.0, 116.0)
    rByH = max(availH * 0.20, 36.0)
    r = min(r, rByH)
    rFit = max((panelR - margin * 2.0) / 4.44, 34.0)
    r = min(r, rFit)
    stacked = r < 24.0 * dpi_scale      # 直径不足 48dp 就改上下排列
    if stacked:
        r = clamp(min(panelR * 0.42, (panelR - margin * 2.0) * 0.5, availH / 4.44), 36.0, 116.0)

    off = r * 1.22
    midX = boardR + panelR * 0.5
    cy = top + availH * 0.60
    need = off + r
    loY = top + need
    hiY = max(vh - margin - need, loY)
    if stacked:
        cy = clamp(cy, loY, hiY)
        digLCx = digRCx = midX
        digLCy, digRCy = cy - off, cy + off
    else:
        cy = clamp(cy, top + r, max(vh - margin - r, top + r))
        digLCx, digRCx = midX - off, midX + off
        digLCy = digRCy = cy

    return dict(padCx=padCx, padCy=padCy, padL=padL, padArm=padArm,
                digLCx=digLCx, digRCx=digRCx, digLCy=digLCy, digRCy=digRCy,
                digR=r, margin=margin, stacked=stacked)


def check(name, vw, vh, dpi_scale):
    """dpi_scale: px / dp，用于把像素换算成 dp"""
    hudH, cell, bl, br = resize(vw, vh)
    c = layout(vw, vh, bl, br, hudH, dpi_scale)
    half = c['padL'] / 2.0
    armH = c['padArm'] / 2.0
    r = c['digR']

    # 十字键：整个方框对角分区，减去中心死区后每个方向的有效面积
    per_dir_area = (c['padL'] ** 2 - c['padArm'] ** 2) / 4.0
    per_dir_dp = (per_dir_area ** 0.5) / dpi_scale     # 等效见方边长
    pad_dp = c['padL'] / dpi_scale
    dig_dp = (r * 2.0) / dpi_scale

    # 挖键视觉间隙（并排看水平间距，上下排列看垂直间距）
    if c['stacked']:
        gap = (c['digRCy'] - c['digLCy']) - 2 * r
        hit_gap = (c['digRCy'] - c['digLCy']) - 2 * r * 1.10
    else:
        gap = (c['digRCx'] - c['digLCx']) - 2 * r
        hit_gap = (c['digRCx'] - c['digLCx']) - 2 * r * 1.10

    problems = []
    if per_dir_dp < 36.0:
        problems.append('方向键每向有效区 %.0fdp 偏小' % per_dir_dp)
    if dig_dp < MIN_TOUCH_DP:
        problems.append('挖键直径 %.0fdp < %.0fdp' % (dig_dp, MIN_TOUCH_DP))
    if gap < 0.35 * r:
        problems.append('挖键间隙过小 %.0fpx' % gap)
    if hit_gap <= 12.0:
        problems.append('挖键命中区间隙仅 %.0fpx' % hit_gap)
    if c['padCx'] - half < -1:
        problems.append('方向键左侧出屏')
    if c['padCx'] + half > bl + 1:
        problems.append('方向键压到关卡 %.0fpx' % (c['padCx'] + half - bl))
    if c['padCy'] + half > vh + 1 or c['padCy'] - half < hudH - 1:
        problems.append('方向键纵向出界')
    if c['digLCx'] - r < br - 1:
        problems.append('挖左键压到关卡 %.0fpx' % (br - (c['digLCx'] - r)))
    if c['digRCx'] + r > vw + 1:
        problems.append('挖右键右侧出屏 %.0fpx' % (c['digRCx'] + r - vw))
    if c['digLCy'] - r < hudH - 1 or (c['stacked'] and c['digLCy'] - r < hudH - 1):
        problems.append('挖键顶部越过 HUD')
    if max(c['digLCy'], c['digRCy']) + r > vh + 1:
        problems.append('挖键底部出屏')

    flag = 'OK ' if not problems else '!! '
    print('%s%-18s %4dx%-4d 格=%-3.0f 方向键%4.0fdp(每向%3.0fdp) 挖键直径%4.0fdp%s 间隙%3.0fpx(命中%3.0f)'
          % (flag, name, vw, vh, cell, pad_dp, per_dir_dp, dig_dp,
             '竖排' if c['stacked'] else '并排', gap, hit_gap))
    for p in problems:
        print('       问题: ' + p)
    return not problems


if __name__ == '__main__':
    # (名称, 宽, 高, px/dp)
    cases = [
        ('1080P 横屏', 2340, 1080, 2.625),
        ('1080P 含导航栏', 2136, 1008, 2.625),
        ('720P 横屏', 1600, 720, 2.0),
        ('720P 小屏', 1280, 720, 2.0),
        ('小屏低端机', 1180, 620, 1.5),
        ('平板 2K', 2560, 1600, 2.0),
        ('平板 4:3 横', 2048, 1536, 2.0),
        ('极窄 20:9', 2400, 1000, 2.75),
        ('极宽 21:9', 2520, 1080, 2.75),
    ]
    ok = True
    for name, w, h, s in cases:
        ok &= check(name, w, h, s)
    print()
    print('全部通过' if ok else '存在需要修的尺寸')
    sys.exit(0 if ok else 1)
