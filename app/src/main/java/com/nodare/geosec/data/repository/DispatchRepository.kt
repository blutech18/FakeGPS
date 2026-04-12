package com.nodare.geosec.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.nodare.geosec.data.model.DispatchSession
import com.nodare.geosec.data.model.User
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.DispatchKeyGenerator
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the online/dispatch status of an employee.
 * Used by Admin/CEO to monitor workforce status.
 */
data class EmployeeStatus(
    val userId: String = "",
    val displayName: String = "",
    val role: String = "",
    val isOnline: Boolean = false,       // has active check-in
    val isDispatching: Boolean = false,   // has active dispatch session
    val dispatchSessionId: String? = null
)

@Singleton
class DispatchRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(Constants.COLLECTION_DISPATCH_SESSIONS)

    /**
     * Starts a new dispatch session with a generated dispatch key.
     * The dispatch key must be entered to end the delivery (Secure Dispatch Key Protocol).
     * @return Resource containing the session ID and dispatch key as "sessionId|dispatchKey"
     */
    suspend fun startSession(
        userId: String,
        userName: String,
        role: String,
        latitude: Double,
        longitude: Double
    ): Resource<String> {
        return try {
            // Check for existing active session
            val existing = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "active")
                .get().await()

            if (!existing.isEmpty) {
                return Resource.Error("An active dispatch session already exists")
            }

            // Generate a secure dispatch key for delivery confirmation
            val dispatchKey = DispatchKeyGenerator.generateKey()

            val session = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "role" to role,
                "startLocation" to GeoPoint(latitude, longitude),
                "endLocation" to null,
                "startTime" to Timestamp.now(),
                "endTime" to null,
                "status" to "active",
                "routeId" to "",
                "dispatchKey" to dispatchKey,
                "isSuspicious" to false,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            val docRef = collection.add(session).await()
            // Return sessionId|dispatchKey so the UI can show the key
            Resource.Success("${docRef.id}|$dispatchKey")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to start dispatch", e)
        }
    }

    /**
     * Ends a dispatch session after validating the dispatch key.
     * The user must enter the correct dispatch key to confirm delivery completion.
     */
    suspend fun endSession(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        enteredKey: String
    ): Resource<Unit> {
        return try {
            // First, verify the dispatch key
            val sessionDoc = collection.document(sessionId).get().await()
            val storedKey = sessionDoc.getString("dispatchKey") ?: ""

            if (!DispatchKeyGenerator.validateKey(enteredKey, storedKey)) {
                return Resource.Error("Invalid dispatch key. Please enter the correct confirmation code.")
            }

            collection.document(sessionId).update(
                mapOf(
                    "endLocation" to GeoPoint(latitude, longitude),
                    "endTime" to Timestamp.now(),
                    "status" to "completed",
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to end dispatch", e)
        }
    }

    suspend fun markSuspicious(sessionId: String): Resource<Unit> {
        return try {
            collection.document(sessionId).update(
                mapOf(
                    "isSuspicious" to true,
                    "status" to "suspicious",
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mark suspicious", e)
        }
    }

    suspend fun getActiveSession(userId: String): DispatchSession? {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "active")
                .get().await()
            snapshot.toObjects(DispatchSession::class.java).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun observeActiveSessions(): Flow<List<DispatchSession>> = callbackFlow {
        val listener = collection
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val sessions = snapshot?.toObjects(DispatchSession::class.java) ?: emptyList()
                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    fun observeUserSessions(userId: String): Flow<List<DispatchSession>> = callbackFlow {
        val listener = collection
            .whereEqualTo("userId", userId)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                val sessions = (snapshot?.toObjects(DispatchSession::class.java) ?: emptyList())
                    .sortedByDescending { it.startTime?.seconds }
                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    fun observeAllSessions(): Flow<List<DispatchSession>> = callbackFlow {
        val listener = collection
            .limit(100)
            .addSnapshotListener { snapshot, _ ->
                val sessions = (snapshot?.toObjects(DispatchSession::class.java) ?: emptyList())
                    .sortedByDescending { it.startTime?.seconds }
                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    suspend fun fetchUserSessions(userId: String): List<DispatchSession> {
        return try {
            val snapshot = collection
                .whereEqualTo("userId", userId)
                .limit(20)
                .get().await()
            (snapshot.toObjects(DispatchSession::class.java))
                .sortedByDescending { it.startTime?.seconds }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * For Admin/CEO: Get all employees with their online/dispatching status.
     * Combines user data with check-in and dispatch session data.
     */
    suspend fun getEmployeeStatuses(): List<EmployeeStatus> {
        return try {
            // Get all users (non-admin roles only: Technician, Car Driver)
            val usersSnapshot = firestore.collection(Constants.COLLECTION_USERS)
                .whereIn("role", listOf(Constants.ROLE_TECHNICIAN, Constants.ROLE_CAR_DRIVER))
                .get().await()
            val users = usersSnapshot.toObjects(User::class.java)

            // Get active check-ins (online users)
            val checkInsSnapshot = firestore.collection(Constants.COLLECTION_CHECK_IN_LOGS)
                .whereEqualTo("isActive", true)
                .get().await()
            val checkedInUserIds = checkInsSnapshot.documents.mapNotNull { it.getString("userId") }.toSet()

            // Get active dispatch sessions (dispatching users)
            val dispatchSnapshot = collection
                .whereEqualTo("status", "active")
                .get().await()
            val dispatchingSessions = dispatchSnapshot.toObjects(DispatchSession::class.java)
            val dispatchingMap = dispatchingSessions.associateBy { it.userId }

            users.map { user ->
                EmployeeStatus(
                    userId = user.id,
                    displayName = user.displayName,
                    role = user.role,
                    isOnline = user.id in checkedInUserIds,
                    isDispatching = user.id in dispatchingMap,
                    dispatchSessionId = dispatchingMap[user.id]?.id
                )
            }.sortedWith(
                compareByDescending<EmployeeStatus> { it.isDispatching }
                    .thenByDescending { it.isOnline }
                    .thenBy { it.displayName }
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Real-time observation of employee statuses for Admin/CEO dashboard.
     */
    fun observeEmployeeStatuses(): Flow<List<EmployeeStatus>> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO)

        // Listen to users collection changes
        val usersListener = firestore.collection(Constants.COLLECTION_USERS)
            .whereIn("role", listOf(Constants.ROLE_TECHNICIAN, Constants.ROLE_CAR_DRIVER))
            .addSnapshotListener { _, _ ->
                scope.launch {
                    val statuses = getEmployeeStatuses()
                    trySend(statuses)
                }
            }

        // Also listen to check-in changes
        val checkInListener = firestore.collection(Constants.COLLECTION_CHECK_IN_LOGS)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { _, _ ->
                scope.launch {
                    val statuses = getEmployeeStatuses()
                    trySend(statuses)
                }
            }

        // Also listen to dispatch session changes
        val dispatchListener = collection
            .whereEqualTo("status", "active")
            .addSnapshotListener { _, _ ->
                scope.launch {
                    val statuses = getEmployeeStatuses()
                    trySend(statuses)
                }
            }

        awaitClose {
            usersListener.remove()
            checkInListener.remove()
            dispatchListener.remove()
            scope.cancel()
        }

    }
}
