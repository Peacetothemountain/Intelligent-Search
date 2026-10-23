package com.pixel.intelligentsearch.feature.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityHardwareInfo
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled

/**
 * The Developer Note's Hardware & Security page: where this app's keys are kept, the key attestation result, and the
 * security posture chart scored from those same readings ([posture]).
 *
 * Every line of text is there at once and stays still. What moves is the banner's secure chip ([SecureChipEmblem]),
 * light travelling in along its pins (quickly while attestation is [verifying], calmly once the answer is in), and
 * the certificate chain, which sways gently with a pulse running down it. With animations off both are still.
 */
@Composable
internal fun HardwareSecurityPage(
    hardwareInfo: SecurityHardwareInfo,
    attestationResult: AttestationResult?,
    posture: List<SecurityPosture.AxisScore>,
    verifying: Boolean = attestationResult == null
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val verified = !verifying && attestationResult?.isHardwareAttested == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecureChipEmblem(
                    verified = verified,
                    verifying = verifying,
                    animate = animationsEnabled,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp).testTag(SECURE_CHIP_TAG)
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
            color = scheme.onSurface
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
                DetailLine("Device: ${hardwareInfo.deviceDisplayName}", scheme.onSurface, FontWeight.SemiBold)
                DetailLine("Hardware Security: ${hardwareInfo.chipName}", scheme.primary, FontWeight.SemiBold)
                DetailLine("TEE Architecture: ${hardwareInfo.teeName}", scheme.onSurfaceVariant, null)
                DetailLine("SoC Platform: ${hardwareInfo.socDisplayName}", scheme.onSurfaceVariant, null)
                Spacer(modifier = Modifier.height(4.dp))
                val status = when {
                    verifying || attestationResult == null -> "Verifying..."
                    attestationResult.isHardwareAttested -> "Hardware Verified"
                    // Only a verified chain earns "Verified"; otherwise just name where the key lives.
                    attestationResult.securityLevel == HardwareSecurityLevel.STRONGBOX -> "StrongBox Backed (unverified)"
                    attestationResult.securityLevel == HardwareSecurityLevel.TEE -> "TEE Backed (TrustZone)"
                    else -> "Software Backed"
                }
                Text(
                    text = "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): $status" + if (verified) " ✓" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (verified) scheme.primary else scheme.onSurfaceVariant,
                    // TalkBack hears the result as it changes, from "Verifying..." to the answer.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                CertificateChainRow(attestationResult, verified, verifying, animationsEnabled)
            }
        }
        SecurityPostureCard(posture)
    }
}

/** One line of the details box. */
@Composable
private fun DetailLine(text: String, color: Color, fontWeight: FontWeight?) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = fontWeight)
}

/**
 * The certificate chain's depth, with the chain drawn beside it (under it when the line is too narrow): one link per
 * certificate, ending in a key when the chain is rooted in Google's hardware root ([CertificateChainLinks], always
 * gently moving). Screen readers hear the depth and whether it verified.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CertificateChainRow(
    attestationResult: AttestationResult?,
    verified: Boolean,
    verifying: Boolean,
    animate: Boolean
) {
    val count = attestationResult?.certificateCount?.takeUnless { verifying }
    val description = when {
        count == null -> "Certificate chain depth: checking"
        verified -> "Certificate chain depth: $count certificates, verified"
        else -> "Certificate chain depth: $count certificates"
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Certificate Chain Depth: ${count ?: "..."} certificates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // While a check runs the last chain stays up, its pulse running quickly, so the row doesn't jump.
        val links = attestationResult?.certificateCount ?: 0
        if (links > 0) {
            CertificateChainLinks(count = links, rooted = attestationResult?.isHardwareAttested == true, verifying = verifying, animate = animate)
        }
    }
}
