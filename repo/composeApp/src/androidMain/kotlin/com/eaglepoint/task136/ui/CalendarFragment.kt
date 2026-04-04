package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CalendarFragment : Fragment() {
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }
    private val orderVm: OrderWorkflowViewModel by lazy { GlobalContext.get().get() }
    private val meetingVm: MeetingWorkflowViewModel by lazy { GlobalContext.get().get() }
    private val resourceVm: ResourceListViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()
        val delegateForUserId = authVm.state.value.principal?.delegateForUserId

        val slotText = view.findViewById<TextView>(R.id.slotText)
        val meetingStatusText = view.findViewById<TextView>(R.id.meetingStatusText)

        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.suggestButton).setOnClickListener {
            orderVm.suggestSlots(role)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.submitMeetingButton).setOnClickListener {
            val resourceId = resourceVm.state.value.resources.firstOrNull()?.id ?: "res-1"
            meetingVm.submitMeeting(
                organizerId = delegateForUserId ?: actorId,
                actorId = actorId,
                resourceId = resourceId,
            )
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.openMeetingButton).setOnClickListener {
            val meetingId = meetingVm.state.value.meetingId
            if (meetingId != null) {
                (activity as? NavigationHost)?.navigateToMeetingDetail(meetingId)
            }
            authVm.touchSession()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            orderVm.state.collectLatest { state ->
                slotText.text = if (state.suggestedSlots.isEmpty()) {
                    "No suggested slots"
                } else {
                    state.suggestedSlots.joinToString(separator = "\n")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            meetingVm.state.collectLatest { state ->
                meetingStatusText.text = "${state.status}${state.note?.let { " - $it" } ?: ""}"
            }
        }
    }
}
