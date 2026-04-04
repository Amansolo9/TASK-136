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

class InvoiceDetailFragment : Fragment() {
    companion object {
        fun newInstance(invoiceId: String) = InvoiceDetailFragment().apply {
            arguments = Bundle().apply { putString("invoiceId", invoiceId) }
        }
    }

    private val financeVm: OrderFinanceViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_invoice_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val invoiceId = arguments?.getString("invoiceId").orEmpty()
        val role = authVm.state.value.role ?: Role.Viewer
        val actorId = authVm.state.value.principal?.userId.orEmpty()

        view.findViewById<TextView>(R.id.invoiceIdText).text = invoiceId
        view.findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            (activity as? NavigationHost)?.navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.refundBtn).setOnClickListener {
            financeVm.refundLatest(role, actorId)
            authVm.touchSession()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            financeVm.state.collectLatest { state ->
                val invoice = state.invoices.firstOrNull { it.id == invoiceId }
                if (invoice == null) {
                    view.findViewById<TextView>(R.id.subtotalText).text = "Invoice not found"
                    view.findViewById<TextView>(R.id.taxText).text = ""
                    view.findViewById<TextView>(R.id.totalText).text = ""
                } else {
                    view.findViewById<TextView>(R.id.subtotalText).text = "Subtotal: $${"%.2f".format(invoice.subtotal)}"
                    view.findViewById<TextView>(R.id.taxText).text = "Tax: $${"%.2f".format(invoice.tax)}"
                    view.findViewById<TextView>(R.id.totalText).text = "Total: $${"%.2f".format(invoice.total)}"
                }

                val noteText = view.findViewById<TextView>(R.id.noteText)
                if (state.note.isNullOrBlank()) {
                    noteText.visibility = View.GONE
                } else {
                    noteText.visibility = View.VISIBLE
                    noteText.text = state.note
                }
            }
        }
    }
}
