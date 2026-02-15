package com.example.supermarketlayoutapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.supermarketlayoutapp.data.dao.*
import com.example.supermarketlayoutapp.data.entity.*

@Database(
    entities = [
        ProductEntity::class,
        FixtureEntity::class,
        ShelfEntity::class,
        FacingEntity::class
    ],
    version = 2,  // バージョンを2にアップ
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
        
        /**
         * バージョン1から2へのマイグレーション
         * FixtureEntityに2Dレイアウト用フィールドを追加
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // fixtureテーブルに新しいカラムを追加
                database.execSQL(
                    "ALTER TABLE fixture ADD COLUMN position_x REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE fixture ADD COLUMN position_y REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE fixture ADD COLUMN rotation REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE fixture ADD COLUMN color INTEGER NOT NULL DEFAULT -12627531"  // 0xFF3F51B5 = -12627531
                )
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supermarket_layout_database"
                )
                    .addMigrations(MIGRATION_1_2)  // マイグレーションを追加
                    .fallbackToDestructiveMigration()  // マイグレーション失敗時は再作成
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
