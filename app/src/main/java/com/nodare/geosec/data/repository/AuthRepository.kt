package com.nodare.geosec.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.nodare.geosec.data.model.User
import com.nodare.geosec.presentation.auth.LoginError
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import android.util.Log
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    val isLoggedIn: Boolean get() = auth.currentUser != null

    suspend fun login(email: String, password: String): Resource<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Resource.Error("Login failed")
            val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                .document(uid).get().await()
            val user = userDoc.toObject(User::class.java)
                ?: return Resource.Error(
                    "User profile not found",
                    errorType = LoginError.General.ProfileNotFound
                )
            Resource.Success(user)
        } catch (e: FirebaseAuthException) {
            val loginError = LoginError.fromFirebaseCode(e.errorCode, e.message)
            Resource.Error(loginError.message, e, errorType = loginError)
        } catch (e: java.net.UnknownHostException) {
            Resource.Error(
                LoginError.General.NetworkError.message,
                e,
                errorType = LoginError.General.NetworkError
            )
        } catch (e: java.net.SocketTimeoutException) {
            Resource.Error(
                LoginError.General.NetworkError.message,
                e,
                errorType = LoginError.General.NetworkError
            )
        } catch (e: javax.net.ssl.SSLException) {
            Resource.Error(
                LoginError.General.NetworkError.message,
                e,
                errorType = LoginError.General.NetworkError
            )
        } catch (e: java.io.IOException) {
            Resource.Error(
                LoginError.General.NetworkError.message,
                e,
                errorType = LoginError.General.NetworkError
            )
        } catch (e: Exception) {
            val isNetworkRelated = e.cause is java.io.IOException
                    || e.cause is javax.net.ssl.SSLException
                    || e.message?.contains("connection", ignoreCase = true) == true
                    || e.message?.contains("ssl", ignoreCase = true) == true
                    || e.message?.contains("network", ignoreCase = true) == true
                    || e.message?.contains("timeout", ignoreCase = true) == true

            if (isNetworkRelated) {
                Resource.Error(
                    LoginError.General.NetworkError.message,
                    e,
                    errorType = LoginError.General.NetworkError
                )
            } else {
                val fallback = LoginError.General.Unknown(
                    "Login failed. Please try again."
                )
                Resource.Error(fallback.message, e, errorType = fallback)
            }
        }
    }

    suspend fun getCurrentUser(): Resource<User> {
        return try {
            val uid = auth.currentUser?.uid
            Log.d(TAG, "getCurrentUser() called, uid=$uid")
            if (uid == null) {
                Log.w(TAG, "getCurrentUser() - not authenticated, no current user")
                return Resource.Error("Not authenticated")
            }
            Log.d(TAG, "getCurrentUser() - fetching Firestore doc for uid=$uid")
            val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                .document(uid).get().await()
            Log.d(TAG, "getCurrentUser() - Firestore doc received, exists=${userDoc.exists()}")
            val user = userDoc.toObject(User::class.java)
            if (user == null) {
                Log.w(TAG, "getCurrentUser() - toObject returned null, doc data: ${userDoc.data}")
                return Resource.Error("User profile not found")
            }
            Log.d(TAG, "getCurrentUser() - success, role=${user.role}, name=${user.displayName}")
            Resource.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentUser() - exception: ${e.javaClass.simpleName}: ${e.message}", e)
            Resource.Error(e.message ?: "Failed to get user", e)
        }
    }

    suspend fun updateFcmToken(token: String): Resource<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Resource.Error("Not authenticated")
            firestore.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .update("fcmToken", token)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update token", e)
        }
    }

    suspend fun updateUserProfile(userId: String, displayName: String, email: String): Resource<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Resource.Error("Not authenticated")
            
            // Update email in Firebase Auth if changed
            if (currentUser.email != email) {
                currentUser.updateEmail(email).await()
            }
            
            // Update display name and email in Firestore
            firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .update(
                    mapOf(
                        "displayName" to displayName,
                        "email" to email
                    )
                ).await()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update profile", e)
        }
    }

    fun logout() {
        auth.signOut()
    }
}
