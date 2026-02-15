package com.example.supermarketlayoutapp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 陳列商品エンティティ
 * 
 * 特定の場所に陳列される商品とその配置情報を管理します。
 */
@Entity(
    tableName = "display_product",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("location_id"),
        Index("product_id")
    ]
)
data class DisplayProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 場所ID */
    @ColumnInfo(name = "location_id")
    val locationId: Long,
    
    /** 商品ID */
    @ColumnInfo(name = "product_id")
    val productId: Long,
    
    /** 陳列数量 */
    @ColumnInfo(name = "quantity")
    val quantity: Int,
    
    /** フェイス数（横に並べる数） */
    @ColumnInfo(name = "facings")
    val facings: Int = 1,
    
    /** 陳列段（0が上段） */
    @ColumnInfo(name = "level")
    val level: Int? = null,
    
    /** X座標(cm) - AI生成後に設定 */
    @ColumnInfo(name = "position_x")
    val positionX: Float? = null,
    
    /** Y座標(cm) - AI生成後に設定 */
    @ColumnInfo(name = "position_y")
    val positionY: Float? = null,
    
    /** 作成日時 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
