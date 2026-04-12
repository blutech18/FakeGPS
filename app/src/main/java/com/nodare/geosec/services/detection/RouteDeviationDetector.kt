package com.nodare.geosec.services.detection

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.nodare.geosec.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteDeviationDetector @Inject constructor() {

    /**
     * Check if the current position deviates from the expected route polyline.
     * @param currentLat Current latitude
     * @param currentLng Current longitude
     * @param encodedPolyline The encoded polyline of the expected route
     * @return true if deviation exceeds threshold
     */
    fun isDeviating(
        currentLat: Double,
        currentLng: Double,
        encodedPolyline: String
    ): Boolean {
        if (encodedPolyline.isBlank()) return false

        return try {
            val routePoints = PolyUtil.decode(encodedPolyline)
            val currentPoint = LatLng(currentLat, currentLng)

            val isOnPath = PolyUtil.isLocationOnPath(
                currentPoint,
                routePoints,
                true, // geodesic
                Constants.ROUTE_DEVIATION_THRESHOLD_METERS
            )

            !isOnPath
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculate the minimum distance from current position to the route polyline.
     */
    fun getDeviationDistance(
        currentLat: Double,
        currentLng: Double,
        encodedPolyline: String
    ): Double {
        if (encodedPolyline.isBlank()) return 0.0

        return try {
            val routePoints = PolyUtil.decode(encodedPolyline)
            val currentPoint = LatLng(currentLat, currentLng)

            var minDistance = Double.MAX_VALUE
            for (i in 0 until routePoints.size - 1) {
                val dist = PolyUtil.distanceToLine(
                    currentPoint,
                    routePoints[i],
                    routePoints[i + 1]
                )
                if (dist < minDistance) {
                    minDistance = dist
                }
            }
            minDistance
        } catch (e: Exception) {
            0.0
        }
    }
}
