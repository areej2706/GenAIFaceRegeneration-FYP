package com.fjwu.pencil2pexel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.fjwu.pencil2pexel.network.ApiClient

class HomeFragment : Fragment() {

    private lateinit var userNameText: TextView
    private lateinit var pictureGenerationCard: CardView
    private lateinit var profileIcon: ImageView
    private lateinit var imagesProcessedCount: TextView
    private lateinit var savedCount: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadUserData()
        setupListeners()
    }

    private fun initViews(view: View) {
        userNameText = view.findViewById(R.id.userNameText)
        pictureGenerationCard = view.findViewById(R.id.pictureGenerationCard)
        profileIcon = view.findViewById(R.id.profileIcon)
        imagesProcessedCount = view.findViewById(R.id.imagesProcessedCount)
        savedCount = view.findViewById(R.id.savedCount)
    }

    private fun loadUserData() {
        val sharedPref = requireContext().getSharedPreferences("pencil2pixel_prefs", android.content.Context.MODE_PRIVATE)
        val userId = ApiClient.getUserId(requireContext())
        val processedKey = if (!userId.isNullOrBlank()) "images_processed_${userId}" else "images_processed"
        val savedKey = if (!userId.isNullOrBlank()) "images_saved_${userId}" else "images_saved"
        val userName = sharedPref.getString("user_name", "User") ?: "User"
        val processedCount = sharedPref.getInt(processedKey, 0)
        val saved = sharedPref.getInt(savedKey, 0)

        userNameText.text = userName
        imagesProcessedCount.text = processedCount.toString()
        savedCount.text = saved.toString()
    }

    private fun setupListeners() {
        pictureGenerationCard.setOnClickListener {
            (activity as? MainActivity)?.navigateToConvert()
        }

        profileIcon.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                    ?.selectedItemId = R.id.nav_profile
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}
