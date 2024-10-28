package com.example.fitnesstracking

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree

//this way the app will use hilt dependencies injections
@HiltAndroidApp
class BaseApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())  //enable the log libraries
    }
}