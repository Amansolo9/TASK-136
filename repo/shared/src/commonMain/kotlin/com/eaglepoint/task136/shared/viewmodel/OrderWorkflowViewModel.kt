package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.orders.OrderState
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.orders.PaymentMethod
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

data class OrderWorkflowState(
    val lastOrderId: String? = null,
    val lastOrderState: String? = null,
    val suggestedSlots: List<String> = emptyList(),
    val error: String? = null,
)

class OrderWorkflowViewModel(
    private val orderDao: OrderDao,
    private val resourceDao: ResourceDao,
    private val stateMachine: OrderStateMachine,
    private val bookingUseCase: BookingUseCase,
    private val permissionEvaluator: PermissionEvaluator,
    private val validationService: ValidationService,
    private val clock: Clock,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(OrderWorkflowState())
    val state: StateFlow<OrderWorkflowState> = _state.asStateFlow()

    /**
     * Resolves the effective user ID for order attribution.
     * Companions act on behalf of their delegating user.
     */
    private fun resolveOrderUserId(actorId: String, delegateForUserId: String?): String {
        return delegateForUserId ?: actorId
    }

    fun createPendingTender(
        role: Role,
        actorId: String,
        resourceId: String,
        quantity: Int,
        delegateForUserId: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.Cash,
    ) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val resource = resourceDao.getById(resourceId)
            if (resource == null) {
                _state.value = _state.value.copy(lastOrderState = "Resource not found")
                return@launch
            }
            if (quantity <= 0) {
                _state.value = _state.value.copy(lastOrderState = "Quantity must be at least 1")
                return@launch
            }
            val effectiveUserId = resolveOrderUserId(actorId, delegateForUserId)
            val orderId = "ord-${clock.now().toEpochMilliseconds()}-${(1000..9999).random()}"
            val now = clock.now().toEpochMilliseconds()
            val totalPrice = resource.unitPrice * quantity
            val priceError = validationService.validatePrice(totalPrice)
            if (priceError != null) {
                _state.value = _state.value.copy(lastOrderState = priceError)
                return@launch
            }
            val order = OrderEntity(
                id = orderId,
                userId = effectiveUserId,
                resourceId = resource.id,
                state = OrderState.Draft.name,
                startTime = now,
                endTime = clock.now().plus(30.minutes).toEpochMilliseconds(),
                expiresAt = null,
                quantity = quantity,
                totalPrice = totalPrice,
                createdAt = now,
                paymentMethod = paymentMethod.name,
                notes = if (effectiveUserId != actorId) "createdBy:$actorId" else null,
            )
            orderDao.upsert(order)
            val transitionError = stateMachine.transitionToPendingTender(orderId, role)
            if (transitionError != null) {
                _state.value = _state.value.copy(lastOrderState = transitionError)
                return@launch
            }
            _state.value = _state.value.copy(lastOrderId = orderId, lastOrderState = OrderState.PendingTender.name)
        }
    }

    internal fun createPendingTenderDemo(
        role: Role,
        actorId: String,
        delegateForUserId: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.Cash,
    ) {
        scope.launch(Dispatchers.IO) {
            val firstResource = resourceDao.page(limit = 1, offset = 0).firstOrNull()
            if (firstResource == null) {
                _state.value = _state.value.copy(lastOrderState = "No resources available")
                return@launch
            }
            createPendingTender(
                role = role,
                actorId = actorId,
                resourceId = firstResource.id,
                quantity = 1,
                delegateForUserId = delegateForUserId,
                paymentMethod = paymentMethod,
            )
        }
    }

    fun confirmLastOrder(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.confirm(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Confirmed.name else "Confirm not allowed",
            )
        }
    }

    fun cancelOrder(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.cancel(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Cancelled.name else "Cancel not allowed",
            )
        }
    }

    fun requestReturn(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestReturn(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.ReturnRequested.name else "Return not allowed",
            )
        }
    }

    fun completeReturn(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Approve)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.completeReturn(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Returned.name else "Complete return not allowed",
            )
        }
    }

    fun requestExchange(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestExchange(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.ExchangeRequested.name else "Exchange not allowed",
            )
        }
    }

    fun requestRefund(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write) || role == Role.Companion) {
            _state.value = _state.value.copy(lastOrderState = "Refund denied for role")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestRefund(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.RefundRequested.name else "Refund not allowed",
            )
        }
    }

    fun completeRefund(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Approve)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.completeRefund(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Refunded.name else "Complete refund not allowed",
            )
        }
    }

    fun markAwaitingDelivery(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.markAwaitingDelivery(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.AwaitingDelivery.name else "Cannot start delivery",
            )
        }
    }

    fun confirmDelivery(role: Role, signature: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.confirmDelivery(orderId, signature, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Delivered.name else "Cannot confirm delivery",
            )
        }
    }

    fun suggestSlots(role: Role, resourceId: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Read)) {
            _state.value = _state.value.copy(suggestedSlots = emptyList())
            return
        }
        scope.launch(Dispatchers.IO) {
            val slots = bookingUseCase.findThreeAvailableSlots(
                resourceId = resourceId,
                duration = 30.minutes,
            )
            val zone = TimeZone.currentSystemDefault()
            _state.value = _state.value.copy(
                suggestedSlots = slots.map {
                    val start = it.start.toLocalDateTime(zone)
                    "${start.date} ${start.time.hour.toString().padStart(2, '0')}:${start.time.minute.toString().padStart(2, '0')}"
                },
            )
        }
    }

    fun suggestSlots(role: Role) {
        scope.launch(Dispatchers.IO) {
            val firstResource = resourceDao.page(limit = 1, offset = 0).firstOrNull()
            if (firstResource == null) {
                _state.value = _state.value.copy(suggestedSlots = emptyList(), error = "No resources available")
                return@launch
            }
            suggestSlots(role, firstResource.id)
        }
    }

    fun loadOrderById(orderId: String) {
        scope.launch(Dispatchers.IO) {
            val order = orderDao.getById(orderId)
            if (order != null) {
                _state.value = _state.value.copy(
                    lastOrderId = order.id,
                    lastOrderState = order.state,
                )
            }
        }
    }

    fun loadOrderById(orderId: String, actorId: String, delegateForUserId: String? = null) {
        scope.launch(Dispatchers.IO) {
            val order = if (delegateForUserId.isNullOrBlank()) {
                orderDao.getByIdForActor(orderId, actorId)
            } else {
                orderDao.getByIdForOwnerOrDelegate(orderId, actorId, delegateForUserId)
            }
            if (order != null) {
                _state.value = _state.value.copy(
                    lastOrderId = order.id,
                    lastOrderState = order.state,
                    error = null,
                )
            } else {
                _state.value = _state.value.copy(error = "Order not found or access denied")
            }
        }
    }

    fun splitOrder(role: Role, splitQuantity: Int = 1) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val result = stateMachine.splitOrder(orderId, splitQuantity)
            if (result != null) {
                _state.value = _state.value.copy(
                    lastOrderId = result.first,
                    lastOrderState = "Split into ${result.first} and ${result.second}",
                )
            } else {
                _state.value = _state.value.copy(lastOrderState = "Split not allowed")
            }
        }
    }

    fun mergeOrders(role: Role, otherOrderId: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val mergedId = stateMachine.mergeOrders(orderId, otherOrderId)
            if (mergedId != null) {
                _state.value = _state.value.copy(
                    lastOrderId = mergedId,
                    lastOrderState = "Merged into $mergedId",
                )
            } else {
                _state.value = _state.value.copy(lastOrderState = "Merge not allowed")
            }
        }
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = OrderWorkflowState()
    }
}
