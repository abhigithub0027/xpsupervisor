package com.miniaturesoftwares.xpjobssuperviser.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.ActivityLoginBinding
import com.miniaturesoftwares.xpjobssuperviser.repo.AuthRepo
import com.miniaturesoftwares.xpjobssuperviser.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phone then OTP, in one screen.
 *
 * Two steps rather than two screens: a supervisor signing in is a single act,
 * and a second Activity buys nothing but a back-stack entry that can strand
 * them between the two halves.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    @Inject
    lateinit var auth: AuthRepo

    private var phone: String = ""
    private var otpSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etPhone.doAfterTextChanged { updateAction() }
        binding.etOtp.doAfterTextChanged { updateAction() }
        binding.btnAction.setOnClickListener { onAction() }
        binding.btnChangeNumber.setOnClickListener { backToPhone() }

        updateAction()
    }

    private fun onAction() {
        if (!otpSent) sendOtp() else verifyOtp()
    }

    private fun sendOtp() {
        phone = binding.etPhone.text.toString().trim()
        if (phone.length != 10) {
            toast(getString(R.string.login_phone_invalid))
            return
        }
        busy(true)
        lifecycleScope.launch {
            auth.sendOtp(phone)
                .catch { busy(false); toast(it.message ?: getString(R.string.login_failed)) }
                .collect {
                    busy(false)
                    otpSent = true
                    renderOtpStep()
                }
        }
    }

    private fun verifyOtp() {
        val otp = binding.etOtp.text.toString().trim()
        if (otp.length < 4) {
            toast(getString(R.string.login_otp_invalid))
            return
        }
        busy(true)
        lifecycleScope.launch {
            auth.verifyOtp(phone, otp)
                .catch { busy(false); toast(it.message ?: getString(R.string.login_failed)) }
                .collect {
                    busy(false)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
        }
    }

    private fun renderOtpStep() {
        binding.layoutOtp.visibility = View.VISIBLE
        binding.btnChangeNumber.visibility = View.VISIBLE
        binding.etPhone.isEnabled = false
        binding.tvSubtitle.text = getString(R.string.login_otp_sent, phone)
        binding.etOtp.requestFocus()
        updateAction()
    }

    private fun backToPhone() {
        otpSent = false
        binding.layoutOtp.visibility = View.GONE
        binding.btnChangeNumber.visibility = View.GONE
        binding.etPhone.isEnabled = true
        binding.etOtp.setText("")
        binding.tvSubtitle.text = getString(R.string.login_subtitle)
        updateAction()
    }

    private fun updateAction() {
        binding.btnAction.text = getString(
            if (otpSent) R.string.login_verify else R.string.login_send_code
        )
        val ready = if (otpSent) binding.etOtp.text.length >= 4
        else binding.etPhone.text.length == 10
        binding.btnAction.isEnabled = ready
        binding.btnAction.alpha = if (ready) 1f else 0.4f
    }

    private fun busy(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnAction.isEnabled = !loading
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
