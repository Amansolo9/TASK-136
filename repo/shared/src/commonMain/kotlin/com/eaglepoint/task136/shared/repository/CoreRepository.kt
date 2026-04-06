package com.eaglepoint.task136.shared.repository

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoreRepository(
    private val userDao: UserDao,
    private val resourceDao: ResourceDao,
    private val orderDao: OrderDao,
    private val permissionEvaluator: PermissionEvaluator,
) {
    suspend fun getUser(role: Role, id: String): UserEntity? = withContext(Dispatchers.IO) {
        permissionEvaluator.guardOrNull(role, ResourceType.User, "maskedPII", Action.Read) {
            userDao.getById(id)
        }
    }

    suspend fun getResource(role: Role, id: String): ResourceEntity? = withContext(Dispatchers.IO) {
        permissionEvaluator.guardOrNull(role, ResourceType.Resource, "*", Action.Read) {
            resourceDao.getById(id)
        }
    }

    suspend fun getOrder(role: Role, id: String, actorId: String, delegateForUserId: String? = null): OrderEntity? = withContext(Dispatchers.IO) {
        permissionEvaluator.guardOrNull(role, ResourceType.Order, "*", Action.Read) {
            when (role) {
                Role.Admin, Role.Supervisor -> orderDao.getById(id)
                else -> if (delegateForUserId.isNullOrBlank()) {
                    orderDao.getByIdForActor(id, actorId)
                } else {
                    orderDao.getByIdForOwnerOrDelegate(id, actorId, delegateForUserId)
                }
            }
        }
    }
}
