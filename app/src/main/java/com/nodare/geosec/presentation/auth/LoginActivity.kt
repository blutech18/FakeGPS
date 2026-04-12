package com.nodare.geosec.presentation.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.firebase.messaging.FirebaseMessaging
import com.nodare.geosec.databinding.ActivityLoginBinding
import com.nodare.geosec.presentation.dashboard.MainActivity
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    /** Guards against clearing errors during programmatic focus/text changes from error display */
    private var isShowingError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.isLoggedIn) {
            navigateToMain()
            return
        }

        setupKeyboardInsets()
        setupUI()
        setupRealtimeValidation()
        observeLoginState()
    }

    private fun setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (imeInsets.bottom > 0) imeInsets.bottom else navInsets.bottom
            }

            if (imeInsets.bottom > 0) {
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, binding.tilPassword.bottom)
                }
            }

            insets
        }
    }

    private fun setupUI() {
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isShowingError) {
                binding.tilEmail.error = null
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, binding.tilEmail.top)
                }
            }
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isShowingError) {
                binding.tilPassword.error = null
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, binding.tilPassword.top)
                }
            }
        }

        binding.btnDismissError.setOnClickListener {
            hideErrorBanner()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            clearAllErrors()

            if (!validateFields(email, password)) return@setOnClickListener

            // Hide keyboard before login attempt
            currentFocus?.let { v ->
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }

            viewModel.login(email, password)
        }
    }

    /**
     * Real-time validation: clear field errors as the user types.
     * Also dismiss the general error banner on any input change.
     */
    private fun setupRealtimeValidation() {
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isShowingError) {
                    binding.tilEmail.error = null
                    hideErrorBanner()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isShowingError) {
                    binding.tilPassword.error = null
                    hideErrorBanner()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun validateFields(email: String, password: String): Boolean {
        if (email.isBlank()) {
            binding.tilEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return false
        }
        if (password.isBlank()) {
            binding.tilPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            return false
        }
        if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return false
        }
        return true
    }

    private fun observeLoginState() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                    clearAllErrors()
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    val user = state.data
                    subscribeToAdminTopicIfNeeded(user.role)
                    Toast.makeText(this, "Welcome, ${user.displayName}", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    handleLoginError(state)
                }
            }
        }
    }

    /**
     * Routes errors to the correct UI element based on the typed LoginError.
     * - Email errors → email field
     * - Password errors → password field
     * - Credential errors → both fields
     * - General errors → error banner
     */
    private fun handleLoginError(error: Resource.Error) {
        val loginError = error.errorType as? LoginError

        // Prevent focus/text listeners from immediately clearing the errors we're about to set
        isShowingError = true

        when (loginError) {
            is LoginError.Email -> {
                binding.tilEmail.error = loginError.message
                binding.etEmail.requestFocus()
            }
            is LoginError.Password -> {
                binding.tilPassword.error = loginError.message
                binding.etPassword.requestFocus()
            }
            is LoginError.InvalidCredential -> {
                binding.tilEmail.error = " "
                binding.tilPassword.error = loginError.message
                binding.etPassword.requestFocus()
            }
            is LoginError.General -> {
                showErrorBanner(loginError.message)
            }
            null -> {
                showErrorBanner(error.message)
            }
        }

        // Scroll to make the error visible, then release the guard
        binding.scrollView.post {
            when (loginError) {
                is LoginError.Email -> binding.scrollView.smoothScrollTo(0, binding.tilEmail.top)
                is LoginError.Password,
                is LoginError.InvalidCredential -> binding.scrollView.smoothScrollTo(0, binding.tilPassword.top)
                else -> binding.scrollView.smoothScrollTo(0, binding.errorBanner.top)
            }
            // Release the guard after the UI has settled so future user input can clear errors
            isShowingError = false
        }
    }

    private fun showErrorBanner(message: String) {
        binding.tvErrorBanner.text = message
        binding.errorBanner.visibility = View.VISIBLE
        binding.errorBanner.alpha = 0f
        binding.errorBanner.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun hideErrorBanner() {
        if (binding.errorBanner.visibility == View.VISIBLE) {
            binding.errorBanner.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.errorBanner.visibility = View.GONE }
                .start()
        }
    }

    private fun clearAllErrors() {
        isShowingError = false
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        hideErrorBanner()
    }

    private fun subscribeToAdminTopicIfNeeded(role: String) {
        if (role == Constants.ROLE_CEO || role == Constants.ROLE_ADMIN) {
            FirebaseMessaging.getInstance().subscribeToTopic("admin_alerts")
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic("admin_alerts")
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isTouchOnEditText(ev: MotionEvent): Boolean {
        val root = window.decorView.rootView
        return isTouchOnEditTextView(root, ev)
    }

    private fun isTouchOnEditTextView(view: View, ev: MotionEvent): Boolean {
        if (view is android.widget.EditText || view is com.google.android.material.textfield.TextInputLayout) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val rect = android.graphics.Rect(
                location[0], location[1],
                location[0] + view.width, location[1] + view.height
            )
            if (rect.contains(ev.x.toInt(), ev.y.toInt())) return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTouchOnEditTextView(view.getChildAt(i), ev)) return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText && !isTouchOnEditText(ev)) {
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
