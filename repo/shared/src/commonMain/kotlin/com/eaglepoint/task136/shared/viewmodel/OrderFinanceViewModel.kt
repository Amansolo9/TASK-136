package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.platform.getDeviceFingerprint
import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.InvoiceDao
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.governance.GovernanceAnalytics
import com.eaglepoint.task136.shared.orders.OrderState
import com.eaglepoint.task136.shared.orders.PaymentMethod
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
import kotlin.random.Random

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
    val paymentMethod: String = PaymentMethod.Cash.name,
    val externalTenderDetails: String? = null,
)

data class OrderFinanceState(
    val cart: List<CartItem> = emptyList(),
    val invoices: List<InvoiceDraft> = emptyList(),
    val refunds: List<String> = emptyList(),
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.Cash,
    val externalTenderDetails: String = "",
    val note: String? = null,
)

class OrderFinanceViewModel(
    private val abac: AbacPolicyEvaluator,
    private val permissionEvaluator: PermissionEvaluator,
    private val validationService: ValidationService,
    private val deviceBindingService: DeviceBindingService,
    private val cartDao: CartDao,
    private val invoiceDao: InvoiceDao? = null,
    private val orderDao: OrderDao? = null,
    private val stateMachine: OrderStateMachine? = null,
    private val notificationGateway: NotificationGateway,
    private val receiptGateway: ReceiptGateway,
    private val governanceAnalytics: GovernanceAnalytics? = null,
    private val deviceFingerprint: String = getDeviceFingerprint(),
    private val clock: kotlinx.datetime.Clock = kotlinx.datetime.Clock.System,
    private val transitionToPendingTender: suspend (orderId: String, role: Role) -> String? =
        { orderId, role -> stateMachine?.transitionToPendingTender(orderId, role) ?: "Order state machine unavailable" },
    private val confirmOrderTransition: suspend (orderId: String, role: Role) -> Boolean =
        { orderId, role -> stateMachine?.confirm(orderId, role) ?: false },
    private val requestRefundTransition: suspend (orderId: String, role: Role) -> Boolean =
        { orderId, role -> stateMachine?.requestRefund(orderId, role) ?: false },
    private val completeRefundTransition: suspend (orderId: String, role: Role) -> Boolean =
        { orderId, role -> stateMachine?.completeRefund(orderId, role) ?: false },
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(OrderFinanceState())
    val state: StateFlow<OrderFinanceState> = _state.asStateFlow()

    private suspend fun resolveDeviceTrust(userId: String): Boolean {
        return deviceBindingService.isDeviceTrusted(userId, deviceFingerprint)
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _state.value = _state.value.copy(selectedPaymentMethod = method, note = null)
    }

    fun setExternalTenderDetails(details: String) {
        _state.value = _state.value.copy(externalTenderDetails = details.trim())
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
            scope.launch(Dispatchers.IO) { governanceAnalytics?.logPriceViolation(unitPrice) }
            return
        }
        if (quantity <= 0) {
            _state.value = _state.value.copy(note = "Quantity must be at least 1")
            return
        }
        val ownerId = delegateForUserId ?: actorId
        val itemId = newStableId("item")
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
            scope.launch(Dispatchers.IO) { governanceAnalytics?.logPriceViolation(subtotal) }
            return
        }

        val ownerId = delegateForUserId ?: actorId
        scope.launch(Dispatchers.IO) {
            val selectedPaymentMethod = _state.value.selectedPaymentMethod
            val externalTenderDetails = _state.value.externalTenderDetails.takeIf { it.isNotBlank() }
            if (selectedPaymentMethod == PaymentMethod.ExternalTender && externalTenderDetails == null) {
                _state.value = _state.value.copy(note = "External tender details are required")
                return@launch
            }

            // Create a real persisted order for this checkout
            val now = clock.now().toEpochMilliseconds()
            val orderId = newStableId("ord")
            val resourceId = _state.value.cart.firstOrNull()?.let { "res-checkout" } ?: "res-unknown"
            val totalQty = _state.value.cart.sumOf { it.quantity }
            if (orderDao != null) {
                orderDao.upsert(
                    OrderEntity(
                        id = orderId,
                        userId = ownerId,
                        resourceId = resourceId,
                        state = OrderState.Draft.name,
                        startTime = now,
                        endTime = now,
                        expiresAt = null,
                        quantity = totalQty,
                        totalPrice = subtotal,
                        createdAt = now,
                        paymentMethod = selectedPaymentMethod.name,
                        notes = buildString {
                            append("checkout-invoice")
                            if (selectedPaymentMethod == PaymentMethod.ExternalTender && externalTenderDetails != null) {
                                append(";externalTenderRecorded:")
                                append(externalTenderDetails)
                            }
                        },
                    ),
                )
            }

            val pendingError = transitionToPendingTender(orderId, role)
            if (pendingError != null) {
                _state.value = _state.value.copy(note = pendingError)
                return@launch
            }
            if (!confirmOrderTransition(orderId, role)) {
                _state.value = _state.value.copy(note = "Order confirmation failed")
                return@launch
            }

            val trusted = resolveDeviceTrust(actorId)
            val isDelegate = delegateForUserId != null
            val context = AccessContext(requesterId = actorId, ownerId = ownerId, isDelegate = isDelegate, deviceTrusted = trusted)
            val tax = subtotal * 0.12
            val showTax = abac.canReadInvoiceTaxField(role, context)
            val invoice = InvoiceDraft(
                id = newStableId("inv"),
                subtotal = subtotal,
                tax = if (showTax) tax else 0.0,
                total = subtotal + tax,
                orderId = orderId,
                ownerId = ownerId,
                actorId = actorId,
                paymentMethod = selectedPaymentMethod.name,
                externalTenderDetails = externalTenderDetails,
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
            // Always persist canonical tax value regardless of role visibility
            invoiceDao?.upsert(
                InvoiceEntity(
                    id = invoice.id,
                    subtotal = subtotal,
                    tax = tax,
                    total = subtotal + tax,
                    orderId = invoice.orderId,
                    ownerId = invoice.ownerId,
                    actorId = invoice.actorId,
                    paymentMethod = invoice.paymentMethod,
                    externalTenderDetails = invoice.externalTenderDetails,
                    createdAt = clock.now().toEpochMilliseconds(),
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

    fun loadInvoiceById(invoiceId: String, role: Role, actorId: String, delegateForUserId: String? = null) {
        scope.launch(Dispatchers.IO) {
            if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Read)) {
                _state.value = _state.value.copy(note = "Invoice access denied")
                return@launch
            }
            val entity = invoiceDao?.getById(invoiceId)
            if (entity == null) {
                _state.value = _state.value.copy(note = "Invoice not found")
                return@launch
            }
            val isPrivileged = role == Role.Admin || role == Role.Supervisor
            val isOwner = entity.ownerId == actorId
            val isActor = entity.actorId == actorId
            val isValidDelegate = delegateForUserId != null &&
                delegateForUserId == entity.ownerId &&
                entity.actorId == actorId
            if (!isPrivileged && !isOwner && !isActor && !isValidDelegate) {
                _state.value = _state.value.copy(note = "Invoice access denied")
                return@launch
            }
            val trusted = resolveDeviceTrust(actorId)
            val context = AccessContext(requesterId = actorId, ownerId = entity.ownerId, isDelegate = isValidDelegate, deviceTrusted = trusted)
            val showTax = abac.canReadInvoiceTaxField(role, context)
            val draft = InvoiceDraft(
                id = entity.id,
                subtotal = entity.subtotal,
                tax = if (showTax) entity.tax else 0.0,
                total = entity.total,
                orderId = entity.orderId,
                ownerId = entity.ownerId,
                actorId = entity.actorId,
                paymentMethod = entity.paymentMethod,
                externalTenderDetails = entity.externalTenderDetails,
            )
            val existing = _state.value.invoices.filter { it.id != draft.id }
            _state.value = _state.value.copy(invoices = existing + draft, note = null)
        }
    }

    fun refundInvoice(invoiceId: String, role: Role, actorId: String, delegateForUserId: String? = null) {
        scope.launch(Dispatchers.IO) {
            if (role == Role.Companion) {
                _state.value = _state.value.copy(note = "Refund denied for role")
                governanceAnalytics?.logRefundDenied(invoiceId, "role_denied:${role.name}")
                return@launch
            }

            // Load the specific invoice from persistence
            val entity = invoiceDao?.getById(invoiceId)
            if (entity == null) {
                _state.value = _state.value.copy(note = "Invoice not found in persistence")
                return@launch
            }
            val orderId = entity.orderId
            if (orderId == null) {
                _state.value = _state.value.copy(note = "Refund requires a linked order")
                return@launch
            }

            val order = orderDao?.getById(orderId)
            if (order == null || order.userId != entity.ownerId) {
                _state.value = _state.value.copy(note = "Refund denied: invalid invoice/order ownership context")
                governanceAnalytics?.logRefundDenied(invoiceId, "invalid_invoice_order_context")
                return@launch
            }

            val trusted = resolveDeviceTrust(actorId)
            val isPrivileged = role == Role.Admin || role == Role.Supervisor
            val isOwner = entity.ownerId == actorId
            val isValidDelegate = delegateForUserId != null &&
                delegateForUserId == entity.ownerId &&
                entity.actorId == actorId
            val context = AccessContext(
                requesterId = actorId,
                ownerId = entity.ownerId,
                isDelegate = isValidDelegate,
                deviceTrusted = trusted,
            )
            if (!abac.canIssueRefund(role, context)) {
                _state.value = _state.value.copy(note = "Refund denied for role")
                governanceAnalytics?.logRefundDenied(invoiceId, "role_denied:${role.name}")
                return@launch
            }
            if (!isPrivileged && !isOwner && !isValidDelegate) {
                _state.value = _state.value.copy(note = "Refund denied: invoice access scope mismatch")
                governanceAnalytics?.logRefundDenied(invoiceId, "scope_mismatch:${role.name}")
                return@launch
            }

            val requested = requestRefundTransition(orderId, role)
            val completed = if (requested) completeRefundTransition(orderId, role) else false
            if (!requested || !completed) {
                _state.value = _state.value.copy(note = "Refund state transition failed")
                return@launch
            }
            _state.value = _state.value.copy(refunds = _state.value.refunds + "Refunded $invoiceId", note = "Refund completed for $invoiceId")
        }
    }

    @Deprecated("Use refundInvoice(invoiceId, role, actorId) for targeted refund")
    fun refundLatest(role: Role, actorId: String) {
        val latest = _state.value.invoices.lastOrNull() ?: return
        refundInvoice(latest.id, role, actorId)
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = OrderFinanceState()
    }

    private fun newStableId(prefix: String): String {
        val ts = clock.now().toEpochMilliseconds()
        val hi = Random.nextLong().toULong().toString(16)
        val lo = Random.nextLong().toULong().toString(16)
        return "$prefix-$ts-$hi$lo"
    }
}
