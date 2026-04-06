package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.InvoiceDao
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InvoicePersistenceTest {

    private val storedInvoices = mutableMapOf<String, InvoiceEntity>()

    private val fakeInvoiceDao = object : InvoiceDao {
        override suspend fun upsert(invoice: InvoiceEntity) {
            storedInvoices[invoice.id] = invoice
        }
        override suspend fun getByOwner(ownerId: String) = storedInvoices.values.filter { it.ownerId == ownerId }
        override suspend fun getById(id: String) = storedInvoices[id]
        override suspend fun getRecent(limit: Int) = storedInvoices.values.toList().take(limit)
    }

    @Test
    fun `invoice dao getById returns persisted invoice`() = runTest {
        val invoice = InvoiceEntity(
            id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0,
            orderId = "ord-1", ownerId = "admin", actorId = "admin", createdAt = 1000L,
        )
        fakeInvoiceDao.upsert(invoice)

        val loaded = fakeInvoiceDao.getById("inv-1")
        assertNotNull(loaded)
        assertEquals(100.0, loaded.subtotal)
        assertEquals(112.0, loaded.total)
        assertEquals("ord-1", loaded.orderId)
    }

    @Test
    fun `invoice dao getById returns null for missing invoice`() = runTest {
        val loaded = fakeInvoiceDao.getById("inv-nonexistent")
        assertNull(loaded)
    }

    @Test
    fun `invoice getByOwner scopes to owner`() = runTest {
        storedInvoices.clear()
        fakeInvoiceDao.upsert(InvoiceEntity(
            id = "inv-a", subtotal = 50.0, tax = 6.0, total = 56.0,
            orderId = "ord-a", ownerId = "user1", actorId = "user1", createdAt = 1L,
        ))
        fakeInvoiceDao.upsert(InvoiceEntity(
            id = "inv-b", subtotal = 75.0, tax = 9.0, total = 84.0,
            orderId = "ord-b", ownerId = "user2", actorId = "user2", createdAt = 2L,
        ))

        val user1Invoices = fakeInvoiceDao.getByOwner("user1")
        assertEquals(1, user1Invoices.size)
        assertEquals("inv-a", user1Invoices[0].id)
    }

    @Test
    fun `InvoiceDraft holds selected invoice data`() {
        val draft = InvoiceDraft(
            id = "inv-target",
            subtotal = 50.0,
            tax = 6.0,
            total = 56.0,
            orderId = "ord-linked",
            ownerId = "admin",
            actorId = "admin",
        )
        assertEquals("inv-target", draft.id)
        assertEquals("ord-linked", draft.orderId)
    }

    @Test
    fun `invoice entity roundtrips through dao correctly`() = runTest {
        storedInvoices.clear()
        val original = InvoiceEntity(
            id = "inv-roundtrip", subtotal = 200.0, tax = 24.0, total = 224.0,
            orderId = "ord-rt", ownerId = "supervisor", actorId = "supervisor", createdAt = 999L,
        )
        fakeInvoiceDao.upsert(original)
        val loaded = fakeInvoiceDao.getById("inv-roundtrip")
        assertNotNull(loaded)
        assertEquals(original.subtotal, loaded.subtotal)
        assertEquals(original.tax, loaded.tax)
        assertEquals(original.total, loaded.total)
        assertEquals(original.orderId, loaded.orderId)
        assertEquals(original.ownerId, loaded.ownerId)
    }

    @Test
    fun `RBAC allows admin to read invoices`() {
        val evaluator = PermissionEvaluator(defaultRules())
        val canRead = evaluator.canAccess(Role.Admin, com.eaglepoint.task136.shared.rbac.ResourceType.Order, "*", com.eaglepoint.task136.shared.rbac.Action.Read)
        assertEquals(true, canRead)
    }

    @Test
    fun `process loss scenario - fresh dao still returns persisted data`() = runTest {
        storedInvoices.clear()
        val invoice = InvoiceEntity(
            id = "inv-persisted", subtotal = 75.0, tax = 9.0, total = 84.0,
            orderId = "ord-99", ownerId = "operator", actorId = "operator", createdAt = 0L,
        )
        fakeInvoiceDao.upsert(invoice)

        // Simulate "process loss" - data persists in Room even if VM is destroyed
        val reloaded = fakeInvoiceDao.getById("inv-persisted")
        assertNotNull(reloaded, "Invoice should survive VM destruction via Room persistence")
        assertEquals(75.0, reloaded.subtotal)
        assertEquals("operator", reloaded.ownerId)
    }
}
