package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long
    
    @Update
    suspend fun update(product: ProductEntity)
    
    @Delete
    suspend fun delete(product: ProductEntity)
    
    @Query("SELECT * FROM product_master WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?
    
    @Query("SELECT * FROM product_master WHERE jan = :jan")
    suspend fun getByJan(jan: String): ProductEntity?
    
    @Query("SELECT * FROM product_master WHERE name LIKE '%' || :keyword || '%'")
    fun searchByName(keyword: String): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM product_master ORDER BY created_at DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>
    
    @Query("DELETE FROM product_master WHERE id NOT IN (SELECT DISTINCT product_id FROM facing)")
    suspend fun deleteUnusedProducts(): Int
}
