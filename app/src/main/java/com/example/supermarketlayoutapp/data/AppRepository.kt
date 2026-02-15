package com.example.supermarketlayoutapp.data

import com.example.supermarketlayoutapp.data.dao.*
import com.example.supermarketlayoutapp.data.entity.*
import kotlinx.coroutines.flow.Flow

class AppRepository(private val database: AppDatabase) {
    
    // Product操作
    private val productDao = database.productDao()
    
    suspend fun insertProduct(product: ProductEntity): Long = productDao.insert(product)
    suspend fun updateProduct(product: ProductEntity) = productDao.update(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.delete(product)
    suspend fun getProductById(id: Long): ProductEntity? = productDao.getById(id)
    suspend fun getProductByJan(jan: String): ProductEntity? = productDao.getByJan(jan)
    fun searchProductsByName(keyword: String): Flow<List<ProductEntity>> = productDao.searchByName(keyword)
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()
    suspend fun deleteUnusedProducts(): Int = productDao.deleteUnusedProducts()
    
    // Fixture操作
    private val fixtureDao = database.fixtureDao()
    
    suspend fun insertFixture(fixture: FixtureEntity): Long = fixtureDao.insert(fixture)
    suspend fun updateFixture(fixture: FixtureEntity) = fixtureDao.update(fixture)
    suspend fun deleteFixture(fixture: FixtureEntity) = fixtureDao.delete(fixture)
    suspend fun getFixtureById(id: Long): FixtureEntity? = fixtureDao.getById(id)
    fun getFixturesByType(type: String): Flow<List<FixtureEntity>> = fixtureDao.getByType(type)
    fun getAllFixtures(): Flow<List<FixtureEntity>> = fixtureDao.getAllFixtures()
    
    // Shelf操作
    private val shelfDao = database.shelfDao()
    
    suspend fun insertShelf(shelf: ShelfEntity): Long = shelfDao.insert(shelf)
    suspend fun insertShelves(shelves: List<ShelfEntity>): List<Long> = shelfDao.insertAll(shelves)
    suspend fun updateShelf(shelf: ShelfEntity) = shelfDao.update(shelf)
    suspend fun deleteShelf(shelf: ShelfEntity) = shelfDao.delete(shelf)
    suspend fun getShelfById(id: Long): ShelfEntity? = shelfDao.getById(id)
    fun getShelvesByFixture(fixtureId: Long): Flow<List<ShelfEntity>> = shelfDao.getShelvesByFixture(fixtureId)
    suspend fun deleteShelvesByFixtureId(fixtureId: Long) = shelfDao.deleteByFixtureId(fixtureId)
    
    // Facing操作
    private val facingDao = database.facingDao()
    
    suspend fun insertFacing(facing: FacingEntity): Long = facingDao.insert(facing)
    suspend fun insertFacings(facings: List<FacingEntity>): List<Long> = facingDao.insertAll(facings)
    suspend fun updateFacing(facing: FacingEntity) = facingDao.update(facing)
    suspend fun deleteFacing(facing: FacingEntity) = facingDao.delete(facing)
    suspend fun getFacingById(id: Long): FacingEntity? = facingDao.getById(id)
    fun getFacingsByShelf(shelfId: Long): Flow<List<FacingEntity>> = facingDao.getFacingsByShelf(shelfId)
    fun getFacingsByProduct(productId: Long): Flow<List<FacingEntity>> = facingDao.getFacingsByProduct(productId)
    suspend fun deleteFacingsByShelfId(shelfId: Long) = facingDao.deleteByShelfId(shelfId)
}
