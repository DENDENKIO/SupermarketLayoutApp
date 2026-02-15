package com.example.supermarketlayoutapp.data.dao

import androidx.room.*
import com.example.supermarketlayoutapp.data.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity): Long
    
    @Update
    suspend fun update(location: LocationEntity)
    
    @Delete
    suspend fun delete(location: LocationEntity)
    
    @Query("SELECT * FROM location ORDER BY created_at DESC")
    fun getAllLocations(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM location WHERE id = :locationId")
    suspend fun getLocationById(locationId: Long): LocationEntity?
}
