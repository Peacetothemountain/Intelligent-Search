package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class SecurityHardwareInfo(
    val chipName: String,
    val shortChipName: String,
    val teeName: String,
    val securityLevel: HardwareSecurityLevel,
    val title: String,
    val description: String,
    val deviceDisplayName: String,
    val socDisplayName: String,
    val isStrongBox: Boolean = false,
    /**
     * What actually holds the keys at [securityLevel]: the security chip ([chipName]) only with StrongBox,
     * otherwise the processor's secure area ([teeName]). The chip names describe the brand's best hardware,
     * which a phone without StrongBox doesn't use, so labels saying where a key is kept use these instead.
     */
    val keyHolderName: String,
    /** Short form of [keyHolderName] for buttons and toasts. */
    val keyHolderShortName: String
)

object HardwareSecurityDetector {

    /** The short label for keys kept in the processor's secure area, which has no brand name worth showing. */
    private const val TEE_KEY_HOLDER_SHORT_NAME = "Hardware Keystore"

    private fun hasStrongBoxFeature(context: Context): Boolean {
        return try {
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun detect(context: Context, securityManager: StrongBoxSecurityManager? = null): SecurityHardwareInfo {
        val isHardwareSupported = securityManager?.isStrongBoxSupported() ?: hasStrongBoxFeature(context)
        val level = if (isHardwareSupported) HardwareSecurityLevel.STRONGBOX else HardwareSecurityLevel.TEE
        return detectSecurityHardware(context, level)
    }

    fun detectSecurityHardware(
        context: Context,
        effectiveLevel: HardwareSecurityLevel
    ): SecurityHardwareInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val board = Build.BOARD.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        // Build.SOC_MODEL is API 31+, which is this app's minSdk. Unit tests run against the stubbed
        // android.jar, where the field can be absent, so the lookup stays guarded.
        val socModel = try {
            Build.SOC_MODEL.orEmpty()
        } catch (_: Throwable) {
            ""
        }
        // Public SoC vendor (e.g. "QTI", "Mediatek", "Google"); replaces reading hidden system properties,
        // which apps targeting recent SDKs can't rely on.
        val socManufacturer = try {
            Build.SOC_MANUFACTURER.orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val hasStrongBox = hasStrongBoxFeature(context) && effectiveLevel == HardwareSecurityLevel.STRONGBOX

        return resolveHardwareInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            device = device,
            hardware = hardware,
            board = board,
            product = product,
            socModel = socModel,
            socManufacturer = socManufacturer,
            hasStrongBoxFeature = hasStrongBox,
            effectiveLevel = effectiveLevel
        )
    }

    fun resolveHardwareInfo(
        manufacturer: String,
        brand: String,
        model: String,
        device: String,
        hardware: String,
        board: String,
        product: String,
        socModel: String,
        socManufacturer: String = "",
        hasStrongBoxFeature: Boolean = false,
        effectiveLevel: HardwareSecurityLevel = if (hasStrongBoxFeature) HardwareSecurityLevel.STRONGBOX else HardwareSecurityLevel.TEE
    ): SecurityHardwareInfo {
        val mfgLower = manufacturer.lowercase()
        val brandLower = brand.lowercase()
        val modelLower = model.lowercase()
        val deviceLower = device.lowercase()
        val hardwareLower = hardware.lowercase()
        // Build reports "unknown" where a value isn't set; treated as missing so the name falls through to the next field.
        val boardLower = board.lowercase().withoutUnknown()
        val productLower = product.lowercase()
        val socLower = socModel.lowercase().withoutUnknown()
        val socVendorLower = socManufacturer.lowercase().withoutUnknown()

        val resolvedSoc = when {
            socLower.isNotBlank() -> socLower
            boardLower.isNotBlank() -> boardLower
            else -> hardwareLower
        }

        val isPixel = mfgLower.contains("google") || brandLower.contains("google") ||
                modelLower.contains("pixel") || productLower.contains("pixel")

        val (chipName, shortChipName, teeName) = when {
            // 1. Google Pixel Family
            isPixel -> {
                val (pixelChip, pixelShort) = when {
                    // Pixel 11 / Tensor G6 generation (Malibu platform, Kodiak device)
                    modelLower.contains("pixel 11") || modelLower.contains("pixel11") ||
                            resolvedSoc.contains("tensor g6") || resolvedSoc.contains("malibu") ||
                            boardLower.contains("malibu") || hardwareLower.contains("malibu") ||
                            deviceLower.contains("kodiak") || hardwareLower.contains("kodiak") ->
                        Pair("Google Titan M3", "Titan M3")

                    // Pixel 10 / Tensor G5 generation (Laguna platform, Frankel / Blazer / Mustang / Rango)
                    modelLower.contains("pixel 10") || modelLower.contains("pixel10") ||
                            resolvedSoc.contains("tensor g5") || resolvedSoc.contains("laguna") ||
                            boardLower.contains("laguna") || hardwareLower.contains("laguna") ||
                            deviceLower.contains("frankel") || deviceLower.contains("blazer") ||
                            deviceLower.contains("mustang") || deviceLower.contains("rango") ->
                        Pair("Google Titan M3", "Titan M3")

                    // Pixel 6 - 9 generation (Tensor G1 through Tensor G4)
                    modelLower.contains("pixel 6") || modelLower.contains("pixel 7") ||
                            modelLower.contains("pixel 8") || modelLower.contains("pixel 9") ||
                            modelLower.contains("fold") || modelLower.contains("tablet") ||
                            deviceLower.contains("felix") || deviceLower.contains("comet") || deviceLower.contains("tangorpro") ||
                            deviceLower.contains("caimito") || deviceLower.contains("komodo") || deviceLower.contains("tokay") ||
                            deviceLower.contains("shiba") || deviceLower.contains("husky") || deviceLower.contains("akita") ||
                            deviceLower.contains("cheetah") || deviceLower.contains("panther") || deviceLower.contains("lynx") ||
                            deviceLower.contains("oriole") || deviceLower.contains("raven") || deviceLower.contains("bluejay") ||
                            resolvedSoc.contains("tensor") || resolvedSoc.contains("gs101") ||
                            resolvedSoc.contains("gs201") || resolvedSoc.contains("zuma") ||
                            boardLower.contains("gs101") || boardLower.contains("gs201") || boardLower.contains("zuma") ||
                            hardwareLower.contains("gs101") || hardwareLower.contains("gs201") || hardwareLower.contains("zuma") ->
                        Pair("Google Titan M2", "Titan M2")

                    // Legacy Pixel 3, 4, 5 series (Snapdragon SoCs + Titan M)
                    modelLower.contains("pixel 3") || modelLower.contains("pixel 4") || modelLower.contains("pixel 5") ||
                            deviceLower.contains("blueline") || deviceLower.contains("crosshatch") ||
                            deviceLower.contains("sargo") || deviceLower.contains("bonito") ||
                            deviceLower.contains("flame") || deviceLower.contains("coral") ||
                            deviceLower.contains("sunfish") || deviceLower.contains("bramble") ||
                            deviceLower.contains("redfin") || deviceLower.contains("barbet") ->
                        Pair("Google Titan M", "Titan M")

                    else -> if (hasStrongBoxFeature || effectiveLevel == HardwareSecurityLevel.STRONGBOX) {
                        Pair("Google Titan Security Module", "Titan Security")
                    } else {
                        Pair("Google Trusty Keystore", "Trusty TEE")
                    }
                }

                Triple(pixelChip, pixelShort, "Google Trusty TEE")
            }

            // 2. Samsung Galaxy Family
            mfgLower.contains("samsung") || brandLower.contains("samsung") -> {
                val hasKnoxVault = effectiveLevel == HardwareSecurityLevel.STRONGBOX || hasStrongBoxFeature

                if (hasKnoxVault) {
                    Triple("Samsung Knox Vault", "Knox Vault", "Samsung Knox TEE (TEEGRIS / Kinibi)")
                } else {
                    Triple("Samsung Knox Hardware Security", "Samsung Knox", "Samsung Knox TEE (TEEGRIS)")
                }
            }

            // 3. Xiaomi / Redmi / POCO / Black Shark
            mfgLower.contains("xiaomi") || brandLower.contains("xiaomi") ||
                    brandLower.contains("redmi") || brandLower.contains("poco") || brandLower.contains("blackshark") -> {
                Triple("Xiaomi HyperOS Hardware Security Element", "HyperOS Security Core", "Xiaomi HyperOS TEE")
            }

            // 4. OnePlus / OPPO / Realme (OPlus Group)
            mfgLower.contains("oneplus") || brandLower.contains("oneplus") ||
                    mfgLower.contains("oppo") || brandLower.contains("oppo") ||
                    mfgLower.contains("realme") || brandLower.contains("realme") -> {
                Triple("OPlus Discrete Security Element (StrongBox)", "OPlus Security Core", "OxygenOS / ColorOS Secure TEE")
            }

            // 5. Motorola / Lenovo
            mfgLower.contains("motorola") || brandLower.contains("motorola") ||
                    brandLower.contains("moto") || mfgLower.contains("lenovo") || brandLower.contains("lenovo") -> {
                Triple("Moto ThinkShield Hardware Security", "ThinkShield", "ThinkShield ARM TrustZone TEE")
            }

            // 6. Sony Xperia
            mfgLower.contains("sony") || brandLower.contains("sony") -> {
                Triple("Sony Xperia Hardware Security Module", "Xperia Security Module", "Sony Xperia Qualcomm QTEE")
            }

            // 7. Nothing / CMF
            mfgLower.contains("nothing") || brandLower.contains("nothing") || brandLower.contains("cmf") -> {
                Triple("Nothing OS Hardware Security Engine", "Nothing Security Core", "Nothing OS ARM TrustZone TEE")
            }

            // 8. Honor
            mfgLower.contains("honor") || brandLower.contains("honor") -> {
                Triple("Honor Discrete Security Chip (HTEE)", "Honor HTEE", "Honor MagicOS HTEE Dual-Engine")
            }

            // 9. Huawei
            mfgLower.contains("huawei") || brandLower.contains("huawei") -> {
                Triple("Huawei In-Chip Security Element (iSE)", "Huawei iSE", "Huawei iTrustee TEE")
            }

            // 10. Vivo / iQOO
            mfgLower.contains("vivo") || brandLower.contains("vivo") || brandLower.contains("iqoo") -> {
                Triple("Vivo Dual-Security Hardware Core", "Vivo Security Core", "OriginOS / Funtouch OS Secure TEE")
            }

            // 11. ASUS (ROG / Zenfone)
            mfgLower.contains("asus") || brandLower.contains("asus") || brandLower.contains("rog") -> {
                Triple("ASUS ROG Hardware Security Engine", "ROG Security Engine", "ASUS Secure Execution TEE")
            }

            // 12. ZTE / Nubia / RedMagic
            mfgLower.contains("zte") || brandLower.contains("zte") ||
                    brandLower.contains("nubia") || brandLower.contains("redmagic") -> {
                Triple("RedMagic Dedicated Hardware Security Core", "RedMagic Security Core", "Nubia Secure Execution TEE")
            }

            // 13. Fairphone
            mfgLower.contains("fairphone") || brandLower.contains("fairphone") -> {
                Triple("Fairphone StrongBox KeyMint Module", "StrongBox", "Fairphone ARM TrustZone TEE")
            }

            // 14. Nokia / HMD Global
            mfgLower.contains("hmd") || brandLower.contains("hmd") || brandLower.contains("nokia") -> {
                Triple("HMD Global Hardware StrongBox Keystore", "StrongBox", "Nokia ARM TrustZone TEE")
            }

            // 15. Transsion Group (Infinix / Tecno / itel)
            mfgLower.contains("transsion") || brandLower.contains("infinix") ||
                    brandLower.contains("tecno") || brandLower.contains("itel") -> {
                Triple("Transsion Hardware Security Module", "Transsion Security Core", "HiOS / XOS Secure TEE")
            }

            // 16. Meizu
            mfgLower.contains("meizu") || brandLower.contains("meizu") -> {
                Triple("Flyme All-Scenario Security Core", "Flyme Security Core", "Flyme Secure TEE")
            }

            // 17. TCL / Alcatel
            mfgLower.contains("tcl") || brandLower.contains("tcl") || brandLower.contains("alcatel") -> {
                Triple("TCL Hardware Security Module", "TCL Security Core", "TCL ARM TrustZone TEE")
            }

            // 18. Sharp Aquos
            mfgLower.contains("sharp") || brandLower.contains("sharp") -> {
                Triple("Sharp Aquos Hardware Security Engine", "Aquos Security Engine", "Sharp Secure Execution TEE")
            }

            // 19. Kyocera
            mfgLower.contains("kyocera") || brandLower.contains("kyocera") -> {
                Triple("Kyocera Rugged Hardware Security Core", "Kyocera Security Core", "Kyocera Secure TEE")
            }

            // 20. HTC
            mfgLower.contains("htc") || brandLower.contains("htc") -> {
                Triple("HTC Hardware Security Module", "HTC Security Module", "HTC ARM TrustZone TEE")
            }

            // 21. LG Electronics
            mfgLower.contains("lge") || brandLower.contains("lge") || brandLower == "lg" -> {
                Triple("LG Gatekeeper Hardware Keystore", "Gatekeeper Hardware", "LG ARM TrustZone TEE")
            }

            // 22. Qualcomm Snapdragon Devices
            socVendorLower == "qti" || socVendorLower.contains("qualcomm") ||
                    resolvedSoc.contains("snapdragon") || resolvedSoc.startsWith("sm") || resolvedSoc.startsWith("msm") ||
                    hardwareLower.contains("qcom") || boardLower.contains("qcom") || mfgLower.contains("qualcomm") -> {
                Triple("Qualcomm SPU (Secure Processing Unit)", "Qualcomm SPU", "Qualcomm Secure Execution Environment (QTEE)")
            }

            // 23. MediaTek Dimensity / Helio
            socVendorLower.contains("mediatek") || resolvedSoc.startsWith("mt") ||
                    resolvedSoc.contains("dimensity") || resolvedSoc.contains("helio") ||
                    hardwareLower.startsWith("mt") || mfgLower.contains("mediatek") -> {
                Triple("MediaTek MTEE StrongBox Coprocessor", "MediaTek MTEE", "MediaTek Micro-TEE (MTEE)")
            }

            // 24. UNISOC
            resolvedSoc.contains("unisoc") || hardwareLower.contains("unisoc") || hardwareLower.contains("sprd") -> {
                Triple("UNISOC Secure Processing Core", "UNISOC Security Core", "UNISOC Secure TEE")
            }

            // 25. Universal StrongBox / KeyMint Fallback
            effectiveLevel == HardwareSecurityLevel.STRONGBOX || hasStrongBoxFeature -> {
                Triple("StrongBox KeyMint Security Module", "StrongBox", "ARM TrustZone TEE Keystore")
            }

            // 26. Universal ARM TrustZone TEE Fallback
            else -> {
                Triple("ARM TrustZone Hardware Keystore", "Hardware Keystore", "ARM TrustZone TEE Keystore")
            }
        }

        val isStrongBox = effectiveLevel == HardwareSecurityLevel.STRONGBOX || hasStrongBoxFeature

        val title = when (effectiveLevel) {
            HardwareSecurityLevel.STRONGBOX -> "$chipName Hardware Secured"
            HardwareSecurityLevel.TEE -> "$teeName Hardware Secured"
            HardwareSecurityLevel.SOFTWARE -> "Software Encryption"
        }

        val description = when (effectiveLevel) {
            HardwareSecurityLevel.STRONGBOX -> {
                "Your encryption keys are generated and kept inside the $chipName, a separate security chip (Android StrongBox), and can't be copied out of it. Data is encrypted with AES-256-GCM."
            }
            HardwareSecurityLevel.TEE -> {
                "Your encryption keys are generated and kept inside the $teeName, the processor's isolated secure area (Android Keystore), and can't be copied out of it. Data is encrypted with AES-256-GCM."
            }
            HardwareSecurityLevel.SOFTWARE -> {
                "Cryptographic keys are stored using Android KeyStore software backing."
            }
        }

        val formattedManufacturer = manufacturer.trim().replaceFirstChar { it.uppercase() }
        val formattedModel = model.trim()
        val deviceDisplayName = when {
            formattedManufacturer.isBlank() && formattedModel.isBlank() -> "Android Device"
            formattedManufacturer.isBlank() -> formattedModel
            formattedModel.isBlank() -> formattedManufacturer
            formattedModel.startsWith(formattedManufacturer, ignoreCase = true) -> formattedModel
            else -> "$formattedManufacturer $formattedModel"
        }

        val socDisplayName = when {
            resolvedSoc.contains("tensor g6") -> "Tensor G6"
            resolvedSoc.contains("tensor g5") -> "Tensor G5"
            resolvedSoc.contains("tensor g4") -> "Tensor G4"
            resolvedSoc.contains("tensor g3") -> "Tensor G3"
            resolvedSoc.contains("tensor g2") -> "Tensor G2"
            resolvedSoc.contains("tensor") -> "Tensor"
            resolvedSoc.contains("sm8750") || resolvedSoc.contains("sun") -> "Snapdragon 8 Elite"
            resolvedSoc.contains("sm8650") || resolvedSoc.contains("pineapple") -> "Snapdragon 8 Gen 3"
            resolvedSoc.contains("sm8550") || resolvedSoc.contains("kalama") -> "Snapdragon 8 Gen 2"
            resolvedSoc.contains("sm8450") || resolvedSoc.contains("taro") -> "Snapdragon 8 Gen 1"
            resolvedSoc.contains("dimensity 9400") -> "Dimensity 9400"
            resolvedSoc.contains("dimensity 9300") -> "Dimensity 9300"
            resolvedSoc.contains("dimensity 9200") -> "Dimensity 9200"
            resolvedSoc.isNotBlank() -> resolvedSoc.replaceFirstChar { it.uppercase() }
            else -> "Hardware Keystore"
        }

        val keysOnChip = effectiveLevel == HardwareSecurityLevel.STRONGBOX

        return SecurityHardwareInfo(
            chipName = chipName,
            shortChipName = shortChipName,
            teeName = teeName,
            securityLevel = effectiveLevel,
            title = title,
            description = description,
            deviceDisplayName = deviceDisplayName,
            socDisplayName = socDisplayName,
            isStrongBox = isStrongBox,
            keyHolderName = if (keysOnChip) chipName else teeName,
            keyHolderShortName = if (keysOnChip) shortChipName else TEE_KEY_HOLDER_SHORT_NAME
        )
    }

    private fun String.withoutUnknown(): String = takeUnless { it.isBlank() || it == "unknown" }.orEmpty()
}
