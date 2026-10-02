package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.TaskTag
import com.miniaturesoftwares.xpjobssuperviser.network.TasksRes
import com.miniaturesoftwares.xpjobssuperviser.repo.TasksRepo
import com.miniaturesoftwares.xpjobssuperviser.ui.models.AvailableTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Task list with live work-type filtering.
 *
 * Same search semantics as the recorder app - debounced, minimum two
 * characters, instant on clear - so an operator and their supervisor searching
 * the same term get the same results.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TasksRepo,
) : ViewModel() {

    private val _isActiveSelected = MutableLiveData(true)
    val isActiveSelected: LiveData<Boolean> get() = _isActiveSelected

    private val _tasks = MutableLiveData<List<AvailableTask>>()
    val tasks: LiveData<List<AvailableTask>> get() = _tasks

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    private val _searchResults = MutableLiveData<List<TaskTag>>(emptyList())
    val searchResults: LiveData<List<TaskTag>> get() = _searchResults

    private val _selectedWorkType = MutableLiveData<TaskTag?>(null)
    val selectedWorkType: LiveData<TaskTag?> get() = _selectedWorkType

    private var searchJob: Job? = null
    private var lastSearchedText: String? = null

    init {
        searchWorkTags("")
        fetchTasks()
    }

    fun onSearchTextChanged(text: String) {
        if (text == lastSearchedText) return
        searchJob?.cancel()
        if (text.isEmpty()) {
            searchWorkTags("")
        } else if (text.length >= MIN_SEARCH_CHARS) {
            searchJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                searchWorkTags(text)
            }
        }
    }

    private fun searchWorkTags(query: String) {
        if (query == lastSearchedText && !_searchResults.value.isNullOrEmpty()) return
        lastSearchedText = query
        viewModelScope.launch {
            repo.searchTags(query)
                .catch {
                    lastSearchedText = null
                    _searchResults.postValue(emptyList())
                }
                .collect { tags ->
                    // The user may have typed on while this was in flight.
                    if (lastSearchedText == query) _searchResults.postValue(tags)
                }
        }
    }

    fun setTabSelected(isActive: Boolean) {
        _isActiveSelected.value = isActive
        fetchTasks()
    }

    fun setSelectedWorkType(tag: TaskTag?) {
        _selectedWorkType.value = tag
        fetchTasks()
    }

    /**
     * Restore the unfiltered suggestion list.
     *
     * Choosing a suggestion mirrors it into the box, which re-ran the search
     * for that exact tag and left the dropdown holding only the chosen one.
     */
    fun reloadAllWorkTypes() {
        searchJob?.cancel()
        lastSearchedText = null
        searchWorkTags("")
    }

    fun clearSearch() {
        searchJob?.cancel()
        lastSearchedText = null
        _selectedWorkType.value = null
        searchWorkTags("")
        fetchTasks()
    }

    fun refresh() = fetchTasks()

    private fun fetchTasks() {
        val status = if (_isActiveSelected.value == true) "available" else "completed"
        val tag = _selectedWorkType.value
        viewModelScope.launch {
            loading.value = true
            error.value = null
            repo.getTasks(
                tag = null, status = status,
                tagId = tag?.id, tagTitle = tag?.workType,
            ).catch {
                error.postValue(it.message)
                loading.postValue(false)
            }.collect { res: TasksRes ->
                _tasks.postValue(
                    res.tasks.filter { !it.task.taskTitle.isNullOrBlank() }
                        .map { AvailableTask.fromTaskMapping(it) }
                )
                loading.postValue(false)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MIN_SEARCH_CHARS = 2
    }
}
