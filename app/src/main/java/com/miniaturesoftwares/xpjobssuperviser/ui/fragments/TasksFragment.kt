package com.miniaturesoftwares.xpjobssuperviser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.databinding.FragmentTasksBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.TagSuggestionAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.TaskAdapter
import com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels.TasksViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TasksViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var suggestionAdapter: TagSuggestionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskAdapter = TaskAdapter()
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = taskAdapter

        setupSearch()
        setupTabs()

        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submit(tasks)
            binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.searchResults.observe(viewLifecycleOwner) { tags ->
            suggestionAdapter.submit(tags)
            renderSuggestions()
        }
        viewModel.selectedWorkType.observe(viewLifecycleOwner) { tag ->
            if (tag == null) {
                binding.layoutFilterChip.visibility = View.GONE
            } else {
                binding.layoutFilterChip.visibility = View.VISIBLE
                binding.tvFilterChip.text = getString(R.string.tasks_filtering_by, tag.workType)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Which tasks exist depends on the categories chosen elsewhere, and
        // that choice is applied by the backend - so refetch on return rather
        // than showing the previous selection's list.
        viewModel.refresh()
    }

    private fun setupSearch() {
        suggestionAdapter = TagSuggestionAdapter { tag ->
            viewModel.setSelectedWorkType(tag)
            binding.etSearch.setText(tag.workType)
            binding.etSearch.setSelection(tag.workType.length)
            hideSuggestions()
            binding.etSearch.clearFocus()
            hideKeyboard()
            // Mirroring the tag into the box re-ran the search for that exact
            // tag, which left the dropdown holding only the chosen one.
            viewModel.reloadAllWorkTypes()
        }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuggestions.adapter = suggestionAdapter

        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.btnClearSearch.visibility =
                if (query.isEmpty()) View.GONE else View.VISIBLE
            viewModel.onSearchTextChanged(query)
        }
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) renderSuggestions() else hideSuggestions()
        }
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.clearSearch()
        }
        binding.btnClearFilterChip.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.clearSearch()
        }
    }

    private fun setupTabs() {
        binding.tabActive.setOnClickListener { selectTab(true) }
        binding.tabCompleted.setOnClickListener { selectTab(false) }
    }

    private fun selectTab(active: Boolean) {
        viewModel.setTabSelected(active)
        binding.tabActive.setBackgroundResource(
            if (active) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
        )
        binding.tabCompleted.setBackgroundResource(
            if (active) R.drawable.bg_tab_unselected else R.drawable.bg_tab_selected
        )
        binding.tabActive.setTextColor(
            resources.getColor(if (active) R.color.white else R.color.red_400, null)
        )
        binding.tabCompleted.setTextColor(
            resources.getColor(if (active) R.color.red_400 else R.color.white, null)
        )
    }

    private fun renderSuggestions() {
        val results = viewModel.searchResults.value.orEmpty()
        binding.rvSuggestions.visibility =
            if (binding.etSearch.hasFocus() && results.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun hideSuggestions() {
        binding.rvSuggestions.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
