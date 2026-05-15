package com.painite.keyboard

import android.app.Application
import com.painite.keyboard.data.AppDatabase
import com.painite.keyboard.data.ClipboardRepository
import com.painite.keyboard.data.SettingsRepository

class PainiteApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val clipboardRepository: ClipboardRepository by lazy {
        ClipboardRepository(database.clipboardDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PainiteApp
            private set
    }
}
