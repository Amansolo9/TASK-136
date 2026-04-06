package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.orders.MAX_PRICE
import com.eaglepoint.task136.shared.orders.MIN_PRICE
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class AdminFragment : Fragment() {

    private val resourceVm: ResourceListViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }
    private val deviceBindingService: DeviceBindingService by lazy { GlobalContext.get().get() }
    private val adapter = ResourceRecyclerAdapter()

    private fun requireAdmin(): Boolean {
        val role = authVm.state.value.role
        if (role != com.eaglepoint.task136.shared.rbac.Role.Admin) {
            (activity as? NavigationHost)?.navigateBack()
            return false
        }
        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Guard: reject non-admin immediately
        if (!requireAdmin()) return

        val recyclerView = view.findViewById<RecyclerView>(R.id.adminResourceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false

        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }

        // Business rules display
        view.findViewById<TextView>(R.id.priceRangeText).text =
            "Price validation: $$MIN_PRICE - $$MAX_PRICE per item"
        view.findViewById<TextView>(R.id.allergenRuleText).text =
            "Allergen flags: Required on all food resources (blank = blocked)"
        view.findViewById<TextView>(R.id.authPolicyText).text =
            "Auth policy: 10+ char password with number, 5 failures = 15 min lockout, 30 min idle timeout, max 2 devices"

        // Add resource (admin-guarded at VM level)
        view.findViewById<MaterialButton>(R.id.addResourceBtn).setOnClickListener {
            if (!requireAdmin()) return@setOnClickListener
            val role = authVm.state.value.role ?: return@setOnClickListener
            val name = view.findViewById<TextInputEditText>(R.id.resourceNameInput).text?.toString()?.trim().orEmpty()
            val category = view.findViewById<TextInputEditText>(R.id.resourceCategoryInput).text?.toString()?.trim().orEmpty()
            val units = view.findViewById<TextInputEditText>(R.id.resourceUnitsInput).text?.toString()?.toIntOrNull() ?: 0
            val price = view.findViewById<TextInputEditText>(R.id.resourcePriceInput).text?.toString()?.toDoubleOrNull() ?: 0.0

            if (name.isBlank()) {
                showStatus(view, "Name is required")
                return@setOnClickListener
            }
            val result = resourceVm.addResource(role = role, name = name, category = category, availableUnits = units, unitPrice = price)
            if (result) {
                showStatus(view, "Resource '$name' added")
                view.findViewById<TextInputEditText>(R.id.resourceNameInput).setText("")
            } else {
                showStatus(view, "Admin role required to add resources")
            }
            authVm.touchSession()
        }

        // Device binding reset (admin-enforced)
        view.findViewById<MaterialButton>(R.id.resetBindingsBtn).setOnClickListener {
            if (!requireAdmin()) return@setOnClickListener
            val role = authVm.state.value.role ?: return@setOnClickListener
            val userId = view.findViewById<TextInputEditText>(R.id.resetUserIdInput).text?.toString()?.trim().orEmpty()
            if (userId.isBlank()) {
                showStatus(view, "User ID is required")
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val success = deviceBindingService.adminResetBindings(role, userId)
                launch(Dispatchers.Main) {
                    if (success) {
                        showStatus(view, "Device bindings reset for '$userId'")
                    } else {
                        showStatus(view, "Admin role required to reset bindings")
                    }
                }
            }
            authVm.touchSession()
        }

        resourceVm.loadPage(limit = 5000)

        viewLifecycleOwner.lifecycleScope.launch {
            resourceVm.state.collectLatest { state ->
                adapter.submitList(state.resources)
                view.findViewById<TextView>(R.id.resourceCountText).text = "Resources (${state.resources.size})"
            }
        }
    }

    private fun showStatus(view: View, message: String) {
        val statusText = view.findViewById<TextView>(R.id.statusText)
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }
}
