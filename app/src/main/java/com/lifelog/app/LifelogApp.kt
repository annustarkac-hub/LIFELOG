package com.lifelog.app

import android.app.Application
import androidx.work.Configuration
import com.lifelog.app.collect.CollectionWorker

class LifelogApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CollectionWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()
}
