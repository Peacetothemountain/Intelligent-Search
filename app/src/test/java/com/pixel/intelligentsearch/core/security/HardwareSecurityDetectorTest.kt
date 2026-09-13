package com.pixel.intelligentsearch.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSecurityDetectorTest {

    @Test
    fun testPixel11ProXL_TitanM3() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 11 Pro XL",
            device = "kodiak",
            hardware = "malibu",
            board = "malibu",
            product = "kodiak",
            socModel = "Tensor G6",
            propStrongBoxModel = "Titan-M3",
            propKeystore = "trusty",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M3", info.chipName)
        assertEquals("Titan M3", info.shortChipName)
        assertEquals("Google Trusty TEE (Post-Quantum Secure)", info.teeName)
        assertEquals("Google Pixel 11 Pro XL", info.deviceDisplayName)
        assertEquals("Tensor G6", info.socDisplayName)
        assertTrue(info.isStrongBox)
        assertEquals("Google Titan M3 Hardware Secured", info.title)
        assertTrue(info.description.contains("NIST post-quantum cryptography"))
    }

    @Test
    fun testPixel10Pro_TitanM3() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 10 Pro",
            device = "frankel",
            hardware = "laguna",
            board = "laguna",
            product = "frankel",
            socModel = "Tensor G5",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M3", info.chipName)
        assertEquals("Titan M3", info.shortChipName)
        assertEquals("Google Trusty TEE (Post-Quantum Secure)", info.teeName)
        assertEquals("Tensor G5", info.socDisplayName)
        assertTrue(info.isStrongBox)
    }

    @Test
    fun testPixel9ProXL_TitanM2() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9 Pro XL",
            device = "komodo",
            hardware = "zuma",
            board = "zuma",
            product = "komodo",
            socModel = "Tensor G4",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M2", info.chipName)
        assertEquals("Titan M2", info.shortChipName)
        assertEquals("Google Trusty TEE", info.teeName)
        assertEquals("Tensor G4", info.socDisplayName)
        assertTrue(info.isStrongBox)
    }

    @Test
    fun testPixel8Pro_TitanM2() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8 Pro",
            device = "husky",
            hardware = "zuma",
            board = "zuma",
            product = "husky",
            socModel = "Tensor G3",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M2", info.chipName)
        assertEquals("Titan M2", info.shortChipName)
        assertEquals("Tensor G3", info.socDisplayName)
    }

    @Test
    fun testPixel7Pro_TitanM2() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 7 Pro",
            device = "cheetah",
            hardware = "gs201",
            board = "cheetah",
            product = "cheetah",
            socModel = "Tensor G2",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M2", info.chipName)
        assertEquals("Titan M2", info.shortChipName)
        assertEquals("Tensor G2", info.socDisplayName)
    }

    @Test
    fun testPixel6Pro_TitanM2() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 6 Pro",
            device = "raven",
            hardware = "gs101",
            board = "raven",
            product = "raven",
            socModel = "Tensor",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Google Titan M2", info.chipName)
        assertEquals("Titan M2", info.shortChipName)
    }

    @Test
    fun testPixel5_TitanM() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 5",
            device = "redfin",
            hardware = "qcom",
            board = "redfin",
            product = "redfin",
            socModel = "SM7250",
            hasStrongBoxFeature = false,
            effectiveLevel = HardwareSecurityLevel.TEE
        )

        assertEquals("Google Titan M", info.chipName)
        assertEquals("Titan M", info.shortChipName)
        assertEquals("Google Trusty TEE", info.teeName)
    }

    @Test
    fun testSamsungGalaxyS25Ultra_KnoxVault() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "samsung",
            brand = "samsung",
            model = "SM-S938B",
            device = "e3q",
            hardware = "qcom",
            board = "sun",
            product = "e3qxxx",
            socModel = "sm8750",
            propKnoxVault = "2.0",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Samsung Knox Vault (EAL6+)", info.chipName)
        assertEquals("Knox Vault", info.shortChipName)
        assertEquals("Samsung Knox TEE (TEEGRIS / Kinibi)", info.teeName)
        assertTrue(info.isStrongBox)
    }

    @Test
    fun testMotoThinkShield() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "motorola",
            brand = "motorola",
            model = "motorola edge 50 ultra",
            device = "eqs",
            hardware = "qcom",
            board = "pineapple",
            product = "eqs_retail",
            socModel = "sm8650",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Moto ThinkShield Hardware Security", info.chipName)
        assertEquals("ThinkShield", info.shortChipName)
        assertEquals("ThinkShield ARM TrustZone TEE", info.teeName)
    }

    @Test
    fun testXiaomiHyperOS() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Xiaomi",
            brand = "xiaomi",
            model = "2410DPN6CC",
            device = "shennong",
            hardware = "qcom",
            board = "pineapple",
            product = "shennong",
            socModel = "sm8650",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("Xiaomi HyperOS Hardware Security Element", info.chipName)
        assertEquals("HyperOS Security Core", info.shortChipName)
        assertEquals("Xiaomi HyperOS TEE", info.teeName)
    }

    @Test
    fun testOnePlusOPlusSecurity() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "OnePlus",
            brand = "oneplus",
            model = "PJZ110",
            device = "OP5D1BL1",
            hardware = "qcom",
            board = "sun",
            product = "PJZ110",
            socModel = "sm8750",
            hasStrongBoxFeature = true,
            effectiveLevel = HardwareSecurityLevel.STRONGBOX
        )

        assertEquals("OPlus Discrete Security Element (StrongBox)", info.chipName)
        assertEquals("OPlus Security Core", info.shortChipName)
        assertEquals("OxygenOS / ColorOS Secure TEE", info.teeName)
    }

    @Test
    fun testSnapdragonReferenceDevice_SPU() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Qualcomm",
            brand = "qcom",
            model = "Snapdragon 8 Elite Reference Device",
            device = "sun",
            hardware = "qcom",
            board = "sun",
            product = "sun",
            socModel = "sm8750",
            hasStrongBoxFeature = false,
            effectiveLevel = HardwareSecurityLevel.TEE
        )

        assertEquals("Qualcomm SPU (Secure Processing Unit)", info.chipName)
        assertEquals("Qualcomm SPU", info.shortChipName)
        assertEquals("Qualcomm Secure Execution Environment (QTEE)", info.teeName)
        assertEquals("Snapdragon 8 Elite", info.socDisplayName)
    }

    @Test
    fun testMediaTekDimensity_MTEE() {
        val info = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "MediaTek",
            brand = "mediatek",
            model = "Dimensity Reference Device",
            device = "k6989v1_64",
            hardware = "mt6989",
            board = "mt6989",
            product = "k6989v1_64",
            socModel = "dimensity 9400",
            hasStrongBoxFeature = false,
            effectiveLevel = HardwareSecurityLevel.TEE
        )

        assertEquals("MediaTek MTEE StrongBox Coprocessor", info.chipName)
        assertEquals("MediaTek MTEE", info.shortChipName)
        assertEquals("MediaTek Micro-TEE (MTEE)", info.teeName)
        assertEquals("Dimensity 9400", info.socDisplayName)
    }
}
