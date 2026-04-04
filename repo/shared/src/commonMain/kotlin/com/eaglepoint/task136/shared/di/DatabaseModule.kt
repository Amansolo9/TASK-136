package com.eaglepoint.task136.shared.di

import com.eaglepoint.task136.shared.db.AppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

fun databaseModule(database: AppDatabase): Module = module {
    single { database }
    single { database.userDao() }
    single { database.resourceDao() }
    single { database.cartDao() }
    single { database.orderDao() }
    single { database.orderLineItemDao() }
    single { database.deviceBindingDao() }
    single { database.governanceDao() }
    single { database.meetingDao() }
    single { database.walletDao() }
    single { database.invoiceDao() }
    single { database.learningDao() }
}
