package com.fjwu.pencil2pexel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fjwu.pencil2pexel.network.ApiClient

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Fade-in animation
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 1000
            fillAfter = true
        }

        findViewById<ImageView>(R.id.splashLogo).startAnimation(fadeIn)
        findViewById<TextView>(R.id.appName).startAnimation(fadeIn)
        findViewById<TextView>(R.id.tagline).startAnimation(fadeIn)

        // Navigate after a short timeout; skip server checks to speed up splash
        navigateAfterDelay()
    }

    private fun navigateAfterDelay() {
        val splashTimeoutMs = 1500L
        Handler(Looper.getMainLooper()).postDelayed({
            val isLoggedIn = ApiClient.isLoggedIn(this@SplashActivity)
            val intent = if (isLoggedIn) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, splashTimeoutMs)
    }
}
