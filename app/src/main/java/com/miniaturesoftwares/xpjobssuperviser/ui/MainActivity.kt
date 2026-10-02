package com.miniaturesoftwares.xpjobssuperviser.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.ActivityMainBinding
import com.miniaturesoftwares.xpjobssuperviser.repo.AuthRepo
import com.miniaturesoftwares.xpjobssuperviser.ui.auth.LoginActivity
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.FleetFragment
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.HomeFragment
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.PeopleFragment
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.SettingsFragment
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.SessionsFragment
import com.miniaturesoftwares.xpjobssuperviser.ui.fragments.TasksFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var auth: AuthRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Every tab except Fleet needs a token; the fleet itself works offline
        // over BLE, but a half-usable app is more confusing than a login screen.
        if (!auth.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.nav_fleet -> FleetFragment()
                    R.id.nav_people -> PeopleFragment()
                    R.id.nav_tasks -> TasksFragment()
                    R.id.nav_sessions -> SessionsFragment()
                    R.id.nav_settings -> SettingsFragment()
                    else -> HomeFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
