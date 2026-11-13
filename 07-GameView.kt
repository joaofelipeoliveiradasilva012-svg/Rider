package com.example.chamarider

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

class GameView(ctx: Context) : SurfaceView(ctx), Runnable {
    private data class Enemy(var lane: Int, var y: Float, val type: Int, var hp: Int = 1, var phase: Float = 0f)
    private data class Shot(var lane: Int, var y: Float, val power: Int = 1)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enemies = mutableListOf<Enemy>()
    private val shots = mutableListOf<Shot>()
    private var loop: Thread? = null
    @Volatile private var running = false
    @Volatile private var move = 0
    @Volatile private var fire = false
    @Volatile private var dash = false
    @Volatile private var restart = false
    private var lane = 1
    private var hp = 5
    private var score = 0
    private var distance = 0f
    private var frame = 0
    private var spawn = 50
    private var invulnerable = 0
    private var dashFrames = 0
    private var gameOver = false
    private var best = 0
    private var touchStartX = 0f
    private var music: MediaPlayer? = null
    private var musicStarted = false
    private val rnd = Random(System.currentTimeMillis())
    private val road get() = width * .72f
    private val left get() = (width - road) / 2f
    private fun laneX(i: Int) = left + road * (i + .5f) / 3f
    private val stage get() = (distance / 650f).toInt() + 1
    private val stageNames = arrayOf("ESTRADA DAS CINZAS", "FLORESTA AMALDIÇOADA", "CIDADE DOS OSSOS", "PORTÕES DO INFERNO", "TRONO DO ABISMO")

    fun resumeGame() {
        if (running) return
        running = true
        loop = Thread(this, "ChamaRiderLoop").also { it.start() }
        startMusic()
    }
    fun pauseGame() {
        running = false
        loop?.join(500)
        loop = null
        music?.pause()
    }
    private fun startMusic() {
        if (musicStarted) { music?.start(); return }
        try {
            val afd = context.assets.openFd("hell_theme.wav")
            music = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                setVolume(.34f, .34f)
                setOnPreparedListener { if (running) it.start() }
                prepareAsync()
            }
            afd.close()
            musicStarted = true
        } catch (_: Exception) { }
    }
    override fun run() {
        while (running) {
            if (!holder.surface.isValid) { Thread.sleep(16); continue }
            update()
            val c = holder.lockCanvas()
            if (c != null) try { drawScene(c) } finally { holder.unlockCanvasAndPost(c) }
            Thread.sleep(16)
        }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        if (e.action == MotionEvent.ACTION_DOWN) {
            touchStartX = x
            if (gameOver) { restart = true; return true }
            val w = width.toFloat(); val h = height.toFloat()
            if (y > h * .77f) {
                when {
                    x < w * .32f -> move = -1
                    x > w * .68f -> fire = true
                    else -> dash = true
                }
            } else if (y > h * .48f && x > w * .67f) fire = true
            else if (y > h * .48f && x < w * .30f) move = -1
            else fire = true
        }
        if (e.action == MotionEvent.ACTION_UP) {
            if (abs(x - touchStartX) > width * .12f) move = if (x > touchStartX) 1 else -1
            fire = false; dash = false
        }
        if (e.action == MotionEvent.ACTION_CANCEL) { fire = false; dash = false }
        return true
    }
    private fun reset() {
        enemies.clear(); shots.clear()
        lane = 1; hp = 5; score = 0; distance = 0f; frame = 0
        spawn = 50; invulnerable = 0; dashFrames = 0; gameOver = false
    }
    private fun update() {
        if (width == 0) return
        if (restart) { restart = false; reset() }
        if (gameOver) return
        frame++
        if (move != 0) { lane = (lane + move).coerceIn(0, 2); move = 0 }
        if (dash) { dash = false; dashFrames = 24; invulnerable = max(invulnerable, 30) }
        if (dashFrames > 0) dashFrames--
        if (invulnerable > 0) invulnerable--
        val speed = height * (.0042f + min(stage, 8) * .00035f)
        distance += speed * .09f
        if (fire && frame % 9 == 0) shots.add(Shot(lane, height * .72f))
        shots.forEach { it.y -= height * .022f }
        shots.removeAll { it.y < -40 }
        if (--spawn <= 0) {
            val type = when {
                stage >= 4 && rnd.nextInt(5) == 0 -> 3
                rnd.nextInt(100) < 27 -> 1
                stage >= 2 && rnd.nextInt(100) < 28 -> 2
                else -> 0
            }
            enemies.add(Enemy(rnd.nextInt(3), -height * .08f, type, if (type == 3) 3 else 1, rnd.nextFloat() * 6))
            spawn = (54 - min(24, stage * 3)) + rnd.nextInt(18)
        }
        val it = enemies.iterator()
        while (it.hasNext()) {
            val en = it.next()
            en.y += speed * if (en.type == 1) 1.25f else if (en.type == 2) .78f else 1f
            en.phase += .08f
            if (en.type == 2) en.lane = ((en.lane + if (sin(en.phase) > .97) 1 else 0).coerceIn(0,2))
            if (en.y > height + 90) { it.remove(); continue }
            if (en.lane == lane && abs(en.y - height * .79f) < height * .065f && invulnerable == 0) {
                it.remove(); hp--; invulnerable = 65
                if (hp <= 0) { gameOver = true; best = max(best, score) }
            }
        }
        val si = shots.iterator()
        while (si.hasNext()) {
            val s = si.next()
            val hit = enemies.firstOrNull { it.lane == s.lane && abs(it.y - s.y) < height * .055f }
            if (hit != null) {
                si.remove(); hit.hp--
                if (hit.hp <= 0) { enemies.remove(hit); score += when(hit.type) { 3 -> 50; 2 -> 20; 1 -> 15; else -> 10 } }
            }
        }
        if (frame % 5 == 0) score++
    }
    private fun color(c: Int) { p.color = c; p.style = Paint.Style.FILL; p.strokeWidth = 2f }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int = Color.WHITE, align: Paint.Align = Paint.Align.LEFT) {
        p.color = col; p.textSize = size; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); p.textAlign = align
        c.drawText(s, x, y, p); p.textAlign = Paint.Align.LEFT
    }
    private fun drawScene(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(10, 9, 16))
        // Infernal sky and distant volcanic silhouettes
        val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f,0f,0f,h*.65f, Color.rgb(53,17,25), Color.rgb(13,10,18), Shader.TileMode.CLAMP) }
        c.drawRect(0f,0f,w,h,sky)
        color(Color.rgb(35,20,27))
        for (i in 0..7) {
            val x = i*w/7f
            val peak = h*(.24f + (i%3)*.035f)
            val path = Path().apply { moveTo(x-w*.08f,h*.55f); lineTo(x+w*.03f,peak); lineTo(x+w*.13f,h*.55f); close() }
            c.drawPath(path,p)
        }
        // Lava cracks / roadside
        color(Color.rgb(105,35,22))
        for (i in 0..5) {
            val x = i*w/5f
            c.drawOval(x-7,h*.56f,x+7,h*.68f,p)
        }
        color(Color.rgb(29,28,34)); c.drawRect(left,0f,left+road,h,p)
        color(Color.rgb(75,48,43)); c.drawRect(left,0f,left+8,h,p); c.drawRect(left+road-8,0f,left+road,h,p)
        color(Color.rgb(115,73,52))
        val dashY = (frame * (h*.009f)) % (h*.13f)
        var y = -h*.13f + dashY
        while (y < h) {
            c.drawRoundRect(width/2f-3,y,width/2f+3,y+h*.055f,4f,4f,p); y += h*.13f
        }
        // stage banner and HUD
        color(Color.argb(180,0,0,0)); c.drawRoundRect(18f,18f,w-18f,h*.105f,18f,18f,p)
        text(c,"CHAMA RIDER",w*.05f,h*.055f,w*.052f,Color.rgb(255,180,100))
        text(c,"FASE $stage  •  ${stageNames[(stage-1).coerceAtMost(stageNames.lastIndex)]}",w*.05f,h*.095f,w*.033f,Color.WHITE)
        text(c,"♥ $hp     ${score} pts",w*.95f,h*.06f,w*.037f,Color.WHITE,Paint.Align.RIGHT)
        text(c,"DISTÂNCIA ${distance.toInt()} m",w*.95f,h*.095f,w*.028f,Color.rgb(255,190,130),Paint.Align.RIGHT)
        // enemies
        enemies.forEach { en ->
            val x = laneX(en.lane); val r = w * if(en.type==3) .075f else .048f
            val glow = when(en.type) { 1 -> Color.rgb(255,90,20); 2 -> Color.rgb(70,180,210); 3 -> Color.rgb(170,25,45); else -> Color.rgb(135,35,165) }
            p.setShadowLayer(18f,0f,0f,glow); color(glow); c.drawCircle(x,en.y,r,p); p.clearShadowLayer()
            color(Color.rgb(25,12,20)); c.drawOval(x-r*.55f,en.y-r*.15f,x-r*.12f,en.y+r*.22f,p); c.drawOval(x+r*.12f,en.y-r*.15f,x+r*.55f,en.y+r*.22f,p)
            color(Color.rgb(255,220,120)); c.drawCircle(x-r*.28f,en.y, r*.09f,p); c.drawCircle(x+r*.28f,en.y,r*.09f,p)
            if(en.type==3) { text(c,"${en.hp}",x,en.y-r-8f,w*.035f,Color.WHITE,Paint.Align.CENTER) }
            if(en.type==2) { color(Color.rgb(90,160,190)); c.drawLine(x-r,en.y-r,x-r*1.7f,en.y-r*1.8f,p); c.drawLine(x+r,en.y-r,x+r*1.7f,en.y-r*1.8f,p) }
        }
        // hellfire projectiles
        shots.forEach { s ->
            p.setShadowLayer(16f,0f,0f,Color.YELLOW); color(Color.rgb(255,170,35)); c.drawCircle(laneX(s.lane),s.y,w*.022f,p); p.clearShadowLayer()
            color(Color.rgb(255,245,160)); c.drawCircle(laneX(s.lane),s.y,w*.009f,p)
        }
        // Motorcycle and skeletal rider
        if(invulnerable % 8 < 5 || invulnerable == 0) {
            val x = laneX(lane); val by = h*.79f
            if(dashFrames>0) { p.setShadowLayer(25f,0f,0f,Color.CYAN); color(Color.argb(100,80,210,255)); c.drawOval(x-w*.09f,by-h*.11f,x+w*.09f,by+h*.09f,p); p.clearShadowLayer() }
            // wheels and bike frame
            color(Color.BLACK); p.style=Paint.Style.STROKE; p.strokeWidth=w*.018f
            c.drawCircle(x,by+h*.035f,w*.035f,p); c.drawCircle(x,by-h*.055f,w*.029f,p)
            p.style=Paint.Style.FILL
            color(Color.rgb(150,25,20)); c.drawLine(x,by-h*.05f,x-w*.035f,by+h*.025f,p); c.drawLine(x,by-h*.05f,x+w*.04f,by+h*.02f,p)
            color(Color.rgb(255,95,20)); c.drawOval(x-w*.018f,by+h*.04f,x+w*.018f,by+h*.12f,p)
            // skeleton torso, skull, helmet horns
            color(Color.rgb(190,185,165)); c.drawRoundRect(x-w*.027f,by-h*.09f,x+w*.027f,by-h*.015f,8f,8f,p)
            color(Color.rgb(220,215,190)); c.drawCircle(x,by-h*.13f,w*.035f,p)
            color(Color.rgb(25,15,18)); c.drawCircle(x-w*.014f,by-h*.135f,w*.008f,p); c.drawCircle(x+w*.014f,by-h*.135f,w*.008f,p)
            color(Color.rgb(160,25,20)); c.drawLine(x-w*.02f,by-h*.16f,x-w*.05f,by-h*.20f,p); c.drawLine(x+w*.02f,by-h*.16f,x+w*.05f,by-h*.20f,p)
            color(Color.rgb(190,185,165)); c.drawLine(x-w*.02f,by-h*.07f,x-w*.06f,by-h*.025f,p); c.drawLine(x+w*.02f,by-h*.07f,x+w*.06f,by-h*.025f,p)
        }
        // Touch controls
        drawButton(c,w*.16f,h*.91f,"◀",Color.rgb(60,55,65))
        drawButton(c,w*.50f,h*.91f,"DASH",Color.rgb(15,100,135))
        drawButton(c,w*.84f,h*.91f,"FIRE",Color.rgb(145,45,20))
        if(gameOver) {
            color(Color.argb(210,0,0,0)); c.drawRect(0f,0f,w,h,p)
            text(c,"FIM DE JOGO",w/2,h*.43f,w*.105f,Color.rgb(255,100,65),Paint.Align.CENTER)
            text(c,"Pontuação: $score  •  Recorde: $best",w/2,h*.50f,w*.05f,Color.WHITE,Paint.Align.CENTER)
            text(c,"Toque para tentar novamente",w/2,h*.58f,w*.045f,Color.rgb(255,205,150),Paint.Align.CENTER)
        }
    }
    private fun drawButton(c: Canvas,x:Float,y:Float,label:String,col:Int) {
        val w=width.toFloat(); val r=w*.105f
        color(Color.argb(175,0,0,0)); c.drawCircle(x,y,r+5,p)
        color(col); c.drawCircle(x,y,r,p)
        p.style=Paint.Style.STROKE; p.strokeWidth=3f; p.color=Color.argb(180,255,220,180); c.drawCircle(x,y,r,p); p.style=Paint.Style.FILL
        text(c,label,x,y+width*.018f,width*.042f,Color.WHITE,Paint.Align.CENTER)
    }
}
