package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.ui.MainActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({

            startActivity(Intent(this, MainActivity::class.java))

            finish()

        }, 4000) // 2 seconds
    }
}