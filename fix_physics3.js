const fs = require('fs');

const path = 'app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt';
const content = fs.readFileSync(path, 'utf8');

const startMarker = 'class BouncerState(val index: Int, val engine: MorphAnimationEngine) {';
const endMarker = 'private fun triggerCrispHapticThump';

const startIndex = content.indexOf(startMarker);
const endIndex = content.indexOf(endMarker);

if (startIndex === -1 || endIndex === -1) {
    console.error('Markers not found!');
    process.exit(1);
}

const newContent = `class BouncerState(val index: Int, val engine: MorphAnimationEngine) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    
    val rotation = Animatable(0f)
    val morphProgress = Animatable(0f)
    val alpha = Animatable(0f)
    
    var morph by mutableStateOf(Morph(engine.shapePool[index * 5], engine.shapePool[(index * 5 + 1) % engine.shapePool.size]))

    var vx = 0f
    var vy = 0f
    var boundsWidth = 0f
    var boundsHeight = 0f
    var sizePx = 0f
    var mass = 1f
    
    fun updateBounds(width: Float, height: Float) {
        boundsWidth = width
        boundsHeight = height
        // "None are smaller though. Some are a medium size bigger."
        // Base is 0.04f. Multipliers: 1.0, 1.2, 1.4, 1.6
        val sizeMultiplier = 1.0f + (index % 4) * 0.2f
        sizePx = width * 0.04f * sizeMultiplier
        // Mass scales with 2D area (radius squared)
        mass = sizeMultiplier * sizeMultiplier
    }

    init {
        val startDelay = listOf(0L, 150L, 350L, 500L)[index % 4]
        engine.coroutineScope.launch {
            rotation.animateTo(360f, infiniteRepeatable(tween(8000 + startDelay.toInt(), easing = LinearEasing)))
        }
        
        // Morph loop
        engine.coroutineScope.launch {
            var poolIdx = index * 5
            while (true) {
                morphProgress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
                poolIdx = (poolIdx + 1) % engine.shapePool.size
                morphProgress.snapTo(0f)
                morph = Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
            }
        }
        
        engine.coroutineScope.launch {
            delay(startDelay)
            
            while (boundsWidth == 0f || boundsHeight == 0f) {
                delay(16)
            }
            
            // Initial positioning
            x = boundsWidth * listOf(0.15f, 0.40f, 0.65f, 0.85f)[index % 4]
            if (index % 2 == 0) {
                y = boundsHeight - sizePx
                vy = -(1500f + Math.random().toFloat() * 1000f)
            } else {
                y = boundsHeight * 0.2f
                vy = 0f
            }
            vx = (if (Math.random() > 0.5) 1f else -1f) * (200f + Math.random().toFloat() * 400f)
            
            launch { alpha.animateTo(1f, tween(300)) }

            val gravity = 3500f 
            val restitution = 0.82f // realistic bouncy ball
            val wallRestitution = 0.85f
            
            val airDragCoeff = 0.8f // scales air resistance
            val groundFrictionCoeff = 2.5f // scales friction when touching the floor
            
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
                
                var bounced = false
                var touchingGround = false
                
                val maxY = boundsHeight - sizePx
                if (y >= maxY) {
                    y = maxY
                    touchingGround = true
                    if (vy > 0) {
                        vy = -vy * restitution
                        // Prevent micro-vibrations going infinitely (Zeno's paradox for physics engines), 
                        // but do not artificially stop if it's visually bounding. 
                        // If it's incredibly tiny, just zero it out naturally.
                        if (kotlin.math.abs(vy) < 15f) {
                            vy = 0f
                        } else {
                            bounced = true
                        }
                    }
                }
                
                val minX = sizePx
                val maxX = boundsWidth - sizePx
                if (x <= minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                        bounced = true
                    }
                } else if (x >= maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                        bounced = true
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
                
                if (bounced) {
                    engine.hapticEventFlow.tryEmit(Unit)
                }
            }
        }
    }
}

@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val engine = LocalMorphEngine.current 
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(engine, lifecycleOwner) {
        engine.hapticEventFlow.collect {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                triggerCrispHapticThump(context, view)
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        LaunchedEffect(width, height) {
            engine.bouncers.forEach { it.updateBounds(width, height) }
        }

        val baseAccentColor = MaterialTheme.colorScheme.primary
        val variant1 = MaterialTheme.colorScheme.secondary
        val variant2 = MaterialTheme.colorScheme.tertiary
        val variant3 = MaterialTheme.colorScheme.primaryContainer

        val colors = listOf(baseAccentColor, variant1, variant2, variant3)

        engine.bouncers.forEachIndexed { index, bouncer ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sizePx = bouncer.sizePx
                if (sizePx <= 0f) return@Canvas
                
                translate(left = bouncer.x, top = bouncer.y) {
                    rotate(bouncer.rotation.value) {
                        val path = android.graphics.Path()
                        bouncer.morph.toPath(progress = bouncer.morphProgress.value, path = path)
                        
                        scale(scale = sizePx, pivot = Offset.Zero) {
                            drawPath(path.asComposePath(), colors[index].copy(alpha = bouncer.alpha.value * 0.9f))
                        }
                    }
                }
            }
        }
    }
}

`;

const replaced = content.substring(0, startIndex) + newContent + content.substring(endIndex);
fs.writeFileSync(path, replaced);
console.log('Done!');
