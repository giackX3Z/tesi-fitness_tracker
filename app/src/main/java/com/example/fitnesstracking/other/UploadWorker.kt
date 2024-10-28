package com.example.fitnesstracking.other

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString("userId")
        val avgSpeed = inputData.getFloat("speed", 0f)
        val caloriesBurned = inputData.getInt("calories", 0)
        val distanceInMeters = inputData.getInt("distance", 0)
        val imageUrl = inputData.getString("imageUrl")
        val steps = inputData.getInt("steps",0)
        val timeInMillis = inputData.getLong("time", 0)


        if (imageUrl == null || userId == null) {
            Timber.tag("com.example.fitnesstracking.other.UploadWorker").e("Image URL or UserId is null")
            return@withContext Result.failure()
        }

        val file = File(imageUrl)
        if (!file.exists()) {
            Timber.tag("com.example.fitnesstracking.other.UploadWorker").e("File does not exist: $imageUrl")
            return@withContext Result.failure()
        }

        return@withContext try {
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("runs/$userId/${UUID.randomUUID()}.jpg")

            Timber.tag("com.example.fitnesstracking.other.UploadWorker").d("Uploading file to Firebase Storage")
            val uploadTask = imageRef.putFile(Uri.fromFile(file))
            uploadTask.await()

            Timber.tag("com.example.fitnesstracking.other.UploadWorker").d("Getting download URL")
            val downloadUrl = imageRef.downloadUrl.await().toString()

            Timber.tag("com.example.fitnesstracking.other.UploadWorker").d("Uploading data to Firestore")
            val fireStore = FirebaseFirestore.getInstance()
            val data = hashMapOf(
                "avgSpeedInKMH" to avgSpeed,
                "caloriesBurned" to caloriesBurned,
                "distanceInMeters" to distanceInMeters,
                "imageUrl" to downloadUrl,
                "steps" to steps,
                "timeInMillis" to timeInMillis,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            // Correct path: users/{userId}/runs/{runId}
            fireStore.collection("users")
                .document(userId)
                .collection("runs")
                .add(data)
                .await()

            file.delete()
            Timber.tag("com.example.fitnesstracking.other.UploadWorker").d("Upload completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.tag("com.example.fitnesstracking.other.UploadWorker").e(e, "Error during upload")
            when (e) {
                is IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}