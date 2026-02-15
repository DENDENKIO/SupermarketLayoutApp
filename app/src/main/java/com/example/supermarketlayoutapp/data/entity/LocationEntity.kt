package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 陳列場所エンティティ
 * 
 * 商品を陳列する場所（エンド、ゴンドラなど）を管理します。
 */
@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 場所名（例：「エンド1」「ゴンドラA-1」） */
    @ColumnInfo(name = "name")
    val name: String,
    
    /** 棚幅(cm) */
    @ColumnInfo(name = "shelf_width")
    val shelfWidth: Float,
    
    /** 棚高(cm) */
    @ColumnInfo(name = "shelf_height")
    val shelfHeight: Float,
    
    /** 段数 */
    @ColumnInfo(name = "shelf_levels")
    val shelfLevels: Int,
    
    /** 作成日時 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
