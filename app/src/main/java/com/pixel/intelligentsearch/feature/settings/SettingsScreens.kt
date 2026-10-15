package com.pixel.intelligentsearch.feature.settings
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.CornerRounding
import androidx.compose.ui.draw.clipToBounds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.*

import androidx.compose.ui.semantics.*


// -----------------------------------------------------------------------------------------
// 3. THE 40 RANDOM SHAPE GENERATOR & ENGINE LOGIC
// -----------------------------------------------------------------------------------------

private fun generate40MaterialShapes(): List<RoundedPolygon> {
    val shapes = mutableListOf<RoundedPolygon>()
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 0.2f)))
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 1f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.5f, rounding = CornerRounding(radius = 0.2f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.7f, rounding = CornerRounding(radius = 0.6f)))
    return shapes.shuffled()
}

@Stable
class MorphAnimationEngine(val coroutineScope: CoroutineScope) {
    val shapePool = generate40MaterialShapes()
    
    // Pre-shuffle initial layout configurations for varied opening sequence
    val startXs = listOf(0.15f, 0.40f, 0.65f, 0.85f).shuffled()
    val startFromBottom = listOf(true, true, false, false).shuffled()

    val bouncers = List(4) { BouncerState(it, this, startXs[it], startFromBottom[it]) }
}

class BouncerState(val index: Int, val engine: MorphAnimationEngine, val startX: Float, val startFromBottom: Boolean) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    
    val rotation = Animatable(0f)
    val morphProgress = Animatable(0f)
    val alpha = Animatable(1f)
    
    var poolIdx = index * 5
    var morph by mutableStateOf(
        Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
    )
    private var isMorphing = false
    private var lastBounceTime = 0L

    fun onBounce() {
        val now = System.currentTimeMillis()
        if (now - lastBounceTime < 250L || isMorphing) return
        lastBounceTime = now

        engine.coroutineScope.launch {
            isMorphing = true
            try {
                morphProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                poolIdx = (poolIdx + 1) % engine.shapePool.size
                morphProgress.snapTo(0f)
                morph = Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
            } finally {
                isMorphing = false
            }
        }
    }

    var vx = 0f
    var vy = 0f
    var boundsWidth = 0f
    var boundsHeight = 0f
    var deviceWidth = 0f
    var deviceHeight = 0f
    var sizePx = 0f
    var mass = 1f
    
    fun updateBounds(width: Float, height: Float, dWidth: Float, dHeight: Float, smallestWidthDp: Int, fontScale: Float) {
        boundsWidth = width
        boundsHeight = height
        deviceWidth = dWidth
        deviceHeight = dHeight
        
        // Scale particle base size proportionally to smallestScreenWidthDp, density, and fontScale
        val dpScale = (smallestWidthDp / 411f).coerceIn(0.65f, 1.4f)
        val fontAdjust = (1f / fontScale.coerceIn(0.8f, 1.5f))
        val baseFraction = 0.038f * dpScale * fontAdjust
        val sizeMultiplier = 1.0f + (index % 4) * 0.2f
        sizePx = (dWidth * baseFraction * sizeMultiplier).coerceIn(16f, (height * 0.35f).coerceAtLeast(24f))
        mass = sizeMultiplier * sizeMultiplier
    }

    init {
        engine.coroutineScope.launch {
            rotation.animateTo(360f, infiniteRepeatable(tween(8000, easing = LinearEasing)))
        }
        
        engine.coroutineScope.launch {
            
            while (boundsWidth == 0f || boundsHeight == 0f) {
                delay(16)
            }
            
            
            // Initial positioning
            x = boundsWidth * startX
            
            // Physics constraints based on the real device screen size
            val gravity = deviceHeight * 0.8f 
            val restitution = 0.82f // realistic bouncy ball
            val wallRestitution = 0.85f
            val airDragCoeff = 0.8f // scales air resistance
            val groundFrictionCoeff = 2.5f // scales friction when touching the floor

            if (startFromBottom) {
                y = boundsHeight - sizePx
                // Cap the velocity so it reaches a height inside the bounds, but uses device physics
                // v = sqrt(2 * g * h) where h is the desired apex height relative to bounds
                val targetApex = boundsHeight * (0.6f + Math.random().toFloat() * 0.3f)
                vy = -kotlin.math.sqrt(2f * gravity * targetApex)
                // Shoot from bottom and arc immediately
                vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.1f + Math.random().toFloat() * deviceWidth * 0.15f)
                onBounce()
            } else {
                y = 0f
                vy = 0f
                // Fall straight down initially
                vx = 0f
            }
            
            var timeSinceSettled = 0f
            
            var lastTime = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val currentTime = androidx.compose.runtime.withFrameNanos { it }
                val dt = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                
                val safeDt = minOf(dt, 0.05f)
                
                // Acceleration = Gravity - (Drag / Mass) * Velocity
                val ax = -(airDragCoeff / mass) * vx
                val ay = gravity - (airDragCoeff / mass) * vy
                
                vx += ax * safeDt
                vy += ay * safeDt
                
                x += vx * safeDt
                y += vy * safeDt
                
                var touchingGround = false
                
                val maxY = (boundsHeight - sizePx).coerceAtLeast(0f)
                val minY = 0f // ceiling
                if (y >= maxY) {
                    y = maxY
                    touchingGround = true
                    if (vy > 0) {
                        vy = -vy * restitution
                        // Prevent micro-vibrations going infinitely (Zeno's paradox for physics engines)
                        if (kotlin.math.abs(vy) < 15f) {
                            vy = 0f
                        } else {
                            onBounce()
                            if (vx == 0f) {
                                // "bounces before arcing away": kick horizontal velocity on the first ground impact
                                vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                            }
                        }
                    }
                } else if (y <= minY) {
                    y = minY
                    if (vy < 0) {
                        vy = -vy * restitution
                    }
                }
                
                val minX = 0f
                val maxX = (boundsWidth - sizePx).coerceAtLeast(0f)
                if (x <= minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                        onBounce()
                    }
                } else if (x >= maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                        onBounce()
                    }
                }
                
                // Apply ground friction if it's on the ground
                if (touchingGround) {
                    val frictionDrag = groundFrictionCoeff * mass * gravity
                    // apply friction opposing velocity
                    if (vx > 0) {
                        vx -= frictionDrag * safeDt / mass
                        if (vx < 0) vx = 0f
                    } else if (vx < 0) {
                        vx += frictionDrag * safeDt / mass
                        if (vx > 0) vx = 0f
                    }
                }
                
                // Continuous Animation: Relaunch shapes if settled on boundary
                // Retain continuous animation loop while settings session is active
                val isSettled = touchingGround && vy == 0f && kotlin.math.abs(vx) < 5f
                if (isSettled) {
                    timeSinceSettled += safeDt
                    // Launch again after resting for a short 0.5 to 1.5 seconds
                    if (timeSinceSettled > 0.5f + Math.random().toFloat()) {
                        val targetApex = boundsHeight * (0.4f + Math.random().toFloat() * 0.4f)
                        vy = -kotlin.math.sqrt(2f * gravity * targetApex)
                        vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                        timeSinceSettled = 0f
                        onBounce()
                    }
                } else {
                    timeSinceSettled = 0f
                }
            }
        }
    }
}

