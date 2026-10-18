package com.example.moneymatev2

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import com.example.moneymatev2.core.di.SyncScheduler
import com.example.moneymatev2.core.reminder.ReminderAuthSessionManager
import javax.inject.Inject


@HiltAndroidApp
class App: Application(), Configuration.Provider{
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderAuthSessionManager:
            ReminderAuthSessionManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject lateinit var authSyncSessionManager: com.example.moneymatev2.data.remote.sync.AuthSyncSessionManager

    override fun onCreate() {

        super.onCreate()
        com.example.moneymatev2.core.util.NightModeSync.applySavedModeOnStartup(this)
        SyncScheduler.schedulePeriodicSync(this)
        reminderAuthSessionManager.start()
        authSyncSessionManager.start()
    }
}