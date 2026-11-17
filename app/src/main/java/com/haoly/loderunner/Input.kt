package com.haoly.loderunner

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sqrt

class InputState {
    var left = false
    var right = false
    var up = false
    var down = false
    var digL = false     // 边沿信号
    var digR = false
}

/**
 * 触屏操控：左侧大十字方向键 + 右侧「挖左 / 挖右」两个大圆键。
 *
 * 防误碰设计：
 *  1) 十字键是一个整体十字形控件，按落点所在臂决定方向，臂与臂之间不重叠；
 *     正中心留死区（不响应），手指从中心滑出才判定方向。
 *  2) 挖键之间留出不小于 0.5 倍半径的空隙，命中半径只比视觉半径大 10%，
 *     不再像早期版本那样把判定圈放大到互相重叠。
 *  3) 左右两侧控件各自限定在自己的面板内，绝不会跨到关卡区。
 *
 * 键盘：方向键或 WASD 移动，Z / X 挖洞。
 */
class InputController {

    val state = InputState()

    // ---- 左侧十字键
    var padCx = 0f
    var padCy = 0f
    var padL = 200f          // 十字外接方框边长（总跨度）
    var padArm = 80f         // 臂宽
    private var idPad = -1
    private var padDir = 0   // 0 无 / 1 左 / 2 右 / 3 上 / 4 下

    // ---- 右侧挖洞键
    var digLCx = 0f
    var digLCy = 0f
    var digRCx = 0f
    var digRCy = 0f
    var digR = 60f
    var digStacked = false      // 面板太窄时改为上下排列，避免两个键挤在一起
    private var idDigL = -1
    private var idDigR = -1

    var holdLeft = false
    var holdRight = false
    var holdUp = false
    var holdDown = false
    var holdDigL = false
    var holdDigR = false

    // 键盘
    private var kLeft = false
    private var kRight = false
    private var kUp = false
    private var kDown = false

    private var vw = 0f
    private var vh = 0f

