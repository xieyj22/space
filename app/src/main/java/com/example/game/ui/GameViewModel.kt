package com.example.game.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.GameAudioEngine
import com.example.game.audio.SoundType
import com.example.game.database.GameDatabase
import com.example.game.database.GameRepository
import com.example.game.database.PlayerStats
import com.example.game.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GameDatabase.getDatabase(application)
    val dao = db.playerDao()
    val repository = GameRepository(dao)
    val audioEngine = GameAudioEngine()

    // Observe persistent system profile
    val playerStats: StateFlow<PlayerStats> = repository.playerStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerStats()
    )

    // Game loop control
    private var gameLoopJob: Job? = null
    private val random = Random()

    // Core Screen Navigator: "MENU" vs "GAME"
    private val _screenState = mutableStateOf("MENU")
    val screenState: State<String> = _screenState

    // Current running state
    private val _gameState = mutableStateOf<GameStatus>(GameStatus.START_SCREEN)
    val gameState: State<GameStatus> = _gameState

    // Map boundaries (Virtual Space: 1280 x 720)
    val virtualWidth = 1280f
    val virtualHeight = 720f

    // Running session variables
    var activeShip = mutableStateOf<ShipType>(ShipType.VANGUARD)
    var shipX = mutableStateOf(virtualWidth / 2f)
    var shipY = mutableStateOf(virtualHeight * 0.75f)
    var shipVx = 0f
    var shipVy = 0f

    var shipHp = mutableStateOf(100f)
    var shipMaxHp = mutableStateOf(100f)
    var shipSpeed = 7f
    var magnetRange = 100f
    var critChance = 0f
    var damageMod = 1.0f

    var score = mutableStateOf(0)
    var wave = mutableStateOf(1)
    var waveTimeLeft = mutableStateOf(30f) // seconds per wave
    var gameTime = mutableStateOf(0f)
    var exp = mutableStateOf(0)
    var expNeeded = mutableStateOf(120)
    var techLevel = mutableStateOf(1)
    var creditsEarned = mutableStateOf(0)

    // Collections (Thread-safe or Main-Thread confined)
    val activeWeapons = mutableStateListOf<ActiveWeapon>()
    val bullets = mutableStateListOf<Bullet>()
    val enemies = mutableStateListOf<Enemy>()
    val expGems = mutableStateListOf<ExpGem>()
    val particles = CopyOnWriteArrayList<Particle>() // Particle effects offloaded or thread-safe
    val floatingTexts = mutableStateListOf<FloatingText>()

    // For Card Select overlay
    val levelUpChoices = mutableStateListOf<UpgradeOption>()

    // Enemy Spawn triggers
    private var lastSpawnTime = 0L
    private var lastShootTime = 0L
    var shieldAngle = 0f

    // Weapon Cooldown Timers
    private var twinBlastersTimer = 0f
    private var laserTimer = 0f
    private var missileTimer = 0f
    private var teslaTimer = 0f
    private var gravityTimer = 0f

    // Active boss tracking
    var bossSpawned = mutableStateOf(false)

    fun navigateToMenu() {
        _screenState.value = "MENU"
        _gameState.value = GameStatus.START_SCREEN
    }

    fun startNewGame() {
        viewModelScope.launch {
            val stats = repository.getStatsDirect()
            val ship = ShipType.entries.find { it.id == stats.selectedShipId } ?: ShipType.VANGUARD
            setupGame(ship, stats)
            _screenState.value = "GAME"
            _gameState.value = GameStatus.RUNNING
            audioEngine.playSound(SoundType.SHIELD_UP)
            startGameLoop()
        }
    }

    private fun setupGame(ship: ShipType, stats: PlayerStats) {
        activeShip.value = ship
        
        // Compute stats with permanent shop enhancements
        val hpBonus = 1f + stats.armorTier * 0.15f
        val speedBonus = 1f + stats.speedTier * 0.10f
        val rangeBonus = 1f + stats.magnetTier * 0.25f
        val critBonus = stats.critTier * 0.05f

        shipMaxHp.value = ship.baseHp * hpBonus
        shipHp.value = shipMaxHp.value
        shipSpeed = ship.baseSpeed * speedBonus
        magnetRange = 100f * rangeBonus
        critChance = critBonus
        damageMod = ship.damageModifier

        shipX.value = virtualWidth / 2f
        shipY.value = virtualHeight * 0.75f
        shipVx = 0f
        shipVy = 0f

        score.value = 0
        wave.value = 1
        waveTimeLeft.value = 32f
        gameTime.value = 0f
        exp.value = 0
        expNeeded.value = 120
        techLevel.value = 1
        creditsEarned.value = 0

        // Reset game list content
        enemies.clear()
        bullets.clear()
        expGems.clear()
        particles.clear()
        floatingTexts.clear()
        activeWeapons.clear()
        levelUpChoices.clear()

        // Unpack starting ship weapon
        activeWeapons.add(ActiveWeapon(ship.startingWeapon, 1))

        twinBlastersTimer = 0f
        laserTimer = 0f
        missileTimer = 0f
        teslaTimer = 0f
        gravityTimer = 0f
        bossSpawned.value = false
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (_gameState.value == GameStatus.RUNNING) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTime).coerceIn(10L, 100L) / 1000f // clamped dt
                lastTime = now

                updatePhysics(delta)
                delay(16) // Target ~60 FPS
            }
        }
    }

    fun pauseGame() {
        if (_gameState.value == GameStatus.RUNNING) {
            _gameState.value = GameStatus.PAUSED
            gameLoopJob?.cancel()
        }
    }

    fun resumeGame() {
        if (_gameState.value == GameStatus.PAUSED) {
            _gameState.value = GameStatus.RUNNING
            startGameLoop()
        }
    }

    /**
     * Updates Joystick Offset inputs from the virtual pad
     */
    fun onMoveInput(vxRatio: Float, vyRatio: Float) {
        if (_gameState.value != GameStatus.RUNNING) return
        shipVx = vxRatio * shipSpeed
        shipVy = vyRatio * shipSpeed
    }

    private fun updatePhysics(dt: Float) {
        gameTime.value += dt
        waveTimeLeft.value -= dt

        // 1. Update Player Positions
        shipX.value = (shipX.value + shipVx).coerceIn(30f, virtualWidth - 30f)
        shipY.value = (shipY.value + shipVy).coerceIn(30f, virtualHeight - 30f)

        // Decelerate slightly if joystick let go
        shipVx *= 0.82f
        shipVy *= 0.82f

        // 2. Wave progression
        if (waveTimeLeft.value <= 0) {
            wave.value += 1
            waveTimeLeft.value = 32f
            
            // Check boss trigger
            if (wave.value == 5 || wave.value == 10) {
                spawnBoss()
            } else {
                audioEngine.playSound(SoundType.LEVEL_UP, 0.4f)
                addNotification("警告! 难度提升：波次 ${wave.value}")
            }
        }

        // 3. Spawning System
        tickEnemySpawning()

        // 4. Update and Fire Player Weapons
        tickPlayerWeapons(dt)

        // 5. Update Bullets
        val bulletsToRemove = mutableListOf<Bullet>()
        for (bullet in bullets) {
            bullet.x += bullet.vx
            bullet.y += bullet.vy

            // Homing flight towards target for missiles
            if (bullet.isMissile && bullet.targetEnemyId != null) {
                val target = enemies.find { it.id == bullet.targetEnemyId }
                if (target != null) {
                    val dx = target.x - bullet.x
                    val dy = target.y - bullet.y
                    val dist = max(1f, sqrt(dx * dx + dy * dy))
                    val speed = sqrt(bullet.vx * bullet.vx + bullet.vy * bullet.vy)
                    val targetVx = (dx / dist) * speed
                    val targetVy = (dy / dist) * speed
                    // Lerp bullet velocity slightly to homing target
                    val lerp = 0.08f
                    bullet.x += (targetVx - bullet.vx) * lerp
                    bullet.y += (targetVy - bullet.vy) * lerp
                }
            }

            // Boundary removal
            if (bullet.x < -100f || bullet.x > virtualWidth + 100f || bullet.y < -100f || bullet.y > virtualHeight + 100f) {
                bulletsToRemove.add(bullet)
                continue
            }

            // Collision checks
            if (bullet.isFromPlayer) {
                // Bullet against Enemy hulls
                for (enemy in enemies) {
                    val dx = bullet.x - enemy.x
                    val dy = bullet.y - enemy.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < bullet.radius + enemy.radius) {
                        bulletsToRemove.add(bullet)
                        damageEnemy(enemy, bullet.damage)
                        
                        // Splash trigger for rockets
                        if (bullet.isMissile) {
                            triggerSplashExplosion(bullet.x, bullet.y, bullet.splashRadius, bullet.damage * 0.75f)
                        }
                        break
                    }
                }
            } else {
                // Enemy Bullet against Player
                val dx = bullet.x - shipX.value
                val dy = bullet.y - shipY.value
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < bullet.radius + 18f) {
                    bulletsToRemove.add(bullet)
                    damagePlayer(bullet.damage)
                }
            }
        }
        bullets.removeAll(bulletsToRemove)

        // 6. Update Shield Orbs Rotation
        shieldAngle += 2.2f * dt
        if (shieldAngle > 2 * Math.PI) shieldAngle = 0f
        tickShieldContact()

        // 7. Update Enemy Positions and Behaviours
        val enemiesToRemove = mutableListOf<Enemy>()
        val currentTime = System.currentTimeMillis()
        for (enemy in enemies) {
            enemy.phase += dt

            // AI types
            when (enemy.type) {
                EnemyType.SCOUT -> {
                    // Scout flies towards ship direction, slightly weaving
                    val dx = shipX.value - enemy.x
                    val dy = shipY.value - enemy.y
                    val dist = max(1f, sqrt(dx * dx + dy * dy))
                    enemy.x += (dx / dist) * 2.8f + sin(enemy.phase * 5f) * 1.5f
                    enemy.y += (dy / dist) * 2.8f
                }
                EnemyType.CHASER -> {
                    // Rapid chaser direct tracking
                    val dx = shipX.value - enemy.x
                    val dy = shipY.value - enemy.y
                    val dist = max(1f, sqrt(dx * dx + dy * dy))
                    enemy.x += (dx / dist) * 3.4f
                    enemy.y += (dy / dist) * 3.4f
                }
                EnemyType.SPITTER -> {
                    // Stays at safe distance, circles player, fires bullets
                    val dx = shipX.value - enemy.x
                    val dy = shipY.value - enemy.y
                    val dist = max(1f, sqrt(dx * dx + dy * dy))

                    if (dist > 300f) {
                        enemy.x += (dx / dist) * 1.8f
                        enemy.y += (dy / dist) * 1.8f
                    } else if (dist < 200f) {
                        enemy.x -= (dx / dist) * 2.0f
                        enemy.y -= (dy / dist) * 2.0f
                    } else {
                        // Strafe circle flight
                        enemy.x += (-dy / dist) * 1.5f
                        enemy.y += (dx / dist) * 1.5f
                    }

                    // Fire cooldown
                    if (currentTime - enemy.bulletTimer > 2200L) {
                        enemy.bulletTimer = currentTime
                        fireEnemyBullet(enemy, dx / dist, dy / dist)
                    }
                }
                EnemyType.METEOR -> {
                    // Flying straight diagonally
                    enemy.x += enemy.vx * 4.5f
                    enemy.y += enemy.vy * 4.5f
                    if (enemy.x < -150f || enemy.x > virtualWidth + 150f || enemy.y < -150f || enemy.y > virtualHeight + 150f) {
                        enemiesToRemove.add(enemy)
                    }
                }
                EnemyType.ELITE -> {
                    // Tanky unit, path directly to player, shoots 3 bullets
                    val dx = shipX.value - enemy.x
                    val dy = shipY.value - enemy.y
                    val dist = max(1f, sqrt(dx * dx + dy * dy))
                    enemy.x += (dx / dist) * 1.5f
                    enemy.y += (dy / dist) * 1.5f

                    if (currentTime - enemy.bulletTimer > 3000L) {
                        enemy.bulletTimer = currentTime
                        fireEliteBulletSpread(enemy, dx / dist, dy / dist)
                    }
                }
                EnemyType.BOSS -> {
                    // Complex Boss Behavior
                    // Hover at top-middle, fly horizontally back and forth
                    val targetY = 150f
                    enemy.y += (targetY - enemy.y) * 0.05f
                    enemy.x += sin(enemy.phase * 0.8f) * 2.5f

                    // Heavy Bullet patterns
                    if (currentTime - enemy.bulletTimer > 1800L) {
                        enemy.bulletTimer = currentTime
                        fireBossPulsePattern(enemy)
                    }
                }
            }

            // Check contact crash damage with player
            val dx = enemy.x - shipX.value
            val dy = enemy.y - shipY.value
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < enemy.radius + 18f && enemy.type != EnemyType.BOSS) {
                damagePlayer(15f * dt) // continuous ticking contact damage
            } else if (dist < enemy.radius + 18f && enemy.type == EnemyType.BOSS) {
                damagePlayer(35f * dt)
            }
        }
        enemies.removeAll(enemiesToRemove)

        // 8. Update EXP gems and attract them to player
        val gemsToRemove = mutableListOf<ExpGem>()
        for (gem in expGems) {
            val dx = shipX.value - gem.x
            val dy = shipY.value - gem.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < magnetRange) {
                gem.isAttracted = true
            }

            if (gem.isAttracted) {
                // Fly rapidly to player
                val speed = 12f
                gem.x += (dx / dist) * speed
                gem.y += (dy / dist) * speed
            }

            // Collection Check
            if (dist < 22f) {
                gemsToRemove.add(gem)
                if (gem.isGold) {
                    creditsEarned.value += gem.creditsAmount
                    score.value += 15
                    audioEngine.playSound(SoundType.PICKUP, 0.45f)
                    addFloatingText("+${gem.creditsAmount} 金币", gem.x, gem.y, Color(0xFFFFD54F))
                } else {
                    gainExp(gem.expAmount)
                    score.value += 5
                    audioEngine.playSound(SoundType.PICKUP, 0.25f)
                    addFloatingText("+${gem.expAmount} 能量", gem.x, gem.y, Color(0xFF64B5F6))
                }
            }
        }
        expGems.removeAll(gemsToRemove)

        // 9. Particles simulation limit size
        val deadParticles = mutableListOf<Particle>()
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            p.currentLife++
            if (p.currentLife >= p.maxLife) {
                deadParticles.add(p)
            }
        }
        particles.removeAll(deadParticles)

        // 10. Update Floating Texts life
        val TextsToRemove = mutableListOf<FloatingText>()
        for (text in floatingTexts) {
            text.y -= 0.8f
            text.life--
            if (text.life <= 0) {
                TextsToRemove.add(text)
            }
        }
        floatingTexts.removeAll(TextsToRemove)
    }

    private fun tickEnemySpawning() {
        val now = System.currentTimeMillis()
        val cooldown = when {
            bossSpawned.value -> 999999L // stop normal spawns during boss battle!
            wave.value == 1 -> 2500L
            wave.value == 2 -> 1800L
            wave.value == 3 -> 1200L
            wave.value == 4 -> 900L
            wave.value < 8 -> 700L
            else -> 500L
        }

        if (now - lastSpawnTime > cooldown && !bossSpawned.value) {
            lastSpawnTime = now
            spawnRandomEnemy()
        }
    }

    private fun spawnRandomEnemy() {
        val side = random.nextInt(4)
        var ex = 0f
        var ey = 0f

        // Random off-screen coordinate bounding
        when (side) {
            0 -> { ex = random.nextFloat() * virtualWidth; ey = -40f } // Top
            1 -> { ex = virtualWidth + 40f; ey = random.nextFloat() * virtualHeight } // Right
            2 -> { ex = random.nextFloat() * virtualWidth; ey = virtualHeight + 40f } // Bottom
            3 -> { ex = -40f; ey = random.nextFloat() * virtualHeight } // Left
        }

        // Determine type based on active waves
        val w = wave.value
        val roll = random.nextFloat()
        val type = when {
            w == 1 -> if (roll < 0.2f) EnemyType.CHASER else EnemyType.SCOUT
            w == 2 -> when {
                roll < 0.2f -> EnemyType.CHASER
                roll < 0.4f -> EnemyType.METEOR
                else -> EnemyType.SCOUT
            }
            w == 3 -> when {
                roll < 0.3f -> EnemyType.CHASER
                roll < 0.5f -> EnemyType.SPITTER
                roll < 0.7f -> EnemyType.METEOR
                else -> EnemyType.SCOUT
            }
            w == 4 -> when {
                roll < 0.35f -> EnemyType.CHASER
                roll < 0.65f -> EnemyType.SPITTER
                roll < 0.8f -> EnemyType.ELITE
                else -> EnemyType.SCOUT
            }
            else -> when {
                roll < 0.25f -> EnemyType.CHASER
                roll < 0.45f -> EnemyType.SPITTER
                roll < 0.65f -> EnemyType.METEOR
                roll < 0.85f -> EnemyType.ELITE
                else -> EnemyType.SCOUT
            }
        }

        var evx = 0f
        var evy = 0f
        if (type == EnemyType.METEOR) {
            val destX = random.nextFloat() * virtualWidth
            val destY = random.nextFloat() * virtualHeight
            val dx = destX - ex
            val dy = destY - ey
            val dist = max(1f, sqrt(dx * dx + dy * dy))
            evx = (dx / dist) * type.speedMultiplier
            evy = (dy / dist) * type.speedMultiplier
        }

        val hp = type.baseHp * (1f + (w - 1) * 0.18f) // Scale HP with WAVE
        enemies.add(
            Enemy(
                id = random.nextLong(),
                x = ex,
                y = ey,
                vx = evx,
                vy = evy,
                maxHp = hp,
                hp = hp,
                radius = 16f * type.sizeMultiplier,
                scoreValue = when(type) {
                    EnemyType.SCOUT -> 100
                    EnemyType.CHASER -> 150
                    EnemyType.SPITTER -> 250
                    EnemyType.METEOR -> 200
                    EnemyType.ELITE -> 500
                    else -> 1000
                },
                creditValue = when(type) {
                    EnemyType.SCOUT -> 1
                    EnemyType.CHASER -> 2
                    EnemyType.SPITTER -> 3
                    EnemyType.METEOR -> 1
                    EnemyType.ELITE -> 10
                    else -> 0
                },
                type = type
            )
        )
    }

    private fun spawnBoss() {
        bossSpawned.value = true
        audioEngine.playSound(SoundType.BOSS_WARNING, 0.7f)
        addNotification("⚠️ 警告! 虚空 Boss 降临战场 ⚠️")

        val bossHp = EnemyType.BOSS.baseHp * (1f + (wave.value / 5) * 0.8f)
        enemies.add(
            Enemy(
                id = 99999L,
                x = virtualWidth / 2f,
                y = -100f,
                vx = 0f,
                vy = 0f,
                maxHp = bossHp,
                hp = bossHp,
                radius = 16f * EnemyType.BOSS.sizeMultiplier,
                scoreValue = 8000,
                creditValue = 150,
                type = EnemyType.BOSS
            )
        )
    }

    private fun fireEnemyBullet(enemy: Enemy, dxRatio: Float, dyRatio: Float) {
        val speed = 4.2f
        bullets.add(
            Bullet(
                x = enemy.x,
                y = enemy.y,
                vx = dxRatio * speed,
                vy = dyRatio * speed,
                radius = 8f,
                damage = 12f,
                color = Color(0xFFFFB74D),
                isFromPlayer = false
            )
        )
    }

    private fun fireEliteBulletSpread(enemy: Enemy, dxRatio: Float, dyRatio: Float) {
        val speed = 4.8f
        val angles = floatArrayOf(-25f, 0f, 25f)
        val baseAngle = atan2(dyRatio, dxRatio)

        for (angleOffset in angles) {
            val rad = baseAngle + Math.toRadians(angleOffset.toDouble()).toFloat()
            bullets.add(
                Bullet(
                    x = enemy.x,
                    y = enemy.y,
                    vx = cos(rad) * speed,
                    vy = sin(rad) * speed,
                    radius = 9f,
                    damage = 18f,
                    color = Color(0xFFCE93D8),
                    isFromPlayer = false
                )
            )
        }
    }

    private fun fireBossPulsePattern(boss: Enemy) {
        val numBullets = 16
        val speed = 3.6f
        // Circular burst fire
        for (i in 0 until numBullets) {
            val angle = (2 * Math.PI * i) / numBullets + boss.phase * 0.4f
            bullets.add(
                Bullet(
                    x = boss.x,
                    y = boss.y,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    radius = 10f,
                    damage = 22f,
                    color = Color(0xFFFF1744),
                    isFromPlayer = false
                )
            )
        }

        // Homing missile targetting player
        if (random.nextFloat() < 0.4f) {
            bullets.add(
                Bullet(
                    x = boss.x,
                    y = boss.y,
                    vx = (shipX.value - boss.x) / 150f, // slower speed
                    vy = (shipY.value - boss.y) / 150f,
                    radius = 12f,
                    damage = 30f,
                    color = Color(0xFFE040FB),
                    isFromPlayer = false,
                    isMissile = true
                )
            )
        }
    }

    private fun tickPlayerWeapons(dt: Float) {
        // Query active player upgrades
        val stats = playerStats.value
        val rateBonus = 1f + stats.rateTier * 0.10f

        for (weapon in activeWeapons) {
            when (weapon.type) {
                WeaponType.TWIN_BLASTERS -> {
                    twinBlastersTimer += dt
                    val cd = (0.35f / rateBonus).coerceAtLeast(0.12f)
                    if (twinBlastersTimer >= cd) {
                        twinBlastersTimer = 0f
                        fireTwinBlaster(weapon.level)
                    }
                }
                WeaponType.LASER_BEAM -> {
                    laserTimer += dt
                    val cd = (0.05f / rateBonus).coerceAtLeast(0.01f) // very high frequency ticks
                    if (laserTimer >= cd) {
                        laserTimer = 0f
                        fireContinuousLaser(weapon.level)
                    }
                }
                WeaponType.MISSILE_LAUNCHER -> {
                    missileTimer += dt
                    val cd = (1.4f / rateBonus).coerceAtLeast(0.45f)
                    if (missileTimer >= cd) {
                        missileTimer = 0f
                        fireMissile(weapon.level)
                    }
                }
                WeaponType.ORBITAL_SHIELD -> {
                    // Shield operates on contact checking, no cooldown triggers needed
                }
                WeaponType.TESLA_COIL -> {
                    teslaTimer += dt
                    val cd = (0.95f / rateBonus).coerceAtLeast(0.3f)
                    if (teslaTimer >= cd) {
                        teslaTimer = 0f
                        fireTeslaLightning(weapon.level)
                    }
                }
                WeaponType.GRAVITY_SINGULARITY -> {
                    gravityTimer += dt
                    val cd = (4.0f / rateBonus).coerceAtLeast(1.5f)
                    if (gravityTimer >= cd) {
                        gravityTimer = 0f
                        fireGravitySingularity(weapon.level)
                    }
                }
            }
        }
    }

    // --- Weapon Shoot Triggers ---

    private fun fireTwinBlaster(level: Int) {
        val dmg = 15f * damageMod * (1f + level * 0.15f)
        val speed = 14f
        audioEngine.playSound(SoundType.LASER, 0.22f)

        when {
            level == 1 -> {
                // One bullet straight up-ahead
                bullets.add(Bullet(shipX.value, shipY.value - 20f, 0f, -speed, 8f, dmg, Color(0xFF00FFCC)))
            }
            level == 2 -> {
                // Two parallel streams
                bullets.add(Bullet(shipX.value - 12f, shipY.value - 20f, 0f, -speed, 8f, dmg, Color(0xFF00FFCC)))
                bullets.add(Bullet(shipX.value + 12f, shipY.value - 20f, 0f, -speed, 8f, dmg, Color(0xFF00FFCC)))
            }
            level == 3 -> {
                // Three spread stream
                bullets.add(Bullet(shipX.value, shipY.value - 20f, 0f, -speed, 9f, dmg, Color(0xFF00FFCC)))
                bullets.add(Bullet(shipX.value - 15f, shipY.value - 20f, -2f, -speed, 8f, dmg, Color(0xFF00FFCC)))
                bullets.add(Bullet(shipX.value + 15f, shipY.value - 20f, 2f, -speed, 8f, dmg, Color(0xFF00FFCC)))
            }
            level == 4 -> {
                // 3 spread stream with pierce count
                bullets.add(Bullet(shipX.value, shipY.value - 20f, 0f, -speed, 10f, dmg, Color(0xFFFFEA00), bounceCount = 1))
                bullets.add(Bullet(shipX.value - 15f, shipY.value - 20f, -2.5f, -speed, 9f, dmg, Color(0xFFFFEA00), bounceCount = 1))
                bullets.add(Bullet(shipX.value + 15f, shipY.value - 20f, 2.5f, -speed, 9f, dmg, Color(0xFFFFEA00), bounceCount = 1))
            }
            else -> {
                // Level 5: 4 heavy spread streams, pierce = 2, huge bullets
                bullets.add(Bullet(shipX.value - 24f, shipY.value - 20f, -3.5f, -speed, 11f, dmg * 1.25f, Color(0xFFFF3D00), bounceCount = 2))
                bullets.add(Bullet(shipX.value - 8f, shipY.value - 20f, -1f, -speed, 12f, dmg * 1.25f, Color(0xFFFF3D00), bounceCount = 2))
                bullets.add(Bullet(shipX.value + 8f, shipY.value - 20f, 1f, -speed, 12f, dmg * 1.25f, Color(0xFFFF3D00), bounceCount = 2))
                bullets.add(Bullet(shipX.value + 24f, shipY.value - 20f, 3.5f, -speed, 11f, dmg * 1.25f, Color(0xFFFF3D00), bounceCount = 2))
                // Extra blast flare particles
                spawnExplosionParticles(shipX.value, shipY.value - 30f, Color(0xFFFF9100), 4)
            }
        }
    }

    private fun fireContinuousLaser(level: Int) {
        val targetsCount = if (level >= 5) 3 else if (level >= 3) 2 else 1
        val dmg = (4f + level * 1.5f) * damageMod * 0.05f // low damage because high speed
        
        // Find nearest enemies
        val targetEnemies = enemies.sortedBy { 
            val dx = it.x - shipX.value
            val dy = it.y - shipY.value
            dx * dx + dy * dy
        }.take(targetsCount)

        for (enemy in targetEnemies) {
            enemy.hp -= dmg
            spawnExplosionParticles(enemy.x, enemy.y, Color(0xFFFFD700),  1)
            
            // Render laser visual bullet segment
            bullets.add(
                Bullet(
                    x = shipX.value,
                    y = shipY.value - 15f,
                    vx = (enemy.x - shipX.value) / 10f, // instant line speed representation
                    vy = (enemy.y - shipY.value) / 10f,
                    radius = 2.5f + level * 0.5f,
                    damage = 0f, // damage is already applied directly!
                    color = if (level >= 5) Color(0xFFFF1744) else if (level >= 3) Color(0xFFFFEA00) else Color(0xFF00FFCC),
                    bounceCount = 0
                )
            )

            if (enemy.hp <= 0) {
                killEnemy(enemy)
            }
        }
    }

    private fun fireMissile(level: Int) {
        if (enemies.isEmpty()) return
        audioEngine.playSound(SoundType.LASER, 0.4f)
        
        val dmg = 40f * damageMod * (1f + level * 0.25f)
        val splash = 70f + level * 15f
        val numMissiles = if (level >= 5) 3 else if (level >= 3) 2 else 1

        val potentialTargets = enemies.shuffled().take(numMissiles)
        for (target in potentialTargets) {
            val dx = target.x - shipX.value
            val dy = target.y - shipY.value
            val dist = max(1f, sqrt(dx * dx + dy * dy))
            val speed = 8.5f + level * 0.5f
            
            bullets.add(
                Bullet(
                    x = shipX.value,
                    y = shipY.value - 15f,
                    vx = (dx / dist) * speed,
                    vy = (dy / dist) * speed,
                    radius = 11f,
                    damage = dmg,
                    color = Color(0xFFFFA726),
                    isFromPlayer = true,
                    isMissile = true,
                    splashRadius = splash,
                    targetEnemyId = target.id
                )
            )
        }
    }

    private fun tickShieldContact() {
        val shieldUpgrade = activeWeapons.find { it.type == WeaponType.ORBITAL_SHIELD } ?: return
        val lv = shieldUpgrade.level
        val numOrbs = if (lv >= 5) 5 else if (lv >= 4) 4 else if (lv >= 3) 3 else if (lv >= 2) 2 else 1
        val radius = 105f + lv * 12f
        val dmg = (20f + lv * 10f) * damageMod * 0.4f // contact ticks

        for (i in 0 until numOrbs) {
            val angle = shieldAngle + (2 * Math.PI * i) / numOrbs
            val ox = shipX.value + cos(angle).toFloat() * radius
            val oy = shipY.value + sin(angle).toFloat() * radius

            // Visual shield spark particles
            if (random.nextFloat() < 0.12f) {
                spawnExplosionParticles(ox, oy, Color(0xFFE040FB), 1)
            }

            // Shield against enemy
            for (enemy in enemies) {
                val dx = ox - enemy.x
                val dy = oy - enemy.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < enemy.radius + 15f) {
                    damageEnemy(enemy, dmg)
                    spawnExplosionParticles(ox, oy, Color(0xFFE040FB), 1)
                    
                    // Lv 5 chain electro-lightning discharge
                    if (lv >= 5 && random.nextFloat() < 0.22f) {
                        fireShieldChainLightning(ox, oy, enemy)
                    }
                }
            }
        }
    }

    private fun fireShieldChainLightning(sx: Float, sy: Float, sourceEnemy: Enemy) {
        val nextEnemies = enemies.filter { it.id != sourceEnemy.id }.sortedBy { 
            val dx = it.x - sourceEnemy.x
            val dy = it.y - sourceEnemy.y
            dx * dx + dy * dy
        }.take(3)

        audioEngine.playSound(SoundType.LASER, 0.15f)
        var lx = sx
        var ly = sy

        for (next in nextEnemies) {
            // Draw bullet chain bolt segment
            bullets.add(Bullet(lx, ly, (next.x - lx) / 12f, (next.y - ly) / 12f, 3f, 0f, Color(0xFFA040FB)))
            damageEnemy(next, 15f * damageMod)
            lx = next.x
            ly = next.y
        }
    }

    private fun fireTeslaLightning(level: Int) {
        if (enemies.isEmpty()) return
        audioEngine.playSound(SoundType.LASER, 0.35f)

        val baseTarget = enemies.minByOrNull { 
            val dx = it.x - shipX.value
            val dy = it.y - shipY.value
            dx * dx + dy * dy
        } ?: return

        val dmg = (22f + level * 12f) * damageMod
        val jumps = if (level >= 5) 8 else if (level >= 3) 5 else if (level >= 2) 3 else 2

        var lastTargetX = shipX.value
        var lastTargetY = shipY.value
        val hitIds = mutableSetOf<Long>()
        var currentEnemy: Enemy? = baseTarget

        for (j in 0 until jumps) {
            if (currentEnemy == null) break
            hitIds.add(currentEnemy.id)

            // Spawn lightning electric segment bullets (instant travel)
            bullets.add(
                Bullet(
                    x = lastTargetX,
                    y = lastTargetY,
                    vx = (currentEnemy.x - lastTargetX) / 8f,
                    vy = (currentEnemy.y - lastTargetY) / 8f,
                    radius = 3.2f,
                    damage = 0f,
                    color = Color(0xFF00E5FF)
                )
            )

            damageEnemy(currentEnemy, dmg)
            spawnExplosionParticles(currentEnemy.x, currentEnemy.y, Color(0xFF00E5FF), 2)

            // Select next jump enemy target closest to current, not hit yet
            lastTargetX = currentEnemy.x
            lastTargetY = currentEnemy.y

            val cloneCurrent = currentEnemy
            currentEnemy = enemies.filter { !hitIds.contains(it.id) }.minByOrNull { 
                val dx = it.x - cloneCurrent.x
                val dy = it.y - cloneCurrent.y
                dx * dx + dy * dy
            }
        }
    }

    private fun fireGravitySingularity(level: Int) {
        val range = 80f + level * 20f
        val life = if (level >= 5) 3.5f else 2.5f
        val pullForce = 2.4f + level * 0.4f
        val freqDamage = (12f + level * 5f) * damageMod * 0.08f // ticks

        // Position black hole near ship or center screen
        val gx = random.nextFloat() * (virtualWidth - 200f) + 100f
        val gy = random.nextFloat() * (virtualHeight - 300f) + 100f

        audioEngine.playSound(SoundType.SHIELD_UP, 0.45f)
        addFloatingText("⚡ 奇点星门", gx, gy, Color(0xFFD500F9), isBig = true)

        viewModelScope.launch {
            val totalTicks = (life * 40).toInt()
            for (t in 0 until totalTicks) {
                if (_gameState.value != GameStatus.RUNNING) continue
                
                // Attract enemies inside range
                for (enemy in enemies) {
                    val dx = gx - enemy.x
                    val dy = gy - enemy.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < range && enemy.type != EnemyType.BOSS) {
                        // pull enemy
                        enemy.x += (dx / dist) * pullForce
                        enemy.y += (dy / dist) * pullForce
                        
                        // tick damage
                        enemy.hp -= freqDamage
                        if (enemy.hp <= 0) {
                            killEnemy(enemy)
                        }
                    }
                }

                // Black hole sparkle particles
                if (t % 2 == 0) {
                    particles.add(
                        Particle(
                            x = gx + cos(t.toFloat()).toFloat() * range * 0.6f,
                            y = gy + sin(t.toFloat()).toFloat() * range * 0.6f,
                            vx = (gx - (gx + cos(t.toFloat()).toFloat() * range * 0.6f)) / 10f,
                            vy = (gy - (gy + sin(t.toFloat()).toFloat() * range * 0.6f)) / 10f,
                            color = Color(0xFFD500F9),
                            size = 10f,
                            maxLife = 15
                        )
                    )
                }
                delay(25) // Tick pause
            }

            // Final catastrophic implosion blast
            audioEngine.playSound(SoundType.EXPLOSION, 0.6f)
            triggerSplashExplosion(gx, gy, range * 1.3f, (50f + level * 35f) * damageMod)
            spawnExplosionParticles(gx, gy, Color(0xFFD500F9), 20)
        }
    }

    private fun triggerSplashExplosion(x: Float, y: Float, radius: Float, dmg: Float) {
        spawnExplosionParticles(x, y, Color(0xFFFF9100), 12)
        val hitEnemies = enemies.filter { 
            val dx = it.x - x
            val dy = it.y - y
            dx * dx + dy * dy < radius * radius
        }

        for (enemy in hitEnemies) {
            damageEnemy(enemy, dmg)
        }
    }

    // --- Damage Operations ---

    private fun damagePlayer(dmg: Float) {
        if (_gameState.value != GameStatus.RUNNING) return
        
        // Void Phantom 40% Dodge check
        if (activeShip.value == ShipType.VOID_PHANTOM && random.nextFloat() < 0.40f) {
            addFloatingText("闪避 Dodge!", shipX.value, shipY.value - 30f, Color(0xFFE040FB))
            return
        }

        shipHp.value -= dmg
        audioEngine.playSound(SoundType.HIT, 0.35f)
        spawnExplosionParticles(shipX.value, shipY.value, Color(0xFFFF5252), 2)

        if (shipHp.value <= 0) {
            shipHp.value = 0f
            triggerGameOver()
        }
    }

    private fun damageEnemy(enemy: Enemy, dmg: Float) {
        // Critical Hit Strike Calculation
        val isCrit = random.nextFloat() < critChance
        val finalDamage = if (isCrit) dmg * 2.5f else dmg

        enemy.hp -= finalDamage
        
        // Floating health tag indicator
        if (isCrit) {
            addFloatingText("CRIT ${finalDamage.toInt()}!", enemy.x, enemy.y - 12f, Color(0xFFFFEA00), isBig = true)
            audioEngine.playSound(SoundType.PICKUP, 0.42f)
            spawnExplosionParticles(enemy.x, enemy.y, Color(0xFFFFEA00), 5)
        } else {
            addFloatingText("${finalDamage.toInt()}", enemy.x, enemy.y - 10f, Color(0xFFFAFAFA))
        }

        if (enemy.hp <= 0) {
            killEnemy(enemy)
        }
    }

    private fun killEnemy(enemy: Enemy) {
        enemies.remove(enemy)
        score.value += enemy.scoreValue

        // Trigger Synth Explosion
        audioEngine.playSound(SoundType.EXPLOSION, 0.32f)

        // Drop EXP shards or Coin Gold
        val isBossGoldChest = enemy.type == EnemyType.BOSS
        if (isBossGoldChest) {
            bossSpawned.value = false
            // Drop super Gold coins chest
            for (i in 0 until 10) {
                expGems.add(
                    ExpGem(
                        id = random.nextLong(),
                        x = enemy.x + random.nextInt(80) - 40,
                        y = enemy.y + random.nextInt(80) - 40,
                        expAmount = 150,
                        isGold = true,
                        creditsAmount = 15
                    )
                )
            }
            audioEngine.playSound(SoundType.LEVEL_UP, 0.7f)
            addNotification("🏆 战役大捷! 歼灭宇宙首领！获得大量金币和能量奖励！")
        } else {
            // Standard small drops
            val dropGold = random.nextFloat() < 0.22f + (enemy.creditValue * 0.05f)
            expGems.add(
                ExpGem(
                    id = random.nextLong(),
                    x = enemy.x,
                    y = enemy.y,
                    expAmount = if (enemy.type == EnemyType.ELITE) 100 else 18,
                    isGold = dropGold,
                    creditsAmount = enemy.creditValue
                )
            )
        }

        // Particle sparks
        spawnExplosionParticles(enemy.x, enemy.y, enemy.type.color, if (enemy.type == EnemyType.ELITE) 15 else 6)
    }

    private fun gainExp(amount: Int) {
        exp.value += amount
        if (exp.value >= expNeeded.value) {
            exp.value -= expNeeded.value
            techLevel.value += 1
            expNeeded.value = (techLevel.value * 110 + 160)
            triggerLevelUpSelection()
        }
    }

    private fun triggerLevelUpSelection() {
        audioEngine.playSound(SoundType.LEVEL_UP, 0.65f)
        pauseGame()
        _gameState.value = GameStatus.LEVEL_UP_CHOICE

        // Generate 3 random, distinct upgrade cards
        levelUpChoices.clear()
        val pool = generateUpgradePool()
        levelUpChoices.addAll(pool.shuffled().take(3))
    }

    private fun generateUpgradePool(): List<UpgradeOption> {
        val pool = mutableListOf<UpgradeOption>()

        // Adding stat upgrades (always available)
        pool.add(UpgradeOption("REPAIR", "核融合纳米修复", "即时焊接重组，修补恢复 35% 装甲耐久值度。", "❤", null))
        pool.add(UpgradeOption("CD_SPEED", "火控超频超导板", "全机载战甲武器射速提升 10%。", "⏳", null))
        pool.add(UpgradeOption("HP_POOL", "矩阵质子阻断盾", "增加机动飞翼上限，装甲最大值提升 15% 并满血复原。", "🛡", null))
        pool.add(UpgradeOption("CRIT_UP", "战术态势热校准", "武器致命爆头暴击率增加 10%。", "🎯", null))
        pool.add(UpgradeOption("MAGNET_UP", "极星纳米牵引锁", "提升 30% 引力引港范围，安全吸附周围能量极质晶片。", "🧲", null))

        // Adding weapons unlocks or increments
        for (w in WeaponType.values()) {
            val active = activeWeapons.find { it.type == w }
            if (active == null) {
                // Unlock card
                pool.add(UpgradeOption("WEAPON_UNLOCK", "激活：${w.techName}", "解锁并组装一级星轨挂件 [${w.techName}]，发挥强悍效能。 ${w.description}", w.iconSymbol, w))
            } else if (active.level < 5) {
                // Level card
                val nextLv = active.level + 1
                pool.add(UpgradeOption("WEAPON_UPGRADE", "升级：${w.techName} (Lv $nextLv)", "使机载 [${w.techName}] 提升到第 ${nextLv} 等级，大幅度强化攻击覆盖面、弹数和破坏。 ${w.description}", w.iconSymbol, w))
            }
        }
        return pool
    }

    fun chooseUpgrade(option: UpgradeOption) {
        when (option.type) {
            "REPAIR" -> {
                shipHp.value = (shipHp.value + shipMaxHp.value * 0.35f).coerceAtMost(shipMaxHp.value)
                addNotification("装甲修复完毕！")
            }
            "CD_SPEED" -> {
                // handled dynamically in calculations
                addNotification("副炮火能武器充能速度提升 10%！")
            }
            "HP_POOL" -> {
                shipMaxHp.value += shipMaxHp.value * 0.15f
                shipHp.value = shipMaxHp.value
                addNotification("纳米飞翼重组！装甲承载极限增加 15%")
            }
            "CRIT_UP" -> {
                critChance += 0.10f
                addNotification("战术火力校正完毕，暴击率 +10%！")
            }
            "MAGNET_UP" -> {
                magnetRange *= 1.30f
                addNotification("量子微粒磁组引力范围额外增强 30%！")
            }
            "WEAPON_UNLOCK" -> {
                val wt = option.targetWeapon ?: return
                activeWeapons.add(ActiveWeapon(wt, 1))
                addNotification("解锁科技：${wt.techName} (Lv 1)！")
            }
            "WEAPON_UPGRADE" -> {
                val wt = option.targetWeapon ?: return
                val active = activeWeapons.find { it.type == wt }
                if (active != null) {
                    active.level += 1
                    addNotification("战术扩能：${wt.techName} 升级到 Lv ${active.level}！")
                }
            }
        }

        audioEngine.playSound(SoundType.SHIELD_UP, 0.5f)
        _gameState.value = GameStatus.RUNNING
        startGameLoop()
    }

    private fun triggerGameOver() {
        _gameState.value = GameStatus.GAME_OVER
        gameLoopJob?.cancel()

        viewModelScope.launch {
            audioEngine.playSound(SoundType.EXPLOSION, 0.7f)
            // Save run credits and high scores to SQL Database
            repository.addCredits(creditsEarned.value)
            repository.updateHighScore(score.value)
        }
    }

    // --- Utility Visual Particle Generators ---

    private fun spawnExplosionParticles(x: Float, y: Float, color: Color, count: Int) {
        val num = count.coerceAtMost(30)
        for (i in 0 until num) {
            val angle = random.nextFloat() * 2 * Math.PI
            val speed = random.nextFloat() * 3.5f + 1f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    color = color,
                    size = random.nextFloat() * 8f + 4f,
                    maxLife = random.nextInt(20) + 12
                )
            )
        }
    }

    private fun addFloatingText(text: String, x: Float, y: Float, color: Color, isBig: Boolean = false) {
        floatingTexts.add(FloatingText(random.nextLong(), text, x, y, color, isBig))
    }

    // List notifications for on-screen log ticker
    val activeLogList = mutableStateListOf<String>()
    private fun addNotification(msg: String) {
        activeLogList.add(msg)
        if (activeLogList.size > 5) {
            activeLogList.removeAt(0)
        }
    }

    // --- Shop Purchase Operations ---

    fun purchaseShopUpgrade(statType: String, cost: Int) {
        viewModelScope.launch {
            val stats = repository.getStatsDirect()
            if (stats.credits < cost) return@launch
            
            val success = repository.upgradeTier(statType, cost)
            if (success) {
                audioEngine.playSound(SoundType.LEVEL_UP, 0.6f)
            }
        }
    }

    fun purchaseUnlockShip(shipId: String, cost: Int) {
        viewModelScope.launch {
            val stats = repository.getStatsDirect()
            if (stats.credits < cost) return@launch

            val success = repository.unlockShip(shipId, cost)
            if (success) {
                audioEngine.playSound(SoundType.LEVEL_UP, 0.6f)
            }
        }
    }

    fun selectActiveShip(shipId: String) {
        viewModelScope.launch {
            repository.selectShip(shipId)
            audioEngine.playSound(SoundType.SHIELD_UP, 0.5f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}

enum class GameStatus {
    START_SCREEN,
    RUNNING,
    PAUSED,
    LEVEL_UP_CHOICE,
    GAME_OVER
}

data class UpgradeOption(
    val type: String, // REPAIR, HP_POOL, CD_SPEED, MAGNET_UP, CRIT_UP, WEAPON_UNLOCK, WEAPON_UPGRADE
    val title: String,
    val text: String,
    val iconSymbol: String,
    val targetWeapon: WeaponType?
)
