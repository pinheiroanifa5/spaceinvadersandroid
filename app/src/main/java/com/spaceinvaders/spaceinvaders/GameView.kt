package com.spaceinvaders.spaceinvaders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.AudioTrack
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    companion object {
        const val LOGICAL_WIDTH = 224
        const val LOGICAL_HEIGHT = 256
        private const val PLAYER_Y = 222f
        private const val PLAYER_SPEED = 2.5f
        private const val PLAYER_WIDTH = 10
        private const val PLAYER_HEIGHT = 8
        private const val SHOOT_SPEED = 4.5f
        private const val FLOOR_Y = 212f
    }

    private enum class Screen {
        SPLASH,
        TITLE,
        GAME,
        GAME_OVER,
        HIGH_SCORE
    }

    private data class Projectile(
        var x: Float,
        var y: Float,
        var vy: Float,
        val isPlayer: Boolean
    )

    private data class Alien(
        var x: Float,
        var y: Float,
        val points: Int,
        val type: Int,
        var alive: Boolean = true,
        var frame: Int = 0
    )

    private data class Barrier(
        val x: Int,
        val y: Int,
        val cells: BooleanArray
    ) {
        fun drawTo(canvas: Canvas, paint: Paint) {
            for (row in 0 until 8) {
                for (col in 0 until 18) {
                    if (cells[row * 18 + col]) {
                        canvas.drawRect(
                            (x + col).toFloat(),
                            (y + row).toFloat(),
                            (x + col + 1).toFloat(),
                            (y + row + 1).toFloat(),
                            paint
                        )
                    }
                }
            }
        }
    }

    private data class MotherShip(
        var x: Float,
        var y: Float,
        val direction: Int,
        var value: Int,
        var active: Boolean = true
    )

    private val prefs = context.getSharedPreferences("space_invaders", Context.MODE_PRIVATE)
    private val soundEngine = SoundEngine()
    private val surfaceHolderRef = holder
    private var renderThread: Thread? = null
    private var running = false
    private var screen = Screen.SPLASH
    private var splashTimer = 1.4f
    private var lastTimeNanos = System.nanoTime()
    private var accumulator = 0L
    private val fixedStepNanos = 1_000_000_000L / 60L
    private val frameBuffer = Bitmap.createBitmap(LOGICAL_WIDTH, LOGICAL_HEIGHT, Bitmap.Config.ARGB_8888)
    private val frameCanvas = Canvas(frameBuffer)
    private val blackPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val redPaint = Paint().apply { color = Color.rgb(255, 20, 20); style = Paint.Style.FILL }
    private val greenPaint = Paint().apply { color = Color.rgb(40, 255, 40); style = Paint.Style.FILL }
    private val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val smallFont = Paint().apply {
        color = Color.WHITE
        textSize = 8f
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
    }
    private val mediumFont = Paint().apply {
        color = Color.WHITE
        textSize = 12f
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
    }
    private val largeFont = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
    }

    private var playerX = LOGICAL_WIDTH / 2f
    private var playerLives = 3
    private var score = 0
    private var hiScore = prefs.getInt("hi_score", 0)
    private var wave = 1
    private var playerShot: Projectile? = null
    private var alienShots = mutableListOf<Projectile>()
    private var aliens = mutableListOf<Alien>()
    private var barriers = mutableListOf<Barrier>()
    private var motherShip: MotherShip? = null
    private var motherShipTimer = 6f
    private var formationDirection = 1f
    private var alienStepTimer = 0f
    private var explosionFlash = 0f
    private var playerInvulnerable = 0f
    private var leftPressed = false
    private var rightPressed = false
    private var firePressed = false
    private var beatCountdown = 0f
    private var beatIndex = 0
    private var gameOverTriggered = false
    private val buttonRectLeft = Rect(10, 214, 64, 244)
    private val buttonRectRight = Rect(78, 214, 132, 244)
    private val buttonRectFire = Rect(156, 214, 214, 244)
    private val buttonRectStart = Rect(60, 140, 164, 170)

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun resumeLoop() {
        if (running) return
        running = true
        renderThread = Thread(this, "SpaceInvadersThread")
        renderThread?.start()
    }

    fun pauseLoop() {
        running = false
        renderThread?.join(500)
        soundEngine.release()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        lastTimeNanos = System.nanoTime()
        if (!running) {
            running = true
            renderThread = Thread(this, "SpaceInvadersThread")
            renderThread?.start()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join(500)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val delta = now - lastTimeNanos
            lastTimeNanos = now
            accumulator += delta
            while (accumulator >= fixedStepNanos) {
                updateGame(1f / 60f)
                accumulator -= fixedStepNanos
            }
            render()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        val screenX = (x.toFloat() / width.toFloat()) * LOGICAL_WIDTH
        val screenY = (y.toFloat() / height.toFloat()) * LOGICAL_HEIGHT
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (screen == Screen.GAME) {
                    when {
                        buttonRectLeft.contains(screenX.toInt(), screenY.toInt()) -> leftPressed = true
                        buttonRectRight.contains(screenX.toInt(), screenY.toInt()) -> rightPressed = true
                        buttonRectFire.contains(screenX.toInt(), screenY.toInt()) -> firePressed = true
                    }
                } else {
                    if (buttonRectStart.contains(screenX.toInt(), screenY.toInt())) {
                        when (screen) {
                            Screen.TITLE -> {
                                startNewGame()
                                screen = Screen.GAME
                            }
                            Screen.GAME_OVER -> {
                                startNewGame()
                                screen = Screen.GAME
                            }
                            Screen.HIGH_SCORE -> screen = Screen.TITLE
                            else -> Unit
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                leftPressed = false
                rightPressed = false
                firePressed = false
            }
        }
        return true
    }

    private fun startNewGame() {
        score = 0
        playerLives = 3
        wave = 1
        gameOverTriggered = false
        playerShot = null
        alienShots.clear()
        aliens.clear()
        barriers = buildBarriers()
        playerX = LOGICAL_WIDTH / 2f
        formationDirection = 1f
        motherShip = null
        motherShipTimer = 6f
        for (row in 0 until 5) {
            val rowType = when (row) {
                0 -> 0
                1, 2 -> 1
                else -> 2
            }
            val points = when (rowType) { 0 -> 30; 1 -> 20; else -> 10 }
            for (col in 0 until 11) {
                aliens.add(
                    Alien(
                        x = 26f + col * 16f,
                        y = 42f + row * 14f,
                        points = points,
                        type = rowType,
                        alive = true,
                        frame = 0
                    )
                )
            }
        }
        soundEngine.playBeat(120f)
    }

    private fun buildBarriers(): MutableList<Barrier> {
        val result = mutableListOf<Barrier>()
        val barrierXPositions = intArrayOf(18, 72, 126, 180)
        for (bx in barrierXPositions) {
            val cells = BooleanArray(18 * 8)
            for (row in 0 until 8) {
                for (col in 0 until 18) {
                    val inside = row > 0 && row < 7 && col > 1 && col < 16
                    val isHole = (row > 2 && row < 6 && col in 5..12 && (col == 5 || col == 12 || row == 3 || row == 5))
                    cells[row * 18 + col] = inside && !isHole
                }
            }
            result.add(Barrier(bx, 180, cells))
        }
        return result
    }

    private fun updateGame(dt: Float) {
        when (screen) {
            Screen.SPLASH -> {
                splashTimer -= dt
                if (splashTimer <= 0f) {
                    screen = Screen.TITLE
                }
            }
            Screen.TITLE -> Unit
            Screen.GAME -> updateBattle(dt)
            Screen.GAME_OVER -> Unit
            Screen.HIGH_SCORE -> Unit
        }
    }

    private fun updateBattle(dt: Float) {
        if (explosionFlash > 0f) {
            explosionFlash -= dt
        }
        if (playerInvulnerable > 0f) {
            playerInvulnerable -= dt
        }

        if (leftPressed) {
            playerX -= PLAYER_SPEED
        }
        if (rightPressed) {
            playerX += PLAYER_SPEED
        }
        playerX = playerX.coerceIn(12f, LOGICAL_WIDTH - 12f)

        if (firePressed && playerShot == null) {
            playerShot = Projectile(playerX, PLAYER_Y - 8f, -SHOOT_SPEED, true)
            soundEngine.playPlayerShot()
        }

        updateBeat(dt)
        updateMotherShip(dt)
        updateFormation(dt)
        updateProjectiles(dt)
        checkCollisions()
        updateWinLoseState()
    }

    private fun updateBeat(dt: Float) {
        beatCountdown -= dt
        val notes = listOf(110f, 98f, 82f, 73f)
        if (beatCountdown <= 0f) {
            val aliveCount = aliens.count { it.alive }
            val pace = max(0.2f, 0.65f - (wave * 0.05f) - (aliveCount * 0.015f))
            beatCountdown = pace
            soundEngine.playBeat(notes[beatIndex % notes.size])
            beatIndex += 1
        }
    }

    private fun updateMotherShip(dt: Float) {
        if (motherShip == null) {
            motherShipTimer -= dt
            if (motherShipTimer <= 0f) {
                val spawnLeft = Random.nextBoolean()
                val x = if (spawnLeft) -12f else LOGICAL_WIDTH.toFloat() + 12f
                val direction = if (spawnLeft) 1 else -1
                motherShip = MotherShip(x, 18f, direction, listOf(50, 100, 150, 300).random())
                soundEngine.playMotherShipTone()
                motherShipTimer = 9f + Random.nextFloat() * 8f
            }
        } else {
            motherShip!!.x += motherShip!!.direction * 1.7f
            if (motherShip!!.x > LOGICAL_WIDTH + 16f || motherShip!!.x < -16f) {
                motherShip = null
            }
        }
    }

    private fun updateFormation(dt: Float) {
        val aliveAliens = aliens.filter { it.alive }
        if (aliveAliens.isEmpty()) {
            wave += 1
            startNewGame()
            screen = Screen.GAME
            return
        }

        val minX = aliveAliens.minOf { it.x }
        val maxX = aliveAliens.maxOf { it.x + 12f }
        alienStepTimer -= dt
        val liveCount = aliveAliens.size
        val stepDelay = max(0.08f, 0.6f - liveCount * 0.012f)

        if (alienStepTimer <= 0f) {
            if ((formationDirection > 0 && maxX >= LOGICAL_WIDTH - 10f) || (formationDirection < 0 && minX <= 10f)) {
                formationDirection *= -1f
                aliveAliens.forEach { it.y += 8f }
            } else {
                aliveAliens.forEach { it.x += formationDirection * 2.5f }
            }
            alienStepTimer = stepDelay

            val alienToShoot = aliveAliens.sortedBy { it.y }.lastOrNull() ?: return
            if (Random.nextFloat() < 0.3f && alienShots.size < 3) {
                alienShots.add(Projectile(alienToShoot.x + 5f, alienToShoot.y + 8f, 2.5f, false))
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        if (playerShot != null) {
            playerShot!!.y += playerShot!!.vy * 1.2f
            if (playerShot!!.y < -2f) {
                playerShot = null
            }
        }

        val activeShots = mutableListOf<Projectile>()
        for (shot in alienShots) {
            shot.y += shot.vy * 1.2f
            if (shot.y < LOGICAL_HEIGHT + 10f && shot.y > -10f) {
                activeShots.add(shot)
            }
        }
        alienShots.clear()
        alienShots.addAll(activeShots)
    }

    private fun checkCollisions() {
        val currentShot = playerShot ?: return
        val hitAlien = aliens.firstOrNull { it.alive && collides(currentShot.x, currentShot.y, 2f, 6f, it.x, it.y, 10f, 8f) }
        if (hitAlien != null) {
            hitAlien.alive = false
            val scoreGain = hitAlien.points
            score += scoreGain
            if (score > hiScore) {
                hiScore = score
                prefs.edit().putInt("hi_score", hiScore).apply()
            }
            soundEngine.playInvaderExplosion()
            currentShot.y = -20f
            this.playerShot = null
        }

        if (motherShip != null) {
            val ship = motherShip
            if (collides(currentShot.x, currentShot.y, 2f, 6f, ship!!.x, ship.y, 12f, 8f)) {
                score += ship.value
                if (score > hiScore) {
                    hiScore = score
                    prefs.edit().putInt("hi_score", hiScore).apply()
                }
                soundEngine.playInvaderExplosion()
                motherShip = null
                currentShot.y = -20f
                this.playerShot = null
            }
        }

        if (currentShot.y < 0f) {
            currentShot.y = -100f
            this.playerShot = null
        }

        for (shot in alienShots) {
            if (shot.isPlayer) continue
            if (collides(shot.x, shot.y, 2f, 6f, playerX, PLAYER_Y - 5f, PLAYER_WIDTH.toFloat(), PLAYER_HEIGHT.toFloat())) {
                if (playerInvulnerable <= 0f) {
                    playerLives -= 1
                    playerInvulnerable = 1.0f
                    explosionFlash = 0.5f
                    soundEngine.playPlayerExplosion()
                    shot.y = LOGICAL_HEIGHT + 10f
                    if (playerLives <= 0) {
                        triggerGameOver()
                    }
                }
            }
        }

        val barrierShotPositions = alienShots.toList() + listOfNotNull(playerShot)
        for (shot in barrierShotPositions) {
            for (barrier in barriers) {
                val bx = (shot.x - barrier.x).toInt()
                val by = (shot.y - barrier.y).toInt()
                if (bx in 0..17 && by in 0..7 && barrier.cells[by * 18 + bx]) {
                    barrier.cells[by * 18 + bx] = false
                    shot.y = if (shot.isPlayer) -20f else LOGICAL_HEIGHT + 20f
                    if (shot.isPlayer) {
                        playerShot = null
                    } else {
                        alienShots.remove(shot)
                    }
                }
            }
        }
    }

    private fun triggerGameOver() {
        if (gameOverTriggered) return
        gameOverTriggered = true
        if (score > hiScore) {
            hiScore = score
            prefs.edit().putInt("hi_score", hiScore).apply()
        }
        screen = Screen.GAME_OVER
    }

    private fun updateWinLoseState() {
        if (aliens.any { it.alive && it.y + 8f >= FLOOR_Y }) {
            triggerGameOver()
        }
    }

    private fun collides(x1: Float, y1: Float, w1: Float, h1: Float, x2: Float, y2: Float, w2: Float, h2: Float): Boolean {
        val left = max(x1, x2)
        val right = min(x1 + w1, x2 + w2)
        val top = max(y1, y2)
        val bottom = min(y1 + h1, y2 + h2)
        return left < right && top < bottom
    }

    private fun render() {
        val canvas = holder.lockCanvas() ?: return
        try {
            frameCanvas.drawRect(0f, 0f, LOGICAL_WIDTH.toFloat(), LOGICAL_HEIGHT.toFloat(), blackPaint)
            frameCanvas.drawRect(0f, 0f, LOGICAL_WIDTH.toFloat(), 18f, redPaint)
            frameCanvas.drawRect(0f, 224f, LOGICAL_WIDTH.toFloat(), LOGICAL_HEIGHT.toFloat(), greenPaint)
            frameCanvas.drawRect(0f, 214f, LOGICAL_WIDTH.toFloat(), 216f, greenPaint)

            drawHud()
            drawBarriers()
            drawMotherShip()
            drawAliens()
            drawPlayer()
            drawProjectiles()
            drawScreenOverlay()

            val dst = Rect(0, 0, width, height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            paint.isFilterBitmap = false
            canvas.drawBitmap(frameBuffer, null, dst, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawHud() {
        frameCanvas.drawText("SCORE<1>", 10f, 14f, smallFont)
        frameCanvas.drawText(score.toString().padStart(5, '0'), 10f, 26f, smallFont)
        frameCanvas.drawText("HI-SCORE", 140f, 14f, smallFont)
        frameCanvas.drawText(hiScore.toString().padStart(5, '0'), 146f, 26f, smallFont)

        for (i in 0 until playerLives) {
            val x = 10 + i * 12
            val y = 236
            frameCanvas.drawRect(x.toFloat(), (y + 2).toFloat(), (x + 7).toFloat(), (y + 8).toFloat(), greenPaint)
            frameCanvas.drawRect((x + 2).toFloat(), y.toFloat(), (x + 5).toFloat(), (y + 2).toFloat(), greenPaint)
        }
    }

    private fun drawBarriers() {
        for (barrier in barriers) {
            barrier.drawTo(frameCanvas, greenPaint)
        }
    }

    private fun drawAliens() {
        for (alien in aliens) {
            if (!alien.alive) continue
            val sprite = getAlienSprite(alien.type, alien.frame)
            val width = sprite.first.size
            val height = sprite.first.size
            for (row in sprite.first.indices) {
                for (col in sprite.first[row].indices) {
                    if (sprite.first[row][col] == '#') {
                        frameCanvas.drawRect(
                            alien.x + col,
                            alien.y + row,
                            alien.x + col + 1f,
                            alien.y + row + 1f,
                            whitePaint
                        )
                    }
                }
            }
        }
    }

    private fun getAlienSprite(type: Int, frame: Int): Pair<List<String>, List<String>> {
        val list = when (type) {
            0 -> listOf(
                listOf("..##..", "#####.", "##.##.", "######", "##.##.", "##.##.", "..##..", "......"),
                listOf("..##..", "#####.", "##.##.", "######", "##.##.", "#####.", ".##...", "......")
            )
            1 -> listOf(
                listOf(".###..", "#####.", "######", "######", "##.##.", "##.##.", ".##...", "......"),
                listOf(".###..", "#####.", "######", "######", "##.##.", "#####.", ".##...", "......")
            )
            else -> listOf(
                listOf(".##...", "#####.", "######", "######", "##.##.", "##.##.", ".##...", "......"),
                listOf(".##...", "#####.", "######", "######", "##.##.", "#####.", "###...", "......")
            )
        }
        val active = if (frame % 2 == 0) list[0] else list[1]
        return Pair(active, active)
    }

    private fun drawPlayer() {
        if (playerInvulnerable > 0f && ((playerInvulnerable * 10).toInt() % 2 == 0)) return
        val ship = arrayOf(
            ".#....#.",
            "######..",
            "######..",
            "#######.",
            ".#####..",
            ".##.##..",
            ".....#.."
        )
        for (row in ship.indices) {
            for (col in ship[row].indices) {
                if (ship[row][col] == '#') {
                    frameCanvas.drawRect(
                        playerX + col - 3f,
                        PLAYER_Y + row - 3f,
                        playerX + col - 2f,
                        PLAYER_Y + row - 2f,
                        greenPaint
                    )
                }
            }
        }
    }

    private fun drawMotherShip() {
        motherShip?.let {
            frameCanvas.drawRect(it.x, it.y, it.x + 12f, it.y + 8f, whitePaint)
        }
    }

    private fun drawProjectiles() {
        playerShot?.let {
            frameCanvas.drawRect(it.x - 1f, it.y, it.x + 1f, it.y + 6f, greenPaint)
        }
        for (shot in alienShots) {
            frameCanvas.drawRect(shot.x - 1f, shot.y, shot.x + 1f, shot.y + 6f, whitePaint)
        }
    }

    private fun drawScreenOverlay() {
        when (screen) {
            Screen.SPLASH -> {
                largeFont.textSize = 20f
                frameCanvas.drawText("SPACE", 54f, 96f, largeFont)
                frameCanvas.drawText("INVADERS", 42f, 124f, largeFont)
            }
            Screen.TITLE -> {
                largeFont.textSize = 18f
                frameCanvas.drawText("SPACE", 54f, 58f, largeFont)
                frameCanvas.drawText("INVADERS", 42f, 82f, largeFont)
                frameCanvas.drawText("=30", 30f, 120f, smallFont)
                frameCanvas.drawText("=20", 30f, 134f, smallFont)
                frameCanvas.drawText("=10", 30f, 148f, smallFont)
                frameCanvas.drawText("=?", 30f, 162f, smallFont)
                frameCanvas.drawText("PLAY", 86f, 162f, mediumFont)
                frameCanvas.drawRect(68f, 142f, 156f, 172f, whitePaint)
            }
            Screen.GAME_OVER -> {
                largeFont.textSize = 18f
                frameCanvas.drawText("GAME OVER", 46f, 90f, largeFont)
                frameCanvas.drawText("SCORE", 70f, 120f, smallFont)
                frameCanvas.drawText(score.toString(), 92f, 136f, mediumFont)
                frameCanvas.drawRect(60f, 150f, 164f, 180f, whitePaint)
                frameCanvas.drawText("PLAY", 90f, 168f, mediumFont)
            }
            Screen.HIGH_SCORE -> {
                largeFont.textSize = 18f
                frameCanvas.drawText("HIGH SCORE", 42f, 90f, largeFont)
                frameCanvas.drawText(hiScore.toString(), 70f, 132f, mediumFont)
                frameCanvas.drawRect(60f, 150f, 164f, 180f, whitePaint)
                frameCanvas.drawText("BACK", 88f, 168f, mediumFont)
            }
            else -> Unit
        }
    }
}
