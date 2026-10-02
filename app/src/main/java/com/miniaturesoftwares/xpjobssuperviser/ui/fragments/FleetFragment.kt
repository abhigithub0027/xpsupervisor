package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.ui.FleetAdapter
import com.miniaturesoftwares.xpjobssuperviser.cybercap.BleReadiness
import com.miniaturesoftwares.xpjobssuperviser.cybercap.DeviceQrPayload
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.ui.DeviceStorageActivity
import com.miniaturesoftwares.xpjobssuperviser.cybercap.PortraitCaptureActivity
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentFleetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The supervisor's whole app, for now: every cap at once.
 *
 * Deliberately a single screen. A supervisor's question is "is the fleet
 * recording", not "what is device 4 doing" - so the list answers the first, and
 * per-device control sits inside each row rather than behind navigation.
 */
@AndroidEntryPoint
class FleetFragment : Fragment() {

    private var _binding: FragmentFleetBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FleetAdapter

    @Inject
    lateinit var fleet: FleetManager

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) {
            toast(getString(R.string.fleet_scan_cancelled))
            return@registerForActivityResult
        }
        onScanned(contents)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { continueAddFlow() }

    private val btPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { continueAddFlow() }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { continueAddFlow() }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { continueAddFlow() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentFleetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FleetAdapter(
            onAction = { state ->
                if (state.canStop()) {
                    if (!fleet.stopRecording(state.device.deviceId)) toastUnreachable(state)
                } else {
                    // taskId is empty here: a supervisor starts a cap, and the
                    // task it belongs to is assigned by whoever is wearing it.
                    if (!fleet.startRecording(state.device.deviceId, "", null)) toastUnreachable(state)
                }
            },
            onLongPress = { showDeviceMenu(it) },
            onOpen = { openRecording(it) },
            onOpenStorage = { state ->
                startActivity(
                    DeviceStorageActivity.intent(
                        requireContext(),
                        state.device.deviceId,
                        state.device.label(),
                    )
                )
            },
        )
        binding.rvFleet.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFleet.adapter = adapter

        binding.btnAddDevice.setOnClickListener { showAddDeviceDialog() }
        binding.btnRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(com.miniaturesoftwares.xpjobssuperviser.R.id.container, DevicesFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.btnStartAll.setOnClickListener {
            fleet.startAll("", null) { started ->
                requireActivity().runOnUiThread { toast(getString(R.string.fleet_started_n, started.size)) }
            }
        }
        binding.btnStopAll.setOnClickListener {
            fleet.stopAll { stopped ->
                requireActivity().runOnUiThread { toast(getString(R.string.fleet_stopped_n, stopped.size)) }
            }
        }

        // The device's own answer to a command. Without this a refusal looks
        // identical to the button doing nothing at all.
        fleet.onAck = { deviceId, ack ->
            if (!ack.ok) {
                val name = fleet.fleet.value
                    .firstOrNull { it.device.deviceId == deviceId }?.device?.label() ?: deviceId
                requireActivity().runOnUiThread {
                    toast(getString(R.string.fleet_refused_toast, name, ack.detail))
                }
            }
        }

        observeFleet()
    }

    override fun onStart() {
        super.onStart()
        fleet.connectAll()
    }

    override fun onDestroyView() {
        fleet.onAck = null
        _binding = null
        super.onDestroyView()
    }

    private fun observeFleet() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fleet.fleet.collect { states ->
                    adapter.submit(states)
                    binding.layoutEmpty.visibility =
                        if (states.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvSubtitle.text = getString(
                        R.string.fleet_summary, fleet.connectedCount, fleet.recordingCount
                    )
                }
            }
        }
    }

    // ---------------- adding a device ----------------

    /**
     * Same readiness checklist as the recorder app: resolve one prerequisite at
     * a time, re-entering here after each, before the camera ever opens.
     */
    private fun continueAddFlow() {
        when (BleReadiness.missing(requireContext()).firstOrNull()) {
            null -> launchScanner()
            BleReadiness.Requirement.CAMERA_PERMISSION ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            BleReadiness.Requirement.BLUETOOTH_PERMISSION ->
                btPermissionLauncher.launch(BleReadiness.requiredPermissions())
            BleReadiness.Requirement.BLUETOOTH_DISABLED ->
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            BleReadiness.Requirement.LOCATION_SERVICES_DISABLED ->
                locationSettingsLauncher.launch(
                    Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
        }
    }

    private fun launchScanner() {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(getString(R.string.fleet_scan_prompt))
                setBeepEnabled(false)
                setCaptureActivity(PortraitCaptureActivity::class.java)
                setOrientationLocked(true)
            }
        )
    }

    private fun onScanned(contents: String) {
        val payload = DeviceQrPayload.parse(contents)
        if (payload == null) {
            toast(getString(R.string.fleet_not_cybercap))
            return
        }
        addToFleet(payload)
    }

    /**
     * Add a cap by typing its device ID, as an alternative to scanning the
     * sticker. The field also accepts a full cybercap:// URL pasted in, so a
     * copied QR payload works too. "Scan QR" drops back to the camera flow.
     */
    private fun showAddDeviceDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_device_manual, null)
        val etId = view.findViewById<EditText>(R.id.etDeviceId)
        val etName = view.findViewById<EditText>(R.id.etDeviceName)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_device_title)
            .setView(view)
            .setNeutralButton(R.string.add_device_scan) { _, _ -> continueAddFlow() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_device_connect) { _, _ ->
                val raw = etId.text.toString().trim()
                if (raw.isEmpty()) {
                    toast(getString(R.string.add_device_id_required))
                    return@setPositiveButton
                }
                val name = etName.text.toString().trim()
                // Accept either a bare device_id or a pasted cybercap:// sticker URL.
                val payload = DeviceQrPayload.parse(raw) ?: DeviceQrPayload(raw, name)
                addToFleet(payload)
            }
            .show()
    }

    private fun addToFleet(payload: DeviceQrPayload) {
        when (fleet.addDevice(payload)) {
            FleetManager.AddResult.Added -> {
                toast(getString(R.string.fleet_added, payload.label()))
                // Past the platform's link cap the newcomer will sit waiting;
                // say so now rather than let it look broken.
                if (fleet.fleet.value.size > FleetManager.MAX_CONCURRENT_LINKS) {
                    toast(getString(R.string.fleet_slot_limit, FleetManager.MAX_CONCURRENT_LINKS))
                }
            }
            FleetManager.AddResult.AlreadyPaired -> toast(getString(R.string.fleet_already_paired))
            FleetManager.AddResult.BluetoothOff -> toast(getString(R.string.fleet_bt_off))
            FleetManager.AddResult.MissingPermission ->
                toast(getString(R.string.fleet_permission_needed))
        }
    }

    private fun openRecording(state: FleetManager.DeviceState) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, CapRecordingFragment.newInstance(state.device.deviceId))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Long-press menu for one cap.
     *
     * Long-press used to remove the device outright. That is a destructive
     * action to reach by accident, and it left the storage view with no entry
     * point at all, so both now sit behind a chooser.
     */
    private fun showDeviceMenu(state: FleetManager.DeviceState) {
        val labels = arrayOf(
            getString(R.string.device_storage),
            getString(R.string.fleet_remove_confirm),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(state.device.label().ifBlank { state.device.deviceId })
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> startActivity(
                        DeviceStorageActivity.intent(
                            requireContext(),
                            state.device.deviceId,
                            state.device.label(),
                        )
                    )
                    1 -> confirmRemove(state)
                }
            }
            .show()
    }

    private fun confirmRemove(state: FleetManager.DeviceState) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.fleet_remove_title)
            .setMessage(getString(R.string.fleet_remove_body, state.device.label()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.fleet_remove_confirm) { _, _ ->
                fleet.removeDevice(state.device.deviceId)
            }
            .show()
    }

    private fun toastUnreachable(state: FleetManager.DeviceState) =
        toast("${state.device.label()} is not reachable")

    private fun toast(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
}
