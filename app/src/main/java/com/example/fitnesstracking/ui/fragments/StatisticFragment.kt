package com.example.fitnesstracking.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.FragmentStatisticsBinding
import com.example.fitnesstracking.other.TrackingUtility
import com.github.mikephil.charting.components.XAxis
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class StatisticFragment : Fragment(R.layout.fragment_statistics) {


    // View Binding
    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show ProgressBar
        binding.progressBar.visibility = View.VISIBLE
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        // Call the cloud function
        getUserRunStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun getUserRunStats() {
        val functions = Firebase.functions

        // Call cloud function
        functions
            .getHttpsCallable("getUserRunStats")
            .call()
            .addOnSuccessListener { result ->
                val data = result.data as Map<*, *>

                // Getting data from cloud function
                val totalTimeInMillis = (data["totalTimeInMillis"] as? Number)?.toLong() ?: 0L
                val totalDistanceInKm = (data["totalDistanceInKm"] as? Number)?.toDouble() ?: 0.0
                val totalCaloriesBurned = (data["totalCaloriesBurned"] as? Number)?.toLong() ?: 0L
                val averageSpeed = (data["averageSpeed"] as? Number)?.toDouble() ?: 0.0
                val totalSteps = (data["totalSteps"] as? Number)?.toLong() ?: 0L

                // Update TextView with processed data
                binding.tvTotalTime.text = TrackingUtility.getFormattedStopWatchTime(totalTimeInMillis)  //formatTime(totalTimeInMillis)
                binding.tvTotalDistance.text = String.format("%.2f", totalDistanceInKm)
                binding.tvTotalCalories.text = "$totalCaloriesBurned"
                binding.tvAverageSpeed.text = String.format("%.1f", averageSpeed)
                binding.tvSteps.text = "$totalSteps"

                // Hide progress bar after data have been loaded
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)


            }
            .addOnFailureListener { e ->
                Timber.d("Cloud Function Error, Error while processing run data $e")

                Toast.makeText(requireContext(), "Error getting stats", Toast.LENGTH_SHORT).show()

                //Hide progress bar after Error
                binding.progressBar.visibility = View.GONE
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

            }
    }
/*
    private fun setupBarChart(){
        binding.barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawLabels(false)
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        binding.barChart.axisLeft.apply {
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        binding.barChart.axisRight.apply {
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        binding.barChart.apply {
            description.text = "Avg Speed Over Time"
            legend.isEnabled = false
        }
    }
*/
}