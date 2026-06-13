package com.example.game.domain

import androidx.compose.ui.graphics.Color

enum class ShipType(
    val id: String,
    val shipName: String,
    val description: String,
    val baseHp: Float,
    val baseSpeed: Float,
    val damageModifier: Float,
    val startingWeapon: WeaponType,
    val cost: Int,
    val primaryColor: Color,
    val accentColor: Color
) {
    VANGUARD(
        id = "VANGUARD",
        shipName = "先锋号 (Vanguard)",
        description = "联盟制式截击机。敏捷灵活、属性均衡，初始搭载【双生等离子炮】。",
        baseHp = 100f,
        baseSpeed = 7f,
        damageModifier = 1.0f,
        startingWeapon = WeaponType.TWIN_BLASTERS,
        cost = 0,
        primaryColor = Color(0xFF00E5FF), // Cyan
        accentColor = Color(0xFF0077D6)
    ),
    DREADNOUGHT(
        id = "DREADNOUGHT",
        shipName = "无畏者 (Dreadnought)",
        description = "重装守卫巡洋舰。移速略低但拥有超高护甲与装甲生命值，初始载入【制导追踪弹】。",
        baseHp = 180f,
        baseSpeed = 5f,
        damageModifier = 1.35f,
        startingWeapon = WeaponType.MISSILE_LAUNCHER,
        cost = 620,
        primaryColor = Color(0xFFFF5252), // Cyber Red
        accentColor = Color(0xFFB71C1C)
    ),
    VOID_PHANTOM(
        id = "VOID_PHANTOM",
        shipName = "虚空幽灵 (Phantom)",
        description = "神秘维度引擎驱动。机动性冠绝星海，具有40%几率闪避任意伤害，初始拥有【共振立场盾】。",
        baseHp = 80f,
        baseSpeed = 9.2f,
        damageModifier = 0.9f,
        startingWeapon = WeaponType.ORBITAL_SHIELD,
        cost = 1350,
        primaryColor = Color(0xFFE040FB), // Psychedelic Violet
        accentColor = Color(0xFF6A1B9A)
    )
}

enum class WeaponType(
    val techName: String,
    val description: String,
    val iconSymbol: String
) {
    TWIN_BLASTERS("双生等离子炮", "向前并发高能等离子射线，每级增加子弹数、穿透及射速。", "☄"),
    LASER_BEAM("扫频高能激光", "对最近的目标射出一束持续追踪并切割熔毁的脉冲激光。", "☇"),
    MISSILE_LAUNCHER("核导追踪飞弹", "发射热追踪导弹，撞击敌船后爆发致命的群伤范围爆炸。", "🚀"),
    ORBITAL_SHIELD("共振立场护盾", "两面环绕机身旋转的高能折射护盾，碾碎擦碰的所有障碍并吞噬其能量。", "🛡"),
    TESLA_COIL("闪电风暴发射器", "高频释放特斯拉狂暴链式电弧，跨目标闪击大群敌舰。", "⚡"),
    GRAVITY_SINGULARITY("强质重力奇点", "随机在场中撕开一个微缩黑洞，吸扯周围敌军，造成持续减速并爆破伤害。", "☄")
}

data class ActiveWeapon(
    val type: WeaponType,
    var level: Int = 1
)

data class Enemy(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val maxHp: Float,
    var hp: Float,
    val radius: Float,
    val scoreValue: Int,
    val creditValue: Int,
    val type: EnemyType,
    var bulletTimer: Long = 0L,
    var phase: Float = 0f // Boss movement / firing animation state
)

enum class EnemyType(
    val color: Color,
    val sizeMultiplier: Float,
    val speedMultiplier: Float,
    val baseHp: Float
) {
    SCOUT(Color(0xFF81C784), 0.9f, 1.2f, 16f),       // Fast green scout
    CHASER(Color(0xFFE57373), 1.1f, 0.9f, 35f),      // Aggressive red interceptor
    SPITTER(Color(0xFFFFB74D), 1.2f, 0.65f, 50f),    // Fires bullets orange
    METEOR(Color(0xFF90A4AE), 1.3f, 1.6f, 80f),      // Speed obstacle flying straight
    ELITE(Color(0xFFBA68C8), 1.6f, 0.75f, 150f),     // Mini-boss violet unit
    BOSS(Color(0xFFFF1744), 3.3f, 0.4f, 1100f)        // Massive core commander
}

data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val damage: Float,
    val color: Color,
    val isFromPlayer: Boolean = true,
    val isMissile: Boolean = false,
    var bounceCount: Int = 0,
    var splashRadius: Float = 0f,
    // Orbital coordinates if part of orbital shield
    var angle: Float = 0f,
    // Target tracking for homing bullets
    var targetEnemyId: Long? = null
)

data class ExpGem(
    val id: Long,
    var x: Float,
    var y: Float,
    val expAmount: Int,
    val isGold: Boolean, // false = Blue Exp Gem, true = Gold Coin (gives Credits!)
    val creditsAmount: Int = 0,
    var isAttracted: Boolean = false
)

data class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    var size: Float,
    val maxLife: Int,
    var currentLife: Int = 0,
    val alpha: Float = 1f
)

data class FloatingText(
    val id: Long,
    val text: String,
    val x: Float,
    var y: Float,
    val color: Color,
    val isBig: Boolean = false,
    var life: Int = 40 // frames to survive
)
