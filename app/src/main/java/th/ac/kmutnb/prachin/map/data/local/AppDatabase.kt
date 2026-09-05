package th.ac.kmutnb.prachin.map.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PoiEntity::class, RouteHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao

    abstract fun routeHistoryDao(): RouteHistoryDao

    companion object {
        private const val NAME = "kmutnb_map.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            ).build().also { instance = it }
        }
    }
}
