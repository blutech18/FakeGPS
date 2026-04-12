package com.nodare.geosec.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp

data class Route(
    @DocumentId val id: String = "",
    val dispatchSessionId: String = "",
    val userId: String = "",
    val waypoints: List<GeoPoint> = emptyList(),
    val expectedPolyline: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
