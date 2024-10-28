package com.example.fitnesstracking.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fitnesstracking.databinding.ItemRunBinding
import com.example.fitnesstracking.data.Run
import com.example.fitnesstracking.other.SortType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class RunAdapter(private val onDeleteClick: (Run) -> Unit) : RecyclerView.Adapter<RunAdapter.RunViewHolder>() {
    private val runs = mutableListOf<Run>()
    private val fireStore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentSortType: SortType = SortType.DATE

    fun updateSortType(sortType: SortType) {
        currentSortType = sortType
        fetchRunsFromFireStore()
    }

    private fun fetchRunsFromFireStore() {
        val userId = auth.currentUser?.uid ?: return
        val query = when (currentSortType) {
            SortType.DATE -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
            SortType.RUNNING_TIME -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("timeInMillis", Query.Direction.DESCENDING)
            SortType.DISTANCE -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("distanceInMeters", Query.Direction.DESCENDING)
            SortType.AVG_SPEED -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("avgSpeedInKMH", Query.Direction.DESCENDING)
            SortType.CALORIES_BURNED -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("caloriesBurned", Query.Direction.DESCENDING)
            SortType.STEPS -> fireStore.collection("users").document(userId).collection("runs")
                .orderBy("steps", Query.Direction.DESCENDING)
        }

        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            runs.clear()
            for (doc in snapshot?.documents ?: emptyList()) {
                val run = doc.toObject(Run::class.java)?.copy(id = doc.id)
                run?.let { runs.add(it) }
            }
            notifyDataSetChanged()
        }
    }

    fun deleteRun(run: Run) {
        val userId = auth.currentUser?.uid ?: return
        fireStore.collection("users").document(userId).collection("runs")
            .document(run.id)
            .delete()
            .addOnSuccessListener {
                //delete image from storage
                deleteImageFromStorage(run.imageUrl)

                val position = runs.indexOf(run)
                if (position != -1) {
                    runs.removeAt(position)
                    notifyItemRemoved(position)
                }
            }
            .addOnFailureListener { e ->
                Timber.d("Error deleting run $e")
            }
    }


    private fun deleteImageFromStorage(imageUrl: String) {
        // Check if the imageUrl is not empty
        if (imageUrl.isBlank()) {
            Timber.d("No image URL provided, skipping image deletion")
            return
        }

        // Extract the image path from the full URL
        val imagePath = try {
            // Assuming the imageUrl is in the format: https://firebasestorage.googleapis.com/v0/b/[project-id].appspot.com/o/runs%2F[image-name]?alt=media&token=[token]
            val encodedPath = imageUrl.substringAfter("/o/").substringBefore("?")
            java.net.URLDecoder.decode(encodedPath, "UTF-8")
        } catch (e: Exception) {
            Timber.e("Error extracting image path: $e")
            return
        }

        // Create a reference to the file to delete
        val imageRef = storage.reference.child(imagePath)

        // Delete the file
        imageRef.delete().addOnSuccessListener {
            Timber.d("Image successfully deleted from Storage")
        }.addOnFailureListener { e ->
            Timber.e("Error deleting image from Storage: $e")
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val binding = ItemRunBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RunViewHolder(binding) { position ->
            val run = runs[position]
            onDeleteClick(run)
        }
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = runs[position]
        holder.bind(run)
    }

    override fun getItemCount() = runs.size

    class RunViewHolder(
        private val binding: ItemRunBinding,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.ivDelete.setOnClickListener {
                onDeleteClick(adapterPosition)
            }
        }

        fun bind(run: Run) {
            binding.tvAvgSpeed.text = "km/h: \n${run.avgSpeedInKMH}"
            binding.tvCalories.text = "Kcal: \n ${run.caloriesBurned}"
            binding.tvDistance.text = "km: \n ${run.distanceInMeters / 1000f}"
            binding.tvTime.text = "Time: \n ${formatTime(run.timeInMillis)}"
            binding.tvDate.text = "Date: \n ${formatDate(run.timestamp)}"
            binding.tvSteps.text = "Steps: \n ${run.steps}"

            Glide.with(binding.root)
                .load(run.imageUrl)
                .into(binding.ivRunImage)
        }


        private fun formatTime(timeInMillis: Long): String {
            val hours = timeInMillis / (1000 * 60 * 60)
            val minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (timeInMillis % (1000 * 60)) / 1000
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun formatDate(timestamp: Date): String {
            val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            return dateFormat.format(timestamp)
        }
    }
}
