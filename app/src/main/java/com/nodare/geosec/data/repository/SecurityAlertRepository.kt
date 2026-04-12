package com.nodare.geosec.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nodare.geosec.data.model.SecurityAlert
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityAlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(Constants.COLLECTION_SECURITY_ALERTS)

    suspend fun createAlert(
        alertType: String,
        userId: String,
        userName: String,
        dispatchSessionId: String,
        description: String,
        severity: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ): Resource<String> {
        return try {
            val data = hashMapOf(
                "alertType" to alertType,
                "userId" to userId,
                "userName" to userName,
                "dispatchSessionId" to dispatchSessionId,
                "description" to description,
                "severity" to severity,
                "latitude" to latitude,
                "longitude" to longitude,
                "isResolved" to false,
                "timestamp" to Timestamp.now(),
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            val docRef = collection.add(data).await()
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create alert", e)
        }
    }

    suspend fun resolveAlert(alertId: String): Resource<Unit> {
        return try {
            collection.document(alertId).update(
                mapOf(
                    "isResolved" to true,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to resolve alert", e)
        }
    }

    fun observeAlerts(): Flow<List<SecurityAlert>> = callbackFlow {
        val listener = collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val alerts = snapshot?.toObjects(SecurityAlert::class.java) ?: emptyList()
                trySend(alerts)
            }
        awaitClose { listener.remove() }
    }

    fun observeUnresolvedAlerts(): Flow<List<SecurityAlert>> = callbackFlow {
        val listener = collection
            .whereEqualTo("isResolved", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val alerts = snapshot?.toObjects(SecurityAlert::class.java) ?: emptyList()
                trySend(alerts)
            }
        awaitClose { listener.remove() }
    }
}
