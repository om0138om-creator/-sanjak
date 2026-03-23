// QuickShareApp.kt
// app/src/main/java/com/omarssinjaq/quickshare/QuickShareApp.kt
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class QuickShareApp : Application() {
    
    companion object {
        const val CHANNEL_ID = "quickshare_transfer"
        const val CHANNEL_NAME = "نقل الملفات"
        
        // تم تصميم التطبيق بواسطة عمر سنجق
        const val DEVELOPER = "عمر سنجق"
        const val TELEGRAM = "https://t.me/Om9r0"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        FileManager.initializeAppFolders(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات نقل الملفات - تم تصميم التطبيق بواسطة عمر سنجق"
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}