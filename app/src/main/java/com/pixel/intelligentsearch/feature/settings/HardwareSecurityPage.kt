package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityHardwareInfo
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The Developer Note's Hardware & Security page: where this app's keys are kept, the key attestation result, and the
 * security posture chart scored from those same readings ([posture]).
 *
 * Opening the page runs one restrained "security scan" on a single clock (see SecurityScanMotion.kt): the banner's
 * shield traces itself and ticks while a soft light passes over the banner, the description rises in, the details
 * resolve line by line from scrambled glyphs, the certificate chain links up and locks link by link as far as
 * verification reaches, and the posture radar draws out with its percentage counting up. When attestation finishes
 * verifying, the check pops in on a spring. Afterwards only the shield's occasional glow and the radar move. Every
 * animated value is read in the draw phase or a graphics layer, so the scan redraws without recomposing; with
 * animations off the page is drawn finished.
 */
@Composable
internal fun HardwareSecurityPage(
    hardwareInfo: SecurityHardwareInfo,
    attestationResult: AttestationResult?,
    posture: List<SecurityPosture.AxisScore>
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val scanClock = remember { Animatable(if (animationsEnabled) 0f else SCAN_END_MILLIS) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            scanClock.snapTo(SCAN_END_MILLIS)
        } else if (scanClock.value < SCAN_END_MILLIS) {
            val left = (SCAN_END_MILLIS - scanClock.value).roundToInt()
            scanClock.animateTo(SCAN_END_MILLIS, tween(left, easing = LinearEasing))
        }
    }
    val scan: () -> Float = remember(scanClock) { { scanClock.value } }

    val verified = attestationResult?.isHardwareAttested == true
    val certificates = attestationResult?.certificateCount ?: 0

    // Verification running down the certificate chain, link by link, once the links have drawn and the chain verified.
    val lockSteps = certificates + if (verified) 1 else 0
    val chainLock = remember { Animatable(if (verified && !animationsEnabled) 1f else 0f) }
    LaunchedEffect(verified, lockSteps, animationsEnabled) {
        when {
            !verified -> chainLock.snapTo(0f)
            !animationsEnabled -> chainLock.snapTo(1f)
            chainLock.value < 1f -> {
                snapshotFlow { scanClock.value >= SCAN_LOCK_START }.first { it }
                chainLock.animateTo(1f, tween(lockSteps * SCAN_LOCK_STEP_MILLIS, easing = LinearEasing))
            }
        }
    }

    // The attestation check pops in on a spring once its line has resolved (again, if the result came in late).
    val checkPop = remember { Animatable(if (verified && !animationsEnabled) 1f else 0f) }
    LaunchedEffect(verified, animationsEnabled) {
        when {
            !verified -> checkPop.snapTo(0f)
            !animationsEnabled -> checkPop.snapTo(1f)
            checkPop.value < 1f -> {
                val lineDone = detailLineStart(ATTESTATION_LINE) + DECRYPT_MILLIS
                val late = scanClock.value >= detailLineStart(ATTESTATION_LINE)
                snapshotFlow { scanClock.value >= lineDone }.first { it }
                if (late) delay(DECRYPT_MILLIS.toLong())
                checkPop.animateTo(1f, ExpressiveMotionTokens.bouncySpring())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A light sheen on a light container, a soft pale one on a dark container: the theme's own lightest tones.
        val sheen = if (scheme.primaryContainer.luminance() > 0.5f) {
            scheme.surfaceContainerLowest.copy(alpha = 0.55f)
        } else {
            scheme.onPrimaryContainer.copy(alpha = 0.16f)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .then(if (animationsEnabled) Modifier.scanSheen(scan, sheen) else Modifier)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScanShield(
                    scan = scan,
                    animate = animationsEnabled,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = hardwareInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = hardwareInfo.description,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.scanReveal(scan, SCAN_DESCRIPTION_START, SCAN_DESCRIPTION_MILLIS, rise = 8.dp)
        )
        MatteShell(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            containerColor = scheme.surfaceContainerLow,
            border = null
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DetailLine(0, "Device: ${hardwareInfo.deviceDisplayName}", scheme.onSurface, FontWeight.SemiBold, scan, animationsEnabled)
                DetailLine(1, "Hardware Security: ${hardwareInfo.chipName}", scheme.primary, FontWeight.SemiBold, scan, animationsEnabled)
                DetailLine(2, "TEE Architecture: ${hardwareInfo.teeName}", scheme.onSurfaceVariant, null, scan, animationsEnabled)
                DetailLine(3, "SoC Platform: ${hardwareInfo.socDisplayName}", scheme.onSurfaceVariant, null, scan, animationsEnabled)
                Spacer(modifier = Modifier.height(4.dp))
                val attestation = "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): " + when {
                    attestationResult == null -> "Verifying..."
                    attestationResult.isHardwareAttested -> "Hardware Verified"
                    // Only a verified chain earns "Verified"; otherwise just name where the key lives.
                    attestationResult.securityLevel == HardwareSecurityLevel.STRONGBOX -> "StrongBox Backed (unverified)"
                    attestationResult.securityLevel == HardwareSecurityLevel.TEE -> "TEE Backed (TrustZone)"
                    else -> "Software Backed"
                }
                val checkColor = scheme.primary
                DetailLine(
                    index = ATTESTATION_LINE,
                    text = attestation,
                    color = if (verified) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    scan = scan,
                    animate = animationsEnabled,
                    trailingCheck = if (!verified) null else {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = checkColor,
                                modifier = Modifier.graphicsLayer {
                                    val s = checkPop.value
                                    scaleX = s
                                    scaleY = s
                                    alpha = s.coerceIn(0f, 1f)
                                }
                            )
                        }
                    }
                )
                CertificateChainRow(attestationResult, verified, scan, { chainLock.value }, animationsEnabled)
            }
        }
        SecurityPostureCard(posture, entryDelayMillis = SCAN_RADAR_START_MILLIS)
    }
}

/** The details line the attestation result is on. */
private const val ATTESTATION_LINE = 4

/** One line of the details box, revealed in turn ([index]) on the scan clock and resolving from scrambled glyphs. */
@Composable
private fun DetailLine(
    index: Int,
    text: String,
    color: Color,
    fontWeight: FontWeight?,
    scan: () -> Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
    trailingCheck: (@Composable () -> Unit)? = null
) {
    val start = detailLineStart(index)
    DecryptText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        progress = { scanStage(scan(), start, DECRYPT_MILLIS.toFloat()) },
        animate = animate,
        seed = index + 1,
        fontWeight = fontWeight,
        trailingCheck = trailingCheck,
        modifier = modifier.scanReveal(scan, start, SCAN_LINE_FADE_MILLIS)
    )
}

/**
 * The certificate chain's depth, with the chain drawn beside it (under it when the line is too narrow): one link per
 * certificate, locking as verification reaches it, ending in a key when the chain is rooted in Google's hardware
 * root. Screen readers hear the depth and whether it verified.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CertificateChainRow(
    attestationResult: AttestationResult?,
    verified: Boolean,
    scan: () -> Float,
    lock: () -> Float,
    animate: Boolean
) {
    val count = attestationResult?.certificateCount
    val description = when {
        count == null -> "Certificate chain depth: checking"
        verified -> "Certificate chain depth: $count certificates, verified"
        else -> "Certificate chain depth: $count certificates"
    }
    val start = detailLineStart(SCAN_CHAIN_LINE)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .scanReveal(scan, start, SCAN_LINE_FADE_MILLIS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        DecryptText(
            text = "Certificate Chain Depth: ${count ?: "..."} certificates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            progress = { scanStage(scan(), start, DECRYPT_MILLIS.toFloat()) },
            animate = animate,
            seed = SCAN_CHAIN_LINE + 1
        )
        if (count != null && count > 0) {
            CertificateChainLinks(count = count, rooted = verified, scan = scan, lock = lock)
        }
    }
}
