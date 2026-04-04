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
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CartFragment : Fragment() {
    private val financeVm: OrderFinanceViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_cart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()
        val delegateForUserId = authVm.state.value.principal?.delegateForUserId

        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.addItemBtn).setOnClickListener {
            financeVm.addCartItem(
                role = role,
                actorId = actorId,
                delegateForUserId = delegateForUserId,
                resourceId = "res-1",
                label = "Service Package ${financeVm.state.value.cart.size + 1}",
                quantity = 1,
                unitPrice = 49.99,
            )
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.splitBtn).setOnClickListener {
            financeVm.splitFirstItem(role, actorId, delegateForUserId)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.mergeBtn).setOnClickListener {
            financeVm.mergeFirstTwoItems(role, actorId, delegateForUserId)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.checkoutBtn).setOnClickListener {
            financeVm.generateInvoice(role, actorId, delegateForUserId)
            authVm.touchSession()
        }
        view.findViewById<MaterialButton>(R.id.invoiceBtn).setOnClickListener {
            val invoiceId = financeVm.state.value.invoices.lastOrNull()?.id
            if (invoiceId != null) {
                (activity as? NavigationHost)?.navigateToInvoiceDetail(invoiceId)
            }
            authVm.touchSession()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            financeVm.state.collectLatest { state ->
                val noteView = view.findViewById<TextView>(R.id.noteText)
                if (state.note != null) {
                    noteView.visibility = View.VISIBLE
                    noteView.text = "Cart: ${state.cart.size} items | ${state.note}"
                } else {
                    noteView.visibility = if (state.cart.isNotEmpty()) View.VISIBLE else View.GONE
                    noteView.text = "Cart: ${state.cart.size} items"
                }
            }
        }
    }
}
