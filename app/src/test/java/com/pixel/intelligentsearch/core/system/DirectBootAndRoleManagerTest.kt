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

    private class ActivityNotFoundException(message: String) : RuntimeException(message)
}
