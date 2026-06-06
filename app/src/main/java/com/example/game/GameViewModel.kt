package com.example.game

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt

// UI state representations
sealed interface GameUiState {
    object MainMenu : GameUiState
    object InGame : GameUiState
    object GameOver : GameUiState
    object GameVictory : GameUiState
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GameDatabase.getDatabase(application)
    private val repository = GameRepository(db.gameSaveDao())

    // Game States
    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.MainMenu)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Screen Shake Offset
    private val _screenShake = MutableStateFlow(Vector2D(0f, 0f))
    val screenShake: StateFlow<Vector2D> = _screenShake.asStateFlow()

    // Text Notification Overlay
    private val _hudNotification = MutableStateFlow<String?>(null)
    val hudNotification: StateFlow<String?> = _hudNotification.asStateFlow()

    // Room upgrade screen active
    private val _isUpgradeMenuOpen = MutableStateFlow(false)
    val isUpgradeMenuOpen: StateFlow<Boolean> = _isUpgradeMenuOpen.asStateFlow()

    // Sector dimensions and map
    var roomGridX = 0
    var roomGridY = 0

    // Sectors definitions
    val sectorsMap = mapOf(
        Pair(0, 0) to Sector(0, 0, "Zen Oasis (Start)", Color(0xFF4CAF50), Color(0xFF1E351E), Color(0xFF2E4E2E), "Tutorial: Move with the joystick. Upgrade at glowing pylons!", false, false),
        Pair(1, 0) to Sector(1, 0, "Emerald Shard Caverns", Color(0xFF00E676), Color(0xFF0D2513), Color(0xFF183D21), "Crystal crypt! Shoot the bright stones to harvest valuable Shards.", false, false),
        Pair(2, 0) to Sector(2, 0, "Guarded Outpost", Color(0xFFFF9100), Color(0xFF33200D), Color(0xFF4E3113), "Heavily defended. Avoid laser circles!", false, false),
        Pair(0, 1) to Sector(0, 1, "Golden Sandy Ruins", Color(0xFFFFD600), Color(0xFF2C2700), Color(0xFF423B00), "Fast mechanical crawlers inhabit this desert sector.", false, false),
        Pair(1, 1) to Sector(1, 1, "The Core Dungeon Ruins", Color(0xFF00E5FF), Color(0xFF0A2229), Color(0xFF123641), "The Altar is located here. Find the Security Card!", true, false),
        Pair(2, 1) to Sector(2, 1, "Sulfur Volcano Depths", Color(0xFFFF5252), Color(0xFF270E0D), Color(0xFF3D1614), "The volcanic streams bubble. Dashing avoids damage.", false, false),
        Pair(0, 2) to Sector(0, 2, "Verdant Hedge Labyrinth", Color(0xFF81C784), Color(0xFF0F1B12), Color(0xFF1B2F1F), "Navigate the hedge rows. Treasure chests lie hidden.", false, false),
        Pair(1, 2) to Sector(1, 2, "Sector Volt Fortification", Color(0xFF7C4DFF), Color(0xFF140D2F), Color(0xFF201549), "Dangerous stationary turrets. Destroy them from afar!", false, false),
        Pair(2, 2) to Sector(2, 2, "VOID REACTOR CHAMBER (Boss)", Color(0xFFD500F9), Color(0xFF160321), Color(0xFF2A063C), "WARNING: Massive bio-reactor active. Slay it to claim ultimate victory!", false, true)
    )

    // Dynamic Lists inside Sector
    val enemies = MutableStateFlow<List<Enemy>>(emptyList())
    val bullets = MutableStateFlow<List<Bullet>>(emptyList())
    val collectibles = MutableStateFlow<List<Collectible>>(emptyList())
    val obstacles = MutableStateFlow<List<Obstacle>>(emptyList())
    val particles = MutableStateFlow<List<Particle>>(emptyList())

    // Player Stats Exposes
    val playerHealth = MutableStateFlow(4f)
    val playerMaxHealth = MutableStateFlow(4f)
    val playerShards = MutableStateFlow(0)
    val playerLevel = MutableStateFlow(1)
    val playerXp = MutableStateFlow(0)
    val hasGreenKeycard = MutableStateFlow(false)

    // Upgrade Levels
    val upgradeHealthLevel = MutableStateFlow(1)
    val upgradeSpeedLevel = MutableStateFlow(1)
    val upgradeDamageLevel = MutableStateFlow(1)
    val upgradeFireRateLevel = MutableStateFlow(1)

    // Target stats (Calculated dynamically)
    val playerSpeed: Float get() = 220f + (upgradeSpeedLevel.value - 1) * 35f
    val bulletDamage: Float get() = 12f + (upgradeDamageLevel.value - 1) * 6f
    val fireRateDelayMs: Long get() = (500f / (1f + (upgradeFireRateLevel.value - 1) * 0.35f)).toLong()

    // Player position internals
    var playerPos = Vector2D(600f, 600f)
    var playerVelocity = Vector2D(0f, 0f)
    var playerAngle = 0f
    var playerInvincibilityTimer = 0f
    var lastShotTime = 0L

    // Dash control
    var isDashing = false
    var dashTimer = 0f
    var dashCooldown = 0f
    var dashDirection = Vector2D(1f, 0f)

    // Engine loop tracker
    private var isEngineRunning = false
    private var shakeDuration = 0f
    private var shakeIntensity = 0f

    // Sector completion and spawning
    private var spawnedRooms = mutableSetOf<String>()
    private var clearedRooms = mutableSetOf<String>()
    private var bossTriggered = false
    private var bossDefeated = false

    init {
        loadSavedGame()
    }

    private fun loadSavedGame() {
        viewModelScope.launch {
            val saved = repository.getSaveStateDirect() ?: GameSaveState()
            applySaveState(saved)
        }
    }

    private fun applySaveState(saved: GameSaveState) {
        playerShards.value = saved.shards
        playerLevel.value = saved.playerLevel
        playerXp.value = saved.currXp
        upgradeHealthLevel.value = saved.maxHealthLevel
        upgradeSpeedLevel.value = saved.speedLevel
        upgradeDamageLevel.value = saved.damageLevel
        upgradeFireRateLevel.value = saved.fireRateLevel
        hasGreenKeycard.value = saved.hasGreenKey

        // Cap health appropriately
        val calculatedMaxHp = 4f + (saved.maxHealthLevel - 1) * 1f
        playerMaxHealth.value = calculatedMaxHp
        playerHealth.value = calculatedMaxHp

        // Position & Sector restoring
        roomGridX = saved.roomGridX
        roomGridY = saved.roomGridY

        playerPos = Vector2D(600f, 600f)
        playerVelocity = Vector2D(0f, 0f)

        // Clear rooms and load
        clearedRooms.clear()
        if (saved.clearedSectors.isNotEmpty()) {
            saved.clearedSectors.split(";").forEach {
                if (it.isNotBlank()) clearedRooms.add(it)
            }
        }

        initializeSector(roomGridX, roomGridY)
    }

    fun saveCurrentProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            val strCleared = clearedRooms.joinToString(";")
            val state = GameSaveState(
                id = 1,
                shards = playerShards.value,
                playerLevel = playerLevel.value,
                currXp = playerXp.value,
                maxHealthLevel = upgradeHealthLevel.value,
                speedLevel = upgradeSpeedLevel.value,
                damageLevel = upgradeDamageLevel.value,
                fireRateLevel = upgradeFireRateLevel.value,
                hasGreenKey = hasGreenKeycard.value,
                roomGridX = roomGridX,
                roomGridY = roomGridY,
                clearedSectors = strCleared
            )
            repository.saveState(state)
        }
    }

    fun resetGame() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearState()
            val cleanState = GameSaveState()
            repository.saveState(cleanState)
            viewModelScope.launch(Dispatchers.Main) {
                applySaveState(cleanState)
                _uiState.value = GameUiState.InGame
                bossTriggered = false
                bossDefeated = false
                postNotification("Adventure Reset! Fly safe, Commander.")
            }
        }
    }

    fun startGame() {
        _uiState.value = GameUiState.InGame
        startTickEngine()
        postNotification("Explore with virtual stick. Find crystals and upgrades!")
    }

    fun closeUpgradeMenu() {
        _isUpgradeMenuOpen.value = false
    }

    fun openUpgradeMenu() {
        _isUpgradeMenuOpen.value = true
    }

    fun purchaseUpgrade(type: String) {
        val currentLevel = when (type) {
            "health" -> upgradeHealthLevel.value
            "speed" -> upgradeSpeedLevel.value
            "damage" -> upgradeDamageLevel.value
            "firerate" -> upgradeFireRateLevel.value
            else -> 1
        }

        if (currentLevel >= 5) {
            postNotification("Stat is already at Maximum Level!")
            return
        }

        // Pricing logic: Level 1->2 costs 15, 2->3 costs 35, 3->4 costs 70, 4->5 costs 120
        val cost = when (currentLevel) {
            1 -> 15
            2 -> 35
            3 -> 70
            4 -> 120
            else -> 150
        }

        if (playerShards.value >= cost) {
            playerShards.value -= cost
            when (type) {
                "health" -> {
                    upgradeHealthLevel.value++
                    playerMaxHealth.value = 4f + (upgradeHealthLevel.value - 1) * 1f
                    playerHealth.value = playerMaxHealth.value // Heal player on heart increase!
                    postNotification("Health upgraded! Recalibrating Shields.")
                    spawnSparkCircle(playerPos, Color.Red, 20)
                }
                "speed" -> {
                    upgradeSpeedLevel.value++
                    postNotification("Engine upgraded! Overclocking thrusters.")
                    spawnSparkCircle(playerPos, Color.Cyan, 20)
                }
                "damage" -> {
                    upgradeDamageLevel.value++
                    postNotification("Blaster upgraded! Weapon yield increased.")
                    spawnSparkCircle(playerPos, Color.Magenta, 20)
                }
                "firerate" -> {
                    upgradeFireRateLevel.value++
                    postNotification("Capacitors upgraded! Blaster cooldown shortened.")
                    spawnSparkCircle(playerPos, Color.Yellow, 20)
                }
            }
            saveCurrentProgress()
        } else {
            postNotification("Insufficient Shards! Need $cost (You have ${playerShards.value})")
        }
    }

    // Engine loop tick initiator
    private fun startTickEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        viewModelScope.launch(Dispatchers.Default) {
            var lastTime = System.nanoTime()
            while (isEngineRunning) {
                val now = System.nanoTime()
                var dt = (now - lastTime) / 1_000_000_000f
                lastTime = now

                // Limit dt spikes
                if (dt > 0.15f) dt = 0.15f

                if (_uiState.value == GameUiState.InGame && !_isUpgradeMenuOpen.value) {
                    tickPhysics(dt)
                }

                delay(16) // ~60fps ticks
            }
        }
    }

    fun stopTickEngine() {
        isEngineRunning = false
    }

    // Handle incoming inputs
    fun onMoveInput(dx: Float, dy: Float) {
        if (isDashing) return // Cannot deviate while dashing
        playerVelocity = Vector2D(dx, dy) * playerSpeed
        if (dx != 0f || dy != 0f) {
            playerAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
    }

    fun triggerDash() {
        if (dashCooldown > 0f || isDashing) return
        isDashing = true
        dashTimer = GameParams.DASH_DURATION
        dashCooldown = GameParams.DASH_COOLDOWN

        // Use movement vector, or current facing angle if stopped
        val currentMovement = playerVelocity.normalized()
        dashDirection = if (currentMovement.length() > 0.1f) {
            currentMovement
        } else {
            val rads = Math.toRadians(playerAngle.toDouble())
            Vector2D(cos(rads).toFloat(), sin(rads).toFloat())
        }

        playerVelocity = dashDirection * (playerSpeed * GameParams.SHIP_DASH_SPEED_MULTIPLIER)
        playerInvincibilityTimer = GameParams.DASH_DURATION // Immune while dashing!

        // Generate spark ripples
        spawnSparkCircle(playerPos, Color(0xFF00E5FF), 15)
        spawnFloatingText(playerPos, "BOOST!", Color(0xFF00E5FF))
    }

    private fun startScreenShake(intensity: Float, duration: Float) {
        shakeIntensity = intensity
        shakeDuration = duration
    }

    private fun postNotification(text: String) {
        _hudNotification.value = text
        // Automatically hide notification after 4.5 seconds
        viewModelScope.launch {
            delay(4500)
            if (_hudNotification.value == text) {
                _hudNotification.value = null
            }
        }
    }

    // MAP INITIALIZER
    private fun initializeSector(x: Int, y: Int) {
        roomGridX = x
        roomGridY = y

        val sectorKey = "$x,$y"
        val sectorInfo = sectorsMap[Pair(x, y)] ?: return

        // Clear elements
        bullets.value = emptyList()
        collectibles.value = emptyList()
        particles.value = emptyList()

        postNotification("Entering Sector: ${sectorInfo.name}")

        // Build walls & shrine beacons
        val listObstacles = mutableListOf<Obstacle>()

        // Draw boundaries
        // Shrines/Beacons in Sector (0,0) and Sector (1,2)
        if (x == 0 && y == 0) {
            listObstacles.add(Obstacle("shrine_start", Vector2D(350f, 350f), 45f, Obstacle.Type.SHRINE_BEACON))
            listObstacles.add(Obstacle("decor_col_a", Vector2D(800f, 250f), 30f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("decor_col_b", Vector2D(800f, 850f), 30f, Obstacle.Type.RUINED_COLUMN))
        }

        if (x == 1 && y == 2) {
            listObstacles.add(Obstacle("shrine_volt", Vector2D(600f, 600f), 45f, Obstacle.Type.SHRINE_BEACON))
        }

        // Room (1,0) has Emerald Shard Caverns. Let's add crystals that break to reveal huge shard volumes!
        if (x == 1 && y == 0) {
            for (i in 0..6) {
                val cx = 200f + (i * 150f)
                val cy = 300f + (sin(i.toDouble()) * 200f).toFloat()
                listObstacles.add(Obstacle("crystal_$i", Vector2D(cx, cy), 35f, Obstacle.Type.GLOWING_CRYSTAL, true, 25f, 25f))
            }
            // Add locked east gate
            listObstacles.add(Obstacle("locked_east_gate", Vector2D(1170f, 600f), 80f, Obstacle.Type.LOCKED_GATE))
        }

        // Room (1,1) contains the Altar Chest that holds the Security Key Card
        if (x == 1 && y == 1) {
            listObstacles.add(Obstacle("altar_key_chest", Vector2D(600f, 600f), 50f, Obstacle.Type.ALTAR_CHEST, true, 40f, 40f))
            listObstacles.add(Obstacle("altar_col_1", Vector2D(450f, 450f), 30f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("altar_col_2", Vector2D(750f, 450f), 30f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("altar_col_3", Vector2D(450f, 750f), 30f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("altar_col_4", Vector2D(750f, 750f), 30f, Obstacle.Type.RUINED_COLUMN))
        }

        // Volcano room blocks
        if (x == 2 && y == 1) {
            listObstacles.add(Obstacle("lava_pool_1", Vector2D(400f, 300f), 120f, Obstacle.Type.LAVA_POOL, false))
            listObstacles.add(Obstacle("lava_pool_2", Vector2D(800f, 900f), 140f, Obstacle.Type.LAVA_POOL, false))
        }

        // Green Forest Obstacles
        if (x == 0 && y == 2) {
            // Place trees & rocks creating a labyrinth
            listObstacles.add(Obstacle("tree_maze_1", Vector2D(200f, 300f), 40f, Obstacle.Type.TREE_GREEN))
            listObstacles.add(Obstacle("tree_maze_2", Vector2D(400f, 300f), 40f, Obstacle.Type.TREE_GREEN))
            listObstacles.add(Obstacle("tree_maze_3", Vector2D(600f, 300f), 40f, Obstacle.Type.TREE_GREEN))
            listObstacles.add(Obstacle("tree_maze_4", Vector2D(800f, 300f), 40f, Obstacle.Type.TREE_GREEN))

            listObstacles.add(Obstacle("rock_maze_1", Vector2D(400f, 600f), 50f, Obstacle.Type.FOREST_ROCK))
            listObstacles.add(Obstacle("rock_maze_2", Vector2D(800f, 600f), 50f, Obstacle.Type.FOREST_ROCK))

            listObstacles.add(Obstacle("tree_maze_5", Vector2D(200f, 900f), 40f, Obstacle.Type.TREE_GREEN))
            listObstacles.add(Obstacle("tree_maze_6", Vector2D(600f, 900f), 40f, Obstacle.Type.TREE_GREEN))
            listObstacles.add(Obstacle("tree_maze_7", Vector2D(1000f, 900f), 40f, Obstacle.Type.TREE_GREEN))
        }

        // Golden dunes rocks
        if (x == 0 && y == 1) {
            listObstacles.add(Obstacle("sand_col1", Vector2D(300f, 400f), 40f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("sand_col2", Vector2D(900f, 800f), 40f, Obstacle.Type.RUINED_COLUMN))
            listObstacles.add(Obstacle("sand_rock1", Vector2D(600f, 250f), 55f, Obstacle.Type.FOREST_ROCK))
        }

        obstacles.value = listObstacles

        // Enemy Spawns: only spawn if the room has not been marked "cleared" before
        val listEnemies = mutableListOf<Enemy>()
        val clearId = "$x,$y"

        if (!clearedRooms.contains(clearId)) {
            // Spawn based on Sector Theme
            when {
                x == 0 && y == 0 -> {
                    // Start oasis, 3 simple slime enemies
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(800f, 300f), Vector2D(0f, 0f), 15f, 15f, Enemy.Type.SLIME, 1f, 65f, 0f, 30f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(900f, 600f), Vector2D(0f, 0f), 15f, 15f, Enemy.Type.SLIME, 1f, 65f, 0f, 30f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(850f, 800f), Vector2D(0f, 0f), 15f, 15f, Enemy.Type.SLIME, 1f, 65f, 0f, 30f))
                }
                x == 1 && y == 0 -> {
                    // Shard Caverns. Spiders that shoot!
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(400f, 700f), Vector2D(0f, 0f), 20f, 20f, Enemy.Type.SCUTTLER, 1f, 110f, 0f, 25f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(800f, 700f), Vector2D(0f, 0f), 20f, 20f, Enemy.Type.SCUTTLER, 1f, 110f, 0f, 25f))
                }
                x == 2 && y == 0 -> {
                    // Guarded Outpost: Drones and Turrets
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(300f, 300f), Vector2D(0f, 0f), 25f, 25f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 1.5f, 35f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(900f, 300f), Vector2D(0f, 0f), 25f, 25f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 2.0f, 35f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(600f, 850f), Vector2D(0f, 0f), 30f, 30f, Enemy.Type.DRONE, 1f, 80f, 0.5f, 32f))
                }
                x == 0 && y == 1 -> {
                    // Golden Dunes. Fast scuttlers
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(500f, 500f), Vector2D(0f, 0f), 22f, 22f, Enemy.Type.SCUTTLER, 1f, 130f, 0f, 25f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(700f, 600f), Vector2D(0f, 0f), 22f, 22f, Enemy.Type.SCUTTLER, 1f, 130f, 0f, 25f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(350f, 800f), Vector2D(0f, 0f), 22f, 22f, Enemy.Type.SLIME, 1f, 75f, 0f, 30f))
                }
                x == 1 && y == 1 -> {
                    // Core Ruins. Guard defense drones!
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(300f, 600f), Vector2D(0f, 0f), 35f, 35f, Enemy.Type.DRONE, 1f, 90f, 1.2f, 32f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(900f, 600f), Vector2D(0f, 0f), 35f, 35f, Enemy.Type.DRONE, 1f, 90f, 1.7f, 32f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(600f, 300f), Vector2D(0f, 0f), 35f, 35f, Enemy.Type.DRONE, 1f, 90f, 0.4f, 32f))
                }
                x == 2 && y == 1 -> {
                    // Volcanic Forge.
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(500f, 500f), Vector2D(0f, 0f), 40f, 40f, Enemy.Type.SLIME, 1.5f, 95f, 0f, 35f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(800f, 450f), Vector2D(0f, 0f), 25f, 25f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 1.0f, 35f))
                }
                x == 0 && y == 2 -> {
                    // Labyrinth: crawlers and drones
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(200f, 500f), Vector2D(0f, 0f), 20f, 20f, Enemy.Type.SCUTTLER, 1f, 140f, 0f, 25f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(1000f, 500f), Vector2D(0f, 0f), 20f, 20f, Enemy.Type.SCUTTLER, 1f, 140f, 0f, 25f))
                }
                x == 1 && y == 2 -> {
                    // Volt Shrine. Heavy turret squad!
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(300f, 300f), Vector2D(0f, 0f), 30f, 30f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 0.8f, 35f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(900f, 900f), Vector2D(0f, 0f), 30f, 30f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 1.8f, 35f))
                    listEnemies.add(Enemy(UUID.randomUUID().toString(), Vector2D(300f, 900f), Vector2D(0f, 0f), 30f, 30f, Enemy.Type.STATIONARY_TURRET, 1f, 0f, 1.3f, 35f))
                }
                x == 2 && y == 2 -> {
                    // BOSS ARENA!
                    bossTriggered = true
                    bossDefeated = false
                    listEnemies.add(Enemy("colossal_void_boss", Vector2D(600f, 500f), Vector2D(0f, 0f), 350f, 350f, Enemy.Type.COLOSSAL_BOSS, 1f, 40f, 2.0f, 90f))
                    postNotification("⚠️ VOID REACTOR MAIN CELL SPARKS! ELIMINATE THE Boss!")
                }
            }
        } else {
            postNotification("Cleared Sector - Safe Zone")
        }

        enemies.value = listEnemies
        saveCurrentProgress()
    }

    // Dynamic Engine physics ticker
    private fun tickPhysics(dt: Float) {
        // 1. Update Shake
        if (shakeDuration > 0f) {
            shakeDuration -= dt
            val rx = ((-100..100).random() / 100f) * shakeIntensity
            val ry = ((-100..100).random() / 100f) * shakeIntensity
            _screenShake.value = Vector2D(rx, ry)
        } else {
            _screenShake.value = Vector2D(0f, 0f)
        }

        // 2. Cooldowns
        if (playerInvincibilityTimer > 0f) {
            playerInvincibilityTimer -= dt
        }
        if (dashCooldown > 0f) {
            dashCooldown -= dt
        }

        // 3. Dash Handling
        if (isDashing) {
            dashTimer -= dt
            if (dashTimer <= 0f) {
                isDashing = false
                playerVelocity = Vector2D(0f, 0f) // stop dash velocity spike
            } else {
                // Emit trailing engine sparks
                spawnParticle(playerPos, playerVelocity * -0.3f, Color(0x6600E5FF), 15f, 0.4f, Particle.Type.ENGINE_TRAIL)
            }
        }

        // 4. Update Player Position & Room Boundaries
        val prevPos = playerPos
        var targetPos = playerPos + playerVelocity * dt

        // Boundaries checks + obstacles colliders
        targetPos = clampToRoom(targetPos, prevPos)
        playerPos = targetPos

        // Check if player reaches gateways and rooms change
        boundaryRoomTransitions()

        // 5. Weapon Auto firing: Shoot nearest enemy automatically if timing allows
        triggerWeaponAutoFiring()

        // 6. Update Bullets
        val activeBullets = bullets.value.toMutableList()
        val bulletIterator = activeBullets.iterator()
        val nextBullets = mutableListOf<Bullet>()

        for (bullet in activeBullets) {
            bullet.position += bullet.velocity * dt

            // Update tail trails
            val trail = bullet.trail
            trail.add(0, bullet.position)
            if (trail.size > 5) {
                trail.removeAt(5)
            }

            var active = true
            // Room Edge death
            if (bullet.position.x < 0f || bullet.position.x > GameParams.ROOM_SIZE ||
                bullet.position.y < 0f || bullet.position.y > GameParams.ROOM_SIZE) {
                active = false
                spawnImpactSparks(bullet.position, bullet.color, 5)
            }

            // Collide with Obstacles
            val currentObstacles = obstacles.value
            for (obs in currentObstacles) {
                if (obs.isCollidable && obs.position.distance(bullet.position) <= obs.radius + bullet.radius) {
                    active = false
                    spawnImpactSparks(bullet.position, bullet.color, 6)

                    // If it is a breakable gemstone crystal, reduce health
                    if (obs.type == Obstacle.Type.GLOWING_CRYSTAL) {
                        damageObstacle(obs, bullet.damage)
                    } else if (obs.type == Obstacle.Type.ALTAR_CHEST) {
                        damageObstacle(obs, bullet.damage)
                    }
                    break
                }
            }

            if (active) {
                nextBullets.add(bullet)
            }
        }
        bullets.value = nextBullets

        // 7. Update Collectibles Magnetic Force
        val activeCollectibles = collectibles.value.toMutableList()
        val nextCollectibles = mutableListOf<Collectible>()
        for (col in activeCollectibles) {
            val dist = col.position.distance(playerPos)
            if (dist < 180f) {
                col.isAttracted = true
            }

            if (col.isAttracted) {
                val dir = (playerPos - col.position).normalized()
                col.velocity += dir * 450f * dt // Accelerate towards player
                // Cap speed
                if (col.velocity.length() > 600f) {
                    col.velocity = col.velocity.normalized() * 600f
                }
                col.position += col.velocity * dt
            }

            // Magnet absorption collider
            if (dist < 30f) {
                // Collect item!
                if (col.type == Collectible.Type.SHARD) {
                    playerShards.value += col.valAmount
                    spawnFloatingText(col.position, "+${col.valAmount} Shard", Color(0xFFFFD600))
                    // Earn XP for Levelups!
                    addXp(col.valAmount * 3)
                    spawnSparkCircle(col.position, Color(0xFFFFD600), 10)
                } else if (col.type == Collectible.Type.HEART) {
                    playerHealth.value = (playerHealth.value + 1f).coerceAtMost(playerMaxHealth.value)
                    spawnFloatingText(col.position, "+1 Integrity", Color.Red)
                    spawnSparkCircle(col.position, Color.Red, 12)
                }

                saveCurrentProgress()
            } else {
                nextCollectibles.add(col)
            }
        }
        collectibles.value = nextCollectibles

        // 8. Update Enemies
        val activeEnemies = enemies.value.toMutableList()
        val nextEnemies = mutableListOf<Enemy>()
        val bulletList = bullets.value

        for (enemy in activeEnemies) {
            enemy.shootTimer += dt

            // AI movement paths
            when (enemy.type) {
                Enemy.Type.SLIME -> {
                    // Small hops towards player
                    val dir = (playerPos - enemy.position).normalized()
                    enemy.wanderAngle += dt * 3f
                    val bob = sin(enemy.wanderAngle) * 20f
                    enemy.velocity = dir * enemy.speed + Vector2D(cos(enemy.wanderAngle).toFloat(), sin(enemy.wanderAngle).toFloat()) * bob
                    enemy.position += enemy.velocity * dt
                }
                Enemy.Type.DRONE -> {
                    // Back and forth strafing circular orbit
                    val toPlayer = playerPos - enemy.position
                    val dist = toPlayer.length()
                    val dir = toPlayer.normalized()
                    if (dist > 300f) {
                        enemy.velocity = dir * enemy.speed
                    } else if (dist < 180f) {
                        enemy.velocity = dir * -enemy.speed
                    } else {
                        // Orbit!
                        enemy.velocity = Vector2D(-dir.y, dir.x) * enemy.speed
                    }
                    enemy.position += enemy.velocity * dt

                    // Shoot plasma bullet periodically
                    if (enemy.shootTimer >= 2.0f) {
                        enemy.shootTimer = 0f
                        shootEnemyBullet(enemy.position, (playerPos - enemy.position).normalized(), Color(0xFF00E676), 80f, 1f)
                    }
                }
                Enemy.Type.SCUTTLER -> {
                    // Sudden fast sprints, pauses to target
                    enemy.shootTimer += dt
                    val dir = (playerPos - enemy.position).normalized()
                    if (enemy.shootTimer < 1.2f) {
                        enemy.velocity = dir * enemy.speed
                    } else if (enemy.shootTimer < 2.0f) {
                        enemy.velocity = Vector2D(0f,0f) // pause
                    } else {
                        enemy.shootTimer = 0f
                        // Spray 3 small burst bullets
                        val angleToPlayer = atan2(dir.y, dir.x)
                        for (i in -1..1) {
                            val fAngle = angleToPlayer + i * 0.25f
                            val bDir = Vector2D(cos(fAngle).toFloat(), sin(fAngle).toFloat())
                            shootEnemyBullet(enemy.position, bDir, Color(0xFFFF5252), 110f, 1f)
                        }
                    }
                    enemy.position += enemy.velocity * dt
                }
                Enemy.Type.STATIONARY_TURRET -> {
                    // Shoots dense circular spirals or circles
                    if (enemy.shootTimer >= 3.0f) {
                        enemy.shootTimer = 0f
                        val segments = 8
                        for (i in 0 until segments) {
                            val angle = (2 * Math.PI * i / segments).toFloat()
                            val bDir = Vector2D(cos(angle), sin(angle))
                            shootEnemyBullet(enemy.position, bDir, Color(0xFFFF9100), 90f, 1f)
                        }
                    }
                }
                Enemy.Type.COLOSSAL_BOSS -> {
                    // Big movement floating around center
                    val center = Vector2D(600f, 400f)
                    val toCenter = center - enemy.position
                    val distCent = toCenter.length()
                    enemy.wanderAngle += dt * 0.8f
                    val circleX = 600f + cos(enemy.wanderAngle) * 200f
                    val circleY = 400f + sin(enemy.wanderAngle) * 100f
                    enemy.position = Vector2D(circleX, circleY)

                    // Boss weapon phases based on current health!
                    val progress = enemy.health / enemy.maxHealth
                    if (progress > 0.66f) {
                        // Phase 1: Radial bursts every 1.5s
                        if (enemy.shootTimer >= 1.5f) {
                            enemy.shootTimer = 0f
                            val arcs = 12
                            for (i in 0 until arcs) {
                                val angle = (2 * Math.PI * i / arcs).toFloat()
                                val bDir = Vector2D(cos(angle), sin(angle))
                                shootEnemyBullet(enemy.position, bDir, Color(0xFFD500F9), 110f, 1f)
                            }
                            spawnFloatingText(enemy.position, "RADIAL FLOOD!", Color(0xFFD500F9))
                        }
                    } else if (progress > 0.33f) {
                        // Phase 2: Rapid homing rocket streams aiming at players
                        if (enemy.shootTimer >= 0.75f) {
                            enemy.shootTimer = 0f
                            val rightDir = (playerPos - enemy.position).normalized()
                            shootEnemyBullet(enemy.position, rightDir, Color(0xFFFF1744), 160f, 1.5f)
                            // Double wing cannons
                            val pAngle = atan2(rightDir.y, rightDir.x)
                            val wing1 = pAngle + 0.5f
                            val wing2 = pAngle - 0.5f
                            shootEnemyBullet(enemy.position, Vector2D(cos(wing1), sin(wing1)), Color(0xFFFFD600), 130f, 1f)
                            shootEnemyBullet(enemy.position, Vector2D(cos(wing2), sin(wing2)), Color(0xFFFFD600), 130f, 1f)
                        }
                    } else {
                        // Phase 3: SPIRAL HELLfire!
                        if (enemy.shootTimer >= 0.12f) {
                            enemy.shootTimer = 0f
                            // Increment spiral angle based on time
                            val step = (System.currentTimeMillis() % 10000) / 10000f * 2f * Math.PI.toFloat()
                            val dir1 = Vector2D(cos(step), sin(step))
                            val dir2 = Vector2D(cos(step + Math.PI.toFloat()), sin(step + Math.PI.toFloat()))
                            shootEnemyBullet(enemy.position, dir1, Color(0xFFD500F9), 130f, 1f)
                            shootEnemyBullet(enemy.position, dir2, Color(0x66D500F9), 130f, 1f)
                        }

                        // Occasionally spawn slime minions!
                        if (Math.random() < 0.006) {
                            val rx = enemy.position.x + ((-100..100).random())
                            val ry = enemy.position.y + 100f
                            spawnFloatingText(enemy.position, "SPAWNING CELL!", Color.Green)
                            // Inject temporary slime
                            viewModelScope.launch {
                                val current = enemies.value.toMutableList()
                                current.add(Enemy(UUID.randomUUID().toString(), Vector2D(rx, ry), Vector2D(0f,0f), 15f, 15f, Enemy.Type.SLIME, 1f, 80f, 0f, 25f))
                                enemies.value = current
                            }
                        }
                    }
                }
            }

            // Clamping enemies to workspace edges
            enemy.position = Vector2D(
                enemy.position.x.coerceIn(50f, GameParams.ROOM_SIZE - 50f),
                enemy.position.y.coerceIn(50f, GameParams.ROOM_SIZE - 50f)
            )

            // Bullet vs Enemy Collision Detector
            var alive = true
            var finalHealth = enemy.health
            for (bullet in bulletList) {
                if (bullet.isPlayerBullet) {
                    if (bullet.position.distance(enemy.position) <= bullet.radius + enemy.size) {
                        // Hit!
                        finalHealth -= bullet.damage
                        spawnFloatingText(bullet.position, bullet.damage.toInt().toString(), Color.White)
                        spawnImpactSparks(bullet.position, Color.White, 5)

                        // Remove bullet immediately
                        removeBullet(bullet.id)

                        if (finalHealth <= 0f) {
                            alive = false
                            onEnemyKilled(enemy)
                            break
                        }
                    }
                }
            }

            if (alive) {
                enemy.health = finalHealth
                nextEnemies.add(enemy)

                // Enemy vs Player ship Collision Detector
                if (!isDashing && playerInvincibilityTimer <= 0f) {
                    if (playerPos.distance(enemy.position) <= enemy.size + 24f) {
                        damagePlayer(enemy.damage)
                    }
                }
            }
        }
        enemies.value = nextEnemies

        // Clear active room if no enemies are left
        checkSectorCleared()

        // 9. Player vs Hostile Bullet Collisions
        for (bullet in bulletList) {
            if (!bullet.isPlayerBullet) {
                if (bullet.position.distance(playerPos) <= bullet.radius + 20f) {
                    if (!isDashing && playerInvincibilityTimer <= 0f) {
                        damagePlayer(bullet.damage)
                        removeBullet(bullet.id)
                        spawnImpactSparks(bullet.position, Color.Red, 8)
                    }
                }
            }
        }

        // 10. Update Particles
        val activeParticles = particles.value.toMutableList()
        val nextParticles = mutableListOf<Particle>()
        for (part in activeParticles) {
            part.life -= dt
            part.position += part.velocity * dt
            if (part.life > 0f) {
                nextParticles.add(part)
            }
        }
        particles.value = nextParticles
    }

    private fun checkSectorCleared() {
        val clearId = "$roomGridX,$roomGridY"
        if (enemies.value.isEmpty() && !clearedRooms.contains(clearId)) {
            clearedRooms.add(clearId)
            spawnSparkCircle(playerPos, Color.Green, 25)
            postNotification("✓ Sector Cleared! Safe to navigate or save progress!")

            // Reveal keycard or chest drops if we just cleared the Altar chest?
            // Actually Altar Chest health depletion handles key generation.
            saveCurrentProgress()

            // Check if boss was slain
            if (bossTriggered && !bossDefeated && clearedRooms.contains("2,2")) {
                bossDefeated = true
                _uiState.value = GameUiState.GameVictory
                postNotification("👑 THE VOID CORE DESTROYED! YOU ESCAPED THE CONFINES!")
                spawnSparkCircle(Vector2D(600f, 500f), Color.Magenta, 60)
            }
        }
    }

    private fun boundaryRoomTransitions() {
        var transitioned = false
        val threshold = 18f

        // Gate North: x center (450..750) going north
        if (playerPos.y < threshold) {
            if (playerPos.x in 450f..750f) {
                if (roomGridY > 0) {
                    roomGridY--
                    playerPos = Vector2D(playerPos.x, GameParams.ROOM_SIZE - threshold - 30f)
                    transitioned = true
                }
            } else {
                playerPos = Vector2D(playerPos.x, threshold)
            }
        }
        // Gate South
        else if (playerPos.y > GameParams.ROOM_SIZE - threshold) {
            if (playerPos.x in 450f..750f) {
                if (roomGridY < 2) {
                    roomGridY++
                    playerPos = Vector2D(playerPos.x, threshold + 30f)
                    transitioned = true
                }
            } else {
                playerPos = Vector2D(playerPos.x, GameParams.ROOM_SIZE - threshold)
            }
        }
        // Gate West
        else if (playerPos.x < threshold) {
            if (playerPos.y in 450f..750f) {
                if (roomGridX > 0) {
                    roomGridX--
                    playerPos = Vector2D(GameParams.ROOM_SIZE - threshold - 30f, playerPos.y)
                    transitioned = true
                }
            } else {
                playerPos = Vector2D(threshold, playerPos.y)
            }
        }
        // Gate East
        else if (playerPos.x > GameParams.ROOM_SIZE - threshold) {
            if (playerPos.y in 450f..750f) {
                // If attempting east from (1,0) to (2,0), check key constraints!
                if (roomGridX == 1 && roomGridY == 0 && !hasGreenKeycard.value) {
                    playerPos = Vector2D(GameParams.ROOM_SIZE - threshold - 10f, playerPos.y)
                    postNotification("❌ ACCESS DENIED: Requires Green Keycard. Locate it in Sector (1,1) ruins!")
                    startScreenShake(12f, 0.4f)
                } else {
                    if (roomGridX < 2) {
                        roomGridX++
                        playerPos = Vector2D(threshold + 30f, playerPos.y)
                        transitioned = true
                    }
                }
            } else {
                playerPos = Vector2D(GameParams.ROOM_SIZE - threshold, playerPos.y)
            }
        }

        if (transitioned) {
            initializeSector(roomGridX, roomGridY)
        }
    }

    private fun triggerWeaponAutoFiring() {
        val now = System.currentTimeMillis()
        if (now - lastShotTime < fireRateDelayMs) return

        // Scan closest enemy within 500 units range
        var bestEnemy: Enemy? = null
        var bestDist = 500f
        for (e in enemies.value) {
            val dist = playerPos.distance(e.position)
            if (dist < bestDist) {
                bestDist = dist
                bestEnemy = e
            }
        }

        val shootDir: Vector2D?
        if (bestEnemy != null) {
            shootDir = (bestEnemy.position - playerPos).normalized()
        } else {
            // No direct target, shoot in facing directions during movement (if velocity is high enough)
            if (playerVelocity.length() > 20f) {
                shootDir = playerVelocity.normalized()
            } else {
                // Shoot straight in direction facing
                val rads = Math.toRadians(playerAngle.toDouble())
                shootDir = Vector2D(cos(rads).toFloat(), sin(rads).toFloat())
            }
        }

        if (shootDir != null) {
            lastShotTime = now

            // Spawn player projectile
            viewModelScope.launch {
                val current = bullets.value.toMutableList()
                val id = UUID.randomUUID().toString()
                current.add(
                    Bullet(
                        id = id,
                        position = playerPos + shootDir * 25f,
                        velocity = shootDir * 450f,
                        isPlayerBullet = true,
                        damage = bulletDamage,
                        color = Color(0xFFFFEE58)
                    )
                )
                bullets.value = current
            }

            // Tiny recoil impulse & spark particles
            spawnParticle(playerPos + shootDir * 20f, shootDir * 80f, Color(0xFFFFEE58), 6f, 0.25f, Particle.Type.ENGINE_TRAIL)
        }
    }

    private fun damagePlayer(dmg: Float) {
        if (playerInvincibilityTimer > 0f || _uiState.value != GameUiState.InGame) return
        playerHealth.value = (playerHealth.value - dmg).coerceAtLeast(0f)
        playerInvincibilityTimer = GameParams.INVINCIBILITY_AFTER_DAMAGE

        startScreenShake(18f, 0.45f)
        spawnSparkCircle(playerPos, Color.Red, 15)
        spawnFloatingText(playerPos, "-${dmg.toInt()} Integrity!", Color.Red)

        if (playerHealth.value <= 0f) {
            _uiState.value = GameUiState.GameOver
            postNotification("Starship Core Meltdown! Game Over.")
            spawnSparkCircle(playerPos, Color.Yellow, 40)
        }
    }

    private fun damageObstacle(obs: Obstacle, dmg: Float) {
        val nextObs = obstacles.value.map {
            if (it.id == obs.id) {
                val updatedHealth = (it.health - dmg).coerceAtLeast(0f)
                it.copy(health = updatedHealth)
            } else {
                it
            }
        }

        obstacles.value = nextObs

        val damaged = nextObs.firstOrNull { it.id == obs.id }
        if (damaged != null && damaged.health <= 0f) {
            // Crystal Explodes!
            viewModelScope.launch {
                val current = obstacles.value.toMutableList()
                current.removeAll { it.id == obs.id }
                obstacles.value = current
            }

            startScreenShake(10f, 0.25f)
            spawnSparkCircle(obs.position, Color(0xFF00E676), 18)

            if (obs.type == Obstacle.Type.GLOWING_CRYSTAL) {
                // Drop multiple glowing shards Currency!
                spawnShards(obs.position, (3..7).random(), 1)
                spawnFloatingText(obs.position, "RESONATOR CRACKED!", Color(0xFF00E676))
            } else if (obs.type == Obstacle.Type.ALTAR_CHEST) {
                // Primary Metroidvania reward: KEY CARD!
                hasGreenKeycard.value = true
                spawnSparkCircle(obs.position, Color.Green, 30)
                spawnFloatingText(obs.position, "GREEN SECURITY CARD!", Color.Green)
                postNotification("🔒 METROIDVANIA GATE LOCK CRACKED! Green access card extracted!")
                saveCurrentProgress()
            }
        }
    }

    private fun removeBullet(id: String) {
        viewModelScope.launch {
            val current = bullets.value.toMutableList()
            current.removeAll { it.id == id }
            bullets.value = current
        }
    }

    private fun onEnemyKilled(enemy: Enemy) {
        startScreenShake(12f, 0.3f)
        spawnSparkCircle(enemy.position, Color.Yellow, 12)
        spawnFloatingText(enemy.position, "+${enemy.points} XP", Color.Cyan)

        // Drop Shards!
        val qty = when (enemy.type) {
            Enemy.Type.SLIME -> 1
            Enemy.Type.SCUTTLER -> 2
            Enemy.Type.DRONE -> 3
            Enemy.Type.STATIONARY_TURRET -> 4
            Enemy.Type.COLOSSAL_BOSS -> 15
        }

        spawnShards(enemy.position, qty, 1)

        // Add XP
        addXp(enemy.points)
    }

    private fun addXp(amount: Int) {
        val nextVal = playerXp.value + amount
        val threshold = playerLevel.value * 120
        if (nextVal >= threshold) {
            playerLevel.value++
            playerXp.value = nextVal - threshold
            // Automatically award +1 shard bundle and visual ring!
            playerShards.value += 10
            spawnSparkCircle(playerPos, Color.Cyan, 30)
            spawnFloatingText(playerPos, "LEVEL UP! +10 SHARDS", Color(0xFF00E5FF))
            postNotification("⭐ STARSHIP PROMOTED! LEVEL ${playerLevel.value}! Spend shards at shrines.")
        } else {
            playerXp.value = nextVal
        }
        saveCurrentProgress()
    }

    private fun shootEnemyBullet(pos: Vector2D, dir: Vector2D, color: Color, speed: Float, damage: Float) {
        viewModelScope.launch {
            val current = bullets.value.toMutableList()
            current.add(
                Bullet(
                    id = UUID.randomUUID().toString(),
                    position = pos + dir * 30f,
                    velocity = dir * speed,
                    isPlayerBullet = false,
                    damage = damage,
                    color = color
                )
            )
            bullets.value = current
        }
    }

    private fun spawnShards(pos: Vector2D, count: Int, amountPerShard: Int) {
        viewModelScope.launch {
            val current = collectibles.value.toMutableList()
            for (i in 0 until count) {
                // Random velocity explosion spreads
                val angle = (Math.random() * 2 * Math.PI).toFloat()
                val speed = (50..130).random().toFloat()
                val vel = Vector2D(cos(angle), sin(angle)) * speed

                // Alternating drops: mostly shards, occasionally rare hearts if low on health!
                val dropType = if (Math.random() < 0.15 && playerHealth.value <= playerMaxHealth.value * 0.5f) {
                    Collectible.Type.HEART
                } else {
                    Collectible.Type.SHARD
                }

                current.add(
                    Collectible(
                        id = UUID.randomUUID().toString(),
                        position = pos,
                        type = dropType,
                        valAmount = amountPerShard,
                        velocity = vel
                    )
                )
            }
            collectibles.value = current
        }
    }

    // PARTICLE SPARK SPAWNERS
    private fun spawnParticle(pos: Vector2D, vel: Vector2D, color: Color, size: Float, duration: Float, type: Particle.Type) {
        viewModelScope.launch {
            val current = particles.value.toMutableList()
            current.add(
                Particle(
                    id = UUID.randomUUID().toString(),
                    position = pos,
                    velocity = vel,
                    color = color,
                    size = size,
                    maxLife = duration,
                    life = duration,
                    type = type
                )
            )
            particles.value = current
        }
    }

    private fun spawnSparkCircle(pos: Vector2D, color: Color, count: Int) {
        viewModelScope.launch {
            val current = particles.value.toMutableList()
            for (i in 0 until count) {
                val angle = (2 * Math.PI * i / count).toFloat()
                val speed = (60..180).random().toFloat()
                val vel = Vector2D(cos(angle), sin(angle)) * speed
                current.add(
                    Particle(
                        id = UUID.randomUUID().toString(),
                        position = pos,
                        velocity = vel,
                        color = color,
                        size = (6..12).random().toFloat(),
                        maxLife = 0.5f,
                        life = 0.5f,
                        type = Particle.Type.SPARK
                    )
                )
            }
            particles.value = current
        }
    }

    private fun spawnImpactSparks(pos: Vector2D, color: Color, count: Int) {
        viewModelScope.launch {
            val current = particles.value.toMutableList()
            for (i in 0 until count) {
                val angle = (Math.random() * 2 * Math.PI).toFloat()
                val speed = (30..110).random().toFloat()
                val vel = Vector2D(cos(angle), sin(angle)) * speed
                current.add(
                    Particle(
                        id = UUID.randomUUID().toString(),
                        position = pos,
                        velocity = vel,
                        color = color,
                        size = (4..8).random().toFloat(),
                        maxLife = 0.3f,
                        life = 0.3f,
                        type = Particle.Type.SPARK
                    )
                )
            }
            particles.value = current
        }
    }

    private fun spawnFloatingText(pos: Vector2D, text: String, color: Color) {
        viewModelScope.launch {
            val current = particles.value.toMutableList()
            current.add(
                Particle(
                    id = UUID.randomUUID().toString(),
                    position = pos,
                    velocity = Vector2D(0f, -60f), // slide upwards
                    color = color,
                    size = 14f,
                    maxLife = 1.1f,
                    life = 1.1f,
                    type = Particle.Type.FLOATING_TEXT,
                    text = text
                )
            )
            particles.value = current
        }
    }

    // CLAMP POSITION + OBSTACLE DEFLATOR
    private fun clampToRoom(newPos: Vector2D, originalPos: Vector2D): Vector2D {
        var finalX = newPos.x
        var finalY = newPos.y

        val limitLeft = 24f
        val limitRight = GameParams.ROOM_SIZE - 24f

        // Clamp to screen bounds unless passing active gateways (450f..750f)
        if (finalY in 450f..750f) {
            // East Gate check of Room (1,0)
            if (finalX > GameParams.ROOM_SIZE - limitLeft && roomGridX == 1 && roomGridY == 0 && !hasGreenKeycard.value) {
                finalX = GameParams.ROOM_SIZE - limitLeft
            }
        } else {
            finalX = finalX.coerceIn(limitLeft, limitRight)
            finalY = finalY.coerceIn(limitLeft, limitRight)
        }

        if (finalX < limitLeft && (finalY < 450f || finalY > 750f)) finalX = limitLeft
        if (finalX > limitRight && (finalY < 450f || finalY > 750f)) finalX = limitRight
        if (finalY < limitLeft && (finalX < 450f || finalX > 750f)) finalY = limitLeft
        if (finalY > limitRight && (finalX < 450f || finalX > 750f)) finalY = limitRight

        // Obstacles collision resolving (Simple Circle-to-Circle push out)
        var adjusted = Vector2D(finalX, finalY)
        val playerRadius = 20f

        for (obs in obstacles.value) {
            if (obs.isCollidable) {
                val dist = adjusted.distance(obs.position)
                val minDist = obs.radius + playerRadius
                if (dist < minDist) {
                    val overlap = minDist - dist
                    val pushDir = if (dist > 0.1f) {
                        (adjusted - obs.position).normalized()
                    } else {
                        Vector2D(1f, 0f)
                    }
                    adjusted += pushDir * (overlap + 0.5f) // push out

                    // Enter shrine beacon range automatically when player touches/collides with it
                    if (obs.type == Obstacle.Type.SHRINE_BEACON) {
                        openUpgradeMenu()
                    }
                }
            } else if (obs.type == Obstacle.Type.LAVA_POOL) {
                if (adjusted.distance(obs.position) < obs.radius) {
                    // Slime/Lava burn!
                    if (!isDashing) {
                        damagePlayer(0.04f) // slow hazard tick damage
                    }
                }
            }
        }

        return adjusted
    }
}
