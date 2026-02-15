package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.ShelfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shelf: ShelfEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shelves: List<ShelfEntity>): List<Long>
    
    @Update
    suspend fun update(shelf: ShelfEntity)
    
    @Delete
    suspend fun delete(shelf: ShelfEntity)
    
    @Query("SELECT * FROM shelf WHERE id = :id")
    suspend fun getById(id: Long): ShelfEntity?
    
    @Query("SELECT * FROM shelf WHERE fixture_id = :fixtureId ORDER BY level ASC")
    fun getShelvesByFixture(fixtureId: Long): Flow<List<ShelfEntity>>
    
    @Query("DELETE FROM shelf WHERE fixture_id = :fixtureId")
    suspend fun deleteByFixtureId(fixtureId: Long)
}
