package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shelf",
    foreignKeys = [
        ForeignKey(
            entity = FixtureEntity::class,
            parentColumns = ["id"],
            childColumns = ["fixture_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["fixture_id"])]
)
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "fixture_id")
    val fixtureId: Long,
    
    @ColumnInfo(name = "level")
    val level: Int, // 1が最下段
    
    @ColumnInfo(name = "length_mm")
    val lengthMm: Int
)
