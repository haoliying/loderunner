package com.haoly.loderunner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val game = Game()
    private val input = InputController()
    private val renderer = Renderer()

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var lastTime = 0L

    private val vibrator: android.os.Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.getSystemService(android.os.Vibrator::class.java) else null

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
        game.onEvent = { ev ->
            when (ev) {
                Game.EV_GOLD -> buzz(35)
                Game.EV_DIG -> buzz(22)
                Game.EV_DIE -> buzz(300)
                Game.EV_CLEAR -> buzz(150)
                Game.EV_EXIT -> buzz(120)
            }
        }
    }

    /** 关卡区居中，两侧留白专门放大号控件，避免手指够到关卡格子 */
    private fun relayout() {
        renderer.resize(width, height)
        input.layout(width.toFloat(), height.toFloat(),
            renderer.boardLeft, renderer.boardRight, renderer.hudBottom,
            resources.displayMetrics.density)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        relayout()
        lastTime = System.nanoTime()
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        relayout()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    fun resumeGame() {
        if (width > 0 && height > 0) relayout()
        startLoop()
    }

    fun pauseGame() {
        stopLoop()
        input.releaseAll()
        game.pause()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastTime = System.nanoTime()
        thread = Thread(this, "lr-loop").apply { start() }
    }

    private fun stopLoop() {
        running = false
        val t = thread
        thread = null
        if (t != null) {
            try { t.join(400) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now
            if (dt > 0.08f) dt = 0.08f
            if (dt < 0.0005f) dt = 0.0005f

            step(dt)

            val spent = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - spent
            if (sleep > 1L) {
                try { Thread.sleep(sleep) } catch (e: InterruptedException) { return }
            }
        }
    }

    private fun step(dt: Float) {
        val st = input.consume()
        game.update(dt, st)
        input.clearEdges()

        val canvas = holder.lockCanvas() ?: return
        try {
            synchronized(holder) { renderer.draw(canvas, game, input) }
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (e: IllegalStateException) { /* ignore */ }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (game.state != Game.State.PLAY) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) game.tap()
            return true
        }
        return input.onTouch(event)
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_BACK) return false
        if (game.state != Game.State.PLAY) {
            game.tap()
            return true
        }
        return input.onKeyDown(code)
    }

    override fun onKeyUp(code: Int, event: KeyEvent): Boolean = input.onKeyUp(code)

    fun onBack(): Boolean {
        if (game.state == Game.State.PLAY) {
            game.pause()
            return true
        }
        return false
    }

    private fun buzz(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // 忽略：无震动权限或硬件
        }
    }
}
