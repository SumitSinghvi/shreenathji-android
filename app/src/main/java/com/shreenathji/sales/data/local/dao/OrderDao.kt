package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY orderTimestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE salesRepId = :repId ORDER BY orderTimestamp DESC")
    fun getOrdersByRep(repId: String): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderWithItemsById(orderId: String): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE isSynced = 0 ORDER BY orderTimestamp ASC")
    suspend fun getUnsyncedOrders(): List<OrderWithItems>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Transaction
    suspend fun insertFullOrder(order: OrderEntity, items: List<OrderItemEntity>) {
        insertOrder(order)
        insertOrderItems(items)
    }

    @Query("DELETE FROM orders WHERE isSynced = 1")
    suspend fun clearSyncedOrders()

    @Query("DELETE FROM order_items WHERE orderId IN (SELECT id FROM orders WHERE isSynced = 1)")
    suspend fun clearSyncedOrderItems()

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    @Transaction
    suspend fun wipeAllOrders() {
        clearAllOrderItems()
        clearAllOrders()
    }

    @Transaction
    suspend fun syncReplaceOrders(orders: List<OrderEntity>, items: List<OrderItemEntity>) {
        clearSyncedOrderItems()
        clearSyncedOrders()
        if (orders.isNotEmpty()) {
            insertOrders(orders)
        }
        if (items.isNotEmpty()) {
            insertOrderItems(items)
        }
    }

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderSynced(orderId: String)

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderAsSynced(orderId: String)

    @Query("UPDATE orders SET orderNumber = :newOrderNumber WHERE id = :orderId")
    suspend fun updateOrderNumber(orderId: String, newOrderNumber: String)
}
