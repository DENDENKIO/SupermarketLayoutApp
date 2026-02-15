package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_master",
    indices = [Index(value = ["jan"], unique = true)]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "jan")
    val jan: String,
    
    @ColumnInfo(name = "maker")
    val maker: String?,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "category")
    val category: String?,
    
    @ColumnInfo(name = "min_price")
    val minPrice: Int?,
    
    @ColumnInfo(name = "max_price")
    val maxPrice: Int?,
    
    @ColumnInfo(name = "width_mm")
    val widthMm: Int?,
    
    @ColumnInfo(name = "height_mm")
    val heightMm: Int?,
    
    @ColumnInfo(name = "depth_mm")
    val depthMm: Int?,
    
    @ColumnInfo(name = "price_updated_at")
    val priceUpdatedAt: Long?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
