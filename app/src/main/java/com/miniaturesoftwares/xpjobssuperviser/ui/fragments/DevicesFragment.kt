package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentDevicesBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.DeviceAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.DevicesViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The registered fleet, as the backend knows it.
 *
 * Distinct from the Fleet tab on purpose: Fleet is live BLE links, this is the
 * register - including caps that are nowhere near this phone. Each row is
 * cross-referenced against the live fleet so "in range" is answered by
 * Bluetooth rather than by a server timestamp.
 */
@AndroidEntryPoint
class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    @Inject
    lateinit var fleet: FleetManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeviceAdapter(inRange = { deviceId ->
            fleet.fleet.value.any { it.device.deviceId == deviceId && it.isConnected }
        })
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            adapter.submit(devices)
            binding.tvEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it == true) View.VISIBLE else View.GONE
        }

        viewModel.fetch()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
