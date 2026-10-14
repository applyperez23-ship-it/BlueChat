package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY customAlias ASC")
    fun getAllContactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsList(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getContactByNodeId(nodeId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Query("UPDATE contacts SET cloudValidationStatus = :status WHERE nodeId = :nodeId")
    suspend fun updateValidationStatus(nodeId: String, status: String)

    @Query("DELETE FROM contacts WHERE nodeId = :nodeId")
    suspend fun deleteContact(nodeId: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}
