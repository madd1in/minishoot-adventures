package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

// 2D Physics Vector helper
data class Vector2D(val x: Float, val y: Float) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector2D(x / scalar, y / scalar) else Vector2D(0f, 0f)
    fun length() = sqrt(x * x + y * y)
    fun normalized(): Vector2D {
        val len = length()
        return if (len > 0f) this / len else Vector2D(0f, 0f)
    }
    fun distance(other: Vector2D) = (this - other).length()
    fun dot(other: Vector2D) = x * other.x + y * other.y
}

// Map Sector description representing one full screen of game board
data class Sector(
    val gridX: Int,
    val gridY: Int,
    val name: String,
    val themeColor: Color,
    val groundColor: Color,
    val gridColor: Color,
    val introductionText: String = "",
    val hasKeyAltar: Boolean = false,
    val isWinRoom: Boolean = false
) {
    fun isConnected(toX: Int, toY: Int): Boolean {
        // Simple cardinal connectivity
        val dx = kotlin.math.abs(gridX - toX)
        val dy = kotlin.math.abs(gridY - toY)
        return (dx == 1 && dy == 0) || (dx == 0 && dy == 1)
    }
}

// Game Settings Constants
object GameParams {
    const val ROOM_SIZE = 1200f // Local coordination dimension (1200 x 1200)
    const val SHIP_DASH_SPEED_MULTIPLIER = 2.8f
    const val DASH_DURATION = 0.25f // Seconds of super sprint
    const val DASH_COOLDOWN = 1.0f // Seconds between dashes
    const val INVINCIBILITY_AFTER_DAMAGE = 1.5f // Seconds of flashing protection
}

// Interactive collectible entities (Gold shards, health packs)
data class Collectible(
    val id: String,
    var position: Vector2D,
    val type: Type,
    val valAmount: Int = 1,
    var velocity: Vector2D = Vector2D(0f, 0f),
    var isAttracted: Boolean = false
) {
    enum class Type {
        SHARD, // Currency for upgrades
        HEART  // Heals 1 HP block
    }
}

// Interactive Obstacles
data class Obstacle(
    val id: String,
    val position: Vector2D,
    val radius: Float,
    val type: Type,
    val isCollidable: Boolean = true,
    var health: Float = 10f, // For breakable items
    val maxHealth: Float = 10f
) {
    enum class Type {
        TREE_GREEN,
        FOREST_ROCK,
        SHRINE_BEACON,  // Upgrades station
        GLOWING_CRYSTAL, // Breakable for heavy shard drops!
        RUINED_COLUMN,
        LOCKED_GATE,     // Requires Green Keycard to bypass!
        ALTAR_CHEST,     // Contains keycard or rare buffs when shot/clicked
        LAVA_POOL
    }
}

// Real-time Combat Bullets
data class Bullet(
    val id: String,
    var position: Vector2D,
    val velocity: Vector2D,
    val isPlayerBullet: Boolean,
    val damage: Float,
    val radius: Float = 8f,
    val color: Color,
    val trail: MutableList<Vector2D> = mutableListOf()
)

// Ambient Particle Visual Effects
data class Particle(
    val id: String,
    var position: Vector2D,
    val velocity: Vector2D,
    val color: Color,
    val size: Float,
    val maxLife: Float,
    var life: Float, // Count down in seconds
    val type: Type,
    val text: String = "" // Used for damage overlays or level ups
) {
    enum class Type {
        SPARK,
        EXPLOSION,
        ENGINE_TRAIL,
        FLOATING_TEXT
    }
}

// Enemy definitions
data class Enemy(
    val id: String,
    var position: Vector2D,
    var velocity: Vector2D = Vector2D(0f, 0f),
    var health: Float,
    val maxHealth: Float,
    val type: Type,
    val damage: Float = 1f,
    val speed: Float,
    var shootTimer: Float = 0f,
    val size: Float,
    val points: Int = 10,
    var wanderAngle: Float = 0f
) {
    enum class Type {
        SLIME,        // Green bouncing chaser
        DRONE,        // Shooting space orb
        SCUTTLER,     // Errant fast crawler
        STATIONARY_TURRET, // Powerful shooting plant
        COLOSSAL_BOSS // Void Core
    }
}
