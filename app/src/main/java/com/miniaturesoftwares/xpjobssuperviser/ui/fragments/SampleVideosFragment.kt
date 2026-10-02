package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentSampleVideosBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.SampleVideoGridAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.SampleVideosViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Full-screen "View All" list of every sample video. */
@AndroidEntryPoint
class SampleVideosFragment : Fragment() {

    private var _binding: FragmentSampleVideosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SampleVideosViewModel by viewModels()
    private lateinit var adapter: SampleVideoGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSampleVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SampleVideoGridAdapter { video ->
            video.url.takeIf { it.isNotBlank() }?.let { url ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
        binding.rvVideos.adapter = adapter

        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            adapter.submitList(videos)
            binding.tvEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
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