@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { MorphAnimationEngine(coroutineScope) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val deviceWidth = configuration.screenWidthDp * density.density
    val deviceHeight = configuration.screenHeightDp * density.density
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val fontScale = density.fontScale

    val baseAccentColor = MaterialTheme.colorScheme.primary
    val variant1 = MaterialTheme.colorScheme.secondary
    val variant2 = MaterialTheme.colorScheme.tertiary
    val variant3 = MaterialTheme.colorScheme.primaryContainer
    val colors = remember(baseAccentColor, variant1, variant2, variant3) {
        listOf(baseAccentColor, variant1, variant2, variant3)
    }

    val sharedNativePath = remember { android.graphics.Path() }
    val sharedComposePath = remember(sharedNativePath) { sharedNativePath.asComposePath() }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        if (width > 0f && height > 0f) {
            engine.bouncers.forEach { it.updateBounds(width, height, deviceWidth, deviceHeight, smallestWidthDp, fontScale) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val bouncers = engine.bouncers
                for (index in bouncers.indices) {
                    val bouncer = bouncers[index]
                    val sizePx = bouncer.sizePx
                    if (sizePx <= 0f) continue
                    
                    val boundedX = bouncer.x.coerceIn(0f, (width - sizePx).coerceAtLeast(0f))
                    val boundedY = bouncer.y.coerceIn(0f, (height - sizePx).coerceAtLeast(0f))
                    
                    translate(left = boundedX, top = boundedY) {
                        rotate(bouncer.rotation.value) {
                            sharedNativePath.rewind()
                            bouncer.morph.toPath(progress = bouncer.morphProgress.value, path = sharedNativePath)
                            
                            scale(scale = sizePx, pivot = Offset.Zero) {
                                drawPath(
                                    path = sharedComposePath,
                                    color = colors[index],
                                    alpha = (bouncer.alpha.value * 0.9f).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




