package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentHomeBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.StartTaskDeviceSheet
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.RecommendedTaskAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.SampleVideoAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The recorder app's home screen, read through a supervisor's eyes.
 *
 * Same layout, so the two apps look like one product. What differs is what the
 * numbers mean: the stat row is the supervisor's own totals from the dashboard,
 * and the device-link line - which in the recorder app tracks one cap - is
 * repurposed here to summarise the whole fleet.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recommendedAdapter: RecommendedTaskAdapter
    private lateinit var sampleVideoAdapter: SampleVideoAdapter

    @Inject
    lateinit var fleet: FleetManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAppName.text = getString(R.string.home_default_greeting)

        // Tapping a recommended task (card or its Start button) lets the
        // supervisor start it on one or more caps at once.
        recommendedAdapter = RecommendedTaskAdapter { mapping ->
            StartTaskDeviceSheet.newInstance(
                mapping.task.id,
                mapping.task.taskTitle ?: getString(R.string.recommended_task),
            ).show(childFragmentManager, "start_task")
        }
        binding.rvRecommendedTasks.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecommendedTasks.adapter = recommendedAdapter

        sampleVideoAdapter = SampleVideoAdapter { video ->
            video.url.takeIf { it.isNotBlank() }?.let { url ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
        binding.rvSampleVideos.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSampleVideos.adapter = sampleVideoAdapter

        // Nothing in this app scans from Home - the Fleet tab owns pairing -
        // so the scan affordance is hidden rather than left inert.
        binding.ivScanDevice.visibility = View.GONE

        binding.tvViewAllVideos.setOnClickListener { push(SampleVideosFragment()) }
        binding.tvViewAllTasks.setOnClickListener { push(AvailableTasksFragment()) }

        viewModel.dashboard.observe(viewLifecycleOwner) { dash ->
            binding.tvAppName.text = dash?.user?.fullName
                ?: getString(R.string.home_default_greeting)
            binding.tvStatSessions.text = (dash?.stats?.sessionsCount ?: 0).toString()
            binding.tvStatUploaded.text = (dash?.stats?.uploadedCount ?: 0).toString()
            binding.tvStatPoints.text = getString(
                R.string.str_0_points_value, dash?.stats?.pointsEarned ?: 0
            )

            sampleVideoAdapter.submitList(dash?.sampleVideos ?: emptyList())
            recommendedAdapter.submitList(dash?.recommendedTasks ?: emptyList())
        }

        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it == true) View.VISIBLE else View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fleet.fleet.collect { states ->
                    binding.tvDeviceLinkStatus.visibility = View.VISIBLE
                    binding.tvDeviceLinkStatus.text = getString(
                        R.string.home_fleet_summary,
                        states.size, fleet.connectedCount, fleet.recordingCount
                    )
                }
            }
        }

        viewModel.fetch()
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetch()
    }

    private fun push(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