    /**
     * @param boardL 关卡区左边界（左侧控件的可用宽度为 [0, boardL)）
     * @param boardR 关卡区右边界（右侧控件的可用宽度为 (boardR, vw]）
     * @param density 屏幕密度，用来把「挖键直径不低于 48dp」换算成像素
     */
    fun layout(w: Float, h: Float, boardL: Float, boardR: Float, hudH: Float, density: Float) {
        vw = w
        vh = h
        val m = minOf(w, h)
        val margin = (m * 0.020f).coerceAtLeast(6f)
        val top = hudH + margin
        val availH = (h - top - margin).coerceAtLeast(80f)

        // ---------------- 左侧：方向键撑满面板
        val panelL = boardL.coerceAtLeast(80f)
        padCy = top + availH * 0.60f
        var l = minOf(panelL - margin * 2f, availH * 0.66f)
        l = minOf(l, (h - margin - padCy) * 2f, (padCy - top) * 2f)
        padL = l.coerceIn(120f, 460f)
        padArm = padL * 0.38f
        padCx = panelL * 0.5f
        // 若跨度被上限截断后放不下，向内收一点避免压到关卡
        if (padCx + padL / 2f > panelL - 4f) padCx = (panelL - 4f - padL / 2f).coerceAtLeast(padL / 2f + 4f)

        // ---------------- 右侧：两个挖键，中间永远留一道缝
        val panelR = (w - boardR).coerceAtLeast(80f)
        var r = (panelR * 0.215f).coerceIn(40f, 116f)
        val rByH = (availH * 0.20f).coerceAtLeast(36f)
        if (r > rByH) r = rByH
        // 两键连间距一共占 4.44r，两边各留一个 margin 的余量
        val rFit = ((panelR - margin * 2f) / 4.44f).coerceAtLeast(34f)
        if (r > rFit) r = rFit
        // 并排会挤到直径不足 48dp —— 改成上下排列，反而能做得更大
        digStacked = r < 24f * density
        if (digStacked) {
            r = minOf(panelR * 0.42f, (panelR - margin * 2f) * 0.5f, availH / 4.44f).coerceIn(36f, 116f)
        }
        digR = r

        val off = r * 1.22f                 // 圆心间距，保证视觉间隙 ≥ 0.44r
        val midX = boardR + panelR * 0.5f
        var cy = top + availH * 0.60f
        val need = off + r
        val loY = top + need
        val hiY = (h - margin - need).coerceAtLeast(loY)
        cy = if (digStacked) cy.coerceIn(loY, hiY)
        else cy.coerceIn(top + r, (h - margin - r).coerceAtLeast(top + r))

        if (digStacked) {
            digLCx = midX
            digRCx = midX
            digLCy = cy - off
            digRCy = cy + off
        } else {
            digLCx = midX - off
            digRCx = midX + off
            digLCy = cy
            digRCy = cy
        }
    }

    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i)
                val x = ev.getX(i)
                val y = ev.getY(i)
                when {
                    hitDig(x, y, digLCx, digLCy) -> { idDigL = id; holdDigL = true; state.digL = true }
                    hitDig(x, y, digRCx, digRCy) -> { idDigR = id; holdDigR = true; state.digR = true }
                    dirAt(x, y) != 0 -> {
                        idPad = id
                        padDir = dirAt(x, y)
                        applyPadDir()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    if (id == idPad) {
                        val d = dirAt(ev.getX(i), ev.getY(i))
                        // 落在中心死区时保持原方向，避免手指微动把方向抖掉
                        if (d != 0) { padDir = d; applyPadDir() }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = ev.actionIndex.coerceIn(0, (ev.pointerCount - 1).coerceAtLeast(0))
                val id = ev.getPointerId(i)
                if (id == idPad) { idPad = -1; padDir = 0; applyPadDir() }
                if (id == idDigL) { idDigL = -1; holdDigL = false }
                if (id == idDigR) { idDigR = -1; holdDigR = false }
            }
        }
        return true
    }

    private fun applyPadDir() {
        holdLeft = padDir == 1
        holdRight = padDir == 2
        holdUp = padDir == 3
        holdDown = padDir == 4
    }

    /** 十字键落点判定：只在十字形臂内有效，正中心死区返回 0 */
    private fun dirAt(x: Float, y: Float): Int {
        val dx = x - padCx
        val dy = y - padCy
        val half = padL * 0.52f
        if (abs(dx) > half || abs(dy) > half) return 0
        val armH = padArm * 0.5f
        val inV = abs(dx) <= armH     // 在竖直臂内
        val inH = abs(dy) <= armH     // 在水平臂内
        if (!inV && !inH) return 0
        if (inV && inH) return 0      // 中心死区：四臂交汇，不判定方向
        return if (inV) {
            if (dy < 0f) 3 else 4
        } else {
            if (dx < 0f) 1 else 2
        }
    }

    /** 挖键判定：略微放宽 10%，但远小于旧版的 1.4 倍，保证两键之间仍有空档 */
    private fun hitDig(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) <= digR * 1.10f
    }

    fun onKeyDown(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> kLeft = true
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> kRight = true
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> kUp = true
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> kDown = true
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_SHIFT_LEFT -> state.digL = true
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_SHIFT_RIGHT -> state.digR = true
            else -> return false
        }
        return true
    }

    fun onKeyUp(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> kLeft = false
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> kRight = false
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> kUp = false
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> kDown = false
            else -> return false
        }
        return true
    }

    fun consume(): InputState {
        state.left = holdLeft || kLeft
        state.right = holdRight || kRight
        state.up = holdUp || kUp
        state.down = holdDown || kDown
        return state
    }

    fun clearEdges() {
        state.digL = false
        state.digR = false
    }

    /** 抬起全部触点，避免 Activity 切走后残留「按住」状态 */
    fun releaseAll() {
        idPad = -1
        idDigL = -1
        idDigR = -1
        padDir = 0
        holdLeft = false
        holdRight = false
        holdUp = false
        holdDown = false
        holdDigL = false
        holdDigR = false
        kLeft = false
        kRight = false
        kUp = false
        kDown = false
    }
}
