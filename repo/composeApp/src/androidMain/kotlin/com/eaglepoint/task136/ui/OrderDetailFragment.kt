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
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class OrderDetailFragment : Fragment() {
    companion object {
        fun newInstance(orderId: String) = OrderDetailFragment().apply {
            arguments = Bundle().apply { putString("orderId", orderId) }
        }
    }

    private val orderVm: OrderWorkflowViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_order_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val orderId = arguments?.getString("orderId").orEmpty()
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()

        view.findViewById<TextView>(R.id.orderIdText).text = orderId
        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }

        orderVm.loadOrderById(orderId, role, actorId, authVm.state.value.principal?.delegateForUserId)

        view.findViewById<MaterialButton>(R.id.confirmBtn).setOnClickListener { orderVm.confirmLastOrder(role) }
        view.findViewById<MaterialButton>(R.id.shipBtn).setOnClickListener { orderVm.markAwaitingDelivery(role) }
        view.findViewById<MaterialButton>(R.id.deliverBtn).setOnClickListener { orderVm.confirmDelivery(role, "signed-$actorId") }
        view.findViewById<MaterialButton>(R.id.receiptBtn).setOnClickListener { authVm.touchSession() }

        viewLifecycleOwner.lifecycleScope.launch {
            orderVm.state.collectLatest { state ->
                val status = if (state.lastOrderId == orderId) state.lastOrderState ?: "Loading..." else "Loading..."
                view.findViewById<TextView>(R.id.statusText).text = status
                val errorView = view.findViewById<TextView>(R.id.errorText)
                if (state.error != null) {
                    errorView.visibility = View.VISIBLE
                    errorView.text = state.error
                } else {
                    errorView.visibility = View.GONE
                }
            }
        }
    }
}
