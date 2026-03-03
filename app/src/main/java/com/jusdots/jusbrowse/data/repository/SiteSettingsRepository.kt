package com.jusdots.jusbrowse.data.repository

import com.jusdots.jusbrowse.data.database.SiteSettingsDao
import com.jusdots.jusbrowse.data.models.SiteSettings
import kotlinx.coroutines.flow.Flow

class SiteSettingsRepository(private val siteSettingsDao: SiteSettingsDao) {

    fun getSettingsForOrigin(origin: String): Flow<SiteSettings?> = 
        siteSettingsDao.getSettingsForOrigin(origin)

    fun getAllSettings(): Flow<List<SiteSettings>> = 
        siteSettingsDao.getAllSettings()

    suspend fun updateSettings(settings: SiteSettings) {
        siteSettingsDao.updateSettings(settings)
    }

    suspend fun deleteSettingsForOrigin(origin: String) {
        siteSettingsDao.deleteSettingsForOrigin(origin)
    }
}
