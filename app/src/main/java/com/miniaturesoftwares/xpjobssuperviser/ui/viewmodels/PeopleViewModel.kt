package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.StaffMember
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.SupervisorApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val api: SupervisorApi,
) : ViewModel() {

    enum class Filter(val apiValue: String?) {
        ALL(null), ACTIVE("active"), IDLE("idle"), OFFLINE("offline")
    }

    private val _staff = MutableLiveData<List<StaffMember>>(emptyList())
    val staff: LiveData<List<StaffMember>> get() = _staff

    private val _filter = MutableLiveData(Filter.ALL)
    val filter: LiveData<Filter> get() = _filter

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    private var query: String = ""

    fun setFilter(filter: Filter) {
        _filter.value = filter
        fetch()
    }

    fun search(text: String) {
        query = text
        fetch()
    }

    fun fetch() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                api.getStaff(_filter.value?.apiValue, query.takeIf { it.isNotBlank() })
            }.onSuccess { response ->
                if (response.isSuccessful) _staff.value = response.body()?.staff.orEmpty()
                else error.value = "Could not load staff (${response.code()})"
            }.onFailure { error.value = it.message }
            loading.value = false
        }
    }
}
