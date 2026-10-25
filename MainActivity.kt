package com.uman.subwayrun

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(SubwayGameView(this))
    }
}

private class SubwayGameView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = Random(System.currentTimeMillis())
    private var started = false
    private var gameOver = false
    private var lane = 1
    private var score = 0
    private var coins = 0
    private var speed = 0.007f
    private var distance = 0f
    private var jumpTimer = 0f
    private var slideTimer = 0f
    private var spawnTimer = 0f
    private var lastTime = System.nanoTime()
    private var downX = 0f
    private var downY = 0f
    private val obstacles = mutableListOf<Thing>()
    private val pickups = mutableListOf<Thing>()
    private data class Thing(var lane: Int, var z: Float, val kind: Int)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0f, 0.04f)
        lastTime = now
        val w = width.toFloat(); val h = height.toFloat()
        drawWorld(canvas, w, h)
        if (started && !gameOver) update(dt, w, h)
        drawThings(canvas, w, h)
        drawRunner(canvas, w, h)
        drawHud(canvas, w, h)
        if (!started) drawOverlay(canvas, w, h, "UMAN: SUPER SPEED", "SUBWAY RUN TRAIN", "TAP TO RUN")
        else if (gameOver) drawOverlay(canvas, w, h, "GAME OVER", "Score: $score   Coins: $coins", "TAP TO RESTART")
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float, w: Float, h: Float) {
        distance += dt * (0.55f + speed * 20f)
        score += (dt * 12).toInt()
        speed = (speed + dt * 0.000025f).coerceAtMost(0.014f)
        jumpTimer = (jumpTimer - dt).coerceAtLeast(0f)
        slideTimer = (slideTimer - dt).coerceAtLeast(0f)
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            val ln = rng.nextInt(3)
            obstacles.add(Thing(ln, 0.02f, if (rng.nextInt(4) == 0) 1 else 0))
            if (rng.nextBoolean()) pickups.add(Thing(rng.nextInt(3), 0.04f, 2))
            spawnTimer = (0.95f - speed * 18f).coerceAtLeast(0.48f)
        }
        val it = obstacles.iterator()
        while (it.hasNext()) {
            val t = it.next(); t.z += dt * (0.40f + speed * 18f)
            if (t.z > 0.88f) {
                if (t.lane == lane && jumpTimer <= 0f && (t.kind == 0 || slideTimer <= 0f)) { gameOver = true; started = true }
                if (t.z > 1.08f) it.remove()
            }
        }
        val ci = pickups.iterator()
        while (ci.hasNext()) {
            val c = ci.next(); c.z += dt * (0.40f + speed * 18f)
            if (c.z > 0.84f && c.lane == lane) { coins++; score += 5; ci.remove() }
            else if (c.z > 1.08f) ci.remove()
        }
    }

    private fun drawWorld(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.rgb(20, 33, 55))
        p.color = Color.rgb(26, 82, 73); c.drawRect(0f, 0f, w, h * .53f, p)
        // skyline
        for (i in 0..8) {
            val bw = w / 8f; val bh = h * (.08f + ((i * 37) % 13) / 100f)
            p.color = if (i % 2 == 0) Color.rgb(39, 61, 78) else Color.rgb(47, 70, 84)
            c.drawRect(i * bw, h * .42f - bh, i * bw + bw * .78f, h * .43f, p)
        }
        val horizon = h * .39f
        val road = Path().apply { moveTo(w * .42f, horizon); lineTo(w * .58f, horizon); lineTo(w * 1.12f, h); lineTo(-w * .12f, h); close() }
        p.color = Color.rgb(48, 52, 62); c.drawPath(road, p)
        // perspective rails and sleepers
        for (i in 0..13) {
            val z = ((i / 13f + distance) % 1f)
            val y = horizon + (h - horizon) * z * z
            val half = w * (.08f + .62f * z)
            p.color = Color.rgb(95, 101, 111); p.strokeWidth = 2f + z * 5f
            c.drawLine(w / 2f - half * .62f, y, w / 2f + half * .62f, y, p)
        }
        for (i in 0..3) {
            val xTop = w / 2f + (i - 1.5f) * w * .045f
            val xBot = w / 2f + (i - 1.5f) * w * .42f
            p.color = Color.rgb(190, 195, 201); p.strokeWidth = 3f
            c.drawLine(xTop, horizon, xBot, h, p)
        }
        // lane dividers
        p.color = Color.rgb(215, 174, 65); p.strokeWidth = 3f
        c.drawLine(w*.36f, horizon, -w*.05f, h, p); c.drawLine(w*.64f, horizon, w*1.05f, h, p)
    }

    private fun xFor(l: Int, z: Float, w: Float): Float {
        val spread = w * (.055f + .30f * z)
        return w / 2f + (l - 1) * spread
    }
    private fun yFor(z: Float, h: Float) = h * .39f + h * .60f * z * z

    private fun drawThings(c: Canvas, w: Float, h: Float) {
        for (t in obstacles.sortedBy { it.z }) {
            val z = t.z.coerceIn(0f, 1.1f); val x = xFor(t.lane, z, w); val y = yFor(z, h)
            val tw = w * (.035f + .13f*z); val th = h * (.06f + .25f*z)
            if (t.kind == 0) {
                p.color = Color.rgb(200, 65, 43); c.drawRoundRect(x-tw, y-th, x+tw, y, tw*.12f, tw*.12f, p)
                p.color = Color.rgb(255, 193, 7); c.drawRect(x-tw*.75f, y-th*.72f, x+tw*.75f, y-th*.62f, p)
                p.color = Color.rgb(35, 44, 55); c.drawRoundRect(x-tw*.65f, y-th*.48f, x+tw*.65f, y-th*.18f, 5f, 5f, p)
                p.color = Color.rgb(20, 20, 25); c.drawCircle(x-tw*.55f, y-th*.04f, tw*.18f, p); c.drawCircle(x+tw*.55f, y-th*.04f, tw*.18f, p)
            } else {
                p.color = Color.rgb(255, 151, 35); c.drawRoundRect(x-tw, y-th*.45f, x+tw, y, tw*.2f, tw*.2f, p)
            }
        }
        for (coin in pickups) {
            val z = coin.z.coerceIn(0f, 1.1f); val x = xFor(coin.lane, z, w); val y = yFor(z, h) - h*(.025f + .07f*z)
            p.color = Color.rgb(255, 205, 35); c.drawCircle(x, y, w*(.009f + .025f*z), p)
            p.color = Color.rgb(255, 245, 150); p.style = Paint.Style.STROKE; p.strokeWidth = 2f+z*2; c.drawCircle(x, y, w*(.006f+.016f*z), p); p.style = Paint.Style.FILL
        }
    }

    private fun drawRunner(c: Canvas, w: Float, h: Float) {
        val baseY = h * .84f
        val lift = if (jumpTimer > 0f) h * .13f * (jumpTimer / .75f).coerceIn(0f,1f) else 0f
        val x = xFor(lane, .72f, w); val y = baseY - lift
        val scale = w * .001f
        p.color = Color.argb(80, 0, 0, 0); c.drawOval(x-w*.07f, baseY-w*.008f, x+w*.07f, baseY+w*.01f, p)
        p.color = Color.rgb(27, 32, 44)
        if (slideTimer > 0f) {
            c.drawRoundRect(x-w*.075f, y-w*.02f, x+w*.08f, y+w*.025f, w*.02f, w*.02f, p)
            p.color = Color.rgb(255, 190, 130); c.drawCircle(x+w*.07f, y-w*.015f, w*.025f, p)
        } else {
            p.color = Color.rgb(255, 190, 130); c.drawCircle(x, y-h*.085f, w*.035f, p)
            p.color = Color.rgb(25, 115, 235); c.drawRoundRect(x-w*.045f, y-h*.06f, x+w*.045f, y+h*.015f, w*.02f, w*.02f, p)
            p.color = Color.rgb(25, 32, 48); c.drawRoundRect(x-w*.05f, y+h*.005f, x-w*.008f, y+h*.07f, 8f, 8f, p); c.drawRoundRect(x+w*.008f, y+h*.005f, x+w*.05f, y+h*.07f, 8f, 8f, p)
            p.color = Color.rgb(255, 190, 130); c.drawLine(x-w*.035f, y-h*.04f, x-w*.07f, y+h*.005f, p); c.drawLine(x+w*.035f, y-h*.04f, x+w*.07f, y+h*.005f, p)
        }
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(170, 0, 0, 0); c.drawRoundRect(w*.035f, h*.025f, w*.965f, h*.09f, 22f, 22f, p)
        p.color = Color.WHITE; p.textSize = w*.047f; p.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        c.drawText("UMAN", w*.07f, h*.066f, p); p.color = Color.YELLOW
        c.drawText("SCORE  $score", w*.34f, h*.066f, p); c.drawText("COINS $coins", w*.69f, h*.066f, p)
    }

    private fun drawOverlay(c: Canvas, w: Float, h: Float, title: String, subtitle: String, button: String) {
        p.color = Color.argb(205, 3, 8, 18); c.drawRect(0f, 0f, w, h, p)
        p.textAlign = Paint.Align.CENTER; p.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        p.color = Color.rgb(255, 211, 45); p.textSize = w*.085f; c.drawText(title, w/2f, h*.38f, p)
        p.color = Color.WHITE; p.textSize = w*.052f; c.drawText(subtitle, w/2f, h*.44f, p)
        p.color = Color.rgb(255, 145, 25); c.drawRoundRect(w*.19f, h*.51f, w*.81f, h*.59f, 25f, 25f, p)
        p.color = Color.WHITE; p.textSize = w*.05f; c.drawText(button, w/2f, h*.56f, p)
        p.color = Color.LTGRAY; p.textSize = w*.035f
        c.drawText("SWIPE LEFT / RIGHT TO CHANGE LANES", w/2f, h*.68f, p)
        c.drawText("SWIPE UP TO JUMP  •  DOWN TO SLIDE", w/2f, h*.72f, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                val dx = event.x-downX; val dy = event.y-downY
                if (!started || gameOver) {
                    started = true; gameOver = false; lane = 1; score = 0; coins = 0; speed = .007f; distance = 0f; jumpTimer = 0f; slideTimer = 0f; spawnTimer = .6f; obstacles.clear(); pickups.clear()
                } else if (abs(dx) > abs(dy) && abs(dx) > 45f) {
                    lane = (lane + if (dx > 0) 1 else -1).coerceIn(0,2)
                } else if (abs(dy) > 45f) {
                    if (dy < 0) jumpTimer = .75f else slideTimer = .7f
                }
                invalidate(); return true
            }
        }
        return true
    }
}
