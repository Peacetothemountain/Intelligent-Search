package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What [LayoutAudit] finds, and which findings fail [ResponsiveLayoutAuditTest]. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h640dp-xhdpi")
class LayoutAuditTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private fun audit(content: @Composable () -> Unit): List<LayoutAudit.Issue> {
        hilt.inject()
        compose.setContent { IntelligentSearchTheme(darkTheme = true) { content() } }
        compose.waitForIdle()
        return LayoutAudit.inspect(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), density = 2f)
    }

    @Test
    fun anEllipsizedLineIsReportedButNotClipped() {
        // A recent search's row on a 320dp phone (OverlayRecentSearchRow).
        val issues = audit {
            Text(
                "translate good morning to spanish",
                fontSize = 15.sp,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(122.dp)
            )
        }
        assertEquals(listOf(LayoutAudit.Kind.ELLIPSIZED_TEXT), issues.map { it.kind })
        assertFalse(LayoutAudit.isSerious(issues.single(), 360))
    }

    @Test
    fun aSmallTargetFailsButAnIconButtonsReservedAreaCounts() {
        val issues = audit {
            Row {
                Box(Modifier.size(30.dp).clickable {}.semantics { contentDescription = "small" })
                IconButton(onClick = {}) { Icon(Icons.Default.Close, contentDescription = "close") }
            }
        }
        val small = issues.filter { it.kind == LayoutAudit.Kind.SMALL_TOUCH_TARGET }
        assertEquals(listOf("[small]"), small.map { it.label })
        assertTrue(LayoutAudit.isSerious(small.single(), 360))
    }

    @Test
    fun overlappingTargetsFail() {
        val issues = audit {
            Box {
                Box(Modifier.size(60.dp).clickable {}.semantics { contentDescription = "a" })
                Box(Modifier.offset(30.dp, 30.dp).size(60.dp).clickable {}.semantics { contentDescription = "b" })
            }
        }
        assertEquals(listOf("[a] over [b]"), issues.filter { it.kind == LayoutAudit.Kind.OVERLAPPING_TARGETS }.map { it.label })
    }

    @Test
    fun aButtonInsideAClickableRowIsNotAnOverlap() {
        val nested = audit {
            Row(Modifier.clickable {}.padding(8.dp)) {
                Text("Row")
                IconButton(onClick = {}) { Icon(Icons.Default.Close, contentDescription = "close") }
            }
        }
        assertTrue(nested.none { it.kind == LayoutAudit.Kind.OVERLAPPING_TARGETS })
    }

    @Test
    fun aMidWordBreakFailsFromTheReflowWidthUp() {
        val split = LayoutAudit.Issue(LayoutAudit.Kind.MID_WORD_BREAK, "Customizatio n", Rect.Zero)
        assertFalse(LayoutAudit.isSerious(split, 319))
        assertTrue(LayoutAudit.isSerious(split, 320))
    }
}
