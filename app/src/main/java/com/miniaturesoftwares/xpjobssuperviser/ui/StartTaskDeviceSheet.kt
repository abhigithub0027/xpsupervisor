package com.miniaturesoftwares.xpjobssuperviser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Task-first, multi-device start: the supervisor already chose a task, this
 * sheet chooses which caps run it. Only connected caps are listed; the ones
 * that can actually start ([FleetManager.DeviceState.canStart]) are pre-ticked,
 * busy/quiet ones are shown disabled so the supervisor can see why.
 *
 * The BLE command is the one that already exists — [FleetManager.startRecording]
 * with a real `taskId` — so this screen only fans it out and reports back.
 */
@AndroidEntryPoint
class StartTaskDeviceSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var fleet: FleetManager

    private val taskId get() = requireArguments().getString(ARG_TASK_ID).orEmpty()
    private val taskName get() = requireArguments().getString(ARG_TASK_NAME).orEmpty()

    /** deviceId -> its checkbox, for the startable caps only. */
    private val checks = linkedMapOf<String, CheckBox>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_start_task_devices, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvTaskName).text = taskName
        val list = view.findViewById<LinearLayout>(R.id.llDevices)
        val cbAll = view.findViewById<CheckBox>(R.id.cbSelectAll)
        val btnStart = view.findViewById<Button>(R.id.btnStart)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)

        val connected = fleet.fleet.value.filter { it.isConnected }
        if (connected.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            cbAll.visibility = View.GONE
            btnStart.isEnabled = false
            btnStart.alpha = 0.5f
            return
        }

        connected.forEach { state ->
            val startable = state.canStart()
            val status = when {
                state.isRecording -> "recording"
                state.isStale -> "no signal"
                startable -> "ready"
                else -> "connecting…"
            }
            val cb = CheckBox(requireContext()).apply {
                text = "${state.device.label()}  ·  $status"
                isEnabled = startable
                isChecked = startable
                textSize = 14f
                setOnCheckedChangeListener { _, _ -> updateButton(btnStart) }
            }
            list.addView(cb)
            if (startable) checks[state.device.deviceId] = cb
        }

        cbAll.setOnCheckedChangeListener { _, isChecked ->
            checks.values.forEach { it.isChecked = isChecked }
        }
        cbAll.isChecked = checks.isNotEmpty()

        btnStart.setOnClickListener { start(btnStart) }
        updateButton(btnStart)
    }

    private fun selectedIds(): List<String> =
        checks.filter { it.value.isChecked }.keys.toList()

    private fun updateButton(btn: Button) {
        val n = selectedIds().size
        btn.isEnabled = n > 0
        btn.alpha = if (n > 0) 1f else 0.5f
        btn.text = getString(R.string.start_task_button, n)
    }

    private fun start(btn: Button) {
        val ids = selectedIds()
        if (ids.isEmpty()) {
            toast(getString(R.string.start_task_none_selected))
            return
        }
        btn.isEnabled = false

        var sent = 0
        ids.forEach { if (fleet.startRecording(it, taskId, null)) sent++ }
        toast(getString(R.string.start_task_sending, sent))

        // The cap's own heartbeat is the authority on whether it actually began
        // recording; give it a moment to flip, then report and close.
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2500)
            if (!isAdded) return@launch
            val recording = fleet.fleet.value.count { it.device.deviceId in ids && it.isRecording }
            toast(getString(R.string.start_task_result, recording, ids.size))
            dismissAllowingStateLoss()
        }
    }

    private fun toast(text: String) =
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val ARG_TASK_ID = "task_id"
        private const val ARG_TASK_NAME = "task_name"

        fun newInstance(taskId: String, taskName: String) = StartTaskDeviceSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TASK_ID, taskId)
                putString(ARG_TASK_NAME, taskName)
            }
        }
    }
}
