package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentSettingsBinding
import com.miniaturesoftwares.xpjobssuperviser.network.SecureMMKVStorage
import com.miniaturesoftwares.xpjobssuperviser.repo.AuthRepo
import com.miniaturesoftwares.xpjobssuperviser.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The recorder app's settings screen, with the operator-only sections removed.
 *
 * A supervisor has no local recordings, so storage destination, session export
 * and the recording-device section are meaningless here. They are hidden rather
 * than left showing controls that would do nothing.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var auth: AuthRepo

    @Inject
    lateinit var storage: SecureMMKVStorage

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Operator-only sections.
        binding.sectionStorage.root.visibility = View.GONE
        binding.btnExportSessions.visibility = View.GONE
        binding.sectionPayment.root.visibility = View.GONE

        // The recorder app's device section is single-device by construction -
        // it offers RECONNECT and FORGET DEVICE for *the* paired cap. A
        // supervisor holds a fleet, so those controls are not merely unwired
        // here, they express the wrong model. Device management lives in the
        // Fleet tab, which can address each cap individually.
        binding.sectionDevices.root.visibility = View.GONE

        binding.tvUserName.text = storage.get<String>(SecureMMKVStorage.SecureKey.USER_FULL_NAME)
            ?: getString(R.string.home_default_greeting)
        binding.tvAppVersion.text = getString(R.string.settings_version, BUILD_VERSION)

        val dark = storage.get<String>(SecureMMKVStorage.SecureKey.APP_THEME) == "dark"
        binding.switchDarkMode.isChecked = dark
        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            storage.set(SecureMMKVStorage.SecureKey.APP_THEME, if (checked) "dark" else "light")
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.btnDeleteAccount.visibility = View.GONE
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_logout_title)
            .setMessage(R.string.settings_logout_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_logout_confirm) { _, _ ->
                auth.logout()
                startActivity(
                    Intent(requireContext(), LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                requireActivity().finish()
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val BUILD_VERSION = "1.0"
    }
}
