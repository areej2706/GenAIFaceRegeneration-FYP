package com.fjwu.pencil2pexel

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fjwu.pencil2pexel.network.ApiClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var userName: TextView
    private lateinit var userEmail: TextView
    private lateinit var imagesCount: TextView
    private lateinit var viewHistoryButton: MaterialButton
    private lateinit var logoutButton: MaterialButton
    private var loadingIndicator: ProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadUserDataFromLocal()
        loadProfileFromApi()
        setupListeners()
    }

    private fun initViews(view: View) {
        userName = view.findViewById(R.id.userName)
        userEmail = view.findViewById(R.id.userEmail)
        imagesCount = view.findViewById(R.id.imagesCount)
        viewHistoryButton = view.findViewById(R.id.viewHistoryButton)
        logoutButton = view.findViewById(R.id.logoutButton)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
    }

    private fun loadUserDataFromLocal() {
        val context = requireContext()
        userName.text = ApiClient.getUserName(context) ?: "User"
        userEmail.text = ApiClient.getUserEmail(context) ?: "user@example.com"
        imagesCount.text = "..."
    }

    private fun loadProfileFromApi() {
        val token = ApiClient.getAuthToken(requireContext()) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadingIndicator?.visibility = View.VISIBLE
                val apiService = ApiClient.getApiService(requireContext())
                val response = apiService.getProfile(token)

                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!.user
                    userName.text = profile.username
                    userEmail.text = profile.email
                    imagesCount.text = (profile.totalImages ?: 0).toString()

                    // Update local cache
                    ApiClient.updateUserData(requireContext(), profile.username, profile.email)
                } else {
                    // Fallback to local data
                    val sharedPref = requireContext().getSharedPreferences("pencil2pixel_prefs", android.content.Context.MODE_PRIVATE)
                    imagesCount.text = sharedPref.getInt("images_processed", 0).toString()

                    if (response.code() == 401) {
                        // Token expired - redirect to login
                        Toast.makeText(requireContext(), "Session expired. Please login again.", Toast.LENGTH_LONG).show()
                        performLogout()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error loading profile", e)
                // Fallback to local data
                val sharedPref = requireContext().getSharedPreferences("pencil2pixel_prefs", android.content.Context.MODE_PRIVATE)
                imagesCount.text = sharedPref.getInt("images_processed", 0).toString()
            } finally {
                loadingIndicator?.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        viewHistoryButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToHistory()
        }

        logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_logout, null)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_Dialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)

        dialogView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
            performLogout()
        }

        dialog.show()
    }

    private fun performLogout() {
        // Clear all auth data using ApiClient
        ApiClient.clearAuthData(requireContext())

        // Navigate to Login
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finishAffinity()
    }

    override fun onResume() {
        super.onResume()
        loadUserDataFromLocal()
        loadProfileFromApi()
    }
}
