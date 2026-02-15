package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.FacingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FacingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(facing: FacingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facings: List<FacingEntity>): List<Long>
    
    @Update
    suspend fun update(facing: FacingEntity)
    
    @Delete
    suspend fun delete(facing: FacingEntity)
    
    @Query("SELECT * FROM facing WHERE id = :id")
    suspend fun getById(id: Long): FacingEntity?
    
    @Query("SELECT * FROM facing WHERE shelf_id = :shelfId ORDER BY position_index ASC")
    fun getFacingsByShelf(shelfId: Long): Flow<List<FacingEntity>>
    
    @Query("SELECT * FROM facing WHERE product_id = :productId")
    fun getFacingsByProduct(productId: Long): Flow<List<FacingEntity>>
    
    @Query("DELETE FROM facing WHERE shelf_id = :shelfId")
    suspend fun deleteByShelfId(shelfId: Long)
}
