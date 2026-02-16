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
        FacingEntity::class,
        LocationEntity::class,
        DisplayProductEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun productDao(): ProductDao
    abstract fun fixtureDao(): FixtureDao
    abstract fun shelfDao(): ShelfDao
    abstract fun facingDao(): FacingDao
    abstract fun locationDao(): LocationDao
    abstract fun displayProductDao(): DisplayProductDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * バージョン1から2へのマイグレーション
         * FixtureEntityに2Dレイアウト用フィールドを追加
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
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
                        "ALTER TABLE fixture ADD COLUMN color INTEGER NOT NULL DEFAULT -12627531"
                    )
                } catch (e: Exception) {
                    // カラムがすでに存在する場合はスキップ
                }
            }
        }
        
        /**
         * バージョン2から3へのマイグレーション
         * 棚割り機能用のテーブルを追加
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // locationテーブルを作成
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS location (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            shelf_width REAL NOT NULL,
                            shelf_height REAL NOT NULL,
                            shelf_levels INTEGER NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    
                    // display_productテーブルを作成
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS display_product (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            location_id INTEGER NOT NULL,
                            product_id INTEGER NOT NULL,
                            quantity INTEGER NOT NULL,
                            facings INTEGER NOT NULL,
                            level INTEGER,
                            position_x REAL,
                            position_y REAL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(location_id) REFERENCES location(id) ON DELETE CASCADE,
                            FOREIGN KEY(product_id) REFERENCES product(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    
                    // インデックスを作成
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_display_product_location_id ON display_product(location_id)"
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_display_product_product_id ON display_product(product_id)"
                    )
                } catch (e: Exception) {
                    // テーブルがすでに存在する場合はスキップ
                }
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supermarket_layout_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()  // マイグレーション失敗時はデータベースを再作成
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
