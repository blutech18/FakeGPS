package com.nodare.geosec.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class SecurityAlert(
    @DocumentId val id: String = "",
    val alertType: String = "",
    val userId: String = "",
    val userName: String = "",
    val dispatchSessionId: String = "",
    val description: String = "",
    val severity: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @get:PropertyName("isResolved") @set:PropertyName("isResolved")
    var isResolved: Boolean = false,
    val timestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
