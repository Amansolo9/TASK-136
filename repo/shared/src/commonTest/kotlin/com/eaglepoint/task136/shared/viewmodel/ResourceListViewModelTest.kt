package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceListViewModelTest {

    private val testClock = object : kotlinx.datetime.Clock {
        override fun now() = kotlinx.datetime.Instant.parse("2026-03-30T10:00:00Z")
    }

    private fun createFakeDao(): FakeResourceDao = FakeResourceDao()

    @Test
    fun `initial state is not loading with empty resources`() {
        val state = ResourceListState()
        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.resources)
        assertNull(state.error)
    }

    @Test
    fun `clearSessionState resets to default`() {
        val dao = createFakeDao()
        val vm = ResourceListViewModel(dao, ValidationService(testClock))
        vm.clearSessionState()
        assertEquals(ResourceListState(), vm.state.value)
    }

    @Test
    fun `loadPage populates resources with Unconfined dispatcher`() {
        val dao = createFakeDao()
        (1..10).forEach { i ->
            dao.putDirect(ResourceEntity("res-$i", "Resource $i", "Ops", i, i * 1.0, "none"))
        }

        val vm = ResourceListViewModel(dao, ValidationService(testClock), ioDispatcher = Dispatchers.Unconfined)
        vm.loadPage(limit = 10)

        assertEquals(10, vm.state.value.resources.size)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `validation service rejects blank allergens`() {
        val vs = ValidationService(testClock)
        assertFalse(vs.validateAllergenFlags(""))
        assertTrue(vs.validateAllergenFlags("gluten"))
    }

    @Test
    fun `error state has null error by default`() {
        val state = ResourceListState()
        assertNull(state.error)
    }
}

private class FakeResourceDao : ResourceDao {
    private val map = mutableMapOf<String, ResourceEntity>()

    fun putDirect(res: ResourceEntity) { map[res.id] = res }

    override suspend fun upsert(resource: ResourceEntity) { map[resource.id] = resource }
    override suspend fun upsertAll(resources: List<ResourceEntity>) { resources.forEach { map[it.id] = it } }
    override suspend fun update(resource: ResourceEntity) { map[resource.id] = resource }
    override suspend fun getById(id: String) = map[id]
    override suspend fun page(limit: Int, offset: Int) = map.values.drop(offset).take(limit)
    override suspend fun countAll() = map.size
    override suspend fun deleteById(id: String) { map.remove(id) }
}
