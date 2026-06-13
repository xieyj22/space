package com.example.game.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(private val playerDao: PlayerDao) {

    val playerStats: Flow<PlayerStats> = playerDao.getPlayerStats().map { stats ->
        stats ?: PlayerStats()
    }

    suspend fun getStatsDirect(): PlayerStats {
        return playerDao.getPlayerStatsDirect() ?: PlayerStats()
    }

    private suspend fun updateStats(block: (PlayerStats) -> PlayerStats) {
        val current = getStatsDirect()
        val updated = block(current)
        playerDao.insertOrUpdate(updated)
    }

    suspend fun addCredits(amount: Int) {
        updateStats { current ->
            current.copy(credits = current.credits + amount)
        }
    }

    suspend fun updateHighScore(newScore: Int) {
        updateStats { current ->
            if (newScore > current.highScore) {
                current.copy(highScore = newScore)
            } else {
                current
            }
        }
    }

    suspend fun selectShip(shipId: String) {
        updateStats { current ->
            if (current.unlockedShips.split(",").contains(shipId)) {
                current.copy(selectedShipId = shipId)
            } else {
                current
            }
        }
    }

    suspend fun unlockShip(shipId: String, cost: Int): Boolean {
        val current = getStatsDirect()
        val unlockedList = current.unlockedShips.split(",")
        if (unlockedList.contains(shipId)) return true
        if (current.credits >= cost) {
            val newList = (unlockedList + shipId).joinToString(",")
            playerDao.insertOrUpdate(
                current.copy(
                    credits = current.credits - cost,
                    unlockedShips = newList,
                    selectedShipId = shipId
                )
            )
            return true
        }
        return false
    }

    suspend fun upgradeTier(statType: String, cost: Int): Boolean {
        val current = getStatsDirect()
        if (current.credits < cost) return false

        val updated = when (statType) {
            "ARMOR" -> if (current.armorTier < 5) current.copy(armorTier = current.armorTier + 1, credits = current.credits - cost) else null
            "SPEED" -> if (current.speedTier < 5) current.copy(speedTier = current.speedTier + 1, credits = current.credits - cost) else null
            "RATE" -> if (current.rateTier < 5) current.copy(rateTier = current.rateTier + 1, credits = current.credits - cost) else null
            "MAGNET" -> if (current.magnetTier < 5) current.copy(magnetTier = current.magnetTier + 1, credits = current.credits - cost) else null
            "CRIT" -> if (current.critTier < 5) current.copy(critTier = current.critTier + 1, credits = current.credits - cost) else null
            else -> null
        }

        return if (updated != null) {
            playerDao.insertOrUpdate(updated)
            true
        } else {
            false
        }
    }
}
