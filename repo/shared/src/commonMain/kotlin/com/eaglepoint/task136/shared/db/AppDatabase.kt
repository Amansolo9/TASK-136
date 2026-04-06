package com.eaglepoint.task136.shared.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ResourceEntity::class,
        CartItemEntity::class,
        OrderEntity::class,
        OrderLineItemEntity::class,
        DeviceBindingEntity::class,
        RuleHitMetricEntity::class,
        DailyLedgerEntity::class,
        DiscrepancyTicketEntity::class,
        MeetingEntity::class,
        MeetingAttendeeEntity::class,
        WalletEntity::class,
        CourseEntity::class,
        EnrollmentEntity::class,
        InvoiceEntity::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun resourceDao(): ResourceDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun orderLineItemDao(): OrderLineItemDao
    abstract fun deviceBindingDao(): DeviceBindingDao
    abstract fun governanceDao(): GovernanceDao
    abstract fun meetingDao(): MeetingDao
    abstract fun walletDao(): WalletDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun learningDao(): LearningDao
}
