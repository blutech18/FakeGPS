package com.nodare.geosec.util

import kotlin.math.*

object LocationUtils {
    /**
     * Calculate distance between two coordinates using the Haversine formula.
     * Returns distance in meters.
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Calculate speed in km/h given distance in meters and time in milliseconds.
     */
    fun calculateSpeedKmh(distanceMeters: Double, timeMs: Long): Double {
        if (timeMs <= 0) return 0.0
        val timeHours = timeMs / 3_600_000.0
        return (distanceMeters / 1000.0) / timeHours
    }
}
