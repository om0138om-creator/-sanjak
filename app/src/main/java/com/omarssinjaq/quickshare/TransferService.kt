// TransferService.kt
// خدمة نقل الملفات في الخلفية
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class TransferService : Service() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "quickshare_transfer"
        const val NOTIFICATION_CHANNEL_NAME = "نقل الملفات"
        const val NOTIFICATION_ID = 1001
        const val COMPLETED_NOTIFICATION_ID = 1002
        
        const val ACTION_START_SEND = "com.omarssinjaq.quickshare.START_SEND"
        const val ACTION_START_RECEIVE = "com.omarssinjaq.quickshare.START_RECEIVE"
        const val ACTION_CANCEL = "com.omarssinjaq.quickshare.CANCEL"
        
        const val EXTRA_FILES = "extra_files"
        const val EXTRA_CONNECTION_TYPE = "extra_connection_type"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        
        // تم تصميم التطبيق بواسطة عمر سنجق
        const val DEVELOPER_NAME = "عمر سنجق"
        const val DEVELOPER_TELEGRAM = "https://t.me/Om9r0"
    }
    
    // ==================== المتغيرات ====================
    private val binder = TransferBinder()
    private var connectionManager: ConnectionManager? = null
    private var transferJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val isTransferring = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    
    // ==================== الـ Flows ====================
    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()
    
    private val _currentProgress = MutableStateFlow<TransferProgressInfo?>(null)
    val currentProgress: StateFlow<TransferProgressInfo?> = _currentProgress.asStateFlow()
    
    private val _transferHistory = MutableStateFlow<List<TransferRecord>>(emptyList())
    val transferHistory: StateFlow<List<TransferRecord>> = _transferHistory.asStateFlow()
    
    // ==================== Binder ====================
    inner class TransferBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    // ==================== دورة حياة الخدمة ====================
    override fun onCreate() {
        super.onCreate()
        connectionManager = ConnectionManager(this)
        connectionManager?.initialize()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SEND -> {
                val fileUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_FILES, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_FILES)
                }
                val connectionType = intent.getStringExtra(EXTRA_CONNECTION_TYPE) ?: "wifi"
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
                
                fileUris?.let { startSending(it, connectionType, deviceAddress) }
            }
            
            ACTION_START_RECEIVE -> {
                val connectionType = intent.getStringExtra(EXTRA_CONNECTION_TYPE) ?: "wifi"
                startReceiving(connectionType)
            }
            
            ACTION_CANCEL -> {
                cancelTransfer()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelTransfer()
        connectionManager?.cleanup()
        releaseWakeLock()
    }
    
    // ==================== إنشاء قناة الإشعارات ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات نقل الملفات - تم تصميم التطبيق بواسطة عمر سنجق"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // ==================== WakeLock ====================
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuickShare::TransferWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // ساعة واحدة كحد أقصى
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    // ==================== بدء الإرسال ====================
    private fun startSending(fileUris: List<Uri>, connectionType: String, deviceAddress: String) {
        if (isTransferring.get()) return
        
        isTransferring.set(true)
        isCancelled.set(false)
        
        _transferState.value = TransferState.Preparing
        startForeground(NOTIFICATION_ID, createProgressNotification("جاري التحضير...", 0))
        
        transferJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // الحصول على تفاصيل الملفات
                val files = fileUris.mapNotNull { uri ->
                    FileManager.getFileDetails(this@TransferService, uri)
                }
                
                if (files.isEmpty()) {
                    _transferState.value = TransferState.Error("لم يتم العثور على ملفات")
                    stopSelf()
                    return@launch
                }
                
                _transferState.value = TransferState.Connecting
                updateNotification("جاري الاتصال...", 0)
                
                // البحث عن الجهاز والاتصال
                val connType = when (connectionType) {
                    "bluetooth" -> ConnectionType.BLUETOOTH
                    "hotspot" -> ConnectionType.HOTSPOT
                    else -> ConnectionType.WIFI_DIRECT
                }
                
                // انتظار الاتصال
                val device = DiscoveredDevice(
                    id = deviceAddress,
                    name = "جهاز",
                    address = deviceAddress,
                    type = connType,
                    signalStrength = 100,
                    deviceType = DeviceType.PHONE
                )
                
                val connected = connectionManager?.connectToDevice(device) ?: false
                
                if (!connected || isCancelled.get()) {
                    _transferState.value = TransferState.Error("فشل الاتصال")
                    stopSelf()
                    return@launch
                }
                
                _transferState.value = TransferState.Transferring
                
                // بدء الإرسال
                val totalSize = files.sumOf { it.size }
                var totalSent = 0L
                val startTime = System.currentTimeMillis()
                
                connectionManager?.sendFiles(files) { progress ->
                    if (isCancelled.get()) {
                        throw CancellationException("تم الإلغاء")
                    }
                    
                    val progressInfo = TransferProgressInfo(
                        type = TransferType.SEND,
                        currentFileName = progress.currentFile,
                        currentFileIndex = progress.currentFileIndex,
                        totalFiles = progress.totalFiles,
                        progress = progress.totalProgress,
                        bytesTransferred = progress.bytesTransferred,
                        totalBytes = progress.totalBytes,
                        speed = progress.speed,
                        remainingTime = calculateRemainingTime(
                            progress.bytesTransferred,
                            progress.totalBytes,
                            startTime
                        )
                    )
                    
                    _currentProgress.value = progressInfo
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        updateNotification(
                            "إرسال: ${progress.currentFile}",
                            (progress.totalProgress * 100).toInt()
                        )
                    }
                }
                
                // اكتمال النقل
                _transferState.value = TransferState.Completed(
                    TransferResult(
                        type = TransferType.SEND,
                        filesCount = files.size,
                        totalSize = totalSize,
                        duration = System.currentTimeMillis() - startTime,
                        success = true
                    )
                )
                
                // حفظ في السجل
                addToHistory(
                    TransferRecord(
                        id = UUID.randomUUID().toString(),
                        type = TransferType.SEND,
                        files = files.map { it.name },
                        totalSize = totalSize,
                        deviceName = device.name,
                        timestamp = Date(),
                        success = true
                    )
                )
                
                showCompletedNotification(
                    "تم الإرسال بنجاح",
                    "تم إرسال ${files.size} ملف"
                )
                
            } catch (e: CancellationException) {
                _transferState.value = TransferState.Cancelled
                showCompletedNotification("تم الإلغاء", "تم إلغاء عملية الإرسال")
            } catch (e: Exception) {
                _transferState.value = TransferState.Error(e.message ?: "خطأ غير معروف")
                showCompletedNotification("فشل الإرسال", e.message ?: "حدث خطأ")
            } finally {
                isTransferring.set(false)
                connectionManager?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }
    
    // ==================== بدء الاستقبال ====================
    private fun startReceiving(connectionType: String) {
        if (isTransferring.get()) return
        
        isTransferring.set(true)
        isCancelled.set(false)
        
        _transferState.value = TransferState.Waiting
        startForeground(NOTIFICATION_ID, createProgressNotification("في انتظار الاتصال...", 0))
        
        transferJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connType = when (connectionType) {
                    "bluetooth" -> ConnectionType.BLUETOOTH
                    "hotspot" -> ConnectionType.HOTSPOT
                    else -> ConnectionType.WIFI_DIRECT
                }
                
                // بدء الاستماع
                connectionManager?.startListening(connType)
                
                // انتظار الاتصال
                connectionManager?.connectionState?.collect { state ->
                    when (state) {
                        is ConnectionState.Connected -> {
                            _transferState.value = TransferState.Transferring
                            updateNotification("جاري الاستقبال...", 0)
                            
                            val startTime = System.currentTimeMillis()
                            val receivedFiles = mutableListOf<ReceivedFile>()
                            
                            connectionManager?.receiveFiles(
                                onFileReceived = { file ->
                                    receivedFiles.add(file)
                                },
                                onProgress = { progress ->
                                    if (isCancelled.get()) {
                                        throw CancellationException("تم الإلغاء")
                                    }
                                    
                                    val progressInfo = TransferProgressInfo(
                                        type = TransferType.RECEIVE,
                                        currentFileName = progress.currentFile,
                                        currentFileIndex = progress.currentFileIndex,
                                        totalFiles = progress.totalFiles,
                                        progress = progress.totalProgress,
                                        bytesTransferred = progress.bytesTransferred,
                                        totalBytes = progress.totalBytes,
                                        speed = progress.speed,
                                        remainingTime = calculateRemainingTime(
                                            progress.bytesTransferred,
                                            progress.totalBytes,
                                            startTime
                                        )
                                    )
                                    
                                    _currentProgress.value = progressInfo
                                    
                                    CoroutineScope(Dispatchers.Main).launch {
                                        updateNotification(
                                            "استقبال: ${progress.currentFile}",
                                            (progress.totalProgress * 100).toInt()
                                        )
                                    }
                                }
                            )
                            
                            val totalSize = receivedFiles.sumOf { it.size }
                            
                            _transferState.value = TransferState.Completed(
                                TransferResult(
                                    type = TransferType.RECEIVE,
                                    filesCount = receivedFiles.size,
                                    totalSize = totalSize,
                                    duration = System.currentTimeMillis() - startTime,
                                    success = true,
                                    receivedFiles = receivedFiles
                                )
                            )
                            
                            addToHistory(
                                TransferRecord(
                                    id = UUID.randomUUID().toString(),
                                    type = TransferType.RECEIVE,
                                    files = receivedFiles.map { it.name },
                                    totalSize = totalSize,
                                    deviceName = state.device.name,
                                    timestamp = Date(),
                                    success = true
                                )
                            )
                            
                            showCompletedNotification(
                                "تم الاستقبال بنجاح",
                                "تم استقبال ${receivedFiles.size} ملف"
                            )
                            
                            return@collect
                        }
                        
                        is ConnectionState.Error -> {
                            throw Exception(state.message)
                        }
                        
                        else -> { }
                    }
                }
                
            } catch (e: CancellationException) {
                _transferState.value = TransferState.Cancelled
                showCompletedNotification("تم الإلغاء", "تم إلغاء عملية الاستقبال")
            } catch (e: Exception) {
                _transferState.value = TransferState.Error(e.message ?: "خطأ غير معروف")
                showCompletedNotification("فشل الاستقبال", e.message ?: "حدث خطأ")
            } finally {
                isTransferring.set(false)
                connectionManager?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }
    
    // ==================== إلغاء النقل ====================
    fun cancelTransfer() {
        isCancelled.set(true)
        transferJob?.cancel()
        connectionManager?.disconnect()
        _transferState.value = TransferState.Cancelled
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    
    // ==================== الإشعارات ====================
    private fun createProgressNotification(text: String, progress: Int): Notification {
        val cancelIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("QuickShare")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إلغاء",
                cancelPendingIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\nتم تصميم التطبيق بواسطة عمر سنجق")
            )
            .build()
    }
    
    private fun updateNotification(text: String, progress: Int) {
        val notification = createProgressNotification(text, progress)
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // تجاهل إذا لم تكن هناك صلاحية
        }
    }
    
    private fun showCompletedNotification(title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // رابط تلجرام المطور
        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_TELEGRAM))
        val telegramPendingIntent = PendingIntent.getActivity(
            this, 1, telegramIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "تواصل مع المطور",
                telegramPendingIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\n✨ تم تصميم التطبيق بواسطة عمر سنجق")
            )
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(COMPLETED_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // تجاهل إذا لم تكن هناك صلاحية
        }
    }
    
    // ==================== دوال مساعدة ====================
    private fun calculateRemainingTime(transferred: Long, total: Long, startTime: Long): Long {
        if (transferred <= 0) return 0
        
        val elapsed = System.currentTimeMillis() - startTime
        val speed = transferred.toDouble() / elapsed
        val remaining = total - transferred
        
        return (remaining / speed).toLong()
    }
    
    private fun addToHistory(record: TransferRecord) {
        val currentHistory = _transferHistory.value.toMutableList()
        currentHistory.add(0, record)
        
        // الاحتفاظ بآخر 50 سجل فقط
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        
        _transferHistory.value = currentHistory
        
        // حفظ في SharedPreferences
        saveHistoryToPrefs(currentHistory)
    }
    
    private fun saveHistoryToPrefs(history: List<TransferRecord>) {
        val prefs = getSharedPreferences("transfer_history", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(history)
        prefs.edit().putString("history", json).apply()
    }
    
    fun loadHistoryFromPrefs() {
        val prefs = getSharedPreferences("transfer_history", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]") ?: "[]"
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<TransferRecord>>() {}.type
        
        val history: List<TransferRecord> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        _transferHistory.value = history
    }
    
    // ==================== الحصول على معلومات المطور ====================
    fun getDeveloperInfo(): DeveloperInfo {
        return DeveloperInfo(
            name = DEVELOPER_NAME,
            telegramUrl = DEVELOPER_TELEGRAM,
            message = "تم تصميم التطبيق بواسطة عمر سنجق"
        )
    }
}

