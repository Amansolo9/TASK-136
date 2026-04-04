package com.eaglepoint.task136.shared.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["role", "isActive"])],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val role: String,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val delegateForUserId: String? = null,
    val maskedPII: String,
    val encryptedWalletRef: String,
    val isActive: Boolean,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
)

@Entity(
    tableName = "resources",
    indices = [Index(value = ["category", "availableUnits"])],
)
data class ResourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val availableUnits: Int,
    val unitPrice: Double,
    val allergens: String = "",
)

@Entity(
    tableName = "cart_items",
    indices = [Index(value = ["userId"])],
)
data class CartItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    @androidx.room.ColumnInfo(defaultValue = "")
    val actorId: String,
    val resourceId: String,
    val label: String,
    val quantity: Int,
    val unitPrice: Double,
)

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["resourceId", "startTime"]),
        Index(value = ["state", "expiresAt"]),
        Index(value = ["userId", "state"]),
        Index(value = ["state", "createdAt"]),
    ],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val resourceId: String,
    val state: String,
    val startTime: Long,
    val endTime: Long,
    val expiresAt: Long?,
    val quantity: Int,
    val totalPrice: Double,
    val createdAt: Long = 0L,
    val paymentMethod: String = "Cash",
    val deliveryState: String = "None",
    val deliverySignature: String? = null,
    val notes: String? = null,
    val tags: String = "",
)

@Entity(
    tableName = "order_line_items",
    indices = [Index(value = ["orderId"])],
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class OrderLineItemEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val resourceId: String,
    val label: String,
    val quantity: Int,
    val unitPrice: Double,
)

@Entity(
    tableName = "device_bindings",
    indices = [Index(value = ["userId"])],
)
data class DeviceBindingEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceFingerprint: String,
    val boundAt: Long,
)

@Entity(
    tableName = "rule_hits",
    indices = [Index(value = ["createdAt", "resolved"])],
)
data class RuleHitMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String,
    val valueObserved: Double,
    val createdAt: Long,
    val resolved: Boolean,
)

@Entity(
    tableName = "daily_ledger",
    indices = [Index(value = ["businessDate"], unique = true)],
)
data class DailyLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDate: String,
    val grossTotal: Double,
    val netTotal: Double,
    val closedAt: Long?,
    val isClosed: Boolean,
)

@Entity(tableName = "discrepancy_tickets")
data class DiscrepancyTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerDate: String,
    val reason: String,
    val amountDelta: Double,
    val createdAt: Long,
)

@Entity(
    tableName = "meetings",
    indices = [
        Index(value = ["organizerId", "startTime"]),
        Index(value = ["resourceId", "startTime"]),
    ],
)
data class MeetingEntity(
    @PrimaryKey val id: String,
    val organizerId: String,
    val resourceId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val status: String,
    val agenda: String = "",
    val attachmentPath: String? = null,
    val note: String? = null,
)

@Entity(
    tableName = "meeting_attendees",
    indices = [Index(value = ["meetingId"])],
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class MeetingAttendeeEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val userId: String,
    val displayName: String,
    val rsvpStatus: String = "Pending",
)

@Entity(
    tableName = "wallets",
    indices = [Index(value = ["userId"], unique = true)],
)
data class WalletEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val balance: Double,
    val updatedAt: Long,
)

@Entity(
    tableName = "courses",
    indices = [Index(value = ["category"])],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val durationMinutes: Int,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "enrollments",
    indices = [Index(value = ["userId", "courseId"])],
)
data class EnrollmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val courseId: String,
    val status: String = "NotStarted",
    val progressPercent: Int = 0,
    val enrolledAt: Long,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["ownerId", "createdAt"])],
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val orderId: String? = null,
    val ownerId: String,
    val actorId: String,
    val createdAt: Long,
)
