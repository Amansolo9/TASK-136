package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.orders.PaymentMethod
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CartFragment : Fragment() {
    private val financeVm: OrderFinanceViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }
    private val resourceVm: ResourceListViewModel by lazy { GlobalContext.get().get() }
    private val cartAdapter = CartItemAdapter()
    private val invoiceAdapter = InvoiceListAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_cart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()
        val delegateForUserId = authVm.state.value.principal?.delegateForUserId

        // Wire RecyclerView + DiffUtil-backed adapters for cart items
        val cartRecyclerView = view.findViewById<RecyclerView>(R.id.cartRecyclerView)
        cartRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        cartRecyclerView.adapter = cartAdapter

        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }
        val tenderGroup = view.findViewById<RadioGroup>(R.id.tenderGroup)
        val externalTenderInputLayout = view.findViewById<TextInputLayout>(R.id.externalTenderInputLayout)
        val externalTenderInput = view.findViewById<TextInputEditText>(R.id.externalTenderInput)
        tenderGroup.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.tenderWallet -> PaymentMethod.InternalWallet
                R.id.tenderExternal -> PaymentMethod.ExternalTender
                else -> PaymentMethod.Cash
            }
            financeVm.selectPaymentMethod(method)
            externalTenderInputLayout.visibility = if (method == PaymentMethod.ExternalTender) View.VISIBLE else View.GONE
            if (method != PaymentMethod.ExternalTender) {
                externalTenderInput.setText("")
                financeVm.setExternalTenderDetails("")
            }
        }
        view.findViewById<MaterialButton>(R.id.addItemBtn).setOnClickListener {
            val resources = resourceVm.state.value.resources
            val nextIndex = financeVm.state.value.cart.size
            val resource = if (resources.isNotEmpty()) resources[nextIndex % resources.size] else null
            financeVm.addCartItem(
                role = role,
                actorId = actorId,
                delegateForUserId = delegateForUserId,
                resourceId = resource?.id ?: "res-1",
                label = resource?.name ?: "Service Package ${nextIndex + 1}",
                quantity = 1,
                unitPrice = resource?.unitPrice ?: 49.99,
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
            financeVm.setExternalTenderDetails(externalTenderInput.text?.toString().orEmpty())
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

        // Load resources for catalog-backed cart
        resourceVm.loadPage(limit = 5000)

        viewLifecycleOwner.lifecycleScope.launch {
            financeVm.state.collectLatest { state ->
                // Update RecyclerView cart list via DiffUtil
                cartAdapter.submitList(state.cart.toList())

                val noteView = view.findViewById<TextView>(R.id.noteText)
                val cartSummary = if (state.cart.isNotEmpty()) {
                    val totalQty = state.cart.sumOf { it.quantity }
                    val totalVal = state.cart.sumOf { it.unitPrice * it.quantity }
                    "Cart: ${state.cart.size} items ($totalQty units) - $${"%.2f".format(totalVal)}"
                } else {
                    "Cart: empty"
                }
                val tags = state.cart.mapNotNull { item -> item.label.takeIf { it.isNotBlank() } }.distinct().take(3)
                val tagLine = if (tags.isNotEmpty()) " | Tags: ${tags.joinToString()}" else ""
                if (state.note != null) {
                    noteView.visibility = View.VISIBLE
                    noteView.text = "$cartSummary$tagLine | ${state.note}"
                } else {
                    noteView.visibility = View.VISIBLE
                    noteView.text = "$cartSummary$tagLine"
                }
            }
        }
    }
}
