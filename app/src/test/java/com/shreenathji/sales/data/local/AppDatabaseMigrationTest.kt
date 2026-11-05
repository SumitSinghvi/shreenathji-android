package com.shreenathji.sales.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Guards against the old failure mode: a schema bump without a migration used to
 * silently wipe the local DB (and every unsynced offline order) on app update.
 *
 * When you bump the DB version: add MIGRATION_N_N+1 to ALL_MIGRATIONS, build once so
 * app/schemas/.../N+1.json is exported, commit it, and add a migrate test below.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class) // skip SalesApplication's WorkManager setup
class AppDatabaseMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val schemaDir = File("schemas/com.shreenathji.sales.data.local.AppDatabase")

    private fun latestExportedVersion(): Int =
        schemaDir.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()

    @Test
    fun migrationChainIsContiguousUpToLatestSchema() {
        val migrations = AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }
        migrations.forEach { assertThat(it.endVersion).isEqualTo(it.startVersion + 1) }
        migrations.zipWithNext { a, b -> assertThat(b.startVersion).isEqualTo(a.endVersion) }
        // First migrated version must follow the last version allowed to be wiped.
        assertThat(migrations.first().startVersion).isEqualTo(AppDatabase.DESTRUCTIVE_FROM_VERSIONS.max() + 1)
        // Forgetting a migration for a new schema version fails here, not on users' phones.
        assertThat(migrations.last().endVersion).isEqualTo(latestExportedVersion())
    }

    // 18.json is reconstructed from 19.json minus the indices MIGRATION_18_19 creates.
    @Test
    fun unsyncedOrderSurvivesMigration18To19() {
        helper.createDatabase(dbName, 18).apply {
            execSQL(
                """INSERT INTO orders (id, orderNumber, partyId, partyName, totalAmount, orderStatus,
                   orderTimestamp, isSynced) VALUES ('o1', 'ORD-1', 'p1', 'Party', 1250.5, 'PENDING', 1700000000000, 0)"""
            )
            execSQL(
                """INSERT INTO order_items (id, orderId, productName, meter, price, totalPrice, isMatching, multiplier)
                   VALUES ('i1', 'o1', 'Cotton', 10.0, 125.05, 1250.5, 0, 1)"""
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 19, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT isSynced, totalAmount FROM orders WHERE id = 'o1'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
                assertThat(c.getDouble(1)).isEqualTo(1250.5)
            }
            db.query("SELECT COUNT(*) FROM order_items WHERE orderId = 'o1'").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    fun productionBuilderOpensLatestSchemaWithoutWipingData() = runBlocking {
        helper.createDatabase(dbName, latestExportedVersion()).apply {
            execSQL(
                """INSERT INTO orders (id, orderNumber, partyId, partyName, totalAmount, orderStatus,
                   orderTimestamp, isSynced) VALUES ('o2', 'ORD-2', 'p1', 'Party', 99.0, 'PENDING', 1700000000000, 0)"""
            )
            close()
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(*AppDatabase.DESTRUCTIVE_FROM_VERSIONS)
            .allowMainThreadQueries()
            .build()
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM orders WHERE isSynced = 0").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
        db.close()
    }
}
