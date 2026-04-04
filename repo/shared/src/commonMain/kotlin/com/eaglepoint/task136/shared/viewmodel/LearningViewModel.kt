package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.CourseEntity
import com.eaglepoint.task136.shared.db.EnrollmentEntity
import com.eaglepoint.task136.shared.db.LearningDao
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class LearningState(
    val courses: List<CourseEntity> = emptyList(),
    val enrollments: List<EnrollmentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val note: String? = null,
)

class LearningViewModel(
    private val learningDao: LearningDao,
    private val permissionEvaluator: PermissionEvaluator,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LearningState())
    val state: StateFlow<LearningState> = _state.asStateFlow()

    fun loadCourses() {
        scope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isLoading = true)
            val courses = learningDao.getAllCourses()
            if (courses.isEmpty()) {
                seedDemoCourses()
            }
            _state.value = _state.value.copy(
                courses = learningDao.getAllCourses(),
                isLoading = false,
            )
        }
    }

    fun loadEnrollments(userId: String) {
        scope.launch(ioDispatcher) {
            _state.value = _state.value.copy(
                enrollments = learningDao.getEnrollmentsByUser(userId),
            )
        }
    }

    fun enroll(role: Role, userId: String, courseId: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Learning, "*", Action.Read)) {
            _state.value = _state.value.copy(note = "Enrollment denied for role")
            return
        }
        scope.launch(ioDispatcher) {
            val existing = learningDao.getEnrollment(userId, courseId)
            if (existing != null) {
                _state.value = _state.value.copy(note = "Already enrolled")
                return@launch
            }
            learningDao.upsertEnrollment(
                EnrollmentEntity(
                    id = "enr-${clock.now().toEpochMilliseconds()}",
                    userId = userId,
                    courseId = courseId,
                    enrolledAt = clock.now().toEpochMilliseconds(),
                ),
            )
            _state.value = _state.value.copy(
                enrollments = learningDao.getEnrollmentsByUser(userId),
                note = "Enrolled successfully",
            )
        }
    }

    fun updateProgress(role: Role, enrollmentId: String, percent: Int) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Learning, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Progress update denied for role")
            return
        }
        scope.launch(ioDispatcher) {
            val enrollments = _state.value.enrollments
            val enrollment = enrollments.firstOrNull { it.id == enrollmentId } ?: return@launch
            val updated = enrollment.copy(
                progressPercent = percent.coerceIn(0, 100),
                status = if (percent >= 100) "Completed" else "InProgress",
                completedAt = if (percent >= 100) clock.now().toEpochMilliseconds() else null,
            )
            learningDao.updateEnrollment(updated)
            _state.value = _state.value.copy(
                enrollments = learningDao.getEnrollmentsByUser(enrollment.userId),
            )
        }
    }

    private suspend fun seedDemoCourses() {
        val courses = listOf(
            CourseEntity("course-1", "Safety Fundamentals", "Basic workplace safety training", "Safety", 30),
            CourseEntity("course-2", "Equipment Operation", "Standard equipment handling procedures", "Operations", 45),
            CourseEntity("course-3", "Customer Service", "Best practices for customer interactions", "Service", 20),
            CourseEntity("course-4", "Data Privacy", "GDPR and data handling requirements", "Compliance", 60),
            CourseEntity("course-5", "Leadership Basics", "Introduction to team leadership", "Management", 40),
        )
        courses.forEach { learningDao.upsertCourse(it) }
    }

    fun clearNote() {
        _state.value = _state.value.copy(note = null)
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = LearningState()
    }
}
