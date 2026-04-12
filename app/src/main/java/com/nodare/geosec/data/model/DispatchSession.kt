package com.nodare.geosec.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class DispatchSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "",
    val startLocation: GeoPoint? = null,
    val endLocation: GeoPoint? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val status: String = "active", // active, completed, suspicious
    val routeId: String = "",
    val dispatchKey: String = "", // Encrypted dispatch key for delivery confirmation
    @get:PropertyName("isSuspicious") @set:PropertyName("isSuspicious")
    var isSuspicious: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
