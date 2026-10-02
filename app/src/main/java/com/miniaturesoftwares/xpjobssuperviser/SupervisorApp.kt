package com.miniaturesoftwares.xpjobssuperviser

import android.app.Application
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SupervisorApp : Application() {

    /**
     * MMKV has to be ready before Hilt injects this Application: injection
     * happens inside super.onCreate(), and any singleton reading MMKV in its
     * constructor is built at that moment. Same reason as the recorder app.
     */
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        base?.let { runCatching { MMKV.initialize(it) } }
    }
}
