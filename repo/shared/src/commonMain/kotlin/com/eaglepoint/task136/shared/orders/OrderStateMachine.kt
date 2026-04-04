package com.eaglepoint.task136.shared.orders

import androidx.room.withTransaction
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.OrderLineItemEntity
import com.eaglepoint.task136.shared.logging.AppLogger
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

enum class OrderState {
    Draft,
    PendingTender,
    Confirmed,
    Cancelled,
    Expired,
    PartiallyFulfilled,
    ReturnRequested,
    Returned,
    ExchangeRequested,
    Exchanged,
    RefundRequested,
    Refunded,
    AwaitingDelivery,
    Delivered,
}

enum class DeliveryState {
    None,
    AwaitingDelivery,
    InTransit,
    Delivered,
    DeliveryConfirmed,
}

enum class PaymentMethod {
    Cash,
    InternalWallet,
    ExternalTender,
}

const val MIN_PRICE = 0.01
const val MAX_PRICE = 9_999.99

private const val TAG = "OrderStateMachine"

open class OrderStateMachine(
    private val database: AppDatabase,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return database.withTransaction { block() }
    }

    fun observeState(orderId: String): Flow<OrderState> {
        return database.orderDao().observeById(orderId)
            .filterNotNull()
            .map { OrderState.valueOf(it.state) }
    }

    fun validatePrice(price: Double): String? {
        if (price < MIN_PRICE) return "Price must be at least $$MIN_PRICE"
        if (price > MAX_PRICE) return "Price must not exceed $$MAX_PRICE"
        return null
    }

    suspend fun transitionToPendingTender(orderId: String, role: Role? = null): String? {
        if (role == Role.Viewer) return "Denied: Viewer cannot create orders"
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = clock.now().plus(30.minutes).toEpochMilliseconds()

        var error: String? = null
        runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction
            val priceError = validatePrice(order.totalPrice)
            if (priceError != null) {
                error = priceError
                return@runInTransaction
            }
            val pending = order.copy(state = OrderState.PendingTender.name, expiresAt = expiresAt)
            database.orderDao().update(pending)
            AppLogger.i(TAG, "Order $orderId transitioned to PendingTender")

            val resource = database.resourceDao().getById(order.resourceId)
            if (resource != null) {
                val newUnits = (resource.availableUnits - order.quantity).coerceAtLeast(0)
                database.resourceDao().update(resource.copy(availableUnits = newUnits))
                AppLogger.i(TAG, "Reserved ${order.quantity} units for $orderId, remaining: $newUnits")
            }
        }

        if (error != null) return error

        scope.launch {
            delay(30.minutes)
            runInTransaction {
                val latest = database.orderDao().getById(orderId) ?: return@runInTransaction
                if (latest.state == OrderState.PendingTender.name && (latest.expiresAt ?: now) <= clock.now().toEpochMilliseconds()) {
                    database.orderDao().update(latest.copy(state = OrderState.Expired.name))
                    restockInventory(latest)
                    AppLogger.i(TAG, "Order $orderId auto-expired after 30 minutes")
                }
            }
        }
        return null
    }

    suspend fun confirm(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val latest = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (latest.state != OrderState.PendingTender.name) return@runInTransaction false
            database.orderDao().update(latest.copy(state = OrderState.Confirmed.name, expiresAt = null))
            AppLogger.i(TAG, "Order $orderId confirmed")
            true
        }
    }

    suspend fun cancel(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state == OrderState.Cancelled.name ||
                order.state == OrderState.Expired.name ||
                order.state == OrderState.Refunded.name
            ) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.Cancelled.name))
            if (order.state == OrderState.PendingTender.name || order.state == OrderState.Confirmed.name) {
                restockInventory(order)
            }
            AppLogger.i(TAG, "Order $orderId cancelled, inventory restocked")
            true
        }
    }

    suspend fun requestReturn(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer || role == Role.Companion) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.Confirmed.name && order.state != OrderState.Delivered.name) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.ReturnRequested.name))
            AppLogger.i(TAG, "Return requested for $orderId")
            true
        }
    }

    suspend fun completeReturn(orderId: String, role: Role? = null): Boolean {
        if (role == null || (role != Role.Admin && role != Role.Supervisor)) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.ReturnRequested.name) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.Returned.name))
            restockInventory(order)
            AppLogger.i(TAG, "Return completed for $orderId, inventory restocked")
            true
        }
    }

    suspend fun requestExchange(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.Confirmed.name && order.state != OrderState.Delivered.name) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.ExchangeRequested.name))
            AppLogger.i(TAG, "Exchange requested for $orderId")
            true
        }
    }

    suspend fun completeExchange(orderId: String, role: Role? = null): Boolean {
        if (role == null || (role != Role.Admin && role != Role.Supervisor)) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.ExchangeRequested.name) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.Exchanged.name))
            restockInventory(order)
            AppLogger.i(TAG, "Exchange completed for $orderId, inventory restocked")
            true
        }
    }

    suspend fun requestRefund(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer || role == Role.Companion) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.Confirmed.name &&
                order.state != OrderState.Delivered.name &&
                order.state != OrderState.Returned.name
            ) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.RefundRequested.name))
            AppLogger.i(TAG, "Refund requested for $orderId")
            true
        }
    }

    suspend fun completeRefund(orderId: String, role: Role? = null): Boolean {
        if (role == null || (role != Role.Admin && role != Role.Supervisor)) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.RefundRequested.name) return@runInTransaction false
            database.orderDao().update(order.copy(state = OrderState.Refunded.name))
            restockInventory(order)
            AppLogger.i(TAG, "Refund completed for $orderId, inventory restocked")
            true
        }
    }

    suspend fun markAwaitingDelivery(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.Confirmed.name) return@runInTransaction false
            database.orderDao().update(
                order.copy(
                    state = OrderState.AwaitingDelivery.name,
                    deliveryState = DeliveryState.AwaitingDelivery.name,
                ),
            )
            AppLogger.i(TAG, "Order $orderId awaiting delivery")
            true
        }
    }

    suspend fun markInTransit(orderId: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.AwaitingDelivery.name) return@runInTransaction false
            database.orderDao().update(
                order.copy(deliveryState = DeliveryState.InTransit.name),
            )
            AppLogger.i(TAG, "Order $orderId marked in transit")
            true
        }
    }

    suspend fun confirmDelivery(orderId: String, signature: String, role: Role? = null): Boolean {
        if (role == Role.Viewer) return false
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction false
            if (order.state != OrderState.AwaitingDelivery.name) return@runInTransaction false
            database.orderDao().update(
                order.copy(
                    state = OrderState.Delivered.name,
                    deliveryState = DeliveryState.DeliveryConfirmed.name,
                    deliverySignature = signature,
                ),
            )
            AppLogger.i(TAG, "Delivery confirmed for $orderId with signature")
            true
        }
    }

    suspend fun splitOrder(orderId: String, splitQuantity: Int): Pair<String, String>? {
        return runInTransaction {
            val order = database.orderDao().getById(orderId) ?: return@runInTransaction null
            if (order.quantity < 2 || splitQuantity < 1 || splitQuantity >= order.quantity) return@runInTransaction null

            val leftQty = splitQuantity
            val rightQty = order.quantity - splitQuantity
            val leftPrice = kotlin.math.round(order.totalPrice * leftQty / order.quantity * 100) / 100.0
            val rightPrice = kotlin.math.round((order.totalPrice - leftPrice) * 100) / 100.0

            val leftId = "${orderId}-a"
            val rightId = "${orderId}-b"

            val leftOrder = order.copy(
                id = leftId,
                quantity = leftQty,
                totalPrice = leftPrice,
                state = OrderState.PartiallyFulfilled.name,
            )
            val rightOrder = order.copy(
                id = rightId,
                quantity = rightQty,
                totalPrice = rightPrice,
                state = OrderState.PartiallyFulfilled.name,
            )

            database.orderDao().upsert(leftOrder)
            database.orderDao().upsert(rightOrder)
            database.orderDao().deleteById(orderId)

            val lineItems = database.orderLineItemDao().getByOrderId(orderId)
            lineItems.forEach { item ->
                database.orderLineItemDao().upsert(item.copy(id = "${item.id}-a", orderId = leftId))
                database.orderLineItemDao().upsert(item.copy(id = "${item.id}-b", orderId = rightId))
            }

            AppLogger.i(TAG, "Order $orderId split into $leftId and $rightId")
            Pair(leftId, rightId)
        }
    }

    suspend fun mergeOrders(orderId1: String, orderId2: String): String? {
        return runInTransaction {
            val order1 = database.orderDao().getById(orderId1) ?: return@runInTransaction null
            val order2 = database.orderDao().getById(orderId2) ?: return@runInTransaction null
            if (order1.userId != order2.userId) return@runInTransaction null

            val mergedId = "${orderId1}+${orderId2}"
            val merged = order1.copy(
                id = mergedId,
                quantity = order1.quantity + order2.quantity,
                totalPrice = order1.totalPrice + order2.totalPrice,
            )
            database.orderDao().upsert(merged)
            database.orderDao().deleteById(orderId1)
            database.orderDao().deleteById(orderId2)

            val items1 = database.orderLineItemDao().getByOrderId(orderId1)
            val items2 = database.orderLineItemDao().getByOrderId(orderId2)
            (items1 + items2).forEach { item ->
                database.orderLineItemDao().upsert(item.copy(orderId = mergedId))
            }

            AppLogger.i(TAG, "Orders $orderId1 and $orderId2 merged into $mergedId")
            mergedId
        }
    }

    suspend fun expireStaleOrders() {
        val now = clock.now().toEpochMilliseconds()
        val stale = database.orderDao().getExpiredPendingOrders(now)
        stale.forEach { order ->
            runInTransaction {
                val latest = database.orderDao().getById(order.id) ?: return@runInTransaction
                if (latest.state == OrderState.PendingTender.name) {
                    database.orderDao().update(latest.copy(state = OrderState.Expired.name))
                    restockInventory(latest)
                    AppLogger.i(TAG, "Stale order ${order.id} expired on startup, inventory restocked")
                }
            }
        }
    }

    private suspend fun restockInventory(order: OrderEntity) {
        val resource = database.resourceDao().getById(order.resourceId) ?: return
        database.resourceDao().update(resource.copy(availableUnits = resource.availableUnits + order.quantity))
    }
}
