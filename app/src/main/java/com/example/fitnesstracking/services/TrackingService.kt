package com.example.fitnesstracking.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.example.fitnesstracking.R
import com.example.fitnesstracking.other.Constants.ACTION_PAUSE_SERVICE
import com.example.fitnesstracking.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.fitnesstracking.other.Constants.ACTION_STOP_SERVICE
import com.example.fitnesstracking.other.Constants.FASTEST_LOCATION_INTERVAL
import com.example.fitnesstracking.other.Constants.LOCATION_UPDATE_INTERVAL
import com.example.fitnesstracking.other.Constants.NOTIFICATION_CHANNEL_ID
import com.example.fitnesstracking.other.Constants.NOTIFICATION_CHANNEL_NAME
import com.example.fitnesstracking.other.Constants.NOTIFICATION_ID
import com.example.fitnesstracking.other.Constants.TIMER_UPDATE_INTERVAL
import com.example.fitnesstracking.other.TrackingUtility
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

//these 2 are the list of list of coordinates, wee need them this way because we want to show when
//user interrupt the tracking, the actual object would be like this:
//val pathPoint = MutableLiveData<MutableList<MutableList<LatLng>>>()
typealias  Polyline = MutableList<LatLng>
typealias  Polylines = MutableList<Polyline>

@AndroidEntryPoint
class TrackingService : LifecycleService(), SensorEventListener {

    private var isFirstRun = true
    private var serviceKilled = false

    //we need this for request PRIORITY_HIGH_ACCURACY, we inject FusedLocationProviderClient with dagger and ServiceModule
    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    //we use this for the timer
    private val timeRunInSeconds = MutableLiveData<Long>()

    //we inject the notification with dagger and ServiceModule
    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    lateinit var curNotificationBuilder: NotificationCompat.Builder //we use this to update the notification


    //steps counter variables
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var initialStepCount: Int = 0
    private var currentStepCount: Int = 0

    //private val _steps = MutableLiveData<Int>()
    //public val steps: LiveData<Int> = _steps



    /*-----------------------------------OBJECTS WE WANT TO OBSERVE-------------------------------*/

    //parte del tracking
    companion object{
        //inside the companion because we want to observe this
        val timeRunInMillis = MutableLiveData<Long>()
        //boolean if we are tracking or not
        val isTracking = MutableLiveData<Boolean>()
        //list of coordinates
        val pathPoint = MutableLiveData<Polylines>()
        //stes
        val steps = MutableLiveData<Int>()
    }

    /*------------------------------------ON CREATE-----------------------------------------------*/

    //this will start the tracking and observer
    override fun onCreate() {
        super.onCreate()
        //step counter
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)


        //tracking
        curNotificationBuilder = baseNotificationBuilder
        postInitialValues()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        isTracking.observe(this, Observer {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        })

    }


    /*--------------------------INITIALIZE VARIABLES AND LISTS------------------------------------*/

    //initialize data in the pathPoint, isTracking for tracking
    // and timeRunInSeconds , timeRunInMillis for the timer
    private fun postInitialValues(){
        //steps
        steps.postValue(0)
        //tracking
        isTracking.postValue(false)
        pathPoint.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeRunInMillis.postValue(0L)
    }

    //add empty data in the list
    private fun addEmptyPolyline() = pathPoint.value?.apply {
        add(mutableListOf())//empty list
        pathPoint.postValue(this) //because there is a change we want to notify the list
    } ?: pathPoint.postValue(mutableListOf(mutableListOf()))


    /*----------------------------------NOTIFICATIONS---------------------------------------------*/


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager){
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    //function to update notification
    private fun updateNotificationTrackingState(isTracking: Boolean){
        val notificationActionText = if(isTracking) "Pause" else "Resume"
        val pendingIntent = if(isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        }
        else{
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        curNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(curNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }
        if(!serviceKilled){
            curNotificationBuilder = baseNotificationBuilder
                .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, curNotificationBuilder.build())
        }
    }



    /*------------------------------METHODS TO TRACK THE USER-------------------------------------*/

    //fun that add the coordinates
    private fun addPathPoint(location: Location?){
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoint.value?.apply {
                last().add(pos)
                pathPoint.postValue(this)  //notify the change
             }
        }
    }

    //request location updates and results
    private val locationCallback = object  : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if(isTracking.value!!){
                result.locations.let { locations ->
                    for(location in locations){
                        addPathPoint(location)
                        Timber.d("NEW LOCATION ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    //update our location tracking
    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking:Boolean){
        if(isTracking){
            if(TrackingUtility.hasLocationPermission(this)){
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                    .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
                    .build()

                fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        }
        else{
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }



    /*------------------------------TIMER SERVICE--------------------------------------------------*/




    private var isTimerEnable = false
    private var lapTime = 0L //we will add time to this variable
    private var timeRun = 0L //total time
    private var timeStarted = 0L //timestamp when we start the timer
    private var lastSecondTimesStamp = 0L

    //called inside the StartCommand when the service receive the commands from the TrackingFragment
    private fun startTimer(){
        //we clear the list at the beginning of the list
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnable = true
        //now we will use Coroutine to optimize the observer
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!){
                //time difference between now and timeStarted
                lapTime = System.currentTimeMillis() - timeStarted
                //post the new lapTime
                timeRunInMillis.postValue(timeRun + lapTime)
                if (timeRunInMillis.value!! >= lastSecondTimesStamp + 1000L){
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimesStamp += 1000L
                }
               //we need to delay the Coroutine so we don't need to update our Observer and LiveData all the time
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }



    /*-------------------COMMANDS METHODS (launched from the TrackingFragment)--------------------*/

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            when(it.action){
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun){
                        startForegroundService()
                        isFirstRun =false
                        Timber.d("Started service")
                    }
                    else{
                        startForegroundService()
                        Timber.d("Resumed service")

                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    pauseService()
                    Timber.d("Paused service")
                }
                ACTION_STOP_SERVICE -> {
                    killService()
                    Timber.d("Stopped service")
                }
            }
        }


        return super.onStartCommand(intent, flags, startId)
    }




    //this start the foreground service an create the notification
    private fun startForegroundService(){
        //step counter start
        startStepCounting()
        //we start timer
        startTimer()
        //we start tracking
        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        //use the observer to update the notification each seconds
        timeRunInSeconds.observe(this, Observer {
            if (!serviceKilled){
                val notification = curNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        })
    }

    //pause the foreground service
    private fun pauseService(){
        stopStepCounting()
        isTracking.postValue(false)
        isTimerEnable = false
    }


    //function to kill the service (when we cancel the run)
    private fun killService(){
        serviceKilled = true
        isFirstRun = true
        pauseService()
        resetSteps()
        postInitialValues()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //for Android 13 (API 33) and successive versions
            stopForeground(STOP_FOREGROUND_REMOVE) // stop foreground and remove notification
        } else {
            // Previous Versions
            @Suppress("DEPRECATION")
            stopForeground(true) // use the deprecated method
        }
        stopSelf()
    }

    /*---------------------------------STEP COUNTER METHODS---------------------------------------*/

    private fun startStepCounting() {
        stepSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopStepCounting() {
        sensorManager.unregisterListener(this)
    }

    private fun resetSteps() {
        initialStepCount = currentStepCount
        steps.postValue(0)
    }


    override fun onSensorChanged(event: SensorEvent) {
        if (isTracking.value == true && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            if (initialStepCount == 0) {
                initialStepCount = event.values[0].toInt()
            }
            currentStepCount = event.values[0].toInt()
            val stepCount = currentStepCount - initialStepCount
            steps.postValue(stepCount)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStepCounting()
    }


}



