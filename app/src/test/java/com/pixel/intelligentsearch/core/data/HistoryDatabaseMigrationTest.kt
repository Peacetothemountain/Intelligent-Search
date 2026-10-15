package com.pixel.intelligentsearch.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search history must survive app updates. The database has no destructive upgrade fallback, so a
 * version bump without a matching migration would fail to open; this catches that before release.
 */
class HistoryDatabaseMigrationTest {

    @Test
    fun migrationsFormAnUnbrokenChainToTheCurrentVersion() {
        val migrations = IntelligentSearchDatabase.MIGRATIONS.sortedBy { it.startVersion }
        var version = 1
        for (migration in migrations) {
            assertEquals("Migration out of order or missing before ${migration.startVersion}", version, migration.startVersion)
            assertEquals("Each migration must advance exactly one version", version + 1, migration.endVersion)
            version = migration.endVersion
        }
        assertEquals(
            "IntelligentSearchDatabase.VERSION was raised without a migration; history would fail to open after updating",
            IntelligentSearchDatabase.VERSION,
            version
        )
    }

    @Test
    fun exportedSchemaExistsForTheCurrentVersion() {
        val schema = java.io.File(
            "schemas/com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase/${IntelligentSearchDatabase.VERSION}.json"
        )
        assertTrue("Run a build so Room exports ${schema.path}, then commit it", schema.exists())
    }
}
