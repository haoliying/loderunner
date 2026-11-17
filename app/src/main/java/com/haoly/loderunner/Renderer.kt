package com.haoly.loderunner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.sin

/**
 * Canvas 自绘：16x16 像素素材按整数倍放大，还原中华学习机 / Apple II 上
 * 《警察抓小偷》的硬像素观感；HUD 与控件用原生分辨率绘制，保证文字清晰。
 */
class Renderer {

    private var vw = 0f
    private var vh = 0f
    private var cell = 64f          // 每格的屏幕像素（16 的整数倍，保证像素不糊）
    private var ox = 0f
    private var oy = 0f
    private var u = 1f
    private var hudH = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFlat = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dst = Rect()
    private val dstF = RectF()
    private val path = Path()

    init {
        Sprites.build()
    }

    var boardLeft = 0f
        private set
    var boardRight = 0f
        private set
    var hudBottom = 0f
        private set

    fun resize(w: Int, h: Int) {
        vw = w.toFloat()
        vh = h.toFloat()
        u = minOf(vw, vh) / 400f
        hudH = (30f * u).coerceAtLeast(26f)
        hudBottom = hudH
        // 两侧留给操控面板（十字键 / 挖洞键），控件放大后需要更宽的留白
        val ctrlW = (minOf(vw, vh) * 0.34f).coerceIn(130f, 330f)
        val availW = (vw - ctrlW * 2f).coerceAtLeast(120f)
        val availH = (vh - hudH - 6f * u).coerceAtLeast(120f)
        val raw = minOf(availW / Cfg.W, availH / Cfg.H)
        cell = (raw / Sprites.TILE).toInt().coerceAtLeast(2).toFloat() * Sprites.TILE
        ox = ((vw - cell * Cfg.W) / 2f)
        oy = hudH + (vh - hudH - cell * Cfg.H) / 2f
        boardLeft = ox
        boardRight = ox + cell * Cfg.W
    }

    // ------------------------------------------------------------------ 主绘制

    fun draw(canvas: Canvas, game: Game, input: InputController) {
        canvas.drawColor(Cfg.C_BG)

        var sx = 0f
        var sy = 0f
        if (game.shake > 0f) {
            val amp = game.shake * game.shake * 10f * u
            sx = (Math.random().toFloat() - 0.5f) * amp
            sy = (Math.random().toFloat() - 0.5f) * amp
        }

        canvas.save()
        canvas.translate(ox + sx, oy + sy)
        drawBoard(canvas, game)
        drawActors(canvas, game)
        drawParticles(canvas, game)
        canvas.restore()

        drawScanlines(canvas)
        drawBorder(canvas)
        drawHud(canvas, game)
        drawControls(canvas, input)
        drawOverlay(canvas, game)

        if (game.flash > 0f) {
            p.color = game.flashColor
            p.alpha = (game.flash * 80).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, vw, vh, p)
            p.alpha = 255
        }
    }

    private fun blit(canvas: Canvas, bmp: Bitmap, tx: Float, ty: Float, alpha: Int = 255) {
        dst.set(
            (tx * cell).toInt(), (ty * cell).toInt(),
            ((tx + 1f) * cell).toInt(), ((ty + 1f) * cell).toInt()
        )
        pFlat.alpha = alpha
        canvas.drawBitmap(bmp, null, dst, pFlat)
        pFlat.alpha = 255
    }

    private fun drawBoard(canvas: Canvas, game: Game) {
        dst.set(0, 0, (cell * Cfg.W).toInt(), (cell * Cfg.H).toInt())
        pFlat.alpha = 255
        canvas.drawBitmap(Sprites.board, null, dst, pFlat)

        for (y in 0 until Cfg.H) {
            for (x in 0 until Cfg.W) {
                val t = game.terrain(x, y)
                when (t) {
                    Cfg.T_BRICK -> blit(canvas, Sprites.brick, x.toFloat(), y.toFloat())
                    Cfg.T_SOLID -> blit(canvas, Sprites.solid, x.toFloat(), y.toFloat())
                    Cfg.T_LADDER -> blit(canvas, Sprites.ladder, x.toFloat(), y.toFloat())
                    Cfg.T_BAR -> blit(canvas, Sprites.bar, x.toFloat(), y.toFloat())
                    Cfg.T_TRAP -> {
                        val fill = game.trapFill(x, y)
                        val st = (fill * (Sprites.TRAP_STAGES - 1)).toInt()
                            .coerceIn(0, Sprites.TRAP_STAGES - 1)
                        blit(canvas, Sprites.trap[st], x.toFloat(), y.toFloat())
                    }
                    Cfg.T_EXIT -> {
                        if (game.exitActive) {
                            val f = ((game.elapsed * 9f).toInt() % Sprites.EXIT_FRAMES)
                            blit(canvas, Sprites.exitOn[f], x.toFloat(), y.toFloat())
                        } else {
                            blit(canvas, Sprites.exitOff, x.toFloat(), y.toFloat())
                        }
                    }
                }
            }
        }

        // 金块
        val gf = (game.elapsed * 10f).toInt()
        for (y in 0 until Cfg.H) {
            for (x in 0 until Cfg.W) {
                if (!game.hasGold(x, y)) continue
                val bob = sin(game.elapsed * 2.6f + (x + y) * 0.7f) * 0.045f
                val f = (gf + x + y) % Sprites.GOLD_FRAMES
                blit(canvas, Sprites.gold[f], x.toFloat(), y.toFloat() + bob)
            }
        }
    }

    private fun poseOf(a: Actor, game: Game): Int {
        val self = game.terrain(a.gx, a.gy)
        return when {
            a.state == Actor.TRAPPED -> Sprites.POSE_FALL
            a.state == Actor.CLIMB -> Sprites.POSE_CLIMB
            a.state == Actor.FALL -> Sprites.POSE_FALL
            self == Cfg.T_BAR -> Sprites.POSE_HANG
            self == Cfg.T_LADDER -> Sprites.POSE_CLIMB
            a.state == Actor.MOVE_H -> Sprites.POSE_RUN
            else -> Sprites.POSE_STAND
        }
    }

    private fun drawChar(
        canvas: Canvas, frames: Array<Array<Bitmap>>, pose: Int, frame: Int,
        ax: Float, ay: Float, face: Int, alpha: Int
    ) {
        val bmp = frames[pose][frame.coerceIn(0, frames[pose].size - 1)]
        val left = (ax - 0.5f) * cell
        val top = (ay - 0.5f) * cell
        dstF.set(left, top, left + cell, top + cell)
        pFlat.alpha = alpha
        if (face < 0) {
            canvas.save()
            canvas.scale(-1f, 1f, left + cell / 2f, top + cell / 2f)
            canvas.drawBitmap(bmp, null, dstF, pFlat)
            canvas.restore()
        } else {
            canvas.drawBitmap(bmp, null, dstF, pFlat)
        }
        pFlat.alpha = 255
    }

    private fun drawActors(canvas: Canvas, game: Game) {
        // 警察
        for (g in game.guards) {
            val pose = poseOf(g, game)
            val frame = when (pose) {
                Sprites.POSE_RUN -> (g.animT * 9f).toInt()
                Sprites.POSE_CLIMB -> (g.animT * 4f).toInt()
                else -> 0
            }
            val jitter = if (g.state == Actor.TRAPPED) sin(game.elapsed * 20f) * 0.07f else 0f
            val alpha = if (g.state == Actor.TRAPPED) 200 else 255
            drawChar(canvas, Sprites.police, pose, frame, g.x + jitter, g.y,
                if (g.x < game.player.x) -1 else 1, alpha)
        }

        // 小偷
        val pl = game.player
        if (game.state != Game.State.DYING || (game.elapsed * 14).toInt() % 2 == 0) {
            val pose = when {
                game.state == Game.State.DYING -> Sprites.POSE_DIE
                game.digging > 0f -> Sprites.POSE_DIG
                else -> poseOf(pl, game)
            }
            val frame = when (pose) {
                Sprites.POSE_RUN -> (pl.animT * 9f).toInt()
                Sprites.POSE_CLIMB -> (pl.animT * 4f).toInt()
                Sprites.POSE_DIE -> (game.elapsed * 8f).toInt()
                else -> 0
            }
            drawChar(canvas, Sprites.thief, pose, frame, pl.x, pl.y, pl.face, 255)
        }
    }

    private fun drawParticles(canvas: Canvas, game: Game) {
        for (c in game.chips) {
            val a = (c.life / 0.6f).coerceIn(0f, 1f)
            val s = 0.07f * cell
            pFlat.color = c.color
            pFlat.alpha = (a * 240).toInt()
            canvas.drawRect(c.x * cell - s, c.y * cell - s, c.x * cell + s, c.y * cell + s, pFlat)
        }
        pFlat.alpha = 255

        for (t in game.texts) {
            val a = (t.life / 1.2f).coerceIn(0f, 1f)
            pText.color = t.color
            pText.alpha = (a * 255).toInt()
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = cell * 0.62f
            canvas.drawText(t.text, (t.x + 0.5f) * cell, (t.y + 0.5f) * cell, pText)
        }
        pText.alpha = 255
    }

    /** 轻微的 CRT 扫描线，强化老式显示器的味道 */
    private fun drawScanlines(canvas: Canvas) {
        val step = (cell / 8f).toInt().coerceAtLeast(3)
        p.color = Color.argb(22, 0, 0, 0)
        var y = oy
        while (y < oy + cell * Cfg.H) {
            canvas.drawRect(ox, y, ox + cell * Cfg.W, y + 1f, p)
            y += step
        }
    }

    private fun drawBorder(canvas: Canvas) {
        val l = ox
        val t = oy
        val r = ox + cell * Cfg.W
        val b = oy + cell * Cfg.H
        val w = (2f * u).coerceAtLeast(2f)
        p.color = Cfg.C_FRAME
        canvas.drawRect(l - w, t - w, r + w, t, p)
        canvas.drawRect(l - w, b, r + w, b + w, p)
        canvas.drawRect(l - w, t - w, l, b + w, p)
        canvas.drawRect(r, t - w, r + w, b + w, p)
        p.color = Cfg.C_FRAME2
        canvas.drawRect(l - w * 2, t - w * 2, r + w * 2, t - w, p)
        canvas.drawRect(l - w * 2, b + w, r + w * 2, b + w * 2, p)
        canvas.drawRect(l - w * 2, t - w * 2, l - w, b + w * 2, p)
        canvas.drawRect(r + w, t - w * 2, r + w * 2, b + w * 2, p)
    }

    // ------------------------------------------------------------------ HUD

    private fun drawHud(canvas: Canvas, game: Game) {
        p.color = Color.argb(230, 8, 8, 16)
        canvas.drawRect(0f, 0f, vw, hudH, p)
        p.color = Color.argb(160, 74, 96, 160)
        canvas.drawRect(0f, hudH, vw, hudH + 1.6f * u, p)

        val pad = 12f * u
        val ty = hudH * 0.70f
        val icon = hudH * 0.62f

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 15f * u
        pText.color = Cfg.C_CYAN
        canvas.drawText("第 %d 关".format(game.level), pad, ty, pText)
        var lx = pad + pText.measureText("第 %d 关".format(game.level)) + 12f * u

        // 生命（小偷头像）
        for (i in 0 until Cfg.START_LIVES) {
            pFlat.alpha = if (i < game.lives) 255 else 55
            dstF.set(lx, ty - icon, lx + icon, ty)
            canvas.drawBitmap(Sprites.thief[Sprites.POSE_STAND][0], null, dstF, pFlat)
            lx += icon + 3f * u
        }
        pFlat.alpha = 255
        lx += 10f * u

        // 金块
        dstF.set(lx, ty - icon, lx + icon, ty)
        canvas.drawBitmap(Sprites.gold[0], null, dstF, pFlat)
        lx += icon + 4f * u
        pText.color = Cfg.C_GOLD
        canvas.drawText("%d/%d".format(game.goldLeft, game.goldTotal), lx, ty, pText)

        // 分数
        pText.textAlign = Paint.Align.RIGHT
        pText.color = Cfg.C_YELLOW
        canvas.drawText("分数 %06d".format(game.score), vw - pad, ty, pText)

        // 开场倒计时 / 提示
        if (game.readyT > 0f) {
            val n = (game.readyT + 0.999f).toInt()
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 20f * u
            pText.color = Cfg.C_RED
            canvas.drawText("警察还没发现你… %d".format(n), vw / 2f, oy + cell * 1.2f, pText)
        } else if (game.hintT > 0f) {
            val a = (game.hintT / 0.5f).coerceIn(0f, 1f)
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 16f * u
            pText.color = game.flashColor
            pText.alpha = (a * 255).toInt()
            canvas.drawText(game.hintText, vw / 2f, hudH + 22f * u, pText)
            pText.alpha = 255
        }
    }

    // ------------------------------------------------------------------ 控件

    private fun drawControls(canvas: Canvas, input: InputController) {
        if (input.padL <= 0f) return
        drawPad(canvas, input)
        drawDigButton(canvas, input.digLCx, input.digLCy, input.digR, input.holdDigL, -1)
        drawDigButton(canvas, input.digRCx, input.digRCy, input.digR, input.holdDigR, 1)
    }

    /**
     * 大号方向键：整个方框按对角线切成四个互不重叠的梯形区（含四个角），
     * 正中是死区方块 —— 所以任何时候只可能落在一个方向上，不存在重叠误判。
     */
    private fun drawPad(canvas: Canvas, input: InputController) {
        val cx = input.padCx
        val cy = input.padCy
        val h = input.padL * 0.5f
        val a = input.padArm * 0.5f
        val rr = input.padL * 0.10f

        // 触摸区底盘（与判定范围一致：整个圆角方框）
        dstF.set(cx - h, cy - h, cx + h, cy + h)
        p.color = Color.argb(34, 150, 180, 235)
        canvas.drawRoundRect(dstF, rr, rr, p)
        pStroke.color = Color.argb(85, 170, 205, 255)
        pStroke.strokeWidth = 2.4f * u
        canvas.drawRoundRect(dstF, rr, rr, pStroke)

        // 四个方向区（梯形）
        quad(canvas, cx - h, cy - h, cx + h, cy - h, cx + a, cy - a, cx - a, cy - a, input.holdUp)
        quad(canvas, cx + h, cy - h, cx + h, cy + h, cx + a, cy + a, cx + a, cy - a, input.holdRight)
        quad(canvas, cx + h, cy + h, cx - h, cy + h, cx - a, cy + a, cx + a, cy + a, input.holdDown)
        quad(canvas, cx - h, cy + h, cx - h, cy - h, cx - a, cy - a, cx - a, cy + a, input.holdLeft)

        // 中心死区：按这里不触发方向，四臂交汇处因此不会误碰
        dstF.set(cx - a, cy - a, cx + a, cy + a)
        p.color = Color.argb(150, 22, 30, 50)
        canvas.drawRoundRect(dstF, a * 0.34f, a * 0.34f, p)
        pStroke.color = Color.argb(110, 150, 180, 230)
        pStroke.strokeWidth = 1.8f * u
        canvas.drawRoundRect(dstF, a * 0.34f, a * 0.34f, pStroke)

        val d = (h + a) * 0.5f
        drawArrow(canvas, cx, cy - d, "▲", a, input.holdUp)
        drawArrow(canvas, cx + d, cy, "▶", a, input.holdRight)
        drawArrow(canvas, cx, cy + d, "▼", a, input.holdDown)
        drawArrow(canvas, cx - d, cy, "◀", a, input.holdLeft)
    }

    private fun quad(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float, held: Boolean
    ) {
        path.rewind()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        p.color = Color.argb(if (held) 175 else 56, 190, 215, 255)
        canvas.drawPath(path, p)
        pStroke.color = Color.argb(if (held) 245 else 110, 255, 255, 255)
        pStroke.strokeWidth = if (held) 3f * u else 1.8f * u
        canvas.drawPath(path, pStroke)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, arrow: String, armH: Float, held: Boolean) {
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = armH * 1.15f
        pText.color = Color.argb(if (held) 255 else 205, 255, 255, 255)
        canvas.drawText(arrow, cx, cy + armH * 0.42f, pText)
    }

    private fun drawDigButton(canvas: Canvas, cx: Float, cy: Float, r: Float, held: Boolean, dir: Int) {
        // 底部投影，让键位在暗背景上更立体、更容易被手指定位
        p.color = Color.argb(if (held) 40 else 90, 0, 0, 0)
        canvas.drawCircle(cx, cy + r * 0.09f, r * 1.04f, p)

        p.color = Color.argb(if (held) 222 else 132, 232, 132, 40)
        canvas.drawCircle(cx, cy, r, p)
        pStroke.color = Color.argb(if (held) 255 else 200, 255, 214, 150)
        pStroke.strokeWidth = if (held) 3.4f * u else 2.4f * u
        canvas.drawCircle(cx, cy, r, pStroke)

        // 十字镐图标
        pStroke.strokeWidth = r * 0.15f
        pStroke.color = Cfg.C_WHITE
        canvas.drawLine(cx - r * 0.34f * dir, cy + r * 0.30f,
            cx + r * 0.26f * dir, cy - r * 0.18f, pStroke)
        canvas.drawLine(cx - r * 0.06f * dir, cy - r * 0.42f,
            cx + r * 0.42f * dir, cy - r * 0.02f, pStroke)

        pText.textAlign = Paint.Align.CENTER
        pText.textSize = r * 0.30f
        pText.color = Color.argb(240, 255, 255, 255)
        canvas.drawText(if (dir < 0) "◀ 挖左" else "挖右 ▶", cx, cy + r * 0.68f, pText)
    }

    // ------------------------------------------------------------------ 覆盖界面

    private fun drawOverlay(canvas: Canvas, game: Game) {
        if (game.state == Game.State.PLAY || game.state == Game.State.DYING) return
        p.color = Color.argb(205, 4, 5, 12)
        canvas.drawRect(0f, 0f, vw, vh, p)

        val cx = vw / 2f
        val blink = ((game.elapsed * 2).toInt() % 2 == 0)
        pText.textAlign = Paint.Align.CENTER

        when (game.state) {
            Game.State.READY -> {
                bigText(canvas, cx, vh * 0.20f, "警察抓小偷", 30f * u, Cfg.C_YELLOW)
                pText.textSize = 12.5f * u
                pText.color = Cfg.C_CYAN
                canvas.drawText("LODE RUNNER · 中华学习机 Apple II 经典重制", cx, vh * 0.28f, pText)

                val lines = arrayOf(
                    "你是小偷：偷光所有金子，再从顶部闪光的梯子溜走",
                    "警察会一路追你 —— 小偷跑得比警察快，但甩不干净",
                    "左下大方向键移动，整块区域都能按，站在梯子上按上下即可攀爬",
                    "右下「挖左 / 挖右」挖掉脚边的砖，挖出的坑能把警察困住",
                    "坑会回填：警察还在里面就被埋掉；你也别把自己埋进去"
                )
                pText.textSize = 12f * u
                pText.color = Color.argb(225, 226, 232, 244)
                var ly = vh * 0.44f
                for (l in lines) {
                    canvas.drawText(l, cx, ly, pText)
                    ly += 18f * u
                }
                if (blink) {
                    pText.textSize = 15f * u
                    pText.color = Cfg.C_GREEN
                    canvas.drawText("点击屏幕开始", cx, vh * 0.86f, pText)
                }
            }
            Game.State.PAUSED -> {
                bigText(canvas, cx, vh * 0.46f, "已暂停", 26f * u, Cfg.C_CYAN)
                pText.textSize = 14f * u
                pText.color = Cfg.C_WHITE
                canvas.drawText("点击屏幕继续", cx, vh * 0.56f, pText)
            }
            Game.State.LEVEL_CLEAR -> {
                bigText(canvas, cx, vh * 0.34f, "第 ${game.level} 关得手！", 26f * u, Cfg.C_GREEN)
                pText.textSize = 14f * u
                pText.color = Cfg.C_WHITE
                canvas.drawText("剩余生命 ${game.lives}　总分 ${game.score}", cx, vh * 0.46f, pText)
                if (blink) {
                    pText.color = Cfg.C_YELLOW
                    canvas.drawText("点击进入第 ${game.level + 1} 关", cx, vh * 0.60f, pText)
                }
            }
            Game.State.GAME_OVER -> {
                bigText(canvas, cx, vh * 0.36f, "被抓住了", 28f * u, Cfg.C_RED)
                pText.textSize = 14f * u
                pText.color = Cfg.C_WHITE
                canvas.drawText("撑到第 ${game.level} 关　总分 ${game.score}", cx, vh * 0.48f, pText)
                if (blink) {
                    pText.color = Cfg.C_CYAN
                    canvas.drawText("点击重新开始", cx, vh * 0.62f, pText)
                }
            }
            else -> {}
        }
    }

    private fun bigText(canvas: Canvas, x: Float, y: Float, text: String, size: Float, color: Int) {
        pStroke.color = Color.BLACK
        pStroke.strokeWidth = size * 0.22f
        pStroke.textSize = size
        pStroke.textAlign = Paint.Align.CENTER
        pStroke.typeface = Typeface.MONOSPACE
        canvas.drawText(text, x, y, pStroke)
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = size
        pText.color = color
        canvas.drawText(text, x, y, pText)
    }
}
