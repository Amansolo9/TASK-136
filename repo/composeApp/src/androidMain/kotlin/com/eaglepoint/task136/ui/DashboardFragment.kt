package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class DashboardFragment : Fragment() {

    private val resourceVm: ResourceListViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }
    private val orderVm: OrderWorkflowViewModel by lazy { GlobalContext.get().get() }
    private val financeVm: OrderFinanceViewModel by lazy { GlobalContext.get().get() }

    private val adapter = ResourceRecyclerAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.resourceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)

        val roleBadge = view.findViewById<TextView>(R.id.roleBadge)
        val actorIdText = view.findViewById<TextView>(R.id.actorIdText)
        val logoutBtn = view.findViewById<MaterialButton>(R.id.logoutButton)
        val loadingBar = view.findViewById<ProgressBar>(R.id.loadingBar)
        val errorBanner = view.findViewById<TextView>(R.id.errorBanner)
        val statResources = view.findViewById<TextView>(R.id.statResources)
        val statCart = view.findViewById<TextView>(R.id.statCart)
        val statInvoices = view.findViewById<TextView>(R.id.statInvoices)
        val statRefunds = view.findViewById<TextView>(R.id.statRefunds)
        val navCalendar = view.findViewById<MaterialButton>(R.id.navCalendar)
        val navCart = view.findViewById<MaterialButton>(R.id.navCart)

        logoutBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    resourceVm.clearSessionState()
                    orderVm.clearSessionState()
                    financeVm.clearSessionState()
                    authVm.logout()
                    (activity as? NavigationHost)?.onLogout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        navCalendar.setOnClickListener { (activity as? NavigationHost)?.navigateToCalendar() }
        navCart.setOnClickListener { (activity as? NavigationHost)?.navigateToCart() }

        val navLearning = view.findViewById<MaterialButton>(R.id.navLearning)
        navLearning.setOnClickListener { (activity as? NavigationHost)?.navigateToLearning() }

        val navAdmin = view.findViewById<MaterialButton>(R.id.navAdmin)
        navAdmin.setOnClickListener { (activity as? NavigationHost)?.navigateToAdmin() }
        // Show admin button only for Admin role
        val currentRole = authVm.state.value.role
        navAdmin.visibility = if (currentRole == Role.Admin) View.VISIBLE else View.GONE

        resourceVm.loadPage(limit = 5000)
        authVm.touchSession()

        viewLifecycleOwner.lifecycleScope.launch {
            authVm.state.collectLatest { state ->
                roleBadge.text = state.role?.name ?: "Unknown"
                actorIdText.text = state.principal?.userId.orEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            resourceVm.state.collectLatest { state ->
                adapter.submitList(state.resources)
                statResources.text = "${state.resources.size}\nResources"

                loadingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                if (state.error != null) {
                    errorBanner.visibility = View.VISIBLE
                    errorBanner.text = state.error
                } else {
                    errorBanner.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            financeVm.state.collectLatest { state ->
                statCart.text = "${state.cart.size}\nCart"
                statInvoices.text = "${state.invoices.size}\nInvoices"
                statRefunds.text = "${state.refunds.size}\nRefunds"
            }
        }
    }
}
