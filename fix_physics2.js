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
    
    fun updateBounds(width: Float, height: Float) {
        boundsWidth = width
        boundsHeight = height
        val baseSize = listOf(0.040f, 0.045f, 0.035f, 0.040f)[index % 4]
        sizePx = width * baseSize
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
            // Perfectly elastic collisions so they never stop and never need to magically respawn
            val restitution = 1.0f 
            val wallRestitution = 1.0f
            
            var lastTime = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val currentTime = androidx.compose.runtime.withFrameNanos { it }
                val dt = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                
                val safeDt = minOf(dt, 0.05f)
                
                vy += gravity * safeDt
                
                x += vx * safeDt
                y += vy * safeDt
                
                var bounced = false
                
                val maxY = boundsHeight - sizePx
                if (y > maxY) {
                    y = maxY
                    if (vy > 0) {
                        vy = -vy * restitution
                        bounced = true
                    }
                }
                
                val minX = sizePx
                val maxX = boundsWidth - sizePx
                if (x < minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                        bounced = true
                    }
                } else if (x > maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                        bounced = true
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
