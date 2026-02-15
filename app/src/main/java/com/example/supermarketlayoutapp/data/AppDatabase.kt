package com.example.supermarketlayoutapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.supermarketlayoutapp.data.dao.*
import com.example.supermarketlayoutapp.data.entity.*

@Database(
    entities = [
        ProductEntity::class,
        FixtureEntity::class,
        ShelfEntity::class,
        FacingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun productDao(): ProductDao
    abstract fun fixtureDao(): FixtureDao
    abstract fun shelfDao(): ShelfDao
    abstract fun facingDao(): FacingDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supermarket_layout_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
