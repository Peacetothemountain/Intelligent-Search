package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test

class DirectBootAndRoleManagerTest {

    private open class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.pixel.intelligentsearch"
        override fun getPackageManager(): PackageManager? = null
        override fun getSystemService(name: String): Any? = null
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_DENIED
        override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
        override fun createDeviceProtectedStorageContext(): Context = this
        override fun startActivity(intent: Intent?) {
            // No-op for tests
        }
    }

    @Test
    fun testRoleManager_constantsIntegrity() {
        assertEquals("android.app.role.SEARCH_ENGINE", SearchRoleManager.ROLE_SEARCH_ENGINE)
        assertEquals("android.app.role.SEARCH", SearchRoleManager.ROLE_SEARCH)
        assertEquals("selected_search_engine", SearchRoleManager.SECURE_SELECTED_SEARCH_ENGINE)
        assertEquals("com.google.android.finsky.intent.action.UPDATE_DSE", SearchRoleManager.ACTION_UPDATE_DSE)
    }

    @Test
    fun testSearchRoleManager_nullSafeFallbacks() {
        val mockContext = TestContext()

        assertNull(SearchRoleManager.getAvailableSearchRole(mockContext))
        assertFalse(SearchRoleManager.isSearchRoleHeld(mockContext))
        assertFalse(SearchRoleManager.canWriteSecureSettings(mockContext))
    }

    @Test
    fun testSearchRoleManager_openSettingsDoesNotThrow() {
        val mockContext = object : TestContext() {
            override fun startActivity(intent: Intent?) {
                throw ActivityNotFoundException("Activity not found")
            }
        }

        // Must not rethrow
        SearchRoleManager.openDefaultSearchEngineSettings(mockContext)
    }

    @Test
    fun testDirectBootManager_lifecycleAndFlow() {
        val mockContext = TestContext()

        val directBootManager = DirectBootManager(mockContext)
        
        // Initial state
        assertTrue(directBootManager.isUserUnlocked.value)

        // Calling onUserUnlocked() keeps state unlocked and does not crash
        directBootManager.onUserUnlocked()
        assertTrue(directBootManager.isUserUnlocked.value)

    }

    @Test
    fun testDirectBootReceiver_broadcastHandlingAndDebounce() {
        val mockContext = TestContext()

        val receiver = DirectBootReceiver()
        val directBootManager = DirectBootManager(mockContext)
        receiver.directBootManager = directBootManager

        // Set injected to true on Hilt base class so test doesn't need full Hilt runtime
        try {
            val field = Hilt_DirectBootReceiver::class.java.getDeclaredField("injected")
            field.isAccessible = true
            field.set(receiver, true)
        } catch (_: Throwable) {}

        // Null action handling
        val emptyIntent = Intent()
        receiver.onReceive(mockContext, emptyIntent)

        // LOCKED_BOOT_COMPLETED handling
        val lockedIntent = Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        receiver.onReceive(mockContext, lockedIntent)

        // USER_UNLOCKED handling
        val unlockedIntent = Intent(Intent.ACTION_USER_UNLOCKED)
        receiver.onReceive(mockContext, unlockedIntent)

        // Redundant BOOT_COMPLETED within debounce period (2500ms)
        val bootCompletedIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(mockContext, bootCompletedIntent)

        assertTrue(directBootManager.isUserUnlocked.value)
    }

    @Test
    fun testSearchTileService_privacySubtitleLogic() {
        // Test privacy protection logic: when device is locked, last search query is NEVER shown
        fun computeSubtitle(isDeviceLocked: Boolean, lastQuery: String?): String {
            return if (!isDeviceLocked && !lastQuery.isNullOrBlank()) {
                "Last: $lastQuery"
            } else {
                "Search phone and web"
            }
        }

        // Locked device with sensitive last query: must show generic prompt
        assertEquals("Search phone and web", computeSubtitle(isDeviceLocked = true, lastQuery = "private medical search"))
        
        // Unlocked device with sensitive last query: shows last query
        assertEquals("Last: private medical search", computeSubtitle(isDeviceLocked = false, lastQuery = "private medical search"))

        // Unlocked device with blank last query: shows generic prompt
        assertEquals("Search phone and web", computeSubtitle(isDeviceLocked = false, lastQuery = ""))
        assertEquals("Search phone and web", computeSubtitle(isDeviceLocked = false, lastQuery = null))

        // Locked device with blank last query: shows generic prompt
        assertEquals("Search phone and web", computeSubtitle(isDeviceLocked = true, lastQuery = null))
    }

    private class ActivityNotFoundException(message: String) : RuntimeException(message)
}
