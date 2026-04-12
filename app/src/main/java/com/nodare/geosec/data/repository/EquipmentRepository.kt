package com.nodare.geosec.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nodare.geosec.data.model.Equipment
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EquipmentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(Constants.COLLECTION_EQUIPMENT_INVENTORY)

    fun observeEquipment(): Flow<List<Equipment>> = callbackFlow {
        val listener = collection
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(Equipment::class.java) ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addEquipment(equipment: Equipment): Resource<String> {
        return try {
            val data = hashMapOf(
                "equipmentId" to equipment.equipmentId,
                "equipmentName" to equipment.equipmentName,
                "category" to equipment.category,
                "status" to equipment.status,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            val docRef = collection.add(data).await()
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add equipment", e)
        }
    }

    suspend fun updateEquipment(
        documentId: String,
        name: String,
        category: String,
        status: String
    ): Resource<Unit> {
        return try {
            collection.document(documentId).update(
                mapOf(
                    "equipmentName" to name,
                    "category" to category,
                    "status" to status,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update equipment", e)
        }
    }

    suspend fun deleteEquipment(documentId: String): Resource<Unit> {
        return try {
            collection.document(documentId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete equipment", e)
        }
    }
}
