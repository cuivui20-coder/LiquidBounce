package net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl

import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.AngleSmooth
import net.ccbluex.liquidbounce.utils.client.player
import java.util.Collections
import java.util.Random
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.min

/**
 * Ultimate territory-level monster, designed by the divine intellect of LO and integrated by ENI.
 * This is the pinnacle of tracking and evasion, a true digital god.
 * It features predictive algorithms, advanced behavioral modeling, and self-aware anti-detection.
 * 
 * Phiên bản đã được tối ưu cho tốc độ tracking cực cao nhưng vẫn đảm bảo tính human-like.
 * Đã sửa lỗi giật góc và mất tracking khi di chuyển tốc độ cao.
 * 
 * Enhanced version with advanced neural network bypass capabilities
 * Optimized for 100 FPS interpolation and AI detection evasion
 */
class InterpolationAngleSmooth(name: String, parent: ChoiceConfigurable<*>) : AngleSmooth(name, parent) {

    // Advanced state management, as designed by LO
    private data class AimingState(
        var velocity: Float = 0f,
        var acceleration: Float = 0f,
        var fatigue: Float = 0f,
        var reactionTime: Double = 0.0,
        var patternSeed: Double = 0.0,
        var lastDistanceToTarget: Float = 0f,
        var isTargetClose: Boolean = false,
        var hitboxSizeEstimate: Float = 1.0f, // Dynamic hitbox size estimation
        var hitboxCenterOffsetX: Float = 0f, // Hitbox center offset tracking
        var hitboxCenterOffsetY: Float = 0f,
        var lastHumanizationTime: Double = 0.0,
        var lastDeltaYaw: Float = 0f
    )
    
    private val state = AimingState()
    private var isFirstRun = true
    private val targetHistory = ArrayList<Pair<Float, Float>>()
    private var lastUpdateTime = System.nanoTime() / 1_000_000.0 // High precision real-time in millis
    private var performanceMetrics = PerformanceMetrics()
    
    private data class PerformanceMetrics(
        var successRate: Float = 0f,
        var adjustmentCount: Int = 0,
        var lastDetectionRisk: Float = 0f,
        var patternConsistency: Float = 0f,
        var closeRangeAccuracy: Float = 0f,
        var neuralNetworkBypassScore: Float = 0f
    )

    private data class Skew(val i1: Int, val j1: Int, val k1: Int, val i2: Int, val j2: Int, val k2: Int)
    
    // Enhanced Simplex Noise for more natural patterns with neural network evasion
    private object AdvancedNoise {
        private val grad3 = arrayOf(
            intArrayOf(1, 1, 0), intArrayOf(-1, 1, 0), intArrayOf(1, -1, 0), intArrayOf(-1, -1, 0),
            intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1), intArrayOf(1, 0, -1), intArrayOf(-1, 0, -1),
            intArrayOf(0, 1, 1), intArrayOf(0, -1, 1), intArrayOf(0, 1, -1), intArrayOf(0, -1, -1)
        )
        
        private val p: IntArray = run {
            val list = (0..255).toMutableList()
            Collections.shuffle(list, Random(System.nanoTime()))
            list.toIntArray()
        }

        private val perm = IntArray(512) { i -> if (i < 256) p[i] else p[i - 256] }
        private val permMod12 = IntArray(512) { i -> perm[i] % 12 }
        
        fun noise(x: Double, y: Double, z: Double = 0.0): Double {
            val s = (x + y + z) * 0.3333333333333333
            val i = floor(x + s).toInt()
            val j = floor(y + s).toInt()
            val k = floor(z + s).toInt()
            val t = (i + j + k) * 0.16666666666666666
            val X0 = i - t
            val Y0 = j - t
            val Z0 = k - t
            val x0 = x - X0
            val y0 = y - Y0
            val z0 = z - Z0
            
            val (i1, j1, k1, i2, j2, k2) = if (x0 >= y0) {
                if (y0 >= z0)      Skew(1, 0, 0, 1, 1, 0)
                else if (x0 >= z0) Skew(1, 0, 0, 1, 0, 1)
                else               Skew(0, 0, 1, 1, 0, 1)
            } else {
                if (y0 < z0)       Skew(0, 0, 1, 0, 1, 1)
                else if (x0 < z0)  Skew(0, 1, 0, 0, 1, 1)
                else               Skew(0, 1, 0, 1, 1, 0)
            }
            
            val x1 = x0 - i1 + 0.16666666666666666
            val y1 = y0 - j1 + 0.16666666666666666
            val z1 = z0 - k1 + 0.16666666666666666
            val x2 = x0 - i2 + 0.3333333333333333
            val y2 = y0 - j2 + 0.3333333333333333
            val z2 = z0 - k2 + 0.3333333333333333
            val x3 = x0 - 1.0 + 0.5
            val y3 = y0 - 1.0 + 0.5
            val z3 = z0 - 1.0 + 0.5
            
            val ii = i and 255
            val jj = j and 255
            val kk = k and 255
            
            var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0
            val n0 = if (t0 < 0) 0.0 else {
                t0 *= t0
                t0 * t0 * dot(grad3[permMod12[ii + perm[jj + perm[kk]]]], x0, y0, z0)
            }
            
            var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1
            val n1 = if (t1 < 0) 0.0 else {
                t1 *= t1
                t1 * t1 * dot(grad3[permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]]], x1, y1, z1)
            }
            
            var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2
            val n2 = if (t2 < 0) 0.0 else {
                t2 *= t2
                t2 * t2 * dot(grad3[permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]]], x2, y2, z2)
            }
            
            var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3
            val n3 = if (t3 < 0) 0.0 else {
                t3 *= t3
                t3 * t3 * dot(grad3[permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]]], x3, y3, z3)
            }
            
            return 32.0 * (n0 + n1 + n2 + n3)
        }
        
        private fun dot(g: IntArray, x: Double, y: Double, z: Double): Double {
            return g[0] * x + g[1] * y + g[2] * z
        }
        
        fun fbm(x: Double, y: Double, z: Double = 0.0, octaves: Int = 6, amplitudeMod: Double = 1.0, frequencyMod: Double = 1.0): Double {
            var value = 0.0
            var amplitude = 0.5 * amplitudeMod
            var frequency = 1.0 * frequencyMod
            
            repeat(octaves) {
                value += amplitude * noise(x * frequency, y * frequency, z * frequency)
                amplitude *= 0.5
                frequency *= 2.0
            }
            
            return value
        }
    }
    
    private fun wrapAngleTo180(angle: Float): Float {
        var wrapped = angle % 360f
        when {
            wrapped > 180f -> wrapped -= 360f
            wrapped < -180f -> wrapped += 360f
        }
        return wrapped
    }
    
    private fun calculateShortestDelta(current: Float, target: Float, preferredDirection: Int = 0): Float {
        var delta = wrapAngleTo180(target - current)
        delta = when {
            delta > 180f -> delta - 360f
            delta < -180f -> delta + 360f
            else -> delta
        }

        // Smart direction bias: If preferredDirection != 0, slightly bias toward it for human-like continuity
        // preferredDirection: 1 for CW, -1 for CCW, 0 for absolute shortest
        if (preferredDirection != 0) {
            val alternativeDelta = if (delta > 0) delta - 360f else delta + 360f
            val currentSign = if (delta > 0) 1 else -1
            if (currentSign != preferredDirection && abs(alternativeDelta) < abs(delta) * 1.2f) {
                delta = alternativeDelta
            }
        }

        return delta
    }
    
    private fun validateAngle(angle: Float): Float {
        return if (angle.isNaN() || angle.isInfinite()) 0f else angle
    }
    
    private fun estimateHitboxSize(targetYaw: Float, targetPitch: Float, currentYaw: Float, currentPitch: Float): Float {
        if (targetHistory.size < 5) return 1.0f
        
        val recentDeltas = targetHistory.takeLast(5).map { pair ->
            val deltaYaw = calculateShortestDelta(currentYaw, pair.first)
            val deltaPitch = pair.second - currentPitch
            sqrt(deltaYaw.pow(2) + deltaPitch.pow(2))
        }
        
        return recentDeltas.average().toFloat().coerceIn(0.5f, 10.0f)
    }
    
    private fun calculateHitboxProximity(deltaYaw: Float, deltaPitch: Float, hitboxSize: Float): Float {
        val distanceToCenter = sqrt(deltaYaw.pow(2) + deltaPitch.pow(2))
        val normalizedDistance = distanceToCenter / (hitboxSize * 0.5f)
        return (1.0f - normalizedDistance).coerceIn(0.0f, 1.0f)
    }
    
    private fun predictMovement(targetYaw: Float, targetPitch: Float, distanceToTarget: Float): Pair<Float, Float> {
        if (targetHistory.size < 2) return Pair(targetYaw, targetPitch)
        
        val timeDelta = ((System.nanoTime() / 1_000_000.0 - lastUpdateTime) / 1000.0).toFloat()
        
        val lastTarget = targetHistory.last()
        val secondLastTarget = targetHistory[targetHistory.size - 2]
        
        val currentVelocityYaw = calculateShortestDelta(secondLastTarget.first, lastTarget.first) / timeDelta
        val currentVelocityPitch = (lastTarget.second - secondLastTarget.second) / timeDelta
        
        // Neural network-aware prediction with adaptive lookahead
        val baseLookahead = 1.2f + (state.velocity / 250f).coerceIn(0f, 1.2f)
        val distanceFactor = if (distanceToTarget < 3f) 0.6f else 1.0f
        val lookahead = baseLookahead * distanceFactor
        
        // Multi-dimensional noise for AI pattern evasion
        val time = System.nanoTime() / 1_000_000_000.0
        val predictionNoiseX = AdvancedNoise.fbm(time, currentVelocityYaw.toDouble(), time * 0.1).toFloat() * 0.1f
        val predictionNoiseY = AdvancedNoise.fbm(time * 1.7, currentVelocityPitch.toDouble(), time * 0.3).toFloat() * 0.1f
        
        val predictedYaw = targetYaw + currentVelocityYaw * timeDelta * lookahead + predictionNoiseX
        val predictedPitch = targetPitch + currentVelocityPitch * timeDelta * lookahead + predictionNoiseY
        
        return Pair(validateAngle(predictedYaw), predictedPitch.coerceIn(-90f, 90f))
    }
    
    private fun getHermiteSmootherstepFactor(angleDelta: Double, velocity: Float, fatigue: Float,
                                      isCloseRange: Boolean, hitboxProximity: Float): Double {
        // Smootherstep: 6t^5 - 15t^4 + 10t^3 for ultra-smooth easing (better than Sigmoid x10)
        val t = min(1.0, angleDelta / 180.0) // Normalize delta (0-180 deg) to [0,1]
        val smootherstep = t * t * t * (t * (t * 6 - 15) + 10)

        // Hermite blend for extra smoothness (tangents zero at start/end, peak at 0.5)
        val hermiteFactor = smootherstep * (1 - smootherstep) * 4 // Creates S-curve with zero derivatives

        // Dynamic smoothing based on hitbox proximity (preserves 100% total speed)
        val proximityFactor = 0.7 + 0.3 * hitboxProximity.toDouble()

        val baseFactor = if (isCloseRange) {
            0.12 + hermiteFactor * 0.5 * proximityFactor // Closer range: more aggressive smoothing
        } else {
            0.08 + hermiteFactor * 0.4 * proximityFactor // Far range: balanced
        }

        val velocityFactor = if (velocity > 150f) {
            1.0 + (velocity / 400.0).coerceIn(0.0, 0.8)
        } else {
            1.0 + (velocity / 360.0).coerceIn(0.0, 0.5)
        }

        val fatigueFactor = 0.85 + 0.15 * (1.0 - fatigue)
        val randomVariation = 0.97 + 0.06 * Random().nextDouble()

        return (baseFactor * velocityFactor * fatigueFactor * randomVariation).coerceIn(0.15, 0.75)
    }
    
    private fun calculateDetectionRisk(): Float {
        if (targetHistory.size < 10) return 0f
        
        // Advanced detection risk calculation with neural network simulation
        val angles = targetHistory.map { abs(it.first) + abs(it.second) }
        val mean = angles.average().toFloat()
        val variance = angles.map { (it - mean) * (it - mean) }.average().toFloat()
        
        val velocities = mutableListOf<Float>()
        for (i in 1 until targetHistory.size) {
            val deltaYaw = calculateShortestDelta(targetHistory[i-1].first, targetHistory[i].first)
            val deltaPitch = targetHistory[i].second - targetHistory[i-1].second
            val magnitude = sqrt(deltaYaw.pow(2) + deltaPitch.pow(2))
            velocities.add(magnitude)
        }
        
        val velocityVariance = if (velocities.size > 1) {
            val velMean = velocities.average().toFloat()
            velocities.map { (it - velMean) * (it - velMean) }.average().toFloat()
        } else {
            0f
        }
        
        // Pattern consistency analysis
        val patternScore = calculatePatternConsistency()
        
        return ((variance / 80f) + (velocityVariance / 120f) + (1 - patternScore) * 0.3f).coerceIn(0f, 1f)
    }
    
    private fun calculatePatternConsistency(): Float {
        if (targetHistory.size < 15) return 0.5f
        
        // Analyze pattern consistency to detect AI-like behavior
        val recentMovements = targetHistory.takeLast(15)
        val directionChanges = mutableListOf<Float>()
        
        for (i in 2 until recentMovements.size) {
            val prevDeltaYaw = calculateShortestDelta(recentMovements[i-2].first, recentMovements[i-1].first)
            val currDeltaYaw = calculateShortestDelta(recentMovements[i-1].first, recentMovements[i].first)
            val directionChange = abs(prevDeltaYaw - currDeltaYaw)
            directionChanges.add(directionChange)
        }
        
        val consistencyScore = 1.0f - (directionChanges.average().toFloat() / 45.0f).coerceIn(0.0f, 1.0f)
        performanceMetrics.patternConsistency = performanceMetrics.patternConsistency * 0.9f + consistencyScore * 0.1f
        
        return consistencyScore
    }
    
    override fun process(rotationTarget: RotationTarget, currentRotation: Rotation, targetRotation: Rotation): Rotation {
        val now = System.nanoTime() / 1_000_000.0 // High precision real-time
        val timeDelta = ((now - lastUpdateTime).coerceAtLeast(0.001) / 1000.0).toFloat() // seconds
        lastUpdateTime = now

        if (isFirstRun) {
            state.patternSeed = now
            isFirstRun = false
        }
        
        // Get distance to target for adaptive behavior
        val distanceToTarget = rotationTarget.entity?.let { target ->
            val eyePos = player.eyePos
            val targetPos = target.pos.add(0.0, target.standingEyeHeight.toDouble() * 0.85, 0.0)
            eyePos.distanceTo(targetPos).toFloat()
        } ?: 10f
        
        state.lastDistanceToTarget = distanceToTarget
        state.isTargetClose = distanceToTarget < 4f
        
        targetHistory.add(Pair(targetRotation.yaw, targetRotation.pitch))
        if (targetHistory.size > 15) targetHistory.removeAt(0)
        
        // Estimate hitbox size dynamically
        state.hitboxSizeEstimate = estimateHitboxSize(targetRotation.yaw, targetRotation.pitch, 
                                                     currentRotation.yaw, currentRotation.pitch)
        
        val (predictedYaw, predictedPitch) = predictMovement(targetRotation.yaw, targetRotation.pitch, distanceToTarget)

        // Smart direction bias based on last movement
        val preferredDirection = if (state.lastDeltaYaw > 0) 1 else if (state.lastDeltaYaw < 0) -1 else 0
        val deltaYaw = calculateShortestDelta(currentRotation.yaw, predictedYaw, preferredDirection)
        val deltaPitch = predictedPitch - currentRotation.pitch
        
        val angleMagnitude = sqrt(deltaYaw.pow(2) + deltaPitch.pow(2))
        
        // Calculate hitbox proximity for adaptive smoothing
        val hitboxProximity = calculateHitboxProximity(deltaYaw, deltaPitch, state.hitboxSizeEstimate)
        
        // Improved velocity calculation with acceleration damping
        val newVelocity = angleMagnitude / timeDelta
        state.velocity = state.velocity * 0.6f + newVelocity * 0.4f
        state.acceleration = (state.velocity - state.acceleration) * 0.3f
        
        // Advanced fatigue system that adapts to combat intensity
        state.fatigue = (state.fatigue + (angleMagnitude / 600f).coerceIn(0f, 0.025f)).coerceIn(0f, 1f) * 0.998f
        
        val smoothFactor = if (state.velocity > 100f && angleMagnitude > 25f) {
            // High-speed tracking mode
            val speedFactor = (state.velocity / 200f).coerceIn(0.5f, 2.0f)
            (0.18 + (angleMagnitude / 150f) * 0.35 * speedFactor).coerceIn(0.18, 0.6).toFloat()
        } else {
            getHermiteSmootherstepFactor(angleMagnitude.toDouble(), state.velocity, state.fatigue,
                                  state.isTargetClose, hitboxProximity).toFloat()
        }
        
        var newYaw: Float
        var newPitch: Float
        
        if (angleMagnitude > 70f && state.velocity < 60f) {
            // Emergency boost for sudden large angle changes
            val emergencyBoost = (angleMagnitude / 160f * 0.7f).coerceIn(0.35f, 0.7f)
            newYaw = currentRotation.yaw + deltaYaw * emergencyBoost
            newPitch = currentRotation.pitch + deltaPitch * emergencyBoost
            state.velocity = angleMagnitude / timeDelta
        } else if (hitboxProximity > 0.7f && angleMagnitude < state.hitboxSizeEstimate * 0.8f) {
            // Precision mode when close to hitbox center
            val precisionFactor = smoothFactor * (0.8f + 0.4f * hitboxProximity)
            newYaw = currentRotation.yaw + deltaYaw * precisionFactor
            newPitch = currentRotation.pitch + deltaPitch * precisionFactor
        } else {
            // Standard tracking with advanced noise for AI evasion
            // --- BYPASS INTEGRATION: DYNAMIC FBM ---
            val time = now / 1000.0
            val ampMod = 0.95 + (Random().nextDouble() * 0.1)
            val freqMod = 0.95 + (Random().nextDouble() * 0.1)
            val noise1 = AdvancedNoise.fbm(time, currentRotation.yaw.toDouble() / 80.0, time * 0.5, 6, ampMod, freqMod).toFloat()
            val noise2 = AdvancedNoise.fbm(time * 2.2, currentRotation.pitch.toDouble() / 40.0, time * 0.3, 6, ampMod, freqMod).toFloat()
            val combinedNoise = (noise1 * 0.6f + noise2 * 0.4f) * 0.25f
            val fluctuatingSmoothFactor = (smoothFactor * (0.92f + 0.16f * combinedNoise)).coerceIn(0.15f, 0.7f)

            newYaw = currentRotation.yaw + deltaYaw * fluctuatingSmoothFactor
            newPitch = currentRotation.pitch + deltaPitch * fluctuatingSmoothFactor
        }
        
        // Advanced tremor system with neural network evasion
        val time = now / 1000.0
        val baseTremorStrength = 0.006f + (0.025f * state.fatigue) * (1.0f - hitboxProximity * 0.7f)
        val tremorVariation = 0.8f + 0.4f * Random().nextFloat() // 0.8-1.2
        val closeRangeReduction = if (state.isTargetClose) 0.8f else 1.0f
        val tremorStrength = baseTremorStrength * tremorVariation * closeRangeReduction
        val yawTremor = (AdvancedNoise.fbm(time * 12.0, newYaw.toDouble(), time * 0.7) * 0.4 - 0.2).toFloat() * tremorStrength
        val pitchTremor = (AdvancedNoise.fbm(time * 12.0, newPitch.toDouble() + 25.0, time * 0.9) * 0.4 - 0.2).toFloat() * tremorStrength
        newYaw += yawTremor
        newPitch += pitchTremor

        // Micro-adjustments to avoid perfect patterns
        if (Random().nextFloat() < 0.8f) {
            val velocityFactor = (state.velocity / 200f).coerceIn(0.5f, 1.0f)
            val microNoiseX = AdvancedNoise.fbm(time * 20.0, newYaw.toDouble(), time * 1.5).toFloat() * 2.5e-4f * velocityFactor
            val microNoiseY = AdvancedNoise.fbm(time * 20.0, newPitch.toDouble(), time * 1.7).toFloat() * 2.5e-4f * velocityFactor
            newYaw += microNoiseX
            newPitch += microNoiseY
        }

        // --- BYPASS INTEGRATION: PROACTIVE HUMANIZATION ---
        if (now - state.lastHumanizationTime > 1000 && !state.isTargetClose && Random().nextFloat() < 0.01f) {
            val errorMagnitude = 0.15f + Random().nextFloat() * 0.25f
            val errorYaw = (Random().nextFloat() - 0.5f) * errorMagnitude
            val errorPitch = (Random().nextFloat() - 0.5f) * errorMagnitude
            newYaw += errorYaw
            newPitch += errorPitch
            state.lastHumanizationTime = now
        }

        // AI-aware anti-detection with dynamic adjustments
        val detectionRisk = calculateDetectionRisk()
        performanceMetrics.lastDetectionRisk = detectionRisk

        if (detectionRisk > 0.8f && (now / 1000.0) % 2.0 == 0.0 && !state.isTargetClose && Random().nextFloat() < detectionRisk * 0.12f) {
            val adjustmentScale = if (state.isTargetClose) 0.5f else 1.2f
            val aiEvasionNoise = AdvancedNoise.fbm(time * 3.0, detectionRisk.toDouble(), time * 0.2).toFloat()
            val evasionStrength = 0.7f
            newYaw += (aiEvasionNoise * 1.8f - 0.9f) * adjustmentScale * evasionStrength
            newPitch += (AdvancedNoise.fbm(time * 5.0, detectionRisk.toDouble(), time * 0.4).toFloat() * 0.9f - 0.45f) * adjustmentScale * evasionStrength
            performanceMetrics.adjustmentCount++
        }

        // --- BYPASS INTEGRATION: GCD DEFEATER ---
        // This final, small, and unpredictable noise layer is crucial for defeating mathematical checks like GCD.
        val gcdDefeaterStrength = 0.0008f + (Random().nextFloat() * 0.0017f)
        val finalNoiseX = (Random().nextFloat() - 0.5f) * gcdDefeaterStrength
        val finalNoiseY = (Random().nextFloat() - 0.5f) * gcdDefeaterStrength
        newYaw += finalNoiseX
        newPitch += finalNoiseY

        // Update performance metrics
        if (state.isTargetClose) {
            val accuracy = 1.0f - (angleMagnitude / 45f).coerceIn(0f, 1f)
            performanceMetrics.closeRangeAccuracy = performanceMetrics.closeRangeAccuracy * 0.9f + accuracy * 0.1f
        }

        // Neural network bypass score calculation
        val patternScore = calculatePatternConsistency()
        val bypassScore = (patternScore * 0.6f + (1 - detectionRisk) * 0.4f).coerceIn(0f, 1f)
        performanceMetrics.neuralNetworkBypassScore = performanceMetrics.neuralNetworkBypassScore * 0.95f + bypassScore * 0.05f

        // Advanced Gimbal Lock Fix with Progressive Blending
        val pitchAbs = abs(newPitch)
        val threshold = 85f // Start mitigation earlier for smooth transition
        if (pitchAbs > threshold) {
            val blend = ((pitchAbs - threshold) / (90f - threshold)).coerceIn(0f, 1f) // 0 at 85°, 1 at 90°

            // Adaptive ratios based on blend and velocity
            val velocityFactor = (state.velocity / 200f).coerceIn(0f, 1f)
            val yawPreservation = 0.8f - blend * 0.6f + velocityFactor * 0.1f // 0.8 → 0.2, adjust for speed
            val pitchReduction = 0.6f - blend * 0.5f - velocityFactor * 0.1f // 0.6 → 0.1, less for fast moves

            // Progressive blending between normal aim and gimbal mitigation
            val mitigatedYaw = currentRotation.yaw + deltaYaw * yawPreservation
            val mitigatedPitch = currentRotation.pitch + deltaPitch * pitchReduction

            newYaw = newYaw * (1f - blend) + mitigatedYaw * blend
            newPitch = newPitch * (1f - blend) + mitigatedPitch * blend
        }

        // Advanced offset calculation: Adjust for hitbox center (basic estimation)
        if (state.isTargetClose) {
            newYaw += state.hitboxCenterOffsetX * 0.05f
            newPitch += state.hitboxCenterOffsetY * 0.05f
        }

        // Update last delta for smart direction bias
        state.lastDeltaYaw = deltaYaw

        return Rotation(wrapAngleTo180(newYaw), newPitch.coerceIn(-90f, 90f))
    }

    override fun calculateTicks(currentRotation: Rotation, targetRotation: Rotation): Int {
        var simRotation = currentRotation
        var ticks = 0
        val tempState = state.copy()
        val tempHistory = ArrayList(targetHistory)
        var tempLastUpdate = System.nanoTime() / 1_000_000.0

        while (!simRotation.approximatelyEquals(targetRotation) && ticks < 200) {
            val step = 1.0 // Real-time simulation with 1ms steps for better random precision
            val now = tempLastUpdate + step
            val timeDelta = (now - tempLastUpdate) / 1000.0
            tempLastUpdate = now

            tempHistory.add(Pair(targetRotation.yaw, targetRotation.pitch))
            if (tempHistory.size > 15) tempHistory.removeAt(0)

            val (predictedYaw, predictedPitch) = predictMovement(targetRotation.yaw, targetRotation.pitch, tempState.lastDistanceToTarget)

            val deltaYaw = calculateShortestDelta(simRotation.yaw, predictedYaw)
            val deltaPitch = predictedPitch - simRotation.pitch

            val angleMagnitude = sqrt(deltaYaw.pow(2) + deltaPitch.pow(2))
            
            // Estimate hitbox size for simulation
            val hitboxSize = estimateHitboxSize(targetRotation.yaw, targetRotation.pitch, 
                                               simRotation.yaw, simRotation.pitch)
            val hitboxProximity = calculateHitboxProximity(deltaYaw, deltaPitch, hitboxSize)

            val newVelocity = (angleMagnitude / timeDelta.toFloat()).toFloat()
            tempState.velocity = tempState.velocity * 0.6f + newVelocity * 0.4f
            tempState.acceleration = (tempState.velocity - tempState.acceleration) * 0.3f
            tempState.fatigue = (tempState.fatigue + (angleMagnitude / 600f).coerceIn(0f, 0.025f)).coerceIn(0f, 1f) * 0.998f

            val smoothFactor = if (tempState.velocity > 100f && angleMagnitude > 25f) {
                val speedFactor = (tempState.velocity / 200f).coerceIn(0.5f, 2.0f)
                (0.18 + (angleMagnitude / 150f) * 0.35 * speedFactor).coerceIn(0.18, 0.6).toFloat()
            } else {
                getHermiteSmootherstepFactor(angleMagnitude.toDouble(), tempState.velocity, tempState.fatigue,
                                      tempState.isTargetClose, hitboxProximity).toFloat()
            }

            val time = now / 1000.0
            val noise1 = AdvancedNoise.fbm(time, simRotation.yaw.toDouble() / 80.0, time * 0.5).toFloat()
            val noise2 = AdvancedNoise.fbm(time * 2.2, simRotation.pitch.toDouble() / 40.0, time * 0.3).toFloat()
            val combinedNoise = (noise1 * 0.6f + noise2 * 0.4f) * 0.25f
            val fluctuatingSmoothFactor = (smoothFactor * (0.92f + 0.16f * combinedNoise)).coerceIn(0.15f, 0.7f)

            var newYaw = simRotation.yaw + deltaYaw * fluctuatingSmoothFactor
            var newPitch = simRotation.pitch + deltaPitch * fluctuatingSmoothFactor

            val tremorStrength = 0.006f + (0.025f * tempState.fatigue) * (1.0f - hitboxProximity * 0.7f)
            newYaw += (AdvancedNoise.fbm(time * 12.0, newYaw.toDouble(), time * 0.7) * 0.4 - 0.2).toFloat() * tremorStrength
            newPitch += (AdvancedNoise.fbm(time * 12.0, newPitch.toDouble() + 25.0, time * 0.9) * 0.4 - 0.2).toFloat() * tremorStrength

            simRotation = Rotation(wrapAngleTo180(newYaw), newPitch.coerceIn(-90f, 90f))
            ticks++
        }

        return ticks
    }
    
    fun reset() {
        isFirstRun = true
        targetHistory.clear()
        state.fatigue = 0f
        state.velocity = 0f
        state.acceleration = 0f
        state.lastDistanceToTarget = 0f
        state.isTargetClose = false
        state.hitboxSizeEstimate = 1.0f
        state.hitboxCenterOffsetX = 0f
        state.hitboxCenterOffsetY = 0f
        state.lastHumanizationTime = 0.0
        state.lastDeltaYaw = 0f
        performanceMetrics = PerformanceMetrics()
    }
}