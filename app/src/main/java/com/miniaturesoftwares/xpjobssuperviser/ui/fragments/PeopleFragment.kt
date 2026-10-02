package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentPeopleBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.PeopleAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.PeopleViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Recording staff this supervisor is responsible for.
 *
 * Backed by [com.miniaturesoftwares.xpjobssuperviser.network.supervisor.SupervisorApi],
 * which is stubbed until the backend ships the endpoints in
 * docs/SUPERVISOR_API.md. The screen does not know or care which it is talking
 * to.
 */
@AndroidEntryPoint
class PeopleFragment : Fragment() {

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PeopleViewModel by viewModels()
    private lateinit var adapter: PeopleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PeopleAdapter()
        binding.rvPeople.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPeople.adapter = adapter

        binding.tabAll.setOnClickListener { viewModel.setFilter(PeopleViewModel.Filter.ALL) }
        binding.tabActive.setOnClickListener { viewModel.setFilter(PeopleViewModel.Filter.ACTIVE) }
        binding.tabIdle.setOnClickListener { viewModel.setFilter(PeopleViewModel.Filter.IDLE) }
        binding.tabOffline.setOnClickListener { viewModel.setFilter(PeopleViewModel.Filter.OFFLINE) }

        binding.etSearch.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }

        viewModel.staff.observe(viewLifecycleOwner) { staff ->
            adapter.submit(staff)
//            binding.tvEmpty.visibility = if (staff.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it == true) View.VISIBLE else View.GONE
        }
        viewModel.filter.observe(viewLifecycleOwner) { render(it) }

        viewModel.fetch()
    }

    private fun render(filter: PeopleViewModel.Filter) {
        fun style(tab: TextView, selected: Boolean) {
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            tab.setTextColor(
                resources.getColor(if (selected) R.color.white else R.color.red_400, null)
            )
        }
        style(binding.tabAll, filter == PeopleViewModel.Filter.ALL)
        style(binding.tabActive, filter == PeopleViewModel.Filter.ACTIVE)
        style(binding.tabIdle, filter == PeopleViewModel.Filter.IDLE)
        style(binding.tabOffline, filter == PeopleViewModel.Filter.OFFLINE)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
