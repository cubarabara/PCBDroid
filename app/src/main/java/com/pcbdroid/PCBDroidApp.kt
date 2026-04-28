package com.pcbdroid

import android.app.Application
import com.pcbdroid.data.repository.ProjectRepository
import com.pcbdroid.di.AppContainer

// Tidak pakai Hilt — manual DI via AppContainer
class PCBDroidApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        // Helper untuk akses dari mana saja jika diperlukan
        lateinit var instance: PCBDroidApp
            private set
    }

    init {
        instance = this
    }
}
