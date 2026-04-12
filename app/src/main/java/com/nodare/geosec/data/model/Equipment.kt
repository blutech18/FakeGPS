package com.nodare.geosec.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Equipment(
    @DocumentId val id: String = "",
    val equipmentId: String = "",
    val equipmentName: String = "",
    val category: String = "",
    val status: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
