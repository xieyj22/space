package com.example.game.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_stats")
data class PlayerStats(
    @PrimaryKey val id: Int = 1,
    val credits: Int = 0,
    val highScore: Int = 0,
    val armorTier: Int = 0,
    val speedTier: Int = 0,
    val rateTier: Int = 0,
    val magnetTier: Int = 0,
    val critTier: Int = 0,
    val unlockedShips: String = "VANGUARD",
    val selectedShipId: String = "VANGUARD"
)
