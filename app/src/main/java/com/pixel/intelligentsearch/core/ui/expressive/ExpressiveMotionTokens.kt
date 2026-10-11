package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.spring

/**
 * Standardized Material 3 Expressive Motion and Spring Physics Tokens.
 * Conforms to Android 17 (Canary ZP11) and Material 3 Expressive motion specifications.
 * Optimized for high refresh rate displays (90Hz / 120Hz) with natural tactile elasticity.
 * All springs specify explicit damping ratio and stiffness values.
 */
object ExpressiveMotionTokens {
    // Touch compression scales
    const val PRESS_SCALE_BUTTON = 0.92f
    const val PRESS_SCALE_PILL = 0.94f
    const val PRESS_SCALE_CARD = 0.96f
    const val PRESS_SCALE_BOUNCY = 0.97f
    const val PRESS_SCALE_SEARCH_BAR = 0.98f
    const val PRESS_SCALE_ROW = 0.985f

    // Explicit physics constants for Spatial motion (layout bounds, translation, size)
    const val SPATIAL_DEFAULT_DAMPING = 0.8f
    const val SPATIAL_DEFAULT_STIFFNESS = 380f

    const val SPATIAL_FAST_DAMPING = 0.6f
    const val SPATIAL_FAST_STIFFNESS = 800f

    const val SPATIAL_SLOW_DAMPING = 0.8f
    const val SPATIAL_SLOW_STIFFNESS = 200f

    // Explicit physics constants for Effects motion (opacity, color, elevation, scale)
    const val EFFECTS_FAST_DAMPING = 1.0f
    const val EFFECTS_FAST_STIFFNESS = 3800f

    const val EFFECTS_DEFAULT_DAMPING = 1.0f
    const val EFFECTS_DEFAULT_STIFFNESS = 1600f

    const val EFFECTS_SLOW_DAMPING = 1.0f
    const val EFFECTS_SLOW_STIFFNESS = 800f

    // Explicit physics constants for Specialized springs
    const val CRITICALLY_DAMPED_DAMPING = 1.0f
    const val CRITICALLY_DAMPED_STIFFNESS = 700f

    const val MORPH_DAMPING = 0.74f
    const val MORPH_STIFFNESS = 420f

    const val BOUNCY_DAMPING = 0.68f
    const val BOUNCY_STIFFNESS = 460f

    const val GENTLE_DAMPING = 1.0f
    const val GENTLE_STIFFNESS = 1500f

    /**
     * Spatial Motion Tokens.
     * Governs position, layout bounds, size transforms, and corner geometry morphing.
     */
    object Spatial {
        /**
         * Material 3 Expressive Default Spatial spring.
         * Damping: 0.8f, Stiffness: 380f.
         * Primary token for container expansions, elastic push physics, and tab shifts.
         */
        fun <T> ExpressiveDefault(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = SPATIAL_DEFAULT_DAMPING,
            stiffness = SPATIAL_DEFAULT_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

        /**
         * Material 3 Expressive Fast Spatial spring.
         * Damping: 0.6f, Stiffness: 800f.
         * High-energy spring with controlled micro-overshoot for fast spatial transitions and squishy morphs.
         */
        fun <T> FastSpatial(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = SPATIAL_FAST_DAMPING,
            stiffness = SPATIAL_FAST_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

        /**
         * Material 3 Expressive Slow Spatial spring.
         * Damping: 0.8f, Stiffness: 200f.
         * Fluid, leisurely spatial movement for large sheet expansions and immersive backdrop reveals.
         */
        fun <T> SlowSpatial(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = SPATIAL_SLOW_DAMPING,
            stiffness = SPATIAL_SLOW_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )
    }

    /**
     * Effects Motion Tokens.
     * Governs non-spatial visual effects such as press scale, alpha transitions, elevation, and tints.
     */
    object Effects {
        /**
         * Material 3 Expressive Fast Effects / Fast Spatial micro-spring.
         * Damping: 1.0f (critically damped, zero overshoot), Stiffness: 3800f.
         * Ideal for instant touch-down scale compression and snappy tap feedback.
         */
        fun <T> FastSpatial(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = EFFECTS_FAST_DAMPING,
            stiffness = EFFECTS_FAST_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

        /**
         * Semantic alias for FastSpatial in non-spatial effect contexts.
         */
        fun <T> FastEffects(visibilityThreshold: T? = null) = FastSpatial<T>(visibilityThreshold)

        /**
         * Material 3 Expressive Default Effects spring.
         * Damping: 1.0f (critically damped), Stiffness: 1600f.
         * Standard token for color fades, elevation changes, and shadow pacing.
         */
        fun <T> ExpressiveDefault(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = EFFECTS_DEFAULT_DAMPING,
            stiffness = EFFECTS_DEFAULT_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

        /**
         * Material 3 Expressive Slow Effects spring.
         * Damping: 1.0f, Stiffness: 800f.
         * Smooth cross-fades and ambient light transitions.
         */
        fun <T> SlowEffects(visibilityThreshold: T? = null) = spring<T>(
            dampingRatio = EFFECTS_SLOW_DAMPING,
            stiffness = EFFECTS_SLOW_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )
    }

    /**
     * Critically damped spring animation specification without overshoot.
     * Essential for container transforms, card expansions, and elevations to prevent ZP11 shadow jitter.
     * Damping: 1.0f, Stiffness: 700f.
     */
    fun <T> criticallyDampedSpring(visibilityThreshold: T? = null) = spring<T>(
        dampingRatio = CRITICALLY_DAMPED_DAMPING,
        stiffness = CRITICALLY_DAMPED_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Snappy spring animation specification for rapid touch micro-interactions.
     * Damping: 0.6f, Stiffness: 800f.
     */
    fun <T> snappySpring(visibilityThreshold: T? = null) = Spatial.FastSpatial<T>(visibilityThreshold)

    /**
     * Snappy spring animation specification for expressive shape morphing.
     * Completes in ~180-220ms with natural tactile elasticity.
     * Damping: 0.74f, Stiffness: 420f.
     */
    fun <T> morphSpring(visibilityThreshold: T? = null) = spring<T>(
        dampingRatio = MORPH_DAMPING,
        stiffness = MORPH_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Bouncy spring animation specification for playful, organic tactile motion with controlled overshoot.
     * Tuned for high refresh rate displays with zero lag.
     * Damping: 0.68f, Stiffness: 460f.
     */
    fun <T> bouncySpring(visibilityThreshold: T? = null) = spring<T>(
        dampingRatio = BOUNCY_DAMPING,
        stiffness = BOUNCY_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Elastic push physics spring specification for layout weight expansions.
     * Damping: 0.8f, Stiffness: 380f.
     */
    fun <T> elasticSpring(visibilityThreshold: T? = null) = Spatial.ExpressiveDefault<T>(visibilityThreshold)

    /**
     * Gentle spring animation specification without overshoot.
     * Ideal for layout bounds, alpha transitions, and structural movement.
     * Damping: 1.0f, Stiffness: 1500f.
     */
    fun <T> gentleSpring(visibilityThreshold: T? = null) = spring<T>(
        dampingRatio = GENTLE_DAMPING,
        stiffness = GENTLE_STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )
}
