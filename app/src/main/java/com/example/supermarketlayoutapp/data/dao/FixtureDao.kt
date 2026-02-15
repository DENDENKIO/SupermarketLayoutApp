package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.FixtureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FixtureDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fixture: FixtureEntity): Long
    
    @Update
    suspend fun update(fixture: FixtureEntity)
    
    @Delete
    suspend fun delete(fixture: FixtureEntity)
    
    @Query("SELECT * FROM fixture WHERE id = :id")
    suspend fun getById(id: Long): FixtureEntity?
    
    @Query("SELECT * FROM fixture WHERE type = :type")
    fun getByType(type: String): Flow<List<FixtureEntity>>
    
    @Query("SELECT * FROM fixture ORDER BY created_at DESC")
    fun getAllFixtures(): Flow<List<FixtureEntity>>
}
