package com.example.fitnesstracking.data

import java.util.Date


data class Run(
    val id: String = "",
    val avgSpeedInKMH: Float = 0f,
    val caloriesBurned: Int = 0,
    val distanceInMeters: Int = 0,
    val imageUrl: String = "",
    val steps: Int = 0,
    val timeInMillis: Long = 0L,
    val timestamp: Date = Date()

) {

}
