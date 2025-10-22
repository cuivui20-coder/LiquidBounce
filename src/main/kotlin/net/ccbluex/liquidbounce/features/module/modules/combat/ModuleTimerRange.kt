/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.combat.findEnemy
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.ccbluex.liquidbounce.utils.entity.ping
import kotlin.random.Random

/**
 * Simple TimerRange module
 *
 * A clean, efficient timer module focused on combat effectiveness
 * with minimal detection risk using KISS principle (Keep It Simple, Stupid).
 */

object ModuleTimerRange : ClientModule("TimerRange", Category.COMBAT) {

    // Core settings - simple and effective
    private val speed by float("Speed", 1.008f, 1.005f..1.015f)
    private val activationRange by float("Range", 3.5f, 2.5f..5.0f)
    private val minCooldown by int("MinCooldown", 20, 15..40)
    private val maxCooldown by int("MaxCooldown", 40, 30..60)
    private val chance by int("Chance", 80, 60..90, "%")
    private val maxDuration by int("MaxDuration", 40, 20..80)
    private val pingCompensationFactor by float("PingCompensation", 0.0001f, 0.0f..0.001f)
    
    // Basic conditions
    private val requiresKillAura by boolean("RequiresKillAura", true)
    private val onlyOnGround by boolean("OnlyOnGround", true)
    private val pauseOnFlag by boolean("PauseOnFlag", true)
    private val onlyWhenAttacking by boolean("OnlyWhenAttacking", false)
    private val onlyWhenMoving by boolean("OnlyWhenMoving", false)
    private val randomizeDuration by boolean("RandomizeDuration", true)
    private val randomizeCooldown by boolean("RandomizeCooldown", true)
    private val humanizeSpeed by boolean("HumanizeSpeed", true)
    private val humanizationAmount by float("HumanizationAmount", 0.001f, 0.0f..0.005f)
    private val proactiveResetOnHighVelocity by boolean("ProactiveResetOnHighVelocity", false)
    private val velocityThreshold by float("VelocityThreshold", 0.4f, 0.1f..1.0f)

    private fun getAdjustedSpeed(): Float {
        val playerPing = player.ping
        val baseSpeed = speed

        var finalSpeed = baseSpeed + (playerPing * pingCompensationFactor)

        if (humanizeSpeed) {
            // Add a random fluctuation based on humanizationAmount
            val fluctuation = (Random.nextFloat() * 2 - 1) * humanizationAmount // -humanizationAmount to +humanizationAmount
            finalSpeed += fluctuation
        }
        
        // Ensure the speed stays within the defined range (1.005f..1.015f)
        return finalSpeed.coerceIn(1.005f, 1.015f)
    }


    // Minimal state - only 4 variables
    private var lastActivation = 0L
    private var currentCooldown = 0
    private var isActive = false
    private var activeTicks = 0

    override fun enable() {
        resetState()
        super.enable()
    }

    override fun disable() {
        Timer.requestTimerSpeed(1.0f, Priority.IMPORTANT_FOR_USAGE_1, this)
        super.disable()
    }

    private fun resetState() {
        lastActivation = 0L
        currentCooldown = 0
        isActive = false
        activeTicks = 0
    }

    val tickHandler = tickHandler {
        // Auto-reset after max duration
        val currentMaxDuration = if (randomizeDuration) {
            Random.nextInt(maxDuration / 2, maxDuration + 1) // Randomize within half to full maxDuration
        } else {
            maxDuration
        }

        if (isActive && activeTicks++ > currentMaxDuration) {
            resetTimer()
            return@tickHandler
        }

        // Basic conditions
        if (onlyOnGround && !player.isOnGround) {
            resetTimer()
            return@tickHandler
        }
        
        if (requiresKillAura && (!ModuleKillAura.running || ModuleKillAura.targetTracker.target == null)) {
            resetTimer()
            return@tickHandler
        }

        // Proactive reset on high velocity
        if (proactiveResetOnHighVelocity) {
            val velocity = player.velocity.length()
            if (velocity > velocityThreshold) {
                resetTimer()
                return@tickHandler
            }
        }

        // Update cooldown
        if (currentCooldown > 0) {
            currentCooldown--
        }

        // Enemy detection
        val enemyInRange = world.findEnemy(0f..activationRange) != null
        
        if (enemyInRange && canActivate()) {
            activateTimer()
        } else if (!enemyInRange) {
            resetTimer()
        }
    }

    private fun canActivate(): Boolean {
        if (isActive) return false

        // New intelligent activation conditions
        if (onlyWhenAttacking && !player.handSwinging) return false
        if (onlyWhenMoving && player.velocity.x == 0.0 && player.velocity.y == 0.0 && player.velocity.z == 0.0) return false

        // Random chance
        if (Random.nextInt(100) >= chance) return false

        // Cooldown check
        if (currentCooldown > 0) return false

        return true
    }

    private fun activateTimer() {
        Timer.requestTimerSpeed(getAdjustedSpeed(), Priority.IMPORTANT_FOR_USAGE_1, this)
        isActive = true
        activeTicks = 0
        lastActivation = System.currentTimeMillis()
        
        // Set random cooldown for next activation
        currentCooldown = if (randomizeCooldown) {
            Random.nextInt(minCooldown, maxCooldown + 1)
        } else {
            minCooldown // Use minCooldown as a fixed value if not randomized
        }
    }

    private fun resetTimer() {
        if (isActive) {
            Timer.requestTimerSpeed(1.0f, Priority.IMPORTANT_FOR_USAGE_1, this)
            isActive = false
        }
    }

    val packetHandler = handler<PacketEvent> {
        if (it.packet is PlayerPositionLookS2CPacket && pauseOnFlag) {
            resetTimer()
        }
    }
}
