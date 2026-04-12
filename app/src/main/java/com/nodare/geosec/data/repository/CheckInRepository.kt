package com.nodare.geosec.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.nodare.geosec.data.model.CheckInLog
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(Constants.COLLECTION_CHECK_IN_LOGS)

    suspend fun checkIn(
        userId: String,
        userName: String,
        role: String,
        deviceId: String,
        latitude: Double,
        longitude: Double
    ): Resource<String> {
        return try {
            // Check for existing active session
            val existing = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .get().await()

            if (!existing.isEmpty) {
                return Resource.Error("Already checked in. Please check out first.")
            }

            val log = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "role" to role,
                "deviceId" to deviceId,
                "checkInTime" to Timestamp.now(),
                "checkOutTime" to null,
                "checkInLocation" to GeoPoint(latitude, longitude),
                "isActive" to true,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            val docRef = collection.add(log).await()
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Check-in failed", e)
        }
    }

    suspend fun checkOut(userId: String): Resource<Unit> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .get().await()

            if (snapshot.isEmpty) {
                return Resource.Error("No active check-in found")
            }

            val doc = snapshot.documents.first()
            doc.reference.update(
                mapOf(
                    "checkOutTime" to Timestamp.now(),
                    "isActive" to false,
                    "updatedAt" to Timestamp.now()
                )
            ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Check-out failed", e)
        }
    }

    suspend fun hasActiveCheckIn(userId: String): Boolean {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .get().await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    fun observeActiveCheckIns(): Flow<List<CheckInLog>> = callbackFlow {
        val listener = collection
            .whereEqualTo("isActive", true)
            .orderBy("checkInTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects(CheckInLog::class.java) ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }

    fun observeCheckInLogs(): Flow<List<CheckInLog>> = callbackFlow {
        val listener = collection
            .orderBy("checkInTime", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects(CheckInLog::class.java) ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }
}
