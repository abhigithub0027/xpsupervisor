package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.ApiService
import com.miniaturesoftwares.xpjobssuperviser.network.DashboardRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _dashboard = MutableLiveData<DashboardRes?>()
    val dashboard: LiveData<DashboardRes?> get() = _dashboard

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    fun fetch() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                withContext(Dispatchers.IO) { api.getDashboard() }
            }.onSuccess { response ->
                if (response.isSuccessful) _dashboard.value = response.body()
                else error.value = "Could not load the dashboard (${response.code()})"
            }.onFailure {
                error.value = it.message
            }
            loading.value = false
        }
    }
}
