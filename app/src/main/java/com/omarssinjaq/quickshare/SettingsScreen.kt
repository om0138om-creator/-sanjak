// SettingsScreen.kt
// شاشة الإعدادات الكاملة
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ==================== شاشة الإعدادات الكاملة ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // حالات الإعدادات
    var deviceName by remember { mutableStateOf(getDeviceName(context)) }
    var savePath by remember { mutableStateOf(getSavePath(context)) }
    var notificationsEnabled by remember { mutableStateOf(getNotificationsEnabled(context)) }
    var soundEnabled by remember { mutableStateOf(getSoundEnabled(context)) }
    var vibrationEnabled by remember { mutableStateOf(getVibrationEnabled(context)) }
    var darkTheme by remember { mutableStateOf(getDarkTheme(context)) }
    var autoAccept by remember { mutableStateOf(getAutoAccept(context)) }
    var keepScreenOn by remember { mutableStateOf(getKeepScreenOn(context)) }
    var showHiddenFiles by remember { mutableStateOf(getShowHiddenFiles(context)) }
    var compressionEnabled by remember { mutableStateOf(getCompressionEnabled(context)) }
    var wifiOnly by remember { mutableStateOf(getWifiOnly(context)) }
    
    // نوافذ الحوار
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showSavePathDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    // حساب حجم الكاش
    var cacheSize by remember { mutableStateOf(calculateCacheSize(context)) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "الإعدادات",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==================== بطاقة الملف الشخصي ====================
            item {
                ProfileCard(
                    deviceName = deviceName,
                    onEditClick = { showDeviceNameDialog = true }
                )
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // ==================== إعدادات النقل ====================
            item {
                SettingsSectionHeader(
                    title = "إعدادات النقل",
                    icon = Icons.Filled.SwapHoriz
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.Folder,
                        title = "مجلد الحفظ",
                        subtitle = savePath,
                        onClick = { showSavePathDialog = true }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.Compress,
                        title = "ضغط الملفات",
                        subtitle = "ضغط الملفات الكبيرة قبل الإرسال",
                        checked = compressionEnabled,
                        onCheckedChange = {
                            compressionEnabled = it
                            saveCompressionEnabled(context, it)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.CheckCircle,
                        title = "قبول تلقائي",
                        subtitle = "قبول الملفات تلقائياً من الأجهزة المعروفة",
                        checked = autoAccept,
                        onCheckedChange = {
                            autoAccept = it
                            saveAutoAccept(context, it)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.Wifi,
                        title = "WiFi فقط",
                        subtitle = "النقل عبر WiFi Direct فقط",
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            saveWifiOnly(context, it)
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // ==================== إعدادات الإشعارات ====================
            item {
                SettingsSectionHeader(
                    title = "الإشعارات",
                    icon = Icons.Filled.Notifications
                )
            }
            
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Filled.Notifications,
                        title = "الإشعارات",
                        subtitle = "إظهار إشعارات النقل",
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                            saveNotificationsEnabled(context, it)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.VolumeUp,
                        title = "الصوت",
                        subtitle = "تشغيل صوت عند اكتمال النقل",
                        checked = soundEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            saveSoundEnabled(context, it)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.Vibration,
                        title = "الاهتزاز",
                        subtitle = "اهتزاز عند اكتمال النقل",
                        checked = vibrationEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            saveVibrationEnabled(context, it)
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // ==================== إعدادات العرض ====================
            item {
                SettingsSectionHeader(
                    title = "العرض",
                    icon = Icons.Filled.Palette
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.DarkMode,
                        title = "المظهر",
                        subtitle = when (darkTheme) {
                            ThemeMode.LIGHT -> "فاتح"
                            ThemeMode.DARK -> "داكن"
                            ThemeMode.SYSTEM -> "تلقائي (حسب النظام)"
                        },
                        onClick = { showThemeDialog = true }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.ScreenLockPortrait,
                        title = "إبقاء الشاشة مضاءة",
                        subtitle = "منع إطفاء الشاشة أثناء النقل",
                        checked = keepScreenOn,
                        onCheckedChange = {
                            keepScreenOn = it
                            saveKeepScreenOn(context, it)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsSwitchItem(
                        icon = Icons.Filled.VisibilityOff,
                        title = "إظهار الملفات المخفية",
                        subtitle = "عرض الملفات التي تبدأ بنقطة",
                        checked = showHiddenFiles,
                        onCheckedChange = {
                            showHiddenFiles = it
                            saveShowHiddenFiles(context, it)
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // ==================== التخزين ====================
            item {
                SettingsSectionHeader(
                    title = "التخزين",
                    icon = Icons.Filled.Storage
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.FolderDelete,
                        title = "مسح الملفات المؤقتة",
                        subtitle = "الحجم الحالي: $cacheSize",
                        onClick = { showClearCacheDialog = true }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        icon = Icons.Filled.Refresh,
                        title = "إعادة ضبط الإعدادات",
                        subtitle = "استعادة الإعدادات الافتراضية",
                        onClick = { showResetDialog = true },
                        textColor = Color.Red.copy(alpha = 0.8f)
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // ==================== حول التطبيق ====================
            item {
                SettingsSectionHeader(
                    title = "حول التطبيق",
                    icon = Icons.Filled.Info
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = "عن التطبيق",
                        subtitle = "الإصدار 1.0.0",
                        onClick = { showAboutDialog = true }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        icon = Icons.Filled.Star,
                        title = "تقييم التطبيق",
                        subtitle = "قيّم التطبيق على المتجر",
                        onClick = {
                            // فتح صفحة المتجر
                            Toast.makeText(context, "شكراً لدعمك!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        icon = Icons.Filled.Share,
                        title = "مشاركة التطبيق",
                        subtitle = "شارك التطبيق مع أصدقائك",
                        onClick = {
                            shareApp(context)
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        icon = Icons.Filled.Policy,
                        title = "سياسة الخصوصية",
                        subtitle = "اقرأ سياسة الخصوصية",
                        onClick = {
                            // فتح صفحة الخصوصية
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
            
            // ==================== بطاقة المطور ====================
            item {
                DeveloperCard()
            }
            
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
    
    // ==================== نوافذ الحوار ====================
    
    // نافذة تغيير اسم الجهاز
    if (showDeviceNameDialog) {
        DeviceNameDialog(
            currentName = deviceName,
            onDismiss = { showDeviceNameDialog = false },
            onConfirm = { newName ->
                deviceName = newName
                saveDeviceName(context, newName)
                showDeviceNameDialog = false
            }
        )
    }
    
    // نافذة اختيار مجلد الحفظ
    if (showSavePathDialog) {
        SavePathDialog(
            currentPath = savePath,
            onDismiss = { showSavePathDialog = false },
            onConfirm = { newPath ->
                savePath = newPath
                saveSavePath(context, newPath)
                showSavePathDialog = false
            }
        )
    }
    
    // نافذة اختيار المظهر
    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = darkTheme,
            onDismiss = { showThemeDialog = false },
            onConfirm = { theme ->
                darkTheme = theme
                saveDarkTheme(context, theme)
                showThemeDialog = false
            }
        )
    }
    
    // نافذة مسح الكاش
    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "مسح الملفات المؤقتة",
            message = "سيتم حذف جميع الملفات المؤقتة ($cacheSize).\nهذا لن يؤثر على الملفات المستلمة.",
            confirmText = "مسح",
            confirmColor = Color.Red,
            onDismiss = { showClearCacheDialog = false },
            onConfirm = {
                FileManager.clearTempFiles(context)
                cacheSize = "0 B"
                showClearCacheDialog = false
                Toast.makeText(context, "تم مسح الملفات المؤقتة", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // نافذة إعادة الضبط
    if (showResetDialog) {
        ConfirmDialog(
            title = "إعادة ضبط الإعدادات",
            message = "سيتم استعادة جميع الإعدادات إلى القيم الافتراضية.\nهل أنت متأكد؟",
            confirmText = "إعادة ضبط",
            confirmColor = Color.Red,
            onDismiss = { showResetDialog = false },
            onConfirm = {
                resetAllSettings(context)
                // تحديث القيم
                deviceName = getDeviceName(context)
                savePath = getSavePath(context)
                notificationsEnabled = true
                soundEnabled = true
                vibrationEnabled = true
                darkTheme = ThemeMode.SYSTEM
                autoAccept = false
                keepScreenOn = true
                showHiddenFiles = false
                compressionEnabled = false
                wifiOnly = false
                showResetDialog = false
                Toast.makeText(context, "تم إعادة ضبط الإعدادات", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // نافذة حول التطبيق
    if (showAboutDialog) {
        AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

// ==================== بطاقة الملف الشخصي ====================
@Composable
fun ProfileCard(
    deviceName: String,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF5B54E8),
                            Color(0xFF4A45D1)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // أيقونة الجهاز
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(35.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(15.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "اسم الجهاز",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        deviceName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "جاهز للمشاركة",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "تعديل",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==================== مكونات الإعدادات ====================
@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF6C63FF),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF6C63FF)
        )
    }
}

@Composable
fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF6C63FF),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Color(0xFF6C63FF) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.White else Color.Gray
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6C63FF),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF2D333B)
            )
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = Color.Gray.copy(alpha = 0.1f),
        modifier = Modifier.padding(horizontal = 56.dp)
    )
}

// ==================== بطاقة المطور ====================
@Composable
fun DeveloperCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF6C63FF).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // صورة المطور
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6C63FF), Color(0xFF03DAC6))
                        )
                    )
                    .border(2.dp, Color(0xFF6C63FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://t.me/i/userpic/320/Om9r0.jpg",
                    contentDescription = "صورة المطور",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(15.dp))
            
            Text(
                "تم تصميم التطبيق بواسطة",
                fontSize = 13.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(5.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "عمر سنجق",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    tint = Color(0xFF1DA1F2),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(15.dp))
            
            // زر التلجرام
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("تواصل عبر تلجرام", fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                "© 2024 جميع الحقوق محفوظة",
                fontSize = 11.sp,
                color = Color.Gray.copy(alpha = 0.6f)
            )
        }
    }
}

// ==================== نوافذ الحوار ====================
@Composable
fun DeviceNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("اسم الجهاز", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "أدخل اسماً يظهر للأجهزة الأخرى",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(15.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6C63FF),
                        cursorColor = Color(0xFF6C63FF)
                    ),
                    singleLine = true,
                    placeholder = { Text("اسم الجهاز") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

@Composable
fun SavePathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val paths = listOf(
        "Download/QuickShare",
        "Download/QuickShare/Received",
        "Documents/QuickShare",
        "QuickShare"
    )
    var selectedPath by remember { mutableStateOf(currentPath) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("مجلد الحفظ", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                paths.forEach { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPath = path }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPath == path,
                            onClick = { selectedPath = path },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF6C63FF)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(path)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedPath) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ThemeDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onConfirm: (ThemeMode) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("المظهر", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTheme = theme }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTheme == theme,
                            onClick = { selectedTheme = theme },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF6C63FF)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            when (theme) {
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            },
                            contentDescription = null,
                            tint = Color(0xFF6C63FF)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            when (theme) {
                                ThemeMode.LIGHT -> "فاتح"
                                ThemeMode.DARK -> "داكن"
                                ThemeMode.SYSTEM -> "تلقائي (حسب النظام)"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTheme) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(message, color = Color.Gray, lineHeight = 22.sp)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // شعار التطبيق
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6C63FF), Color(0xFF03DAC6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(15.dp))
                
                Text(
                    "QuickShare",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    "الإصدار 1.0.0",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // معلومات
                InfoRow(icon = Icons.Filled.Android, label = "الحد الأدنى", value = "Android 7.0")
                InfoRow(icon = Icons.Filled.Update, label = "آخر تحديث", value = "ديسمبر 2024")
                InfoRow(icon = Icons.Filled.Code, label = "لغة البرمجة", value = "Kotlin")
                
                Spacer(modifier = Modifier.height(20.dp))
                
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    "تم تصميم التطبيق بواسطة",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(5.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                        context.startActivity(intent)
                    }
                ) {
                    Text(
                        "عمر سنجق",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إغلاق")
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF6C63FF),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== نموذج المظهر ====================
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

// ==================== دوال الإعدادات ====================
private const val PREFS_NAME = "quickshare_settings"

fun getDeviceName(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString("device_name", "QuickShare-${Build.MODEL}") ?: "QuickShare Device"
}

fun saveDeviceName(context: Context, name: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString("device_name", name).apply()
}

fun getSavePath(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString("save_path", "Download/QuickShare") ?: "Download/QuickShare"
}

fun saveSavePath(context: Context, path: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString("save_path", path).apply()
}

fun getNotificationsEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("notifications_enabled", true)
}

fun saveNotificationsEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("notifications_enabled", enabled).apply()
}

fun getSoundEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("sound_enabled", true)
}

fun saveSoundEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("sound_enabled", enabled).apply()
}

fun getVibrationEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("vibration_enabled", true)
}

fun saveVibrationEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("vibration_enabled", enabled).apply()
}

fun getDarkTheme(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return ThemeMode.valueOf(prefs.getString("dark_theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
}

fun saveDarkTheme(context: Context, theme: ThemeMode) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString("dark_theme", theme.name).apply()
}

fun getAutoAccept(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("auto_accept", false)
}

fun saveAutoAccept(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("auto_accept", enabled).apply()
}

fun getKeepScreenOn(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("keep_screen_on", true)
}

fun saveKeepScreenOn(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("keep_screen_on", enabled).apply()
}

fun getShowHiddenFiles(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("show_hidden_files", false)
}

fun saveShowHiddenFiles(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("show_hidden_files", enabled).apply()
}

fun getCompressionEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("compression_enabled", false)
}

fun saveCompressionEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("compression_enabled", enabled).apply()
}

fun getWifiOnly(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("wifi_only", false)
}

fun saveWifiOnly(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("wifi_only", enabled).apply()
}

fun resetAllSettings(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
}

fun calculateCacheSize(context: Context): String {
    val cacheDir = context.cacheDir
    val size = getFolderSize(cacheDir)
    return formatFileSize(size)
}

fun getFolderSize(folder: java.io.File): Long {
    var size = 0L
    folder.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getFolderSize(file) else file.length()
    }
    return size
}

fun shareApp(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "QuickShare - مشاركة الملفات")
        putExtra(
            Intent.EXTRA_TEXT,
            "جرب تطبيق QuickShare لمشاركة الملفات بسرعة!\n\n" +
            "تم تصميم التطبيق بواسطة عمر سنجق\n" +
            "https://t.me/Om9r0"
        )
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة عبر"))
}