package com.example.moneymatev2.data.remote.sync

import android.content.Context
import com.example.moneymatev2.core.di.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncTrigger @Inject constructor(
    @ApplicationContext private val context: Context
){
    fun requestImmediateSync(){
        SyncScheduler.triggerImmediateSync(context)
    }
}