package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Standardized Material 3 Expressive Motion and Spring Physics Tokens.
 * Optimized for high refresh rate displays (90Hz / 120Hz) with natural tactile elasticity.
 */
object ExpressiveMotionTokens {
    const val PRESS_SCALE_BUTTON = 0.92f
    const val PRESS_SCALE_PILL = 0.94f
    const val PRESS_SCALE_CARD = 0.96f
    const val PRESS_SCALE_SEARCH_BAR = 0.98f

    /**
     * Snappy spring animation specification for expressive shape morphing.
     * Completes in ~180-220ms with natural tactile elasticity.
     */
    fun <T> morphSpring() = spring<T>(
        dampingRatio = 0.74f,
        stiffness = 420f
    )

    /**
     * Bouncy spring animation specification for playful, organic tactile motion.
     * Tuned for high refresh rate displays with zero lag.
     */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.74f,
        stiffness = 420f
    )

    /**
     * Elastic push physics spring specification for layout weight expansions.
     */
    fun <T> elasticSpring() = spring<T>(
        dampingRatio = 0.76f,
        stiffness = 380f
    )

    /**
     * Gentle spring animation specification without overshoot.
     * Ideal for layout bounds, alpha transitions, and structural movement.
     */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
