package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentSessionsBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.SessionAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.SessionsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SessionsFragment : Fragment() {

    private var _binding: FragmentSessionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SessionsViewModel by viewModels()
    private lateinit var adapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SessionAdapter()
        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter

        binding.tabAll.setOnClickListener { viewModel.setFilter(SessionsViewModel.Filter.ALL) }
        binding.tabPassed.setOnClickListener { viewModel.setFilter(SessionsViewModel.Filter.PASSED) }
        binding.tabFailed.setOnClickListener { viewModel.setFilter(SessionsViewModel.Filter.FAILED) }

        viewModel.visible.observe(viewLifecycleOwner) { sessions ->
            adapter.submit(sessions)
            binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            // Counts come from the full set, not the filtered one, so the tabs
            // keep saying how much is behind them while a filter is applied.
            binding.tabPassed.text =
                getString(R.string.sessions_passed_n, viewModel.passedCount)
            binding.tabFailed.text =
                getString(R.string.sessions_failed_n, viewModel.failedCount)
        }
        viewModel.filter.observe(viewLifecycleOwner) { render(it) }

        viewModel.fetch()
    }

    private fun render(filter: SessionsViewModel.Filter) {
        fun style(tab: TextView, selected: Boolean) {
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            tab.setTextColor(
                resources.getColor(if (selected) R.color.white else R.color.red_400, null)
            )
        }
        style(binding.tabAll, filter == SessionsViewModel.Filter.ALL)
        style(binding.tabPassed, filter == SessionsViewModel.Filter.PASSED)
        style(binding.tabFailed, filter == SessionsViewModel.Filter.FAILED)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
