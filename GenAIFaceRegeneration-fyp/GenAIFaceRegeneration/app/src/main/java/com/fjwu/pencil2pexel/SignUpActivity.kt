package com.fjwu.pencil2pexel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fjwu.pencil2pexel.network.ApiClient
import com.fjwu.pencil2pexel.network.SignupRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var usernameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var signUpButton: MaterialButton
    private lateinit var loginLink: TextView
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        usernameInput = findViewById(R.id.usernameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        signUpButton = findViewById(R.id.signUpButton)
        loginLink = findViewById(R.id.loginLink)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        signUpButton.setOnClickListener {
            if (validateInputs()) {
                performSignUp()
            }
        }

        loginLink.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Validate username
        if (username.isEmpty()) {
            usernameInputLayout.error = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameInputLayout.error = "Username must be at least 3 characters"
            isValid = false
        } else if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            usernameInputLayout.error = "Username can only contain letters, numbers, and underscores"
            isValid = false
        } else {
            usernameInputLayout.error = null
        }

        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Invalid email format"
            isValid = false
        } else {
            emailInputLayout.error = null
        }

        // Validate password (minimum 6 characters as per API)
        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordInputLayout.error = null
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "Please confirm your password"
            isValid = false
        } else if (confirmPassword != password) {
            confirmPasswordInputLayout.error = getString(R.string.password_mismatch)
            isValid = false
        } else {
            confirmPasswordInputLayout.error = null
        }

        return isValid
    }

    private fun setLoading(isLoading: Boolean) {
        signUpButton.isEnabled = !isLoading
        usernameInput.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
        confirmPasswordInput.isEnabled = !isLoading
        loginLink.isEnabled = !isLoading

        if (isLoading) {
            signUpButton.text = getString(R.string.signing_up)
            progressBar?.visibility = View.VISIBLE
        } else {
            signUpButton.text = getString(R.string.sign_up)
            progressBar?.visibility = View.GONE
        }
    }

    private fun performSignUp() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(this@SignUpActivity)
                val request = SignupRequest(
                    username = username,
                    email = email,
                    password = password
                )
                val response = apiService.signup(request)

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!

                    // Save authentication data
                    ApiClient.saveAuthData(
                        this@SignUpActivity,
                        authResponse.token,
                        authResponse.user
                    )

                    Toast.makeText(
                        this@SignUpActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to Main
                    startActivity(Intent(this@SignUpActivity, MainActivity::class.java))
                    finishAffinity()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } else {
                    // Handle error response
                    val errorMessage = when (response.code()) {
                        400 -> {
                            val errorBody = response.errorBody()?.string()
                            ApiClient.parseError(errorBody)
                        }
                        409 -> getString(R.string.username_email_exists)
                        else -> {
                            val errorBody = response.errorBody()?.string()
                            ApiClient.parseError(errorBody)
                        }
                    }

                    setLoading(false)
                    Toast.makeText(this@SignUpActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                val errorMessage = when {
                    e.message?.contains("Unable to resolve host") == true ->
                        getString(R.string.network_error)
                    e.message?.contains("timeout") == true ->
                        "Connection timeout. Please try again."
                    else ->
                        "Error: ${e.message ?: getString(R.string.network_error)}"
                }
                Toast.makeText(this@SignUpActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}
