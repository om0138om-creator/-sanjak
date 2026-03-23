// FileManager.kt
// إدارة الملفات والتخزين
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ==================== مدير الملفات الرئيسي ====================
object FileManager {
    
    private const val APP_FOLDER = "QuickShare"
    private const val RECEIVED_FOLDER = "Received"
    private const val TEMP_FOLDER = "Temp"
    private const val BUFFER_SIZE = 8192
    
    // ==================== إنشاء المجلدات ====================
    fun initializeAppFolders(context: Context): Boolean {
        return try {
            val baseDir = getAppDirectory(context)
            val receivedDir = File(baseDir, RECEIVED_FOLDER)
            val tempDir = File(baseDir, TEMP_FOLDER)
            
            baseDir.mkdirs()
            receivedDir.mkdirs()
            tempDir.mkdirs()
            
            // إنشاء ملف .nomedia لمنع ظهور الملفات في المعرض
            val nomediaFile = File(tempDir, ".nomedia")
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // ==================== الحصول على مجلد التطبيق ====================
    fun getAppDirectory(context: Context): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadDir, APP_FOLDER)
    }
    
    fun getReceivedDirectory(context: Context): File {
        return File(getAppDirectory(context), RECEIVED_FOLDER)
    }
    
    fun getTempDirectory(context: Context): File {
        return File(getAppDirectory(context), TEMP_FOLDER)
    }
    
    // ==================== حفظ الملف المستلم ====================
    suspend fun saveReceivedFile(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        fileSize: Long,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val receivedDir = getReceivedDirectory(context)
            var targetFile = File(receivedDir, fileName)
            
            // إذا الملف موجود، أضف رقم
            var counter = 1
            while (targetFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val extension = fileName.substringAfterLast(".", "")
                targetFile = if (extension.isNotEmpty()) {
                    File(receivedDir, "${nameWithoutExt}_$counter.$extension")
                } else {
                    File(receivedDir, "${fileName}_$counter")
                }
                counter++
            }
            
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val progress = if (fileSize > 0) {
                        (totalBytesRead.toFloat() / fileSize).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    onProgress(progress)
                }
                
                outputStream.flush()
            }
            
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ==================== قراءة الملف للإرسال ====================
    fun getFileInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ==================== الحصول على معلومات الملف ====================
    fun getFileDetails(context: Context, uri: Uri): FileDetails? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(name)
                    
