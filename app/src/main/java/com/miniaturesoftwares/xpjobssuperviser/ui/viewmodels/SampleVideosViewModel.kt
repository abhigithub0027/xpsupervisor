package com.miniaturesoftwares.xpjobssuperviser.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniaturesoftwares.xpjobssuperviser.network.ApiService
import com.miniaturesoftwares.xpjobssuperviser.network.SampleVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SampleVideosViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _videos = MutableLiveData<List<SampleVideo>>(emptyList())
    val videos: LiveData<List<SampleVideo>> get() = _videos

    val loading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    fun fetch() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                withContext(Dispatchers.IO) { api.getSampleVideos() }
            }.onSuccess { response ->
                if (response.isSuccessful) _videos.value = response.body().orEmpty()
                else error.value = "Could not load sample videos (${response.code()})"
            }.onFailure {
                error.value = it.message
            }
            loading.value = false
        }
    }
}
