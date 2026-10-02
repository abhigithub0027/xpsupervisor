package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.CyberCapHeartbeat
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentCapRecordingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live recording stats for one cap, mirroring the recorder app's screen.
 *
 * The phone captures nothing here - everything shown comes from that cap's
 * heartbeat in [FleetManager]. Because those numbers are only as trustworthy as
 * the last heartbeat, staleness is made loud: once the beats stop, the values
 * are marked as history rather than left looking live.
 */
@AndroidEntryPoint
class CapRecordingFragment : Fragment() {

    @Inject
    lateinit var fleet: FleetManager

    private var _binding: FragmentCapRecordingBinding? = null
    private val binding get() = _binding!!

    private val deviceId get() = requireArguments().getString(ARG_DEVICE_ID).orEmpty()
    private var stopRequested = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCapRecordingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStop.setOnClickListener { confirmStop() }
        binding.btnClose.setOnClickListener { parentFragmentManager.popBackStack() }

        // Every heartbeat re-renders the whole screen.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fleet.fleet.collect { states ->
                    val state = states.firstOrNull { it.device.deviceId == deviceId }
                    if (state == null) {
                        // The cap was removed from the fleet - nothing to show.
                        parentFragmentManager.popBackStack()
                        return@collect
                    }
                    render(state)
                }
            }
        }

        // Heartbeats arrive ~1/sec, so "last update" would sit frozen between
        // them. Tick separately to keep staleness honest.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    currentState()?.let { renderLinkAge(it) }
                    delay(1_000)
                }
            }
        }
    }

    private fun currentState(): FleetManager.DeviceState? =
        fleet.fleet.value.firstOrNull { it.device.deviceId == deviceId }

    private fun render(state: FleetManager.DeviceState) {
        binding.tvDeviceName.text = state.device.label()
        val hb = state.heartbeat
        val live = state.isConnected && !state.isStale

        if (hb?.recording == true) {
            binding.tvRecordingState.setText(R.string.caprec_state_recording)
            binding.vRecordingDot.setBackgroundResource(R.drawable.bg_circle_yellow)
            binding.btnStop.isEnabled = !stopRequested && live
        } else {
            binding.tvRecordingState.setText(R.string.caprec_state_idle)
            binding.vRecordingDot.setBackgroundResource(R.drawable.bg_circle_dark)
            // Stopped - by our command, on its own, or from a fault. Either way
            // this screen no longer has a recording to control.
            if (stopRequested && hb != null && !hb.recording) {
                Toast.makeText(requireContext(), R.string.caprec_stopped, Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
                return
            }
            binding.btnStop.isEnabled = false
        }

        binding.tvElapsed.text = formatDuration(hb?.sessionSec ?: 0)
        binding.tvSegments.text = ((hb?.segmentIndex ?: -1) + 1).toString()
        binding.tvFreeSpace.text = hb?.freeMb?.let(::formatFreeSpace)
            ?: getString(R.string.caprec_dash)
        binding.tvTemperature.text = hb?.socTempC?.let { "$it°C" }
            ?: getString(R.string.caprec_dash)

        val taskId = hb?.taskId.orEmpty()
        binding.tvTaskName.text =
            if (taskId.isNotEmpty()) getString(R.string.caprec_task_line, taskId) else ""

        binding.tvSessionId.text = getString(
            R.string.caprec_session_line,
            hb?.sessionId?.ifEmpty { "—" } ?: "—"
        )
        binding.tvOwner.text = getString(
            R.string.caprec_owner_line,
            when (hb?.owner) {
                "phone" -> getString(R.string.caprec_owner_phone)
                "button" -> getString(R.string.caprec_owner_button)
                else -> getString(R.string.caprec_owner_none)
            }
        )

        renderWarnings(hb, live)
        renderLinkAge(state)
    }

    private fun renderWarnings(hb: CyberCapHeartbeat?, live: Boolean) {
        val refusal = currentState()?.activeRefusal
        val warnings = mutableListOf<String>()
        when {
            refusal != null && !refusal.ok ->
                warnings += getString(R.string.caprec_stop_refused, refusal.detail)
            hb != null && live -> {
                if (!hb.sdMounted) warnings += getString(R.string.caprec_warn_no_sd)
                hb.freeMb?.let { if (it < LOW_SPACE_MB) warnings += getString(R.string.caprec_warn_space) }
                hb.socTempC?.let { if (it >= HOT_TEMP_C) warnings += getString(R.string.caprec_warn_temp, it) }
            }
        }
        if (warnings.isEmpty()) {
            binding.tvWarning.visibility = View.GONE
        } else {
            binding.tvWarning.visibility = View.VISIBLE
            binding.tvWarning.text = warnings.joinToString("\n")
        }
    }

    private fun renderLinkAge(state: FleetManager.DeviceState) {
        val hb = state.heartbeat
        if (hb == null) {
            binding.tvLastBeat.text = getString(R.string.caprec_no_updates)
            binding.tvLinkBanner.visibility = View.VISIBLE
            return
        }
        val ageMs = System.currentTimeMillis() - hb.receivedAtMs
        binding.tvLastBeat.text = getString(R.string.caprec_last_update, (ageMs / 1000).toInt())
        val stale = ageMs > FleetManager.STALE_AFTER_MS
        binding.tvLinkBanner.visibility = if (stale) View.VISIBLE else View.GONE
        if (stale) binding.btnStop.isEnabled = false
    }

    private fun confirmStop() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.caprec_stop_title)
            .setMessage(R.string.caprec_stop_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.caprec_stop_recording) { _, _ ->
                stopRequested = true
                binding.btnStop.isEnabled = false
                if (fleet.stopRecording(deviceId)) {
                    Toast.makeText(requireContext(), R.string.caprec_stopping, Toast.LENGTH_SHORT).show()
                } else {
                    stopRequested = false
                    binding.btnStop.isEnabled = true
                    Toast.makeText(requireContext(), R.string.caprec_send_failed, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun formatDuration(totalSec: Int): String =
        String.format("%02d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)

    private fun formatFreeSpace(freeMb: Long): String =
        if (freeMb >= 1024) String.format("%.1f GB", freeMb / 1024.0) else "$freeMb MB"

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "device_id"
        private const val LOW_SPACE_MB = 2048L
        private const val HOT_TEMP_C = 80

        fun newInstance(deviceId: String) = CapRecordingFragment().apply {
            arguments = Bundle().apply { putString(ARG_DEVICE_ID, deviceId) }
        }
    }
}