// ==================== نماذج البيانات ====================
enum class TransferType {
    SEND,
    RECEIVE
}

sealed class TransferState {
    object Idle : TransferState()
    object Preparing : TransferState()
    object Waiting : TransferState()
    object Connecting : TransferState()
    object Transferring : TransferState()
    data class Completed(val result: TransferResult) : TransferState()
    data class Error(val message: String) : TransferState()
    object Cancelled : TransferState()
}

data class TransferProgressInfo(
    val type: TransferType,
    val currentFileName: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val progress: Float,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speed: Long,
    val remainingTime: Long
)

data class TransferResult(
    val type: TransferType,
    val filesCount: Int,
    val totalSize: Long,
    val duration: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val receivedFiles: List<ReceivedFile>? = null
)

data class TransferRecord(
    val id: String,
    val type: TransferType,
    val files: List<String>,
    val totalSize: Long,
    val deviceName: String,
    val timestamp: Date,
    val success: Boolean
)

data class DeveloperInfo(
    val name: String,
    val telegramUrl: String,
    val message: String
)

// ==================== امتدادات مساعدة ====================
fun TransferProgressInfo.getSpeedText(): String {
    return when {
        speed >= 1048576 -> String.format("%.1f MB/s", speed / 1048576.0)
        speed >= 1024 -> String.format("%.1f KB/s", speed / 1024.0)
        else -> "$speed B/s"
    }
}

fun TransferProgressInfo.getRemainingTimeText(): String {
    val seconds = remainingTime / 1000
    return when {
        seconds >= 3600 -> String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        seconds >= 60 -> String.format("%d:%02d", seconds / 60, seconds % 60)
        else -> "$seconds ثانية"
    }
}

fun TransferRecord.getFormattedDate(): String {
    val now = Date()
    val diff = now.time - timestamp.time
    
    return when {
        diff < 60 * 1000 -> "الآن"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} دقيقة"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} ساعة"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} يوم"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(timestamp)
    }
}

fun TransferResult.getFormattedDuration(): String {
    val seconds = duration / 1000
    return when {
        seconds >= 60 -> "${seconds / 60} دقيقة و ${seconds % 60} ثانية"
        else -> "$seconds ثانية"
    }
}

fun TransferResult.getAverageSpeed(): String {
    if (duration <= 0) return "0 B/s"
    
    val speed = (totalSize * 1000) / duration
    return when {
        speed >= 1048576 -> String.format("%.1f MB/s", speed / 1048576.0)
        speed >= 1024 -> String.format("%.1f KB/s", speed / 1024.0)
        else -> "$speed B/s"
    }
}