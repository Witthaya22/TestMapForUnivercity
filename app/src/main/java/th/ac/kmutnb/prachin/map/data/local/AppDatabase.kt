package th.ac.kmutnb.prachin.map.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PoiEntity::class, WalkPathEntity::class, RouteHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao

    abstract fun walkPathDao(): WalkPathDao

    abstract fun routeHistoryDao(): RouteHistoryDao

    companion object {
        private const val NAME = "kmutnb_map.db"

        /**
         * Adds the table that stores surveyed paths.
         *
         * A real migration rather than a destructive one: by the time anyone records a path
         * they have already edited POIs and walked points, and losing that to a schema change
         * would be the worst possible moment to lose it.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walk_path` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT, " +
                        "`type` TEXT NOT NULL, " +
                        "`encodedPoints` TEXT NOT NULL, " +
                        "`oneway` INTEGER NOT NULL, " +
                        "`lit` INTEGER NOT NULL, " +
                        "`covered` INTEGER NOT NULL, " +
                        "`lengthMeters` REAL NOT NULL, " +
                        "`gpsAccuracy` REAL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
