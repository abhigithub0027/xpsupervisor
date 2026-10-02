package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentAvailableTasksBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.StartTaskDeviceSheet
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.TaskAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.TasksViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen "View All" task list. Reuses [TasksViewModel] (same list, tabs and
 * caching as the Tasks tab). Tapping a task opens the multi-device Start sheet.
 */
@AndroidEntryPoint
class AvailableTasksFragment : Fragment() {

    private var _binding: FragmentAvailableTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TasksViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAvailableTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TaskAdapter { task ->
            StartTaskDeviceSheet.newInstance(task.taskId, task.title)
                .show(childFragmentManager, "start_task")
        }
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = adapter

        binding.tvTabActive.setOnClickListener { viewModel.setTabSelected(true) }
        binding.tvTabCompleted.setOnClickListener { viewModel.setTabSelected(false) }

        viewModel.isActiveSelected.observe(viewLifecycleOwner) { active ->
            styleTab(binding.tvTabActive, active == true)
            styleTab(binding.tvTabCompleted, active == false)
        }
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            adapter.submit(tasks)
            binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it == true) View.VISIBLE else View.GONE
        }
    }

    private fun styleTab(tv: TextView, selected: Boolean) {
        val attr = if (selected) R.attr.primaryTextColor else R.attr.secondaryTextColor
        tv.setTextColor(MaterialColors.getColor(tv, attr))
        tv.alpha = if (selected) 1f else 0.7f
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
