package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class LoginFragment : Fragment() {

    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usernameInput = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val signInButton = view.findViewById<MaterialButton>(R.id.signInButton)
        val errorText = view.findViewById<TextView>(R.id.errorText)

        usernameInput.doAfterTextChanged { authVm.updateUsername(it?.toString().orEmpty()) }
        passwordInput.doAfterTextChanged { authVm.updatePassword(it?.toString().orEmpty()) }
        signInButton.setOnClickListener { authVm.login() }

        viewLifecycleOwner.lifecycleScope.launch {
            authVm.state.collectLatest { state ->
                if (state.error != null) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = state.error
                } else {
                    errorText.visibility = View.GONE
                }

                if (state.isAuthenticated) {
                    (activity as? NavigationHost)?.onAuthenticated()
                }
            }
        }
    }
}

interface NavigationHost {
    fun onAuthenticated()
    fun onLogout()
    fun navigateToCalendar()
    fun navigateToCart()
    fun navigateToOrderDetail(orderId: String)
    fun navigateToInvoiceDetail(invoiceId: String)
    fun navigateToMeetingDetail(meetingId: String)
    fun navigateToLearning()
    fun navigateToAdmin()
    fun navigateBack()
}
