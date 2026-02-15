package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fixture")
data class FixtureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "type")
    val type: String, // GONDOLA, END, ISLAND, FREEZER
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "length_cm")
    val lengthCm: Float,
    
    @ColumnInfo(name = "width_cm")
    val widthCm: Float,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

// 什器タイプの定数
object FixtureType {
    const val GONDOLA = "GONDOLA"
    const val END = "END"
    const val ISLAND = "ISLAND"
    const val FREEZER = "FREEZER"
}
