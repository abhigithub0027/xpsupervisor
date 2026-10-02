package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.DeviceRecord
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.SupervisorApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The registered device roster.
 *
 * Distinct from the Fleet tab: this is what the *backend* knows about every cap
 * the supervisor owns, including ones not currently in Bluetooth range. Fleet
 * shows live links; this shows the register.
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val api: SupervisorApi,
) : ViewModel() {

    private val _devices = MutableLiveData<List<DeviceRecord>>(emptyList())
    val devices: LiveData<List<DeviceRecord>> get() = _devices

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    fun fetch() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching { api.getDevices() }
                .onSuccess { response ->
                    if (response.isSuccessful) _devices.value = response.body()?.devices.orEmpty()
                    else error.value = "Could not load devices (${response.code()})"
                }
                .onFailure { error.value = it.message }
            loading.value = false
        }
    }
}
