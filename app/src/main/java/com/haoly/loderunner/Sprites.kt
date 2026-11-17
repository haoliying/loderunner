package com.haoly.loderunner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.util.Random
import kotlin.math.round

/**
 * 全部像素素材在运行时预渲染成 Bitmap，再按整数倍放大绘制 —— 保证是硬的像素块，不会糊。
 * 一格 = 16 x 16 像素（棋盘 448 x 256，接近当年 Apple II / 中华学习机的分辨率）。
 */
object Sprites {

    const val TILE = 16

    // 角色姿态
    const val POSE_STAND = 0
    const val POSE_RUN = 1
    const val POSE_CLIMB = 2
    const val POSE_FALL = 3
    const val POSE_HANG = 4
    const val POSE_DIG = 5
    const val POSE_DIE = 6

    const val RUN_FRAMES = 8
    const val GOLD_FRAMES = 6
    const val EXIT_FRAMES = 6
    const val TRAP_STAGES = 7

    lateinit var brick: Bitmap
        private set
    lateinit var solid: Bitmap
        private set
    lateinit var ladder: Bitmap
        private set
    lateinit var bar: Bitmap
        private set
    lateinit var board: Bitmap
        private set
    lateinit var trap: Array<Bitmap>
        private set
    lateinit var gold: Array<Bitmap>
        private set
    lateinit var exitOff: Bitmap
        private set
    lateinit var exitOn: Array<Bitmap>
        private set
    lateinit var thief: Array<Array<Bitmap>>
        private set
    lateinit var police: Array<Array<Bitmap>>
        private set

    private var ready = false

    fun build() {
        if (ready) return
        brick = makeBrick()
        solid = makeSolid()
        ladder = makeLadder(Cfg.C_LADDER_DK, Cfg.C_LADDER_LT)
        bar = makeBar()
        board = makeBoard()
        trap = Array(TRAP_STAGES) { makeTrap(it) }
        gold = Array(GOLD_FRAMES) { makeGold(it) }
        exitOff = makeExit(-1)
        exitOn = Array(EXIT_FRAMES) { makeExit(it) }
        thief = Array(7) { pose -> frameCount(pose).let { n -> Array(n) { f -> buildChar(false, pose, f) } } }
        police = Array(7) { pose -> frameCount(pose).let { n -> Array(n) { f -> buildChar(true, pose, f) } } }
        ready = true
    }

    private fun frameCount(pose: Int) = when (pose) {
        POSE_RUN -> RUN_FRAMES
        POSE_CLIMB -> 2
        POSE_DIE -> 2
        else -> 1
    }

    // ---------------------------------------------------------------- 画具

    private class Pix(b: Bitmap) {
        private val c = Canvas(b)
        private val p = Paint().apply { isAntiAlias = false }

        fun r(x: Int, y: Int, w: Int, h: Int, color: Int) {
            if (w <= 0 || h <= 0) return
            p.color = color
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
        }

        fun r(x: Int, y: Int, w: Int, h: Int, color: Int, alpha: Int) {
            if (w <= 0 || h <= 0) return
            p.color = color
            p.alpha = alpha
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
            p.alpha = 255
        }
    }

    private fun newBmp(w: Int = TILE, h: Int = TILE): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // ---------------------------------------------------------------- 地形

