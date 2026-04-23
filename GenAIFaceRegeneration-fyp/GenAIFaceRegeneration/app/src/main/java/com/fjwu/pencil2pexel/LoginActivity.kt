package com.fjwu.pencil2pexel

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fjwu.pencil2pexel.network.ApiClient
import com.fjwu.pencil2pexel.network.LoginRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var signUpLink: TextView
    private lateinit var logo: ImageView
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        loginButton = findViewById(R.id.loginButton)
        signUpLink = findViewById(R.id.signUpLink)
        logo = findViewById(R.id.logo)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        loginButton.setOnClickListener {
            if (validateInputs()) {
                performLogin()
            }
        }

        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showBaseUrlDialog()
                return true
            }
        })

        logo.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showBaseUrlDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Base URL")
        builder.setMessage("Enter the API server URL (use HTTP, not HTTPS).\nFor emulator: http://10.0.2.2:5000\nFor physical device: http://YOUR_PC_IP:5000")

        val input = EditText(this)
        input.hint = "http://10.0.2.2:5000"
        input.setText(ApiClient.getBaseUrl(this))
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, _ ->
            val baseUrl = input.text.toString().trim()
            if (baseUrl.isNotEmpty()) {
                ApiClient.setBaseUrl(this, baseUrl)
                Toast.makeText(this, "Base URL saved: ${ApiClient.getBaseUrl(this)}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Base URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNeutralButton("Reset") { dialog, _ ->
            ApiClient.resetBaseUrl(this)
            Toast.makeText(this, "Base URL reset to default", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ContextCompat.getColor(this, R.color.accent))
        }
        dialog.show()
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Invalid email format"
            isValid = false
        } else {
            emailInputLayout.error = null
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordInputLayout.error = null
        }

        return isValid
    }

    private fun setLoading(isLoading: Boolean) {
        loginButton.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
        signUpLink.isEnabled = !isLoading

        if (isLoading) {
            loginButton.text = getString(R.string.logging_in)
            progressBar?.visibility = View.VISIBLE
        } else {
            loginButton.text = getString(R.string.login)
            progressBar?.visibility = View.GONE
        }
    }

    private fun performLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(this@LoginActivity)
                val request = LoginRequest(email = email, password = password)
                val response = apiService.login(request)

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!

                    // Save authentication data
                    ApiClient.saveAuthData(
                        this@LoginActivity,
                        authResponse.token,
                        authResponse.user
                    )

                    Toast.makeText(
                        this@LoginActivity,
                        "Login successful!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to Main
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finishAffinity()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } else {
                    // Handle error response
                    val errorMessage = when (response.code()) {
                        400 -> "Missing email or password"
                        401 -> getString(R.string.login_error)
                        else -> {
                            val errorBody = response.errorBody()?.string()
                            ApiClient.parseError(errorBody)
                        }
                    }

                    setLoading(false)
                    Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
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
                Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}
