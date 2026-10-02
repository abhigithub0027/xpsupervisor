package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.ApiService
import com.miniaturesoftwares.xpjobssuperviser.network.UploadedSessionApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Sessions across the fleet.
 *
 * A supervisor sees uploaded work, not local recordings - unlike the recorder
 * app there is no on-device session directory here, so everything comes from
 * the backend.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    enum class Filter { ALL, PASSED, FAILED }

    private val _all = MutableLiveData<List<UploadedSessionApi>>(emptyList())

    private val _filter = MutableLiveData(Filter.ALL)
    val filter: LiveData<Filter> get() = _filter

    private val _visible = MutableLiveData<List<UploadedSessionApi>>(emptyList())
    val visible: LiveData<List<UploadedSessionApi>> get() = _visible

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    val passedCount: Int get() = _all.value.orEmpty().count { !isFailed(it) }
    val failedCount: Int get() = _all.value.orEmpty().count { isFailed(it) }

    fun setFilter(filter: Filter) {
        _filter.value = filter
        applyFilter()
    }

    fun fetch() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                withContext(Dispatchers.IO) { api.getUploadedSessions("uploaded") }
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    _all.value = response.body()?.sessions.orEmpty()
                    applyFilter()
                } else {
                    error.value = "Could not load sessions (${response.code()})"
                }
            }.onFailure { error.value = it.message }
            loading.value = false
        }
    }

    private fun applyFilter() {
        val all = _all.value.orEmpty()
        _visible.value = when (_filter.value) {
            Filter.PASSED -> all.filterNot(::isFailed)
            Filter.FAILED -> all.filter(::isFailed)
            else -> all
        }
    }

    /** QC verdict is the only signal the backend gives about session quality. */
    private fun isFailed(session: UploadedSessionApi): Boolean =
        session.qcStatusXp?.equals("Fail", ignoreCase = true) == true
}
