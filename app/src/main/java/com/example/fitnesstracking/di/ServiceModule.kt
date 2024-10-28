package com.example.fitnesstracking.di

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import com.example.fitnesstracking.other.Constants
import com.example.fitnesstracking.ui.MainActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.fitnesstracking.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @ServiceScoped
    @Provides
    fun provideFusedLocationProviderClient(
        @ApplicationContext app: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(app)
    }

    //this is used to reopen the app when the notification is pressed and it will be
    //reopened in the tracking fragment using our nav_graph.xml global tracking
    @ServiceScoped
    @Provides
    fun provideMainActivityPendingIntent(
        @ApplicationContext app: Context
    ) =    PendingIntent.getActivity(
    app,
    0,
    Intent (app, MainActivity::class.java).also {
        it.action = Constants.ACTION_SHOW_TRACKING_FRAGMENT
    },
        FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
    )


    //this create the actual notification with the standard data, we will update the data while the app is going
    @ServiceScoped
    @Provides
    fun provideBaseNotificationBuilder(
        @ApplicationContext app: Context,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(app, Constants.NOTIFICATION_CHANNEL_ID)
        .setAutoCancel(false)
        .setOngoing(true)
        .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
        .setContentTitle("Fitness Tracking")
        .setContentText("00:00:00")
        .setContentIntent(pendingIntent)



}