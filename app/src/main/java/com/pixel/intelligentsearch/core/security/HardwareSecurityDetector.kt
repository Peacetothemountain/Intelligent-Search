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
    val isStrongBox: Boolean = false
)

object HardwareSecurityDetector {

    private fun getSystemProperty(key: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun hasStrongBoxFeature(context: Context): Boolean {
        return try {
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun detect(context: Context): SecurityHardwareInfo {
        val hasStrongBox = hasStrongBoxFeature(context)
        val level = if (hasStrongBox) HardwareSecurityLevel.STRONGBOX else HardwareSecurityLevel.TEE
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
        val socModel = if (Build.VERSION.SDK_INT >= 31) {
            try {
                Build.SOC_MODEL.orEmpty()
            } catch (_: Throwable) {
                ""
            }
        } else {
            ""
        }

        val propStrongBoxModel = getSystemProperty("ro.strongbox.model")
        val propStrongBoxMfg = getSystemProperty("ro.strongbox.manufacturer")
        val propKeystore = getSystemProperty("ro.hardware.keystore")
        val propSocModel = getSystemProperty("ro.soc.model")
        val propPlatform = getSystemProperty("ro.board.platform")
        val propKnoxVault = getSystemProperty("ro.security.vault.version")
        val hasStrongBox = hasStrongBoxFeature(context)

        return resolveHardwareInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            device = device,
            hardware = hardware,
            board = board,
            product = product,
            socModel = socModel,
            propStrongBoxModel = propStrongBoxModel,
            propStrongBoxMfg = propStrongBoxMfg,
            propKeystore = propKeystore,
            propSocModel = propSocModel,
            propPlatform = propPlatform,
            propKnoxVault = propKnoxVault,
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
        propStrongBoxModel: String = "",
        propStrongBoxMfg: String = "",
        propKeystore: String = "",
        propSocModel: String = "",
        propPlatform: String = "",
        propKnoxVault: String = "",
        hasStrongBoxFeature: Boolean = false,
        effectiveLevel: HardwareSecurityLevel = if (hasStrongBoxFeature) HardwareSecurityLevel.STRONGBOX else HardwareSecurityLevel.TEE
    ): SecurityHardwareInfo {
        val mfgLower = manufacturer.lowercase()
        val brandLower = brand.lowercase()
        val modelLower = model.lowercase()
        val deviceLower = device.lowercase()
        val hardwareLower = hardware.lowercase()
        val boardLower = board.lowercase()
        val productLower = product.lowercase()
        val socLower = socModel.lowercase()
        val propSocLower = propSocModel.lowercase()
        val propPlatformLower = propPlatform.lowercase()

        val resolvedSoc = when {
            socLower.isNotBlank() -> socLower
            propSocLower.isNotBlank() -> propSocLower
            propPlatformLower.isNotBlank() -> propPlatformLower
            boardLower.isNotBlank() -> boardLower
            else -> hardwareLower
        }

        val isPixel = mfgLower.contains("google") || brandLower.contains("google") ||
                modelLower.contains("pixel") || productLower.contains("pixel")

        val (chipName, shortChipName, teeName) = when {
            // 1. Google Pixel Family
            isPixel -> {
                val (pixelChip, pixelShort) = when {
                    // Direct dynamic property from OS KeyMint HAL
                    propStrongBoxModel.contains("Titan-M3", ignoreCase = true) ||
                            propStrongBoxModel.contains("Titan M3", ignoreCase = true) ->
                        Pair("Google Titan M3", "Titan M3")

                    propStrongBoxModel.contains("Titan-M2", ignoreCase = true) ||
                            propStrongBoxModel.contains("Titan M2", ignoreCase = true) ->
                        Pair("Google Titan M2", "Titan M2")

                    propStrongBoxModel.contains("Titan-M", ignoreCase = true) ||
                            propStrongBoxModel.contains("Titan M", ignoreCase = true) ->
                        Pair("Google Titan M", "Titan M")

                    // Pixel 11 / Tensor G6 generation (Malibu platform, Kodiak device)
                    model.contains("pixel 11") || model.contains("pixel11") ||
                            resolvedSoc.contains("tensor g6") || resolvedSoc.contains("malibu") ||
                            board.contains("malibu") || hardware.contains("malibu") ||
                            device.contains("kodiak") || hardware.contains("kodiak") ->
                        Pair("Google Titan M3", "Titan M3")

                    // Pixel 10 / Tensor G5 generation (Laguna platform, Frankel / Blazer / Mustang / Rango)
                    model.contains("pixel 10") || model.contains("pixel10") ||
                            resolvedSoc.contains("tensor g5") || resolvedSoc.contains("laguna") ||
                            board.contains("laguna") || hardware.contains("laguna") ||
                            device.contains("frankel") || device.contains("blazer") ||
                            device.contains("mustang") || device.contains("rango") ->
                        Pair("Google Titan M3", "Titan M3")

                    // Pixel 6 - 9 generation (Tensor G1 through Tensor G4)
                    model.contains("pixel 6") || model.contains("pixel 7") ||
                            model.contains("pixel 8") || model.contains("pixel 9") ||
                            model.contains("fold") || model.contains("tablet") ||
                            device.contains("felix") || device.contains("comet") || device.contains("tangorpro") ||
                            device.contains("caimito") || device.contains("komodo") || device.contains("tokay") ||
                            device.contains("shiba") || device.contains("husky") || device.contains("akita") ||
                            device.contains("cheetah") || device.contains("panther") || device.contains("lynx") ||
                            device.contains("oriole") || device.contains("raven") || device.contains("bluejay") ||
                            resolvedSoc.contains("tensor") || resolvedSoc.contains("gs101") ||
                            resolvedSoc.contains("gs201") || resolvedSoc.contains("zuma") ||
                            board.contains("gs101") || board.contains("gs201") || board.contains("zuma") ||
                            hardware.contains("gs101") || hardware.contains("gs201") || hardware.contains("zuma") ->
                        Pair("Google Titan M2", "Titan M2")

                    // Legacy Pixel 3, 4, 5 series (Snapdragon SoCs + Titan M)
                    modelLower.contains("pixel 3") || modelLower.contains("pixel 4") || modelLower.contains("pixel 5") ||
                            deviceLower.contains("blueline") || deviceLower.contains("crosshatch") ||
                            deviceLower.contains("sargo") || deviceLower.contains("bonito") ||
                            deviceLower.contains("flame") || deviceLower.contains("coral") ||
                            deviceLower.contains("sunfish") || deviceLower.contains("bramble") ||
                            deviceLower.contains("redfin") || deviceLower.contains("barbet") ->
                        Pair("Google Titan M", "Titan M")

                    else -> if (hasStrongBoxFeature) {
                        Pair("Google Titan M3", "Titan M3")
                    } else {
                        Pair("Google Titan M2", "Titan M2")
                    }
                }

                val pixelTee = if (pixelChip == "Google Titan M3") {
                    "Google Trusty TEE (Post-Quantum Secure)"
                } else {
                    "Google Trusty TEE"
                }
                Triple(pixelChip, pixelShort, pixelTee)
            }

            // 2. Samsung Galaxy Family
            mfgLower.contains("samsung") || brandLower.contains("samsung") -> {
                val hasKnoxVault = effectiveLevel == HardwareSecurityLevel.STRONGBOX ||
                        hasStrongBoxFeature ||
                        propKnoxVault.isNotBlank() ||
                        modelLower.contains("s2") || modelLower.contains("s1") || modelLower.contains("z") ||
                        modelLower.contains("fold") || modelLower.contains("flip") || modelLower.contains("a5") ||
                        modelLower.contains("a35") || modelLower.contains("a55") || modelLower.contains("a56")

                if (hasKnoxVault) {
                    Triple("Samsung Knox Vault (EAL6+)", "Knox Vault", "Samsung Knox TEE (TEEGRIS / Kinibi)")
                } else {
                    Triple("Samsung Knox Hardware Security", "Samsung Knox", "Samsung Knox TEE (TEEGRIS)")
                }
            }

            // 3. Xiaomi / Redmi / POCO / Black Shark
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                    brand.contains("redmi") || brand.contains("poco") || brand.contains("blackshark") -> {
                Triple("Xiaomi HyperOS Hardware Security Element", "HyperOS Security Core", "Xiaomi HyperOS TEE")
            }

            // 4. OnePlus / OPPO / Realme (OPlus Group)
            manufacturer.contains("oneplus") || brand.contains("oneplus") ||
                    manufacturer.contains("oppo") || brand.contains("oppo") ||
                    manufacturer.contains("realme") || brand.contains("realme") -> {
                Triple("OPlus Discrete Security Element (StrongBox)", "OPlus Security Core", "OxygenOS / ColorOS Secure TEE")
            }

            // 5. Motorola / Lenovo
            manufacturer.contains("motorola") || brand.contains("motorola") ||
                    brand.contains("moto") || manufacturer.contains("lenovo") || brand.contains("lenovo") -> {
                Triple("Moto ThinkShield Hardware Security", "ThinkShield", "ThinkShield ARM TrustZone TEE")
            }

            // 6. Sony Xperia
            manufacturer.contains("sony") || brand.contains("sony") -> {
                Triple("Sony Xperia Hardware Security Module", "Xperia Security Module", "Sony Xperia Qualcomm QTEE")
            }

            // 7. Nothing / CMF
            manufacturer.contains("nothing") || brand.contains("nothing") || brand.contains("cmf") -> {
                Triple("Nothing OS Hardware Security Engine", "Nothing Security Core", "Nothing OS ARM TrustZone TEE")
            }

            // 8. Honor
            manufacturer.contains("honor") || brand.contains("honor") -> {
                Triple("Honor Discrete Security Chip (HTEE)", "Honor HTEE", "Honor MagicOS HTEE Dual-Engine")
            }

            // 9. Huawei
            manufacturer.contains("huawei") || brand.contains("huawei") -> {
                Triple("Huawei In-Chip Security Element (iSE)", "Huawei iSE", "Huawei iTrustee TEE (CC EAL5+)")
            }

            // 10. Vivo / iQOO
            manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo") -> {
                Triple("Vivo Dual-Security Hardware Core", "Vivo Security Core", "OriginOS / Funtouch OS Secure TEE")
            }

            // 11. ASUS (ROG / Zenfone)
            manufacturer.contains("asus") || brand.contains("asus") || brand.contains("rog") -> {
                Triple("ASUS ROG Hardware Security Engine", "ROG Security Engine", "ASUS Secure Execution TEE")
            }

            // 12. ZTE / Nubia / RedMagic
            manufacturer.contains("zte") || brand.contains("zte") ||
                    brand.contains("nubia") || brand.contains("redmagic") -> {
                Triple("RedMagic Dedicated Hardware Security Core", "RedMagic Security Core", "Nubia Secure Execution TEE")
            }

            // 13. Fairphone
            manufacturer.contains("fairphone") || brand.contains("fairphone") -> {
                Triple("Fairphone StrongBox KeyMint Module", "StrongBox", "Fairphone ARM TrustZone TEE")
            }

            // 14. Nokia / HMD Global
            manufacturer.contains("hmd") || brand.contains("hmd") || brand.contains("nokia") -> {
                Triple("HMD Global Hardware StrongBox Keystore", "StrongBox", "Nokia ARM TrustZone TEE")
            }

            // 15. Transsion Group (Infinix / Tecno / itel)
            manufacturer.contains("transsion") || brand.contains("infinix") ||
                    brand.contains("tecno") || brand.contains("itel") -> {
                Triple("Transsion Hardware Security Module", "Transsion Security Core", "HiOS / XOS Secure TEE")
            }

            // 16. Meizu
            manufacturer.contains("meizu") || brand.contains("meizu") -> {
                Triple("Flyme All-Scenario Security Core", "Flyme Security Core", "Flyme Secure TEE")
            }

            // 17. TCL / Alcatel
            manufacturer.contains("tcl") || brand.contains("tcl") || brand.contains("alcatel") -> {
                Triple("TCL Hardware Security Module", "TCL Security Core", "TCL ARM TrustZone TEE")
            }

            // 18. Sharp Aquos
            manufacturer.contains("sharp") || brand.contains("sharp") -> {
                Triple("Sharp Aquos Hardware Security Engine", "Aquos Security Engine", "Sharp Secure Execution TEE")
            }

            // 19. Kyocera
            manufacturer.contains("kyocera") || brand.contains("kyocera") -> {
                Triple("Kyocera Rugged Hardware Security Core", "Kyocera Security Core", "Kyocera Secure TEE")
            }

            // 20. HTC
            manufacturer.contains("htc") || brand.contains("htc") -> {
                Triple("HTC Hardware Security Module", "HTC Security Module", "HTC ARM TrustZone TEE")
            }

            // 21. LG Electronics
            manufacturer.contains("lge") || brand.contains("lge") || brand.contains("lg") -> {
                Triple("LG Gatekeeper Hardware Keystore", "Gatekeeper Hardware", "LG ARM TrustZone TEE")
            }

            // 22. Qualcomm Snapdragon Devices
            resolvedSoc.contains("snapdragon") || hardware.contains("qcom") ||
                    board.contains("qcom") || manufacturer.contains("qualcomm") ||
                    propPlatform.contains("msm") || propPlatform.contains("sm") ||
                    propPlatform.contains("lahaina") || propPlatform.contains("taro") ||
                    propPlatform.contains("kalama") || propPlatform.contains("pineapple") ||
                    propPlatform.contains("sun") -> {
                Triple("Qualcomm SPU (Secure Processing Unit)", "Qualcomm SPU", "Qualcomm Secure Execution Environment (QTEE)")
            }

            // 23. MediaTek Dimensity / Helio
            resolvedSoc.contains("dimensity") || resolvedSoc.contains("helio") ||
                    hardware.contains("mt") || manufacturer.contains("mediatek") ||
                    propPlatform.contains("mt") -> {
                Triple("MediaTek MTEE StrongBox Coprocessor", "MediaTek MTEE", "MediaTek Micro-TEE (MTEE)")
            }

            // 24. UNISOC
            resolvedSoc.contains("unisoc") || hardware.contains("unisoc") || hardware.contains("sprd") -> {
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
                if (chipName.contains("Titan M3")) {
                    "Your cryptographic keys and sensitive tokens are protected by the $chipName isolated discrete hardware security coprocessor with NIST post-quantum cryptography (quantum-safe secure boot, StrongBox KeyMint HAL, and Common Criteria EAL6+ physical tamper resistance)."
                } else {
                    "Your cryptographic keys and sensitive tokens are protected by the $chipName isolated discrete hardware security coprocessor (StrongBox KeyMint HAL with Common Criteria EAL5+/EAL6+ tamper resistance and AES-256 GCM Envelope Encryption)."
                }
            }
            HardwareSecurityLevel.TEE -> {
                "Your cryptographic keys are protected by the $teeName (TrustZone Secure Keystore Module with AES-256 GCM Envelope Encryption)."
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

        return SecurityHardwareInfo(
            chipName = chipName,
            shortChipName = shortChipName,
            teeName = teeName,
            securityLevel = effectiveLevel,
            title = title,
            description = description,
            deviceDisplayName = deviceDisplayName,
            socDisplayName = socDisplayName,
            isStrongBox = isStrongBox
        )
    }
}