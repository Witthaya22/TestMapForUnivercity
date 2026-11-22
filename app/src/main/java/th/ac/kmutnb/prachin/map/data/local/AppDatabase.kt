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
        TrackLogEntity::class,
        GpsPointEntity::class,
        HazardPointEntity::class,
        HazardSoundEntity::class,
        RouteHistoryEntity::class,
        SosReportEntity::class,
        UserAccountEntity::class,
        TouristPositionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao

    abstract fun trackLogDao(): TrackLogDao

    abstract fun gpsPointDao(): GpsPointDao

    abstract fun hazardPointDao(): HazardPointDao

    abstract fun hazardSoundDao(): HazardSoundDao

    abstract fun routeHistoryDao(): RouteHistoryDao

    abstract fun sosReportDao(): SosReportDao

    abstract fun userAccountDao(): UserAccountDao

    abstract fun touristPositionDao(): TouristPositionDao

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

        /**
         * Gives each hazard a tone, and catalogues the sounds the user imported.
         *
         * `soundId` is added nullable rather than with a default, and null is not "no
         * sound": it means the tone follows the hazard's severity. Every row that exists
         * at this point was marked before sounds did, by someone who never chose one, so
         * that is what they meant - and it keeps a warning that used to be spoken from
         * turning silent because of a schema change.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `hazard_point` ADD COLUMN `soundId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hazard_sound` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Replaces `walk_path` with `track_log`.
         *
         * The old table described a path (a name, a type, whether it was lit or covered)
         * and recorded nothing about the walk that produced it. That is backwards for a
         * survey: the description was four guesses typed while standing in the rain, and
         * the one thing nobody could reconstruct afterwards - how good the signal was -
         * was never stored at all.
         *
         * Every existing row is copied over rather than dropped. A recorded path is
         * somebody's afternoon, and its geometry is still exactly as good as it was; only
         * the measurements are unknown, which is honest, because they were never taken.
         * The old name becomes the note, and every copied track stays in the routing
         * graph, because it was in it before this migration ran.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_log` (" +
                        "`id` TEXT NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`encodedPoints` TEXT NOT NULL, " +
                        "`lengthMeters` REAL NOT NULL, " +
                        "`averageAccuracyMeters` REAL NOT NULL, " +
                        "`worstAccuracyMeters` REAL NOT NULL, " +
                        "`satellitesUsed` INTEGER NOT NULL, " +
                        "`satellitesVisible` INTEGER NOT NULL, " +
                        "`fixCount` INTEGER NOT NULL, " +
                        "`rejectedCount` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`isUsedForRouting` INTEGER NOT NULL, " +
                        "`recordedAt` INTEGER NOT NULL, " +
                        "`captureMode` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_track_log_isUsedForRouting`" +
                        " ON `track_log` (`isUsedForRouting`)"
                )
                // Numbered by walking order with a correlated count rather than a window
                // function: SQLite on API 24 predates OVER().
                db.execSQL(
                    "INSERT INTO `track_log` (`id`, `code`, `encodedPoints`, `lengthMeters`," +
                        " `averageAccuracyMeters`, `worstAccuracyMeters`, `satellitesUsed`," +
                        " `satellitesVisible`, `fixCount`, `rejectedCount`, `durationSeconds`," +
                        " `note`, `isUsedForRouting`, `recordedAt`, `captureMode`) " +
                        "SELECT w.`id`," +
                        " 'T' || substr('000' || (1 + (SELECT COUNT(*) FROM `walk_path` e" +
                        "   WHERE e.`createdAt` < w.`createdAt`)), -3)," +
                        " w.`encodedPoints`, w.`lengthMeters`," +
                        " COALESCE(w.`gpsAccuracy`, 0.0), COALESCE(w.`gpsAccuracy`, 0.0)," +
                        " 0, 0, 0, 0, 0, COALESCE(w.`name`, ''), 1, w.`createdAt`, 'map' " +
                        "FROM `walk_path` w"
                )
                db.execSQL("DROP TABLE IF EXISTS `walk_path`")
            }
        }

        /**
         * Gives each track vertex its own accuracy and provenance.
         *
         * Existing rows get an empty value, which the model reads as "walked, accuracy
         * unknown" - true, because until now only the whole-walk figures were kept and
         * the per-fix numbers are gone. Nothing is guessed back into them.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `track_log` ADD COLUMN `encodedVertexInfo` TEXT NOT NULL" +
                        " DEFAULT ''"
                )
            }
        }

        /**
         * Adds the table behind SOS calls for help. A new table only: nothing already
         * stored is touched, and a call sent before an update must survive it.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sos_report` (" +
                        "`id` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`username` TEXT NOT NULL, " +
                        "`lat` REAL, " +
                        "`lon` REAL, " +
                        "`accuracyMeters` REAL, " +
                        "`category` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL, " +
                        "`trailName` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`handledAt` INTEGER, " +
                        "`adminNote` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Accounts and tourist positions (proposal §2.3.1.1-2), and who sent each SOS.
         * New tables and added columns only; every call and track already stored stays.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_account` (" +
                        "`id` TEXT NOT NULL, `memberId` TEXT NOT NULL, `username` TEXT NOT NULL, " +
                        "`passwordHash` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`email` TEXT NOT NULL, `nationality` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`permissions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                        "`isDemo` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_account_username` ON `user_account` (`username`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_account_email` ON `user_account` (`email`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tourist_position` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, " +
                        "`trailId` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                        "`accuracyMeters` REAL NOT NULL, `recordedAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tourist_position_userId_recordedAt` " +
                        "ON `tourist_position` (`userId`, `recordedAt`)"
                )
                db.execSQL("ALTER TABLE `sos_report` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `sos_report` ADD COLUMN `touristId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `sos_report` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sos_report` ADD COLUMN `synced` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            ).build().also { instance = it }
        }
    }
}
