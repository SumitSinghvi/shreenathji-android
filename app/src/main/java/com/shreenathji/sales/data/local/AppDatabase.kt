package com.shreenathji.sales.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shreenathji.sales.data.local.dao.DailyVisitPlanDao
import com.shreenathji.sales.data.local.dao.DesignDao
import com.shreenathji.sales.data.local.dao.DesignFolderDao
import com.shreenathji.sales.data.local.dao.LocationDao
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.dao.PartyDao
import com.shreenathji.sales.data.local.dao.ProductDao
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.DesignEntity
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import kotlinx.coroutines.CoroutineScope

@Database(
    entities = [
        PartyEntity::class,
        ProductEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        LocationLogEntity::class,
        DailyVisitPlanEntity::class,
        DesignEntity::class,
        DesignFolderEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partyDao(): PartyDao
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun locationDao(): LocationDao
    abstract fun dailyVisitPlanDao(): DailyVisitPlanDao
    abstract fun designDao(): DesignDao
    abstract fun designFolderDao(): DesignFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            return try {
                db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex != -1 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                            return true
                        }
                    }
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, table: String, column: String, columnDef: String) {
            try {
                if (!columnExists(db, table, column)) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $columnDef")
                }
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "Could not add column $column to $table: ${e.message}")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "parties", "isSynced", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_visit_plans` (
                        `id` TEXT NOT NULL,
                        `planDate` TEXT NOT NULL,
                        `salesRepId` TEXT NOT NULL,
                        `salesRepName` TEXT NOT NULL,
                        `partyId` TEXT NOT NULL,
                        `partyName` TEXT NOT NULL,
                        `partyAddress` TEXT,
                        `partyPhone` TEXT,
                        `priorityOrder` INTEGER NOT NULL,
                        `instructions` TEXT,
                        `visitPurpose` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `visitedAt` INTEGER,
                        `orderId` TEXT,
                        `remarks` TEXT,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                addColumnIfNotExists(db, "daily_visit_plans", "remarks", "TEXT")
                addColumnIfNotExists(db, "daily_visit_plans", "isSynced", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `designs` (
                        `id` TEXT NOT NULL,
                        `imageUrl` TEXT NOT NULL,
                        `thumbUrl` TEXT,
                        `originalFilename` TEXT,
                        `notes` TEXT,
                        `uploadedByStaffId` TEXT,
                        `uploadedByName` TEXT,
                        `capturedOn` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                addColumnIfNotExists(db, "orders", "designIdsJson", "TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `design_folders` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `parentId` TEXT,
                        `sortOrder` INTEGER NOT NULL,
                        `coverDesignId` TEXT,
                        `createdAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                addColumnIfNotExists(db, "designs", "folderId", "TEXT")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "order_items", "designId", "TEXT")
                addColumnIfNotExists(db, "order_items", "imageUrl", "TEXT")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "products", "isMatching", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "order_items", "isMatching", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "order_items", "multiplier", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfNotExists(db, "order_items", "cutMeter", "REAL")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "products", "matchingMultiplier", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "products", "isTopBottom", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "products", "defaultRateBottom", "REAL")
                addColumnIfNotExists(db, "order_items", "component", "TEXT")
                addColumnIfNotExists(db, "order_items", "setGroupId", "TEXT")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_visit_plans_planDate` ON `daily_visit_plans` (`planDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_visit_plans_partyId_planDate` ON `daily_visit_plans` (`partyId`, `planDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_visit_plans_isSynced` ON `daily_visit_plans` (`isSynced`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_orderTimestamp` ON `orders` (`orderTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_isSynced` ON `orders` (`isSynced`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_logs_isSynced_timestamp` ON `location_logs` (`isSynced`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_parties_isSynced` ON `parties` (`isSynced`)")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shreenathji_sales.db"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
