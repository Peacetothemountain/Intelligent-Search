package com.pixel.intelligentsearch.screenshots

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.occasion.Occasion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each doodle around the standard G on a light and a dark pill, as a strip of moments through its loop; the first is
 * the still frame (what shows with animations off). Pins both the art and its motion.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class DoodleArtGoldenTest(private val occasion: Occasion) {

    @Test
    fun frames() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val drawable = doodleDrawable(activity, occasion)
        val strip = DoodleStrip(activity)
        strip.snapshot(drawable)
        val timeline = Timeline(drawable)
        for (time in FRAME_TIMES_MS.drop(1)) {
            timeline.seek(time)
            strip.snapshot(drawable)
        }
        activity.setContentView(strip, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        strip.captureRoboImage(
            filePath = "src/test/screenshots/doodles/${occasion.name.lowercase()}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    /**
     * Puts every animator of a doodle at one moment of the loop as a phone would show it. Robolectric can't run them
     * itself: it plays "infinite" animations once and its frame clock skips ahead.
     */
    internal class Timeline(drawable: AnimatedVectorDrawable) {
        private val animators: List<ValueAnimator>

        init {
            val avd = AnimatedVectorDrawable::class.java
            avd.getMethod("forceAnimationOnUI").invoke(drawable)
            avd.getDeclaredMethod("ensureAnimatorSet").apply { isAccessible = true }.invoke(drawable)
            val ui = avd.getDeclaredField("mAnimatorSet").apply { isAccessible = true }.get(drawable)!!
            val set = ui.javaClass.getDeclaredField("mSet").apply { isAccessible = true }.get(ui) as AnimatorSet
            animators = leaves(set)
        }

        private fun leaves(animator: Animator): List<ValueAnimator> = when (animator) {
            is AnimatorSet -> animator.childAnimations.flatMap(::leaves)
            is ValueAnimator -> listOf(animator)
            else -> emptyList()
        }

        /** Every animator of the doodle repeats forever (restarting, or back and forth). */
        fun seek(timeMs: Long) {
            for (animator in animators) {
                val local = timeMs - animator.startDelay
                if (local < 0) continue
                val cycles = local.toDouble() / animator.duration
                val iteration = cycles.toLong()
                var fraction = (cycles - iteration).toFloat()
                if (animator.repeatMode == ValueAnimator.REVERSE && iteration % 2 == 1L) fraction = 1 - fraction
                animator.setCurrentFraction(fraction)
            }
        }
    }

    /** Moments side by side; each shows the doodle on a light pill above a dark one. */
    private class DoodleStrip(context: Context) : View(context) {
        private val g = ContextCompat.getDrawable(context, R.drawable.ic_g_logo_colored)!!
        private val cell = (48 * resources.displayMetrics.density).toInt()
        private val frames = mutableListOf<Bitmap>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
            setMeasuredDimension(cell * FRAME_TIMES_MS.size, cell * 2)

        fun snapshot(doodle: AnimatedVectorDrawable) {
            val frame = Bitmap.createBitmap(cell, cell * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            for ((row, pill) in listOf(LIGHT_PILL, DARK_PILL).withIndex()) {
                canvas.save()
                canvas.translate(0f, (row * cell).toFloat())
                paint.color = pill
                canvas.drawRoundRect(RectF(0f, 0f, cell.toFloat(), cell.toFloat()), cell / 2f, cell / 2f, paint)
                g.setBounds(cell / 4, cell / 4, cell * 3 / 4, cell * 3 / 4)
                g.draw(canvas)
                doodle.setBounds(0, 0, cell, cell)
                doodle.draw(canvas)
                canvas.restore()
            }
            frames += frame
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0xFF888888.toInt())
            for ((i, frame) in frames.withIndex()) canvas.drawBitmap(frame, (i * cell).toFloat(), 0f, null)
        }
    }

    companion object {
        private const val LIGHT_PILL = 0xFFF1F3F4.toInt()
        private const val DARK_PILL = 0xFF303134.toInt()
        private val FRAME_TIMES_MS = listOf(0L, 300L, 700L, 1100L, 1600L, 2200L, 2900L, 3600L)

        /** The doodle as its layout loads it, the way the widget does. */
        fun doodleDrawable(context: Context, occasion: Occasion): AnimatedVectorDrawable {
            val bar = LayoutInflater.from(context).inflate(occasion.layout, null) as ProgressBar
            return bar.indeterminateDrawable.mutate() as AnimatedVectorDrawable
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = Occasion.entries.map { arrayOf(it) }
    }
}
