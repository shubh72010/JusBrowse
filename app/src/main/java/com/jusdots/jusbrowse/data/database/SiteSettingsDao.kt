package com.jusdots.jusbrowse.data.database

import androidx.room.*
import com.jusdots.jusbrowse.data.models.SiteSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteSettingsDao {
    @Query("SELECT * FROM site_settings WHERE origin = :origin")
    fun getSettingsForOrigin(origin: String): Flow<SiteSettings?>

    @Query("SELECT * FROM site_settings")
    fun getAllSettings(): Flow<List<SiteSettings>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: SiteSettings)

    @Delete
    suspend fun deleteSettings(settings: SiteSettings)

    @Query("DELETE FROM site_settings WHERE origin = :origin")
    suspend fun deleteSettingsForOrigin(origin: String)
}