                    FileDetails(
                        uri = uri,
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        extension = name.substringAfterLast(".", ""),
                        icon = getFileIcon(mimeType),
                        color = getFileColor(mimeType),
                        lastModified = Date()
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ==================== الحصول على جميع الملفات ====================
    suspend fun getAllFiles(context: Context, type: FileType): List<FileDetails> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileDetails>()
        
        val collection = when (type) {
            FileType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            FileType.DOCUMENT -> MediaStore.Files.getContentUri("external")
            FileType.APP -> null
            FileType.ALL -> MediaStore.Files.getContentUri("external")
        }
        
        if (type == FileType.APP) {
            return@withContext getInstalledApps(context)
        }
        
        collection?.let { uri ->
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            
            val selection = when (type) {
                FileType.DOCUMENT -> "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/pdf' OR " +
                        "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/msword' OR " +
                        "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/vnd.ms-excel' OR " +
                        "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats%' OR " +
                        "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%'"
                else -> null
            }
            
            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val mimeType = cursor.getString(mimeColumn) ?: ""
                    val date = cursor.getLong(dateColumn) * 1000
                    
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    
                    files.add(
                        FileDetails(
                            uri = contentUri,
                            name = name,
                            size = size,
                            mimeType = mimeType,
                            extension = name.substringAfterLast(".", ""),
                            icon = getFileIcon(mimeType),
                            color = getFileColor(mimeType),
                            lastModified = Date(date)
                        )
                    )
                }
            }
        }
        
        files
    }
    
    // ==================== الحصول على التطبيقات المثبتة ====================
    private fun getInstalledApps(context: Context): List<FileDetails> {
        val apps = mutableListOf<FileDetails>()
        val packageManager = context.packageManager
        
        val packages = packageManager.getInstalledApplications(0)
        
        for (appInfo in packages) {
            try {
                // تجاهل تطبيقات النظام
                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) continue
                
                val apkFile = File(appInfo.sourceDir)
                if (apkFile.exists()) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    apps.add(
                        FileDetails(
                            uri = Uri.fromFile(apkFile),
                            name = "$appName.apk",
                            size = apkFile.length(),
                            mimeType = "application/vnd.android.package-archive",
                            extension = "apk",
                            icon = Icons.Filled.Android,
                            color = Color(0xFF03DAC6),
                            lastModified = Date(apkFile.lastModified()),
                            packageName = appInfo.packageName
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return apps.sortedByDescending { it.lastModified }
    }
    
    // ==================== الحصول على الصور المصغرة ====================
    suspend fun getImageThumbnail(context: Context, uri: Uri, size: Int = 200): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } else {
                val inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== ضغط الملفات ====================
    suspend fun compressFiles(
        context: Context,
        files: List<FileDetails>,
        outputName: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val tempDir = getTempDirectory(context)
            val zipFile = File(tempDir, "$outputName.zip")
            
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                var processed = 0
                
                for (file in files) {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        val entry = ZipEntry(file.name)
                        zipOut.putNextEntry(entry)
                        
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                        
                        zipOut.closeEntry()
                    }
                    
                    processed++
                    onProgress(processed.toFloat() / files.size)
                }
            }
            
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ==================== فك ضغط الملفات ====================
    suspend fun extractZip(
        context: Context,
        zipUri: Uri,
        onProgress: (Float, String) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val extractedFiles = mutableListOf<File>()
        
        try {
            val receivedDir = getReceivedDirectory(context)
            
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry?
                    var count = 0
                    
                    while (zipIn.nextEntry.also { entry = it } != null) {
                        entry?.let { zipEntry ->
                            if (!zipEntry.isDirectory) {
                                val outputFile = File(receivedDir, zipEntry.name)
                                outputFile.parentFile?.mkdirs()
                                
                                FileOutputStream(outputFile).use { outputStream ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var bytesRead: Int
                                    
                                    while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                    }
                                }
                                
                                extractedFiles.add(outputFile)
                                count++
                                onProgress(count.toFloat(), zipEntry.name)
                            }
                        }
                        zipIn.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        extractedFiles
    }
    
    // ==================== حساب MD5 للتحقق ====================
    suspend fun calculateMD5(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("MD5")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== حذف الملفات المؤقتة ====================
    fun clearTempFiles(context: Context) {
        try {
            val tempDir = getTempDirectory(context)
            tempDir.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ==================== فتح الملف ====================
    fun openFile(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val mimeType = getMimeTypeFromExtension(file.name)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // ==================== مشاركة الملف ====================
    fun shareFile(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val mimeType = getMimeTypeFromExtension(file.name)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "مشاركة عبر"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // ==================== دوال مساعدة ====================
    private fun getMimeTypeFromExtension(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
    
    private fun getFileIcon(mimeType: String): ImageVector {
        return when {
            mimeType.startsWith("image/") -> Icons.Filled.Image
            mimeType.startsWith("video/") -> Icons.Filled.VideoLibrary
            mimeType.startsWith("audio/") -> Icons.Filled.MusicNote
            mimeType.contains("pdf") -> Icons.Filled.PictureAsPdf
            mimeType.contains("word") || mimeType.contains("document") -> Icons.Filled.Description
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> Icons.Filled.TableChart
            mimeType.contains("powerpoint") || mimeType.contains("presentation") -> Icons.Filled.Slideshow
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") -> Icons.Filled.FolderZip
            mimeType.contains("android") || mimeType.contains("apk") -> Icons.Filled.Android
            mimeType.startsWith("text/") -> Icons.Filled.Article
            else -> Icons.Filled.InsertDriveFile
        }
    }
    
    private fun getFileColor(mimeType: String): Color {
        return when {
            mimeType.startsWith("image/") -> Color(0xFF4CAF50)
            mimeType.startsWith("video/") -> Color(0xFFE91E63)
            mimeType.startsWith("audio/") -> Color(0xFFFF9800)
            mimeType.contains("pdf") -> Color(0xFFF44336)
            mimeType.contains("word") || mimeType.contains("document") -> Color(0xFF2196F3)
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> Color(0xFF4CAF50)
            mimeType.contains("powerpoint") || mimeType.contains("presentation") -> Color(0xFFFF5722)
            mimeType.contains("zip") || mimeType.contains("rar") -> Color(0xFF795548)
            mimeType.contains("android") || mimeType.contains("apk") -> Color(0xFF03DAC6)
            else -> Color(0xFF9E9E9E)
        }
    }
    
    // ==================== الحصول على حجم المجلد ====================
    fun getFolderSize(folder: File): Long {
        var size = 0L
        
        folder.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getFolderSize(file)
            } else {
                file.length()
            }
        }
        
        return size
    }
    
    // ==================== الحصول على عدد الملفات ====================
    fun getFileCount(folder: File): Int {
        var count = 0
        
        folder.listFiles()?.forEach { file ->
            count += if (file.isDirectory) {
                getFileCount(file)
            } else {
                1
            }
        }
        
        return count
    }
}

// ==================== نماذج البيانات ====================
data class FileDetails(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val extension: String,
    val icon: ImageVector,
    val color: Color,
    val lastModified: Date,
    val packageName: String? = null,
    val thumbnail: Bitmap? = null
)

enum class FileType {
    ALL,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    APP
}

// ==================== إحصائيات الملفات ====================
data class FileStatistics(
    val totalFiles: Int,
    val totalSize: Long,
    val imageCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val documentCount: Int,
    val appCount: Int,
    val otherCount: Int
)

// ==================== حساب الإحصائيات ====================
suspend fun calculateFileStatistics(context: Context): FileStatistics = withContext(Dispatchers.IO) {
    var totalFiles = 0
    var totalSize = 0L
    var imageCount = 0
    var videoCount = 0
    var audioCount = 0
    var documentCount = 0
    var appCount = 0
    var otherCount = 0
    
    val receivedDir = FileManager.getReceivedDirectory(context)
    
    receivedDir.listFiles()?.forEach { file ->
        if (file.isFile) {
            totalFiles++
            totalSize += file.length()
            
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: ""
            
            when {
                mimeType.startsWith("image/") -> imageCount++
                mimeType.startsWith("video/") -> videoCount++
                mimeType.startsWith("audio/") -> audioCount++
                mimeType.contains("pdf") || mimeType.contains("document") || 
                mimeType.contains("word") || mimeType.contains("excel") -> documentCount++
                mimeType.contains("android") || file.extension == "apk" -> appCount++
                else -> otherCount++
            }
        }
    }
    
    FileStatistics(
        totalFiles = totalFiles,
        totalSize = totalSize,
        imageCount = imageCount,
        videoCount = videoCount,
        audioCount = audioCount,
        documentCount = documentCount,
        appCount = appCount,
        otherCount = otherCount
    )
}

// ==================== تنسيق التاريخ ====================
fun formatDate(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    
    return when {
        diff < 60 * 1000 -> "الآن"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} دقيقة"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} ساعة"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} يوم"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }
}

// ==================== التحقق من نوع الملف ====================
fun isImageFile(mimeType: String) = mimeType.startsWith("image/")
fun isVideoFile(mimeType: String) = mimeType.startsWith("video/")
fun isAudioFile(mimeType: String) = mimeType.startsWith("audio/")
fun isApkFile(mimeType: String) = mimeType.contains("android") || mimeType.contains("apk")
fun isDocumentFile(mimeType: String) = mimeType.contains("pdf") || 
    mimeType.contains("document") || mimeType.contains("word") || 
    mimeType.contains("excel") || mimeType.contains("text")
fun isArchiveFile(mimeType: String) = mimeType.contains("zip") || 
    mimeType.contains("rar") || mimeType.contains("7z") || mimeType.contains("tar")