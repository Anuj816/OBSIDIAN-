package com.anuj.obsidian.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anuj.obsidian.data.model.ExtensionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions")
    fun getAllExtensions(): Flow<List<ExtensionItem>>

    @Query("SELECT * FROM extensions WHERE isInstalled = 1")
    fun getInstalledExtensions(): Flow<List<ExtensionItem>>

    @Query("SELECT * FROM extensions WHERE isEnabled = 1")
    fun getEnabledExtensions(): Flow<List<ExtensionItem>>

    @Query("SELECT * FROM extensions WHERE isEnabled = 1")
    suspend fun getEnabledExtensionsDirect(): List<ExtensionItem>

    @Query("SELECT COUNT(*) FROM extensions")
    suspend fun getExtensionCount(): Int

    @Query("SELECT * FROM extensions WHERE id = :id")
    suspend fun getExtensionById(id: String): ExtensionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensions(extensions: List<ExtensionItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: ExtensionItem)

    @Update
    suspend fun updateExtension(extension: ExtensionItem)

    @Delete
    suspend fun deleteExtension(extension: ExtensionItem)

    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun deleteExtensionById(id: String)

    @Query("DELETE FROM extensions WHERE id NOT IN (:allowedIds)")
    suspend fun pruneExtensions(allowedIds: List<String>)
}
