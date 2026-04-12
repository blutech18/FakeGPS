package com.nodare.geosec.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nodare.geosec.data.local.dao.PendingGpsLogDao
import com.nodare.geosec.data.local.entity.PendingGpsLogEntity
import com.nodare.geosec.data.model.GpsLog
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsLogRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val pendingGpsLogDao: PendingGpsLogDao
) {
    private val collection = firestore.collection(Constants.COLLECTION_GPS_LOGS)

    suspend fun saveGpsLog(
        userId: String,
        dispatchSessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float
    ): Resource<Unit> {
        return try {
            val data = hashMapOf(
                "userId" to userId,
                "dispatchSessionId" to dispatchSessionId,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "speed" to speed,
                "timestamp" to Timestamp.now(),
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            collection.add(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save GPS log", e)
        }
    }

    suspend fun saveLocalGpsLog(
        userId: String,
        dispatchSessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float
    ) {
        val entity = PendingGpsLogEntity(
            userId = userId,
            dispatchSessionId = dispatchSessionId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            speed = speed,
            timestamp = System.currentTimeMillis()
        )
        pendingGpsLogDao.insert(entity)
    }

    suspend fun syncPendingLogs(): Resource<Int> {
        return try {
            val pending = pendingGpsLogDao.getUnsyncedLogs()
            if (pending.isEmpty()) return Resource.Success(0)

            val batch = firestore.batch()
            val syncedIds = mutableListOf<Long>()

            for (log in pending) {
                val docRef = collection.document()
                val data = hashMapOf(
                    "userId" to log.userId,
                    "dispatchSessionId" to log.dispatchSessionId,
                    "latitude" to log.latitude,
                    "longitude" to log.longitude,
                    "accuracy" to log.accuracy,
                    "speed" to log.speed,
                    "timestamp" to Timestamp(log.timestamp / 1000, 0),
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
                batch.set(docRef, data)
                syncedIds.add(log.id)
            }

            batch.commit().await()
            pendingGpsLogDao.markAsSynced(syncedIds)
            pendingGpsLogDao.deleteSyncedLogs()

            Resource.Success(syncedIds.size)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sync failed", e)
        }
    }

    fun observeGpsLogs(userId: String): Flow<List<GpsLog>> = callbackFlow {
        val listener = collection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects(GpsLog::class.java) ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getLatestLogForSession(sessionId: String): GpsLog? {
        return try {
            val snapshot = collection
                .whereEqualTo("dispatchSessionId", sessionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()
            snapshot.toObjects(GpsLog::class.java).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun observeSessionGpsLogs(sessionId: String): Flow<List<GpsLog>> = callbackFlow {
        val listener = collection
            .whereEqualTo("dispatchSessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects(GpsLog::class.java) ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }
}
