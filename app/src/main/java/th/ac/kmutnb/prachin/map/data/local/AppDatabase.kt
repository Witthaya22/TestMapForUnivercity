package th.ac.kmutnb.prachin.map.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PoiEntity::class,
        WalkPathEntity::class,
        GpsPointEntity::class,
        HazardPointEntity::class,
        RouteHistoryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao

    abstract fun walkPathDao(): WalkPathDao

    abstract fun gpsPointDao(): GpsPointDao

    abstract fun hazardPointDao(): HazardPointDao

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

        /**
         * Adds the table behind the GPS point log.
         *
         * Separate from `poi` rather than a few more columns on it: a logged point is a
         * measurement of where the device stood, while a POI is a place on the campus with
         * a name and a category. Mixing them would put half-finished measurements on the
         * map and campus semantics into the exported readings - see `docs/GPS_LOGGING.md`.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gps_point` (" +
                        "`id` TEXT NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, " +
                        "`accuracyMeters` REAL NOT NULL, " +
                        "`elevationMeters` REAL, " +
                        "`verticalAccuracyMeters` REAL, " +
                        "`satellitesUsed` INTEGER NOT NULL, " +
                        "`satellitesVisible` INTEGER NOT NULL, " +
                        "`sampleCount` INTEGER NOT NULL, " +
                        "`rejectedCount` INTEGER NOT NULL, " +
                        "`spreadMeters` REAL NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`recordedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Adds the table of marked hazards.
         *
         * Indexed on `isActive` because the map screen re-reads the active hazards on every
         * change while navigating, and a table someone has been filling in for a term is
         * mostly rows that were dealt with months ago.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hazard_point` (" +
                        "`id` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`severity` TEXT NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, " +
                        "`radiusMeters` REAL NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`gpsAccuracy` REAL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_hazard_point_isActive`" +
                        " ON `hazard_point` (`isActive`)"
                )
            }
        }

        /**
         * Records which screen each GPS reading was taken on.
         *
         * Existing rows default to the map screen, and that is not a guess: it was the
         * only collecting screen there was when they were written.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `gps_point` ADD COLUMN `captureMode` TEXT NOT NULL" +
                        " DEFAULT 'map'"
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