    private fun makeBrick(): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        p.r(0, 0, 16, 16, Cfg.C_BRICK)
        // 砖缝：两横 + 每层错缝的两竖
        p.r(0, 5, 16, 1, Cfg.C_MORTAR)
        p.r(0, 11, 16, 1, Cfg.C_MORTAR)
        p.r(0, 1, 1, 4, Cfg.C_MORTAR)
        p.r(8, 1, 1, 4, Cfg.C_MORTAR)
        p.r(4, 7, 1, 4, Cfg.C_MORTAR)
        p.r(12, 7, 1, 4, Cfg.C_MORTAR)
        p.r(0, 13, 1, 2, Cfg.C_MORTAR)
        p.r(8, 13, 1, 2, Cfg.C_MORTAR)
        // 顶部受光 / 底部阴影
        p.r(0, 0, 16, 1, Cfg.C_BRICK_LT)
        p.r(0, 15, 16, 1, Cfg.C_BRICK_DK)
        // 砖面质感
        p.r(2, 2, 2, 1, Cfg.C_BRICK_LT)
        p.r(10, 3, 3, 1, Cfg.C_BRICK_LT)
        p.r(6, 8, 2, 1, Cfg.C_BRICK_LT)
        p.r(1, 9, 2, 1, Cfg.C_BRICK_DK)
        p.r(9, 14, 3, 1, Cfg.C_BRICK_DK)
        return b
    }

    private fun makeSolid(): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        p.r(0, 0, 16, 16, Cfg.C_SOLID)
        p.r(0, 0, 16, 2, Cfg.C_SOLID_LT)
        p.r(0, 0, 2, 16, Cfg.C_SOLID_LT)
        p.r(0, 14, 16, 2, Cfg.C_SOLID_DK)
        p.r(14, 0, 2, 16, Cfg.C_SOLID_DK)
        p.r(3, 3, 10, 10, 0xFF4A5470.toInt())
        p.r(3, 3, 10, 1, Cfg.C_SOLID_DK)
        p.r(4, 4, 2, 2, Cfg.C_RIVET)
        p.r(10, 4, 2, 2, Cfg.C_RIVET)
        p.r(4, 10, 2, 2, Cfg.C_RIVET)
        p.r(10, 10, 2, 2, Cfg.C_RIVET)
        p.r(7, 6, 2, 1, Cfg.C_SOLID_LT)
        return b
    }

    private fun makeLadder(dk: Int, lt: Int): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        p.r(3, 0, 2, 16, dk)
        p.r(11, 0, 2, 16, dk)
        p.r(3, 0, 1, 16, lt)
        p.r(11, 0, 1, 16, lt)
        for (y in intArrayOf(1, 6, 11)) {
            p.r(2, y, 12, 2, dk)
            p.r(3, y, 10, 1, lt)
        }
        return b
    }

    private fun makeBar(): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        // 横杆画在格子顶部：小偷是挂在杆子下面的
        p.r(0, 0, 16, 3, Cfg.C_BAR_DK)
        p.r(0, 0, 16, 1, Cfg.C_BAR_LT)
        p.r(0, 1, 16, 2, Cfg.C_BAR)
        p.r(1, 3, 2, 2, Cfg.C_BAR_DK)
        p.r(13, 3, 2, 2, Cfg.C_BAR_DK)
        return b
    }

    private fun makeTrap(stage: Int): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        p.r(0, 0, 16, 16, Cfg.C_TRAP)
        // 坑口碎土
        p.r(0, 0, 16, 2, Cfg.C_DIRT_DK)
        p.r(0, 0, 16, 1, Cfg.C_DIRT)
        p.r(0, 1, 3, 1, Cfg.C_DIRT)
        p.r(11, 1, 4, 1, Cfg.C_DIRT)
        p.r(0, 2, 1, 12, 0xFF2A2036.toInt())
        p.r(15, 2, 1, 12, 0xFF2A2036.toInt())
        // 回填：土从底部往上长
        val h = stage * 16 / TRAP_STAGES
        if (h > 0) {
            p.r(0, 16 - h, 16, h, Cfg.C_DIRT_DK)
            p.r(0, 16 - h, 16, 1, Cfg.C_DIRT)
            p.r(2, 16 - h + 2, 3, 1, Cfg.C_DIRT)
            p.r(9, 16 - h + 3, 4, 1, Cfg.C_DIRT)
        }
        return b
    }

    private fun makeGold(frame: Int): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        // 菱形金块
        for (i in 0..4) p.r(8 - (i + 1), 4 + i, 2 * (i + 1), 1, Cfg.C_GOLD)
        val bot = intArrayOf(8, 6, 4, 2)
        for (i in bot.indices) p.r(8 - bot[i] / 2, 9 + i, bot[i], 1, Cfg.C_GOLD)
        // 立体：左上亮、右下暗
        p.r(6, 5, 3, 2, Cfg.C_GOLD_LT)
        p.r(5, 6, 2, 1, Cfg.C_GOLD_LT)
        p.r(4, 7, 3, 2, Cfg.C_GOLD_LT)
        p.r(5, 10, 6, 1, Cfg.C_GOLD_DK)
        p.r(6, 11, 4, 1, Cfg.C_GOLD_DK)
        p.r(3, 8, 10, 1, Cfg.C_GOLD_DK)
        // 闪光（每帧位置不同）
        val sx = intArrayOf(11, 2, 12, 3, 10, 4)[frame]
        val sy = intArrayOf(3, 10, 7, 4, 12, 6)[frame]
        p.r(sx, sy - 1, 1, 3, Cfg.C_GOLD_LT)
        p.r(sx - 1, sy, 3, 1, Cfg.C_GOLD_LT)
        if (frame % 2 == 0) p.r(sx, sy, 1, 1, Cfg.C_WHITE)
        return b
    }

    private fun makeExit(frame: Int): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        val on = frame >= 0
        val a = if (on) 255 else 42
        val rail = if (on && frame % 2 == 0) Cfg.C_EXIT_LT else Cfg.C_EXIT
        p.r(4, 0, 2, 16, rail, a)
        p.r(10, 0, 2, 16, rail, a)
        for (i in 0..2) {
            val y = 2 + i * 5
            val lit = on && ((frame + i) % EXIT_FRAMES < 2)
            p.r(4, y, 8, 2, if (lit) Cfg.C_WHITE else rail, a)
        }
        if (on) {
            // 上行箭头
            p.r(7, 0, 2, 3, Cfg.C_EXIT_LT, 200)
            p.r(6, 1, 4, 1, Cfg.C_EXIT_LT, 160)
        }
        return b
    }

    private fun makeBoard(): Bitmap {
        val b = newBmp(Cfg.W * TILE, Cfg.H * TILE)
        val p = Pix(b)
        // 竖向渐变：顶部偏蓝紫，底部更黑
        for (y in 0 until Cfg.H * TILE) {
            val t = y.toFloat() / (Cfg.H * TILE - 1)
            val r = (0x0B + (0x14 - 0x0B) * (1 - t)).toInt()
            val g = (0x0A + (0x10 - 0x0A) * (1 - t)).toInt()
            val bl = (0x16 + (0x26 - 0x16) * (1 - t)).toInt()
            p.r(0, y, Cfg.W * TILE, 1, 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl)
        }
        // 洞穴颗粒
        val rng = Random(20261002)
        for (i in 0 until 900) {
            val x = rng.nextInt(Cfg.W * TILE)
            val y = rng.nextInt(Cfg.H * TILE)
            val c = if (rng.nextBoolean()) Cfg.C_CAVE else 0xFF241C34.toInt()
            p.r(x, y, 1, 1, c, 90)
        }
        return b
    }

    // ---------------------------------------------------------------- 角色

    private fun buildChar(police: Boolean, pose: Int, frame: Int): Bitmap {
        val b = newBmp()
        val p = Pix(b)
        val armC = if (police) Cfg.C_PO_UNI else Cfg.C_SKIN
        val pantC = if (police) Cfg.C_PO_PANTS else Cfg.C_TH_PANTS

        fun head(oy: Int) {
            if (police) {
                p.r(5, oy, 6, 1, Cfg.C_PO_CAP)
                p.r(4, oy + 1, 8, 1, Cfg.C_PO_CAP)
                p.r(7, oy + 1, 2, 1, Cfg.C_BADGE)
                p.r(4, oy + 2, 8, 1, Cfg.C_PO_CAP_DK)
                p.r(4, oy + 3, 8, 4, Cfg.C_SKIN)
                p.r(5, oy + 4, 2, 1, Cfg.C_BLACK)
                p.r(9, oy + 4, 2, 1, Cfg.C_BLACK)
                p.r(7, oy + 6, 2, 1, 0xFF8A4A3A.toInt())
                p.r(6, oy + 7, 4, 1, Cfg.C_SKIN)
            } else {
                p.r(5, oy, 6, 1, Cfg.C_TH_HAT)
                p.r(4, oy + 1, 8, 1, Cfg.C_TH_HAT)
                p.r(4, oy + 2, 8, 1, Cfg.C_TH_HAT)
                p.r(4, oy + 3, 8, 3, Cfg.C_TH_MASK)
                p.r(5, oy + 4, 2, 1, Cfg.C_WHITE)
                p.r(9, oy + 4, 2, 1, Cfg.C_WHITE)
                p.r(4, oy + 6, 8, 1, Cfg.C_SKIN)
                p.r(7, oy + 6, 2, 1, Cfg.C_TH_MASK)
                p.r(6, oy + 7, 4, 1, Cfg.C_SKIN)
            }
        }

        fun torso(oy: Int, rows: Int = 5) {
            if (police) {
                p.r(4, oy + 8, 8, rows, Cfg.C_PO_UNI)
                p.r(6, oy + 9, 2, 2, Cfg.C_BADGE)
                p.r(6, oy + 9, 2, 1, 0xFFFFE98A.toInt())
                p.r(4, oy + 8 + rows - 1, 8, 1, Cfg.C_BLACK)
                p.r(7, oy + 8 + rows - 1, 2, 1, Cfg.C_BADGE)
            } else {
                for (i in 0 until rows) {
                    p.r(4, oy + 8 + i, 8, 1, if (i % 2 == 0) Cfg.C_WHITE else Cfg.C_TH_STRIPE)
                }
            }
        }

        fun legsAt(py: Int, lx: Int, rx: Int) {
            p.r(lx, py, 2, 2, pantC)
            p.r(rx, py, 2, 2, pantC)
            p.r(lx - 1, py + 2, 3, 1, Cfg.C_BLACK)
            p.r(rx - 1, py + 2, 3, 1, Cfg.C_BLACK)
        }

        fun arm(x: Int, y: Int, h: Int, handTop: Boolean) {
            p.r(x, y, 2, h, armC)
            p.r(x, if (handTop) y - 1 else y + h, 2, 1, Cfg.C_SKIN)
        }

        when (pose) {
            POSE_STAND -> {
                head(0); torso(0)
                arm(2, 8, 3, false)
                arm(12, 8, 3, false)
                legsAt(13, 5, 9)
            }
            POSE_RUN -> {
                val s = round(2.0 * kotlin.math.sin(2.0 * Math.PI * frame / RUN_FRAMES))
                    .toInt().coerceIn(-1, 1)
                head(0); torso(0)
                arm(2, 8 - s, 3, false)
                arm(12, 8 + s, 3, false)
                legsAt(13, 5 + s, 9 - s)
            }
            POSE_CLIMB -> {
                head(0); torso(0)
                if (frame == 0) {
                    arm(2, 6, 3, true)
                    arm(12, 9, 3, false)
                    legsAt(13, 6, 9)
                } else {
                    arm(2, 9, 3, false)
                    arm(12, 6, 3, true)
                    legsAt(13, 5, 10)
                }
            }
            POSE_FALL -> {
                head(0); torso(0)
                arm(2, 5, 4, true)
                arm(12, 5, 4, true)
                legsAt(13, 4, 10)
            }
            POSE_HANG -> {
                head(2); torso(1, 4)
                arm(2, 2, 8, true)
                arm(12, 2, 8, true)
                p.r(5, 14, 2, 1, pantC)
                p.r(9, 14, 2, 1, pantC)
                p.r(4, 15, 3, 1, Cfg.C_BLACK)
                p.r(8, 15, 3, 1, Cfg.C_BLACK)
            }
            POSE_DIG -> {
                head(1); torso(1, 4)
                arm(2, 10, 3, false)
                arm(12, 10, 3, false)
                legsAt(13, 5, 9)
            }
            POSE_DIE -> {
                head(0)
                // X 眼
                p.r(5, 4, 1, 1, Cfg.C_WHITE); p.r(6, 5, 1, 1, Cfg.C_WHITE)
                p.r(6, 4, 1, 1, Cfg.C_WHITE); p.r(5, 5, 1, 1, Cfg.C_WHITE)
                p.r(9, 4, 1, 1, Cfg.C_WHITE); p.r(10, 5, 1, 1, Cfg.C_WHITE)
                p.r(10, 4, 1, 1, Cfg.C_WHITE); p.r(9, 5, 1, 1, Cfg.C_WHITE)
                torso(0)
                if (frame == 0) {
                    p.r(1, 8, 3, 2, armC)
                    p.r(12, 8, 3, 2, armC)
                    legsAt(13, 4, 10)
                } else {
                    arm(2, 5, 4, true)
                    arm(12, 5, 4, true)
                    legsAt(13, 6, 8)
                }
            }
        }
        return b
    }
}
