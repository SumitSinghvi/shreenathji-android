package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY category, name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM products WHERE id NOT IN (:ids)")
    suspend fun deleteProductsNotIn(ids: List<String>)

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteProductsByIds(ids: List<String>)

    @Transaction
    suspend fun syncReplaceProducts(products: List<ProductEntity>) {
        // Upsert all remote products (INSERT OR REPLACE on primary key)
        if (products.isNotEmpty()) {
            insertProducts(products)
        }
        // Remove products deleted server-side
        if (products.isNotEmpty()) {
            val remoteIds = products.map { it.id }
            deleteProductsNotIn(remoteIds)
        }
    }

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getCount(): Int
}
