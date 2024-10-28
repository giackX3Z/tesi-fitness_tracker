package com.example.fitnesstracking.ui.fragments

import com.example.fitnesstracking.other.UploadWorker
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.FragmentTrackingBinding
import com.example.fitnesstracking.other.Constants.ACTION_PAUSE_SERVICE
import com.example.fitnesstracking.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.fitnesstracking.other.Constants.ACTION_STOP_SERVICE
import com.example.fitnesstracking.other.Constants.MAP_ZOOM
import com.example.fitnesstracking.other.Constants.MET
import com.example.fitnesstracking.other.Constants.MET_CONVERSION_TO_KCALXKM
import com.example.fitnesstracking.other.Constants.POLYLINE_COLOR
import com.example.fitnesstracking.other.Constants.POLYLINE_WIDTH
import com.example.fitnesstracking.other.TrackingUtility
import com.example.fitnesstracking.services.Polyline

import com.example.fitnesstracking.services.TrackingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.round


const val CANCEL_TRACKING_DIALOG_TAG = "CancelDialog"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking){


    //user UID
    private lateinit var userId: String

    //user weight used for local processing
    private var userWeight: Double? = null

    //we need these from the trackingService to draw the line
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()

    //google maps map
    private var map: GoogleMap? = null

    //timer
    private var curTimeInMillis = 0L

    // 'X' to abort run
    private var menuGl: Menu?= null

    //step counter
    private var currentSteps = 0


    // Coroutines Job for updating calories and distance runtime
    private var updateJob: Job? = null


    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using View Binding
        _binding = FragmentTrackingBinding.inflate(inflater, container, false)



        //MenuProvider
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Inflates the menu; this adds items to the action bar if it is present.
                menuInflater.inflate(R.menu.toolbar_tracking_menu, menu)
                menuGl = menu
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                //logic that can be use to modify the visibility of the menu
                if (curTimeInMillis > 0L){
                    menuGl?.getItem(1)?.isVisible = true
                }
            }

            //logic to handle menu item selection
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.miCancelTracking -> {
                        showCancelTrackingDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        // Get user id
        this.userId = FirebaseAuth.getInstance().currentUser?.uid.toString()
        println(userId)
        getUserWeight()

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        //use google maps API
        binding.mapView.onCreate(savedInstanceState)
        binding.btnToggleRun.setOnClickListener {
            toggleRun()
        }

        if (savedInstanceState != null){
            val cancelTrackingDialog = parentFragmentManager.findFragmentByTag(
                CANCEL_TRACKING_DIALOG_TAG) as CancelTrackingDialog?
            cancelTrackingDialog?.setYesListener {
                stopRun()
            }
        }

        binding.btnFinishRun.setOnClickListener {
            println(pathPoints)
            binding.progressBar.visibility = View.VISIBLE
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            if (isNetworkAvailable()){
                cloudProcessing()
            }
            else{
                localProcessing()
            }


        }
        //use google maps API
        binding.mapView.getMapAsync {
            map = it
            addAllPolylines()
        }

        subscribeToObserver()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopRepeatingTask() // Stop updating when the view is destroyed
        _binding = null
    }


    /*------------------------------------------------------------------------------------------*/

    //method to show the 'X' to abort run when required
    private fun showCancelTrackingDialog(){
        CancelTrackingDialog().apply {
            setYesListener {
                stopRun()
            }
        }.show(parentFragmentManager, CANCEL_TRACKING_DIALOG_TAG)
    }


    /*--------------------METHODS TO DRAW THE LINE---------------------------*/

    //use the service to draw the line based on the mutableList in the trackingService
    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            // Check if there are at least 2 points to draw the line and if pathPoints is not empty
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2] // second to last LatLng
            val lastLatLng = pathPoints.last().last() // last LatLng
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng) // Adding individual LatLng objects
                .add(lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    //we need this to redraw the line if we rotate screen
    private fun addAllPolylines(){
        for(polyline in pathPoints){
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }


    //update the boolean isTracking to track if we are currently tracking the user or not
    private fun updateTracking(isTracking : Boolean){
        this.isTracking = isTracking
        if(!isTracking && curTimeInMillis > 0L){
            binding.btnToggleRun.text = "Start"
            binding.btnFinishRun.visibility = View.VISIBLE
            stopRepeatingTask()

        } else if(isTracking){
            binding.btnToggleRun.text = "Stop"
            menuGl?.getItem(1)?.isVisible=true
            binding.btnFinishRun.visibility = View.GONE
            startRepeatingTask()
        }
    }

    //move the camera to the new pathpoint
    private fun moveCameraToUser(){
        if(pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()){
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    //zoom out the camera to make a screenshot of the whole run
    private fun zoomOutToSeeWholeTrack(){
        val bounds = LatLngBounds.builder()
        for (polyline in pathPoints){
           for(pos in polyline){
               bounds.include(pos)
           }
        }
        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                binding.mapView.width,
                binding.mapView.height,
                (binding.mapView.height * 0.05f).toInt()
            )
        )

    }



    /*----------------------------------------OBSERVERS------------------------------------------*/


    //this subscribe to observer
    private fun subscribeToObserver(){
        //observe if we are currently tracking or not
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        //observe a change in our mutableList and draw the new piece of line if there is a change
        TrackingService.pathPoint.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()
        })

        //observe the changing time
        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeInMillis, true)
            binding.tvTimer.text = formattedTime
        })

        //observe changing steps
        TrackingService.steps.observe(viewLifecycleOwner, Observer { steps ->
            currentSteps = steps
            updateStepCount(steps)
        })
    }


    private fun updateStepCount(steps: Int) {
        binding.tvStepCounter.text = "Steps: $steps"
    }


    /*--------------------------METHODS TO START/STOP THE TrackingService------------------------*/

    //send the appropriate command to the service
    private fun sendCommandToService(action: String) =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }

    //function that toggle run (setOnClickListener)
    private fun toggleRun(){
        if (isTracking){
            menuGl?.getItem(1)?.isVisible=true
            stopRepeatingTask()
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            startRepeatingTask()
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }


    //stop the service (setOnClickListener)
    private fun stopRun(){
        binding.tvTimer.text = "00:00:00:00"
        binding.tvStepCounter.text = "Steps: 0"
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment)
    }


    /*-----------------------METHODS TO UPLOAD DATA AND CALL CLOUD FUNCTIONS----------------------*/

    // Primary function that end run and send data
    private fun cloudProcessing() {
        zoomOutToSeeWholeTrack()

        // Calculate total distance with cloud function
        getTotalDistance { distanceInMeters ->
            // After getting total distance take the map screenshot
            captureMapSnapshot { bitmap ->
                // Load image on Firebase Storage
                uploadMapSnapshot(bitmap, { imageUrl ->
                    // Send run data to cloud function
                    sendRunDataToCloudFunction(distanceInMeters, imageUrl)
                }, { error ->
                    Toast.makeText(requireContext(), "Loading image error: ${error.message}", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                })
            }
        }
    }

    // Function to calculate total distance with callable cloud function
    private fun getTotalDistance(onDistanceReady: (Int) -> Unit) {
        val simplifiedPathPoints = pathPoints.map { subList ->
            subList.map { latLng ->
                mapOf("latitude" to latLng.latitude, "longitude" to latLng.longitude)
            }
        }

        FirebaseFunctions.getInstance()
            .getHttpsCallable("calculateTotalDistance")
            .call(hashMapOf("coordinates" to simplifiedPathPoints))
            .addOnSuccessListener { result ->
                val data = result.data as Map<*, *>
                val distanceInMeters = (data["distanceInMeters"] as String).toDouble().toInt()
                println("Total Distance in Meters: $distanceInMeters")
                // Callback if distance
                onDistanceReady(distanceInMeters)
            }
            .addOnFailureListener { e ->
                println("Error: ${e.message}")
                Toast.makeText(requireContext(), "Error while calculating distance : ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
    }

    // Function to take the map screenshot
    private fun captureMapSnapshot(onSnapshotReady: (Bitmap) -> Unit) {
        map?.snapshot { bitmap ->
            if (bitmap != null) {
                onSnapshotReady(bitmap)
            } else {
                Toast.makeText(requireContext(), "Error while capturing map screenshot", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }
    }

    // Function to load the screenshot to the firebase storage
    private fun uploadMapSnapshot(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storage = Firebase.storage
        val storageRef = storage.reference
        val imageRef = storageRef.child("runs/${UUID.randomUUID()}.jpg")

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val uploadTask = imageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                onSuccess(uri.toString())
            }.addOnFailureListener { exception ->
                onFailure(exception)
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }.addOnFailureListener { exception ->
            onFailure(exception)
            binding.progressBar.visibility = View.GONE
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    // Function to send run data (distance, timeInMillis, imgURL) to cloud function
    private fun sendRunDataToCloudFunction(totalDistance: Int, imageUrl: String) {
        val totalTime = curTimeInMillis
        Timber.d("Distance: $totalDistance, Time: $totalTime,Image URL: $imageUrl")
        addRunDataToCloudFunction(totalDistance, totalTime, imageUrl, currentSteps, { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }, { error ->
            Toast.makeText(requireContext(), "Error during data upload: ${error.message}", Toast.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        })
    }

    // Function to call the cloud function and stopService when run saved
    private fun addRunDataToCloudFunction(
        distance: Int,
        time: Long,
        imageUrl: String,
        steps: Int,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "distanceInMeters" to distance,
            "timeInMillis" to time,
            "imageUrl" to imageUrl,
            "steps" to steps
        )

        FirebaseFunctions.getInstance()
            .getHttpsCallable("addRun")
            .call(data)
            .addOnSuccessListener {
                onSuccess("Run saved successfully!")
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                stopRun()
            }
            .addOnFailureListener { e ->
                onFailure(e)
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
    }

    /*---------------------------------LOCAL PROCESSING METHODS-----------------------------------*/
    //Called onCreateView
    private fun getUserWeight() {

        // Get user data for db
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    // Get weight and save it
                    val weightString = document.getString("weight")
                    userWeight = weightString?.toDouble()
                    Timber.tag("TrackingFragment").d("User weight: $userWeight")
                } else {
                    Toast.makeText(requireContext(), "You are currently offline, Weight fixed to 80 for the run", Toast.LENGTH_LONG).show()
                    userWeight = 80f.toDouble()
                    Timber.tag("TrackingFragment").d("Doc not found")
                }
            }
            .addOnFailureListener { exception ->
                Timber.tag("TrackingFragment").d(exception, "Error getting data: ")
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
    }

    //Check if internet is available, used to start local processing if not
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }


    private fun calculateSpeedAndCalories(): Triple<Int, Float, Int>{
        var distanceInMeters = 0
        for (polyline in pathPoints){
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
        }
        val avgSpeed = round((distanceInMeters / 1000f) / (curTimeInMillis / 1000f / 60 / 60) * 10 ) / 10f
        val caloriesBurned = ((distanceInMeters/1000f) * (MET * MET_CONVERSION_TO_KCALXKM) * userWeight!!).toInt()

        return Triple(distanceInMeters, avgSpeed, caloriesBurned)
    }



    private fun localProcessing(){

        zoomOutToSeeWholeTrack()

        captureMapSnapshot { bitmap ->
            val (distance, speed, calories) = calculateSpeedAndCalories()


            val file = File(context?.cacheDir, "${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(file)

            // Compresses the bitmap in JPEG and save it
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()


            //val uploadWorkRequest = PeriodicWorkRequestBuilder<com.example.fitnesstracking.other.UploadWorker>(
            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()

                .setInputData(workDataOf(
                    "userId" to userId,
                    "speed" to speed,
                    "calories" to calories,
                    "distance" to distance,
                    "imageUrl" to file.absolutePath,
                    "steps" to currentSteps,
                    "time" to curTimeInMillis,

                ))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                   10,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(
                    "uploadRunData_$userId",
                    ExistingWorkPolicy.APPEND,
                    uploadWorkRequest
                )
            binding.progressBar.visibility = View.GONE
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            Toast.makeText(context, "Internet Error, data will be uploaded later...", Toast.LENGTH_LONG ).show()
            stopRun()
        }
    }




    /*--------------------------GOOGLE MAPS REQUIRED METHODS-====--------------------------------*/


    //use google maps API
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_binding != null) {
            // Save State
            binding.mapView.onSaveInstanceState(outState)
        }
    }




    private fun stopRepeatingTask() {
        updateJob?.cancel() // Stop the coroutine when not tracking
    }

    // Start or stop the coroutine based on isTracking state
    private fun startRepeatingTask() {
        updateJob?.cancel() // Cancel any previous job if it exists
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isTracking) {

                if (isNetworkAvailable()){
                    cloudProcessingRuntime()
                }
                else{
                    localProcessingRuntime()
                }

                // Wait 5 seconds before calling task
                delay(5000L)
            }
        }
    }

    private fun cloudProcessingRuntime(){
        getTotalDistance { distanceInMeters ->
            calculateCaloriesBurnedRuntime(distanceInMeters)
        }
    }

    private fun calculateCaloriesBurnedRuntime(distanceInMeters: Int) {
        val functionData = hashMapOf(
            "distanceInMeters" to distanceInMeters
        )

        FirebaseFunctions.getInstance()
            .getHttpsCallable("caloriesBurnedRuntime")
            .call(functionData)
            .addOnSuccessListener { result ->
                val resultData = result.data as Map<*, *>
                val caloriesBurned = (resultData["caloriesBurned"] as? Number)?.toLong() ?: 0L
                binding.tvActualDistance.text = "Current distance:\n $distanceInMeters"
                binding.tvActualCalories.text = "Current Kcal burned:\n $caloriesBurned"
            }
            .addOnFailureListener { e ->
                // Errors
                Timber.tag("CloudFunction").e(e, "Error calling caloriesBurnedRuntime")
            }
    }

    private fun localProcessingRuntime(){
        var distanceInMeters = 0
        for (polyline in pathPoints){
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
        }
        var caloriesBurned = 0
        if (distanceInMeters > 0){
            caloriesBurned = ((distanceInMeters/1000f) * (MET * MET_CONVERSION_TO_KCALXKM) * userWeight!!).toInt()
        }
        binding.tvActualDistance.text = "Current distance:\n $distanceInMeters"
        binding.tvActualCalories.text = "Current Kcal burned:\n $caloriesBurned"
    }
}

