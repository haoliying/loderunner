package com.haoly.loderunner

import java.util.Random
import kotlin.math.sqrt

class Actor {
    companion object {
        const val STAND = 0
        const val MOVE_H = 1
        const val CLIMB = 2
        const val FALL = 3
        const val TRAPPED = 4
    }

    var gx = 0
    var gy = 0
    var x = 0f
    var y = 0f
    var tx = 0
    var ty = 0
    var state = STAND
    var face = 1
    var animT = 0f
    var thinkT = 0f
    var trappedT = 0f
    var fallThrough = false
}

class Chip(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
class FloatText(var x: Float, var y: Float, var life: Float, val text: String, val color: Int)

/**
 * Lode Runner 核心：格子地形 + 逐格平滑步进的移动模型。
 * 规则尽量还原 Apple II 原版——不能跳，只能走 / 爬梯 / 挂横杆 / 下落 / 挖洞。
 */
class Game {

    enum class State { READY, PLAY, DYING, LEVEL_CLEAR, GAME_OVER, PAUSED }

    companion object {
        const val EV_GOLD = 0
        const val EV_DIG = 1
        const val EV_DIE = 2
        const val EV_CLEAR = 3
        const val EV_EXIT = 4
    }

    var state = State.READY
    var level = 1
    var score = 0
    var lives = Cfg.START_LIVES
    var goldLeft = 0
    var goldTotal = 0
    var exitActive = false
    var elapsed = 0f
    var dieT = 0f
    var shake = 0f
    var flash = 0f
    var flashColor = Cfg.C_RED
    var hintText = ""
    var hintT = 0f
    var digCd = 0f
    var digging = 0f          // >0 表示正在做挖掘动作，渲染时切换到挖掘姿态
    var readyT = 0f           // 开场 / 复活后警察冻结的倒计时

    var onEvent: ((Int) -> Unit)? = null

    val player = Actor()
    val guards = ArrayList<Actor>()
    val chips = ArrayList<Chip>()
    val texts = ArrayList<FloatText>()

    private val rng = Random()
    private val grid = IntArray(Cfg.W * Cfg.H)
    private val gold = BooleanArray(Cfg.W * Cfg.H)
    private val trapT = FloatArray(Cfg.W * Cfg.H)
    private var baseGrid = IntArray(Cfg.W * Cfg.H)
    private var baseGold = BooleanArray(Cfg.W * Cfg.H)
    private val guardSpawns = ArrayList<Pair<Int, Int>>()
    private var spawnX = 2
    private var spawnY = 14
    private val guardDist = IntArray(Cfg.W * Cfg.H)
    private var distT = 0f

    // ------------------------------------------------------------ 地形查询

    private fun idx(x: Int, y: Int) = y * Cfg.W + x
    fun terrain(x: Int, y: Int): Int =
        if (x in 0 until Cfg.W && y in 0 until Cfg.H) grid[idx(x, y)] else Cfg.T_SOLID

    private fun canEnter(x: Int, y: Int): Boolean {
        if (x !in 0 until Cfg.W || y !in 0 until Cfg.H) return false
        return when (grid[idx(x, y)]) {
            Cfg.T_BRICK, Cfg.T_SOLID -> false
            Cfg.T_EXIT -> exitActive      // 收齐金子前，出口梯还没出现
            else -> true
        }
    }

    /** 是否踩得住：所在格是梯子/横杆，或脚下是砖/钢/梯子/横杆 */
    private fun standingOn(a: Actor): Boolean {
        when (terrain(a.gx, a.gy)) {
            Cfg.T_LADDER, Cfg.T_BAR -> return true
        }
        return when (terrain(a.gx, a.gy + 1)) {
            Cfg.T_BRICK, Cfg.T_SOLID, Cfg.T_LADDER, Cfg.T_BAR -> true
            else -> false
        }
    }

    fun hasGold(x: Int, y: Int): Boolean = gold[idx(x, y)]

    /** 坑的回填进度：0 刚挖开，1 即将合拢 */
    fun trapFill(x: Int, y: Int): Float {
        val i = idx(x, y)
        if (grid[i] != Cfg.T_TRAP) return 0f
        return (1f - trapT[i] / Cfg.TRAP_TIME).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------ 关卡

    fun loadLevel(n: Int) {
        level = n
        guardSpawns.clear()
        val rows = Levels.DATA[(n - 1) % Levels.DATA.size]
        for (y in 0 until Cfg.H) {
            val row = rows[y]
            for (x in 0 until Cfg.W) {
                val ch = row[x]
                val t = when (ch) {
                    '#' -> Cfg.T_BRICK
                    '=' -> Cfg.T_SOLID
                    'H' -> Cfg.T_LADDER
                    '-' -> Cfg.T_BAR
                    'X' -> Cfg.T_EXIT
                    else -> Cfg.T_EMPTY
                }
                grid[idx(x, y)] = t
                gold[idx(x, y)] = (ch == 'G')
                when (ch) {
                    'P' -> { spawnX = x; spawnY = y }
                    'E' -> guardSpawns.add(Pair(x, y))
                }
            }
        }
        baseGrid = grid.copyOf()
        baseGold = gold.copyOf()
        // 关卡数超过预设后循环复用布局，并追加警察保持压力
        val extra = ((n - Levels.DATA.size) / 1).coerceAtLeast(0)
        if (extra > 0) {
            val spots = ArrayList<Pair<Int, Int>>()
            for (y in 2 until Cfg.H - 1) for (x in 1 until Cfg.W - 1) {
                if (grid[idx(x, y)] == Cfg.T_EMPTY && !gold[idx(x, y)] &&
                    terrain(x, y + 1) in intArrayOf(Cfg.T_BRICK, Cfg.T_SOLID)
                ) spots.add(Pair(x, y))
            }
            spots.shuffle(rng)
            for (i in 0 until minOf(extra, 3, spots.size)) guardSpawns.add(spots[i])
        }
        goldTotal = gold.count { it }
        resetActors(Cfg.READY_TIME)
        exitActive = false
        elapsed = 0f
        state = State.PLAY
        showHint("第 $level 关 · 偷走 $goldTotal 块金子", Cfg.C_YELLOW)
    }

    private fun resetActors(grace: Float = 1.5f) {
        for (y in 0 until Cfg.H) for (x in 0 until Cfg.W) {
            grid[idx(x, y)] = baseGrid[idx(x, y)]
            gold[idx(x, y)] = baseGold[idx(x, y)]
            trapT[idx(x, y)] = 0f
        }
        goldLeft = goldTotal
        guards.clear()
        for (s in guardSpawns) {
            val g = Actor()
            g.gx = s.first
            g.gy = s.second
            g.x = s.first + 0.5f
            g.y = s.second + 0.5f
            g.thinkT = rng.nextFloat() * 0.2f
            guards.add(g)
        }
        player.gx = spawnX
        player.gy = spawnY
        player.x = spawnX + 0.5f
        player.y = spawnY + 0.5f
        player.state = Actor.STAND
        player.face = 1
        player.animT = 0f
        digCd = 0f
        digging = 0f
        readyT = grace
        exitActive = false
        distT = 0f
        guardDist.fill(-1)
    }

    fun tap() {
        when (state) {
            State.READY -> { level = 1; score = 0; lives = Cfg.START_LIVES; loadLevel(1) }
            State.PAUSED -> state = State.PLAY
            State.LEVEL_CLEAR -> loadLevel(level + 1)
            State.GAME_OVER -> { level = 1; score = 0; lives = Cfg.START_LIVES; loadLevel(1) }
            else -> {}
        }
    }

    fun pause() {
        if (state == State.PLAY) state = State.PAUSED
    }

    private fun showHint(t: String, c: Int) {
        hintText = t
        hintT = 2.2f
        flashColor = c
    }

    // ------------------------------------------------------------ 主循环

    fun update(dt: Float, inp: InputState) {
        elapsed += dt
        shake = (shake - dt * 3f).coerceAtLeast(0f)
        flash = (flash - dt * 2.4f).coerceAtLeast(0f)
        if (hintT > 0f) hintT -= dt
        if (digCd > 0f) digCd -= dt

        updateChips(dt)

        when (state) {
            State.PLAY -> {
                readyT = (readyT - dt).coerceAtLeast(0f)
                digging = (digging - dt).coerceAtLeast(0f)
                updateTraps(dt)
                handleInput(inp)
                updateActor(player, dt, true)
                if (readyT <= 0f) updateGuards(dt)   // 开场给玩家反应时间
                checkCaught()
            }
            State.DYING -> {
                dieT -= dt
                updateActor(player, dt, true)
                if (dieT <= 0f) {
                    if (lives > 0) {
                        resetActors()
                        state = State.PLAY
                    } else {
                        state = State.GAME_OVER
                    }
                }
            }
            else -> {}
        }
    }

    private fun updateChips(dt: Float) {
        var i = chips.size - 1
        while (i >= 0) {
            val c = chips[i]
            c.life -= dt
            c.x += c.vx * dt
            c.y += c.vy * dt
            c.vy += 9f * dt
            if (c.life <= 0f) chips.removeAt(i)
            i--
        }
        i = texts.size - 1
        while (i >= 0) {
            texts[i].life -= dt
            texts[i].y -= dt * 1.4f
            if (texts[i].life <= 0f) texts.removeAt(i)
            i--
        }
    }

    private fun updateTraps(dt: Float) {
        for (i in grid.indices) {
            if (grid[i] != Cfg.T_TRAP) continue
            trapT[i] -= dt
            if (trapT[i] <= 0f) {
                val x = i % Cfg.W
                val y = i / Cfg.W
                grid[i] = Cfg.T_BRICK
                for (g in guards) {
                    if (g.state == Actor.TRAPPED && g.gx == x && g.gy == y) {
                        respawnGuard(g)
                        texts.add(FloatText(x.toFloat(), y.toFloat(), 1.0f, "埋掉一个！", Cfg.C_GREEN))
                        score += 150
                    }
                }
                // 小偷自己还待在坑里 → 被埋
                if (player.gx == x && player.gy == y) {
                    die("被埋在自己挖的坑里")
                }
                for (k in 0..5) {
                    chips.add(Chip(x + 0.5f, y + 0.5f, (rng.nextFloat() - 0.5f) * 2f,
                        -rng.nextFloat() * 2f, 0.35f, Cfg.C_BRICK_LT))
                }
            }
        }
    }

    // ------------------------------------------------------------ 玩家

    private fun handleInput(inp: InputState) {
        val p = player
        if (p.state != Actor.STAND) return

        if (inp.digL) dig(-1)
        else if (inp.digR) dig(1)

        if (p.state != Actor.STAND) return

        when {
            inp.left -> { p.face = -1; tryMoveH(p, -1) }
            inp.right -> { p.face = 1; tryMoveH(p, 1) }
            inp.up -> tryClimb(p, -1)
            inp.down -> tryClimb(p, 1)
        }
    }

    private fun tryMoveH(a: Actor, dir: Int) {
        val nx = a.gx + dir
        if (!canEnter(nx, a.gy)) {
            a.face = dir
            return
        }
        a.tx = nx
        a.ty = a.gy
        a.state = Actor.MOVE_H
    }

    private fun tryClimb(a: Actor, dir: Int) {
        if (dir < 0) {
            val up = terrain(a.gx, a.gy - 1)
            if (up == Cfg.T_LADDER || (up == Cfg.T_EXIT && exitActive)) {
                a.tx = a.gx
                a.ty = a.gy - 1
                a.state = Actor.CLIMB
            }
        } else {
            val dn = terrain(a.gx, a.gy + 1)
            if (dn == Cfg.T_LADDER || (dn == Cfg.T_EXIT && exitActive)) {
                a.tx = a.gx
                a.ty = a.gy + 1
                a.state = Actor.CLIMB
            } else if (dn == Cfg.T_BAR || dn == Cfg.T_EMPTY || dn == Cfg.T_TRAP) {
                startFall(a, dn == Cfg.T_BAR)
            }
        }
    }

    private fun startFall(a: Actor, through: Boolean) {
        if (!canEnter(a.gx, a.gy + 1)) return
        a.tx = a.gx
        a.ty = a.gy + 1
        a.fallThrough = through
        a.state = Actor.FALL
    }

    private fun dig(dir: Int) {
        val p = player
        p.face = dir
        if (digCd > 0f) return
        val tx = p.gx + dir
        val ty = p.gy + 1
        if (terrain(tx, ty) == Cfg.T_BRICK) {
            grid[idx(tx, ty)] = Cfg.T_TRAP
            trapT[idx(tx, ty)] = Cfg.TRAP_TIME
            digCd = Cfg.DIG_CD
            digging = 0.26f
            for (k in 0..9) {
                chips.add(Chip(tx + 0.5f, ty + 0.3f, (rng.nextFloat() - 0.5f) * 3f,
                    -rng.nextFloat() * 3f, 0.4f, Cfg.C_DIRT))
            }
            onEvent?.invoke(EV_DIG)
        } else {
            digCd = 0.14f
            digging = 0.18f
        }
    }

    // ------------------------------------------------------------ 移动

    private fun updateActor(a: Actor, dt: Float, isPlayer: Boolean) {
        a.animT += dt
        when (a.state) {
            Actor.MOVE_H, Actor.CLIMB, Actor.FALL -> {
                val spd = when (a.state) {
                    Actor.MOVE_H -> if (isPlayer) Cfg.P_SPEED else Cfg.G_SPEED
                    Actor.CLIMB -> if (isPlayer) Cfg.P_CLIMB else Cfg.G_CLIMB
                    else -> if (isPlayer) Cfg.P_FALL else Cfg.G_FALL
                }
                val tgx = a.tx + 0.5f
                val tgy = a.ty + 0.5f
                val dx = tgx - a.x
                val dy = tgy - a.y
                val d = sqrt(dx * dx + dy * dy)
                val step = spd * dt
                if (d <= step || d < 0.0001f) {
                    a.x = tgx
                    a.y = tgy
                    a.gx = a.tx
                    a.gy = a.ty
                    a.state = Actor.STAND
                    onArrive(a, isPlayer)
                } else {
                    a.x += dx / d * step
                    a.y += dy / d * step
                }
            }
            Actor.STAND -> {
                if (!standingOn(a)) startFall(a, false)
            }
            Actor.TRAPPED -> {
                a.trappedT -= dt
            }
        }
    }

    private fun onArrive(a: Actor, isPlayer: Boolean) {
        if (isPlayer) {
            val i = idx(a.gx, a.gy)
            if (gold[i]) {
                gold[i] = false
                goldLeft--
                score += Cfg.GOLD_SCORE
                texts.add(FloatText(a.gx.toFloat(), a.gy.toFloat(), 0.9f, "+${Cfg.GOLD_SCORE}", Cfg.C_YELLOW))
                for (k in 0..7) {
                    chips.add(Chip(a.x, a.y, (rng.nextFloat() - 0.5f) * 3f, -rng.nextFloat() * 2.5f,
                        0.45f, Cfg.C_GOLD))
                }
                onEvent?.invoke(EV_GOLD)
                if (goldLeft <= 0 && !exitActive) {
                    exitActive = true
                    showHint("金子偷完了！快爬上顶部闪光的梯子", Cfg.C_GREEN)
                    flash = 0.6f
                    flashColor = Cfg.C_GREEN
                    onEvent?.invoke(EV_EXIT)
                }
            }
            // 站在坑里是允许的（挖穿地板下一层是核心玩法），
            // 只有坑回填时人还在里面才会被埋 —— 见 updateTraps
            if (exitActive && a.gy <= 1) {
                levelClear()
                return
            }
        } else {
            if (terrain(a.gx, a.gy) == Cfg.T_TRAP) {
                a.state = Actor.TRAPPED
                a.trappedT = trapT[idx(a.gx, a.gy)]
            }
        }
        if (a.state == Actor.STAND && !standingOn(a)) startFall(a, false)
    }

    // ------------------------------------------------------------ 警察

    private fun updateGuards(dt: Float) {
        distT -= dt
        if (distT <= 0f) {
            distT = 0.2f
            buildGuardField()
        }
        for (g in guards) {
            if (g.state == Actor.TRAPPED) {
                g.trappedT -= dt
                continue
            }
            if (g.state == Actor.STAND) {
                g.thinkT -= dt
                if (g.thinkT <= 0f) {
                    g.thinkT = 0.18f
                    guardStep(g)
                }
            }
            updateActor(g, dt, false)
        }
    }

    private fun buildGuardField() {
        guardDist.fill(-1)
        val n = Cfg.W * Cfg.H
        val q = IntArray(n)
        var head = 0
        var tail = 0
        val s = idx(player.gx, player.gy)
        guardDist[s] = 0
        q[tail++] = s
        while (head < tail) {
            val cur = q[head++]
            val cx = cur % Cfg.W
            val cy = cur / Cfg.W
            val nd = guardDist[cur] + 1
            if (cx > 0) {
                val i = cur - 1
                if (canEnter(cx - 1, cy) && guardDist[i] == -1) { guardDist[i] = nd; q[tail++] = i }
            }
            if (cx < Cfg.W - 1) {
                val i = cur + 1
                if (canEnter(cx + 1, cy) && guardDist[i] == -1) { guardDist[i] = nd; q[tail++] = i }
            }
            if (cy > 0) {
                val i = cur - Cfg.W
                if (canEnter(cx, cy - 1) && guardDist[i] == -1) { guardDist[i] = nd; q[tail++] = i }
            }
            if (cy < Cfg.H - 1) {
                val i = cur + Cfg.W
                if (canEnter(cx, cy + 1) && guardDist[i] == -1) { guardDist[i] = nd; q[tail++] = i }
            }
        }
    }

    private fun guardStep(g: Actor) {
        var bx = g.gx
        var by = g.gy
        var bd = Int.MAX_VALUE
        val cur = guardDist[idx(g.gx, g.gy)]
        val cands = intArrayOf(
            idx(g.gx - 1, g.gy), idx(g.gx + 1, g.gy),
            idx(g.gx, g.gy - 1), idx(g.gx, g.gy + 1)
        )
        val valid = booleanArrayOf(
            g.gx > 0 && canEnter(g.gx - 1, g.gy),
            g.gx < Cfg.W - 1 && canEnter(g.gx + 1, g.gy),
            g.gy > 0 && canEnter(g.gx, g.gy - 1),
            g.gy < Cfg.H - 1 && canEnter(g.gx, g.gy + 1)
        )
        for (k in 0..3) {
            if (!valid[k]) continue
            val d = guardDist[cands[k]]
            if (d >= 0 && d < bd) {
                bd = d
                bx = cands[k] % Cfg.W
                by = cands[k] / Cfg.W
            }
        }
        if (bd >= cur && cur >= 0) {
            // 走不动或没收益：随便晃一下，避免杵在原地
            val dir = if (rng.nextBoolean()) 1 else -1
            if (canEnter(g.gx + dir, g.gy)) tryMoveH(g, dir)
            return
        }
        if (by < g.gy) {
            val up = terrain(g.gx, g.gy - 1)
            if (up == Cfg.T_LADDER || terrain(g.gx, g.gy) == Cfg.T_LADDER) {
                g.tx = g.gx
                g.ty = g.gy - 1
                g.state = Actor.CLIMB
            } else if (bx != g.gx) tryMoveH(g, bx - g.gx)
        } else if (by > g.gy) {
            val dn = terrain(g.gx, g.gy + 1)
            if (dn == Cfg.T_LADDER) {
                g.tx = g.gx
                g.ty = g.gy + 1
                g.state = Actor.CLIMB
            } else if (canEnter(g.gx, g.gy + 1)) startFall(g, false)
            else if (bx != g.gx) tryMoveH(g, bx - g.gx)
        } else {
            tryMoveH(g, bx - g.gx)
        }
    }

    private fun respawnGuard(g: Actor) {
        // 从关卡顶部掉回来
        var x = spawnX
        for (k in 0 until 40) {
            val cx = rng.nextInt(Cfg.W - 2) + 1
            if (canEnter(cx, 1) && terrain(cx, 1) != Cfg.T_SOLID) {
                x = cx
                break
            }
        }
        g.gx = x
        g.gy = 1
        g.x = x + 0.5f
        g.y = 1.5f
        g.state = Actor.STAND
        g.trappedT = 0f
        g.thinkT = 0.3f
    }

    // ------------------------------------------------------------ 生死

    private fun checkCaught() {
        for (g in guards) {
            if (g.state == Actor.TRAPPED) continue
            val dx = g.x - player.x
            val dy = g.y - player.y
            if (dx * dx + dy * dy < Cfg.HIT_DIST * Cfg.HIT_DIST) {
                die("被警察抓住了")
                return
            }
        }
    }

    private fun die(reason: String) {
        if (state != State.PLAY) return
        lives--
        state = State.DYING
        dieT = 1.1f
        shake = 1f
        flash = 1f
        flashColor = Cfg.C_RED
        hintText = reason
        hintT = 1.4f
        for (k in 0..13) {
            chips.add(Chip(player.x, player.y, (rng.nextFloat() - 0.5f) * 5f,
                -rng.nextFloat() * 4f, 0.6f, Cfg.C_WHITE))
        }
        onEvent?.invoke(EV_DIE)
    }

    private fun levelClear() {
        val bonus = Cfg.CLEAR_SCORE + lives * Cfg.LIVE_BONUS
        score += bonus
        state = State.LEVEL_CLEAR
        flash = 0.7f
        flashColor = Cfg.C_GREEN
        texts.add(FloatText(player.x, player.y, 1.2f, "+$bonus", Cfg.C_GREEN))
        onEvent?.invoke(EV_CLEAR)
    }

    fun isRunning() = state == State.PLAY
}
