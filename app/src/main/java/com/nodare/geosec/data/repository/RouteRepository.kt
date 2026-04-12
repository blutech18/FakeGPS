package com.nodare.geosec.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.nodare.geosec.data.model.Route
import com.nodare.geosec.data.remote.api.DirectionsApiService
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val directionsApiService: DirectionsApiService
) {
    private val collection = firestore.collection(Constants.COLLECTION_ROUTES)

    suspend fun saveRoute(
        dispatchSessionId: String,
        userId: String,
        waypoints: List<GeoPoint>,
        encodedPolyline: String
    ): Resource<String> {
        return try {
            val data = hashMapOf(
                "dispatchSessionId" to dispatchSessionId,
                "userId" to userId,
                "waypoints" to waypoints,
                "expectedPolyline" to encodedPolyline,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            val docRef = collection.add(data).await()
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save route", e)
        }
    }

    suspend fun fetchAndSaveRoute(
        dispatchSessionId: String,
        userId: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String
    ): Resource<String> {
        return try {
            val origin = "$originLat,$originLng"
            val destination = "$destLat,$destLng"
            val response = directionsApiService.getDirections(origin, destination, apiKey)

            if (response.status != "OK" || response.routes.isEmpty()) {
                return Resource.Error("No route found from Directions API")
            }

            val polyline = response.routes[0].overviewPolyline?.points ?: ""
            if (polyline.isBlank()) {
                return Resource.Error("Empty polyline returned")
            }

            val waypoints = listOf(
                GeoPoint(originLat, originLng),
                GeoPoint(destLat, destLng)
            )

            saveRoute(dispatchSessionId, userId, waypoints, polyline)
        } catch (e: Exception) {
            Log.w("RouteRepository", "Failed to fetch route: ${e.message}")
            Resource.Error(e.message ?: "Failed to fetch route", e)
        }
    }

    suspend fun getRouteForSession(sessionId: String): Route? {
        return try {
            val snapshot = collection
                .whereEqualTo("dispatchSessionId", sessionId)
                .get().await()
            snapshot.toObjects(Route::class.java).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
