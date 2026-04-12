package com.nodare.geosec.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class CheckInLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "",
    val deviceId: String = "",
    val checkInTime: Timestamp? = null,
    val checkOutTime: Timestamp? = null,
    val checkInLocation: GeoPoint? = null,
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
