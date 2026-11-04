package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(
    tableName = "parties",
    indices = [Index(value = ["isSynced"])]
)
data class PartyEntity(
    @PrimaryKey
    val id: String,
    val businessName: String,
    val contactPerson: String? = null,
    val phone: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val defaultAgent: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val assignedRepIdsJson: String? = null,
    val isSynced: Boolean = true
) {
    companion object {
        @Ignore
        private val gson = Gson()

        @Ignore
        private val listType = object : TypeToken<List<String>>() {}.type
    }

    val formattedAddress: String
        get() = listOfNotNull(
            addressLine1?.ifBlank { null },
            addressLine2?.ifBlank { null }
        ).joinToString(", ")

    /**
     * Parse the assignedRepIdsJson into a proper List<String>.
     * Returns empty list if null or unparseable.
     */
    fun getAssignedRepIds(): List<String> {
        val raw = assignedRepIdsJson ?: return emptyList()
        return try {
            gson.fromJson<List<String>>(raw, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if this party is allotted to a specific staff member by their ID.
     * Returns false if repId is blank (no identity = no match).
     * Uses exact match against parsed list elements.
     */
    fun isAllottedTo(repId: String?): Boolean {
        if (repId.isNullOrBlank()) return false
        return getAssignedRepIds().any { it.equals(repId, ignoreCase = true) }
    }
}
