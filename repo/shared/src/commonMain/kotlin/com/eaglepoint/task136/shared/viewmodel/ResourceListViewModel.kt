package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResourceListState(
    val isLoading: Boolean = false,
    val resources: List<ResourceEntity> = emptyList(),
    val error: String? = null,
)

class ResourceListViewModel(
    private val resourceDao: ResourceDao,
    private val validationService: ValidationService,
    private val isDebug: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ResourceListState())
    val state: StateFlow<ResourceListState> = _state.asStateFlow()

    fun loadPage(limit: Int = 5000, offset: Int = 0) {
        scope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                if (isDebug && resourceDao.countAll() == 0) {
                    val allergenOptions = listOf("none", "gluten", "dairy", "nuts", "soy", "eggs", "shellfish")
                    val seed = (1..5000).map { index ->
                        ResourceEntity(
                            id = "res-$index",
                            name = "Resource $index",
                            category = if (index % 2 == 0) "Logistics" else "Operations",
                            availableUnits = index % 12,
                            unitPrice = (index % 200) + 0.99,
                            allergens = allergenOptions[index % allergenOptions.size],
                        )
                    }
                    val validated = seed.filter { validationService.validateAllergenFlags(it.allergens) }
                    resourceDao.upsertAll(validated)
                }

                val rows = resourceDao.page(limit = limit, offset = offset)
                _state.value = ResourceListState(isLoading = false, resources = rows)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load resources: ${e.message}")
            }
        }
    }

    fun addResource(role: Role, name: String, category: String, availableUnits: Int, unitPrice: Double): Boolean {
        if (role != Role.Admin) {
            _state.value = _state.value.copy(error = "Admin role required to add resources")
            return false
        }
        scope.launch(ioDispatcher) {
            val id = "res-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
            val resource = ResourceEntity(
                id = id,
                name = name,
                category = category,
                availableUnits = availableUnits,
                unitPrice = unitPrice,
            )
            resourceDao.upsert(resource)
            val rows = resourceDao.page(limit = 5000, offset = 0)
            _state.value = _state.value.copy(resources = rows)
        }
        return true
    }

    @Deprecated("Use addResource(role, ...) with admin guard")
    fun addResource(name: String, category: String, availableUnits: Int, unitPrice: Double) {
        addResource(Role.Admin, name, category, availableUnits, unitPrice)
    }

    fun deleteResource(role: Role, resourceId: String): Boolean {
        if (role != Role.Admin) {
            _state.value = _state.value.copy(error = "Admin role required to delete resources")
            return false
        }
        scope.launch(ioDispatcher) {
            resourceDao.deleteById(resourceId)
            val rows = resourceDao.page(limit = 5000, offset = 0)
            _state.value = _state.value.copy(resources = rows)
        }
        return true
    }

    @Deprecated("Use deleteResource(role, ...) with admin guard")
    fun deleteResource(resourceId: String) {
        deleteResource(Role.Admin, resourceId)
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = ResourceListState()
    }
}
