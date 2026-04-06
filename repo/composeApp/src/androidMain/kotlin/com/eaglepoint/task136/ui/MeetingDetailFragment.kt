package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.eaglepoint.task136.R
import com.eaglepoint.task136.media.ImageBitmapLruCache
import com.eaglepoint.task136.media.ImageDownsampler
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

class MeetingDetailFragment : Fragment() {
    companion object {
        fun newInstance(meetingId: String) = MeetingDetailFragment().apply {
            arguments = Bundle().apply { putString("meetingId", meetingId) }
        }
        private val imageCache = ImageBitmapLruCache(maxBytes = 20 * 1024 * 1024)
    }

    private val meetingVm: MeetingWorkflowViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }
    private val downsampler = ImageDownsampler()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_meeting_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val meetingId = arguments?.getString("meetingId").orEmpty()
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()
        val delegateFor = authVm.state.value.principal?.delegateForUserId

        view.findViewById<TextView>(R.id.meetingIdText).text = meetingId
        val canApprove = role == Role.Admin || role == Role.Supervisor
        val canManage = role != Role.Viewer

        view.findViewById<MaterialButton>(R.id.addAttendeeBtn).isEnabled = canManage
        view.findViewById<MaterialButton>(R.id.approveBtn).isEnabled = canApprove
        view.findViewById<MaterialButton>(R.id.denyBtn).isEnabled = canApprove

        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.addAttendeeBtn).setOnClickListener {
            meetingVm.addAttendee("User-${(1..99).random()}", role, actorId, delegateFor ?: actorId, delegateFor != null)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.approveBtn).setOnClickListener {
            meetingVm.approve(role)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.denyBtn).setOnClickListener {
            meetingVm.deny(role)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.checkInBtn).setOnClickListener {
            meetingVm.checkIn(role)
            authVm.touchSession()
        }

        val toggleCheckInBtn = view.findViewById<MaterialButton>(R.id.toggleCheckInBtn)
        toggleCheckInBtn.isEnabled = canApprove
        toggleCheckInBtn.setOnClickListener {
            val current = meetingVm.state.value.requireCheckIn
            meetingVm.setRequireCheckIn(role, !current)
            authVm.touchSession()
        }

        view.findViewById<MaterialButton>(R.id.addAttachmentBtn).apply {
            isEnabled = canManage
            setOnClickListener {
                meetingVm.addAttachment("attachment-${System.currentTimeMillis()}.jpg", role)
                authVm.touchSession()
            }
        }

        view.findViewById<MaterialButton>(R.id.removeAttachmentBtn).apply {
            isEnabled = canManage
            setOnClickListener {
                meetingVm.removeAttachment(role)
                authVm.touchSession()
            }
        }

        meetingVm.loadMeetingDetail(meetingId, role, actorId, delegateFor ?: actorId, delegateFor != null)

        viewLifecycleOwner.lifecycleScope.launch {
            meetingVm.state.collectLatest { state ->
                view.findViewById<TextView>(R.id.statusText).text = state.status.name
                view.findViewById<TextView>(R.id.agendaText).text = if (state.agenda.isBlank()) "Agenda: -" else "Agenda: ${state.agenda}"
                view.findViewById<TextView>(R.id.attendeesText).text = if (state.attendees.isEmpty()) {
                    "Attendees: none"
                } else {
                    "Attendees: ${state.attendees.joinToString { it.name }}"
                }

                val noteText = view.findViewById<TextView>(R.id.noteText)
                if (state.note.isNullOrBlank()) {
                    noteText.visibility = View.GONE
                } else {
                    noteText.visibility = View.VISIBLE
                    noteText.text = state.note
                }

                view.findViewById<MaterialButton>(R.id.checkInBtn).isEnabled = canManage && state.requireCheckIn
                view.findViewById<MaterialButton>(R.id.toggleCheckInBtn).text =
                    if (state.requireCheckIn) "Disable Check-in" else "Enable Check-in"

                val attachmentText = view.findViewById<TextView>(R.id.attachmentText)
                val removeBtn = view.findViewById<MaterialButton>(R.id.removeAttachmentBtn)
                if (state.attachmentPath != null) {
                    attachmentText.visibility = View.VISIBLE
                    attachmentText.text = "Attachment: ${state.attachmentPath}"
                    removeBtn.visibility = View.VISIBLE
                    // Load attachment image via downsampler + LRU cache pipeline
                    loadAttachmentImage(state.attachmentPath!!)
                } else {
                    attachmentText.visibility = View.GONE
                    removeBtn.visibility = View.GONE
                }
            }
        }
    }

    private fun loadAttachmentImage(path: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Check LRU cache first
            val cached = imageCache.get(path)
            if (cached != null) return@launch // Image already cached and ready

            // Load and downsample off main thread
            withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val bitmap = downsampler.downsample(bytes, maxWidth = 800, maxHeight = 600)
                        if (bitmap != null) {
                            imageCache.put(path, bitmap)
                        }
                    }
                } catch (_: Exception) {
                    // File may not exist yet for placeholder attachments
                }
            }
        }
    }
}
