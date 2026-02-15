package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.DisplayProductEntity
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * 陳列商品と商品情報を結合したデータクラス
 */
data class DisplayProductWithProduct(
    @Embedded val displayProduct: DisplayProductEntity,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id"
    )
    val product: ProductEntity
)

@Dao
interface DisplayProductDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(displayProduct: DisplayProductEntity): Long
    
    @Update
    suspend fun update(displayProduct: DisplayProductEntity)
    
    @Delete
    suspend fun delete(displayProduct: DisplayProductEntity)
    
    @Query("SELECT * FROM display_product WHERE location_id = :locationId ORDER BY level ASC, position_x ASC")
    fun getDisplayProductsByLocation(locationId: Long): Flow<List<DisplayProductEntity>>
    
    @Transaction
    @Query("SELECT * FROM display_product WHERE location_id = :locationId ORDER BY level ASC, position_x ASC")
    fun getDisplayProductsWithProductByLocation(locationId: Long): Flow<List<DisplayProductWithProduct>>
    
    @Query("DELETE FROM display_product WHERE location_id = :locationId")
    suspend fun deleteAllByLocation(locationId: Long)
}
