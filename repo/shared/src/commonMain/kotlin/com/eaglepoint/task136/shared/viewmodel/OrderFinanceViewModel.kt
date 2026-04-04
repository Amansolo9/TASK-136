package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.platform.getDeviceFingerprint
import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.InvoiceDao
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.AccessContext
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptLineItem
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import com.eaglepoint.task136.shared.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "OrderFinanceViewModel"

data class CartItem(
    val id: String,
    val label: String,
    val quantity: Int,
    val unitPrice: Double,
)

data class InvoiceDraft(
    val id: String,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val orderId: String? = null,
    val ownerId: String = "",
    val actorId: String = "",
)

data class OrderFinanceState(
    val cart: List<CartItem> = emptyList(),
    val invoices: List<InvoiceDraft> = emptyList(),
    val refunds: List<String> = emptyList(),
    val note: String? = null,
)

class OrderFinanceViewModel(
    private val abac: AbacPolicyEvaluator,
    private val permissionEvaluator: PermissionEvaluator,
    private val validationService: ValidationService,
    private val deviceBindingService: DeviceBindingService,
    private val cartDao: CartDao,
    private val invoiceDao: InvoiceDao? = null,
    private val stateMachine: OrderStateMachine? = null,
    private val notificationGateway: NotificationGateway,
    private val receiptGateway: ReceiptGateway,
    private val deviceFingerprint: String = getDeviceFingerprint(),
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(OrderFinanceState())
    val state: StateFlow<OrderFinanceState> = _state.asStateFlow()

    private suspend fun resolveDeviceTrust(userId: String): Boolean {
        return deviceBindingService.isDeviceTrusted(userId, deviceFingerprint)
    }

    fun addCartItem(
        role: Role,
        actorId: String,
        delegateForUserId: String? = null,
        resourceId: String,
        label: String,
        quantity: Int,
        unitPrice: Double,
    ) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Add item denied for role")
            return
        }
        val priceError = validationService.validatePrice(unitPrice)
        if (priceError != null) {
            _state.value = _state.value.copy(note = priceError)
            return
        }
        if (quantity <= 0) {
            _state.value = _state.value.copy(note = "Quantity must be at least 1")
            return
        }
        val ownerId = delegateForUserId ?: actorId
        val itemId = "item-${_state.value.cart.size + 1}"
        val next = CartItem(
            id = itemId,
            label = label,
            quantity = quantity,
            unitPrice = unitPrice,
        )
        _state.value = _state.value.copy(cart = _state.value.cart + next, note = null)

        scope.launch(Dispatchers.IO) {
            cartDao.upsert(
                CartItemEntity(
                    id = itemId,
                    userId = ownerId,
                    actorId = actorId,
                    resourceId = resourceId,
                    label = next.label,
                    quantity = next.quantity,
                    unitPrice = next.unitPrice,
                ),
            )
        }
    }

    internal fun addDemoItem(role: Role, actorId: String, delegateForUserId: String? = null) {
        addCartItem(
            role = role,
            actorId = actorId,
            delegateForUserId = delegateForUserId,
            resourceId = "res-1",
            label = "Service Package ${_state.value.cart.size + 1}",
            quantity = 1,
            unitPrice = 49.99,
        )
    }

    fun splitFirstItem(role: Role, actorId: String, delegateForUserId: String? = null) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Split denied for role")
            return
        }
        val first = _state.value.cart.firstOrNull() ?: return
        if (first.quantity < 2) {
            _state.value = _state.value.copy(note = "Split requires quantity >= 2")
            return
        }
        val ownerId = delegateForUserId ?: actorId
        val left = first.copy(id = "${first.id}-a", quantity = first.quantity / 2)
        val right = first.copy(id = "${first.id}-b", quantity = first.quantity - left.quantity)
        _state.value = _state.value.copy(cart = listOf(left, right) + _state.value.cart.drop(1), note = "Order split")
        scope.launch(Dispatchers.IO) {
            cartDao.deleteByIdForUser(first.id, ownerId)
            cartDao.upsert(CartItemEntity(id = left.id, userId = ownerId, actorId = actorId, resourceId = "res-1", label = left.label, quantity = left.quantity, unitPrice = left.unitPrice))
            cartDao.upsert(CartItemEntity(id = right.id, userId = ownerId, actorId = actorId, resourceId = "res-1", label = right.label, quantity = right.quantity, unitPrice = right.unitPrice))
        }
    }

    fun mergeFirstTwoItems(role: Role, actorId: String, delegateForUserId: String? = null) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Merge denied for role")
            return
        }
        val ownerId = delegateForUserId ?: actorId
        val cart = _state.value.cart
        if (cart.size < 2) return
        val totalQuantity = cart[0].quantity + cart[1].quantity
        val totalValue = (cart[0].unitPrice * cart[0].quantity) + (cart[1].unitPrice * cart[1].quantity)
        val merged = cart[0].copy(
            id = "${cart[0].id}+${cart[1].id}",
            quantity = totalQuantity,
            unitPrice = kotlin.math.round(totalValue / totalQuantity * 100) / 100.0,
        )
        _state.value = _state.value.copy(cart = listOf(merged) + cart.drop(2), note = "Orders merged")
        scope.launch(Dispatchers.IO) {
            cartDao.deleteByIdForUser(cart[0].id, ownerId)
            cartDao.deleteByIdForUser(cart[1].id, ownerId)
            cartDao.upsert(CartItemEntity(id = merged.id, userId = ownerId, actorId = actorId, resourceId = "res-1", label = merged.label, quantity = merged.quantity, unitPrice = merged.unitPrice))
        }
    }

    fun generateInvoice(role: Role, actorId: String, delegateForUserId: String? = null) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Invoice denied for role")
            return
        }
        if (_state.value.cart.isEmpty()) return
        val subtotal = _state.value.cart.sumOf { it.unitPrice * it.quantity }
        val priceError = validationService.validatePrice(subtotal)
        if (priceError != null) {
            _state.value = _state.value.copy(note = priceError)
            return
        }

        val ownerId = delegateForUserId ?: actorId
        scope.launch(Dispatchers.IO) {
            val trusted = resolveDeviceTrust(actorId)
            val isDelegate = delegateForUserId != null
            val context = AccessContext(requesterId = actorId, ownerId = ownerId, isDelegate = isDelegate, deviceTrusted = trusted)
            val tax = subtotal * 0.12
            val showTax = abac.canReadInvoiceTaxField(role, context)
            val invoice = InvoiceDraft(
                id = "inv-${_state.value.invoices.size + 1}",
                subtotal = subtotal,
                tax = if (showTax) tax else 0.0,
                total = subtotal + tax,
                orderId = _state.value.cart.firstOrNull()?.id,
                ownerId = ownerId,
                actorId = actorId,
            )
            val receiptItems = _state.value.cart.map {
                ReceiptLineItem(label = it.label, amount = it.quantity * it.unitPrice)
            }
            _state.value = _state.value.copy(
                cart = emptyList(),
                invoices = _state.value.invoices + invoice,
                note = "Invoice generated",
            )
            cartDao.clearForUser(ownerId)
            invoiceDao?.upsert(
                InvoiceEntity(
                    id = invoice.id,
                    subtotal = invoice.subtotal,
                    tax = invoice.tax,
                    total = invoice.total,
                    orderId = invoice.orderId,
                    ownerId = invoice.ownerId,
                    actorId = invoice.actorId,
                    createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                ),
            )

            try {
                notificationGateway.scheduleInvoiceReady(invoice.id, invoice.total)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Notification scheduling failed: ${e.message}")
            }

            try {
                receiptGateway.shareReceipt(invoice.id, ownerId, receiptItems, invoice.total)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Receipt generation failed: ${e.message}")
            }
        }
    }

    fun refundLatest(role: Role, actorId: String) {
        val latest = _state.value.invoices.lastOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val trusted = resolveDeviceTrust(actorId)
            val context = AccessContext(requesterId = actorId, ownerId = actorId, isDelegate = false, deviceTrusted = trusted)
            if (!abac.canIssueRefund(role, context)) {
                _state.value = _state.value.copy(note = "Refund denied for role")
                return@launch
            }
            val orderId = latest.orderId
            if (orderId == null || stateMachine == null) {
                _state.value = _state.value.copy(note = "Refund requires a linked order")
                return@launch
            }
            val requested = stateMachine.requestRefund(orderId, role)
            val completed = if (requested) stateMachine.completeRefund(orderId, role) else false
            if (!requested || !completed) {
                _state.value = _state.value.copy(note = "Refund state transition failed")
                return@launch
            }
            _state.value = _state.value.copy(refunds = _state.value.refunds + "Refunded ${latest.id}", note = "Refund completed")
        }
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = OrderFinanceState()
    }
}
