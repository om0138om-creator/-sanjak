// SendActivity.kt
// شاشة إرسال الملفات
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class SendActivity : ComponentActivity() {
    
    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تهيئة WiFi Direct
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager?.initialize(this, mainLooper, null)
        
        // تهيئة البلوتوث
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        setContent {
            QuickShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D1117)
                ) {
                    SendScreen(
                        onBack = { finish() },
                        wifiP2pManager = wifiP2pManager,
                        channel = channel,
                        bluetoothAdapter = bluetoothAdapter
                    )
                }
            }
        }
    }
}

// ==================== شاشة الإرسال ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    wifiP2pManager: WifiP2pManager?,
    channel: WifiP2pManager.Channel?,
    bluetoothAdapter: BluetoothAdapter?
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedFiles by remember { mutableStateOf<List<SelectedFile>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("all") }
    var isSearching by remember { mutableStateOf(false) }
    var availableDevices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var transferStatus by remember { mutableStateOf("") }
    var connectionType by remember { mutableStateOf("wifi") } // wifi أو bluetooth
    
    // منتقي الملفات
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val files = uris.mapNotNull { uri ->
            getFileInfo(context, uri)
        }
        selectedFiles = selectedFiles + files
    }
    
    // منتقي ملفات متعددة الأنواع
    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val files = uris.mapNotNull { uri ->
            getFileInfo(context, uri)
        }
        selectedFiles = selectedFiles + files
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentStep) {
                            0 -> "اختر الملفات"
                            1 -> "اختر طريقة الاتصال"
                            2 -> "البحث عن الأجهزة"
                            3 -> "جاري الإرسال"
                            else -> "إرسال"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) currentStep-- else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // مؤشر التقدم
            StepIndicator(currentStep = currentStep)
            
            when (currentStep) {
                0 -> FileSelectionStep(
                    selectedFiles = selectedFiles,
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it },
                    onPickFiles = { type ->
                        when (type) {
                            "images" -> filePickerLauncher.launch("image/*")
                            "videos" -> filePickerLauncher.launch("video/*")
                            "audio" -> filePickerLauncher.launch("audio/*")
                            "apps" -> filePickerLauncher.launch("application/vnd.android.package-archive")
                            else -> multipleFilePickerLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    onRemoveFile = { file ->
                        selectedFiles = selectedFiles.filter { it != file }
                    },
                    onNext = {
                        if (selectedFiles.isNotEmpty()) {
                            currentStep = 1
                        } else {
                            Toast.makeText(context, "اختر ملفاً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                1 -> ConnectionTypeStep(
                    selectedType = connectionType,
                    onTypeSelected = { connectionType = it },
                    onNext = {
                        currentStep = 2
                        isSearching = true
                        // بدء البحث عن الأجهزة
                        searchForDevices(
                            context = context,
                            connectionType = connectionType,
                            wifiP2pManager = wifiP2pManager,
                            channel = channel,
                            bluetoothAdapter = bluetoothAdapter,
                            onDevicesFound = { devices ->
                                availableDevices = devices
                                isSearching = false
                            }
                        )
                    }
                )
                
                2 -> DeviceSearchStep(
                    isSearching = isSearching,
                    devices = availableDevices,
                    connectionType = connectionType,
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        currentStep = 3
                        // بدء النقل
                        startFileTransfer(
                            context = context,
                            device = device,
                            files = selectedFiles,
                            connectionType = connectionType,
                            onProgress = { progress, status ->
                                transferProgress = progress
                                transferStatus = status
                            },
                            onComplete = {
                                Toast.makeText(context, "تم الإرسال بنجاح!", Toast.LENGTH_LONG).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, "خطأ: $error", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onRefresh = {
                        isSearching = true
                        availableDevices = emptyList()
                        searchForDevices(
                            context = context,
                            connectionType = connectionType,
                            wifiP2pManager = wifiP2pManager,
                            channel = channel,
                            bluetoothAdapter = bluetoothAdapter,
                            onDevicesFound = { devices ->
                                availableDevices = devices
                                isSearching = false
                            }
                        )
                    }
                )
                
                3 -> TransferProgressStep(
                    progress = transferProgress,
                    status = transferStatus,
                    files = selectedFiles,
                    device = selectedDevice,
                    onCancel = {
                        currentStep = 0
                        selectedFiles = emptyList()
                        transferProgress = 0f
                    }
                )
            }
        }
    }
}

// ==================== مؤشر الخطوات ====================
@Composable
fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val steps = listOf("الملفات", "الاتصال", "الأجهزة", "الإرسال")
        
        steps.forEachIndexed { index, step ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= currentStep) Color(0xFF6C63FF)
                            else Color(0xFF2D333B)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < currentStep) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    step,
                    fontSize = 11.sp,
                    color = if (index <= currentStep) Color(0xFF6C63FF) else Color.Gray
                )
            }
            
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(3.dp)
                        .background(
                            if (index < currentStep) Color(0xFF6C63FF)
                            else Color(0xFF2D333B)
                        )
                )
            }
        }
    }
}

// ==================== خطوة اختيار الملفات ====================
@Composable
fun FileSelectionStep(
    selectedFiles: List<SelectedFile>,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    onPickFiles: (String) -> Unit,
    onRemoveFile: (SelectedFile) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // فئات الملفات
        Text(
            "نوع الملفات",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val categories = listOf(
                CategoryItem("all", "الكل", Icons.Filled.Folder, Color(0xFF6C63FF)),
                CategoryItem("images", "الصور", Icons.Filled.Image, Color(0xFF4CAF50)),
                CategoryItem("videos", "الفيديو", Icons.Filled.VideoLibrary, Color(0xFFE91E63)),
                CategoryItem("audio", "الصوت", Icons.Filled.MusicNote, Color(0xFFFF9800)),
                CategoryItem("apps", "التطبيقات", Icons.Filled.Android, Color(0xFF03DAC6)),
                CategoryItem("documents", "المستندات", Icons.Filled.Description, Color(0xFF2196F3))
            )
            
            items(categories) { category ->
                CategoryChip(
                    category = category,
                    isSelected = selectedCategory == category.id,
                    onClick = {
                        onCategoryChange(category.id)
                        onPickFiles(category.id)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // زر إضافة ملفات
        OutlinedButton(
            onClick = { onPickFiles(selectedCategory) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(2.dp, Color(0xFF6C63FF).copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF6C63FF)
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("إضافة ملفات", fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // قائمة الملفات المختارة
        if (selectedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "الملفات المختارة (${selectedFiles.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatFileSize(selectedFiles.sumOf { it.size }),
                    fontSize = 14.sp,
                    color = Color(0xFF6C63FF)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(selectedFiles) { file ->
                    SelectedFileCard(
                        file = file,
                        onRemove = { onRemoveFile(file) }
                    )
                }
            }
        } else {
            // رسالة فارغة
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    Text(
                        "اختر الملفات للمشاركة",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        }
        
        // زر المتابعة
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF)
            ),
            enabled = selectedFiles.isNotEmpty()
        ) {
            Text("متابعة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null)
        }
    }
}

// ==================== خطوة اختيار نوع الاتصال ====================
@Composable
fun ConnectionTypeStep(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            "اختر طريقة الاتصال",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // WiFi Direct
        ConnectionTypeCard(
            icon = Icons.Filled.Wifi,
            title = "WiFi Direct",
            description = "سرعة فائقة تصل إلى 40 ميجابايت/ث\nلا يحتاج إنترنت أو راوتر",
            isSelected = selectedType == "wifi",
            color = Color(0xFF6C63FF),
            onClick = { onTypeSelected("wifi") }
        )
        
        Spacer(modifier = Modifier.height(15.dp))
        
        // Bluetooth
        ConnectionTypeCard(
            icon = Icons.Filled.Bluetooth,
            title = "البلوتوث",
            description = "اتصال مستقر وآمن\nمناسب للملفات الصغيرة",
            isSelected = selectedType == "bluetooth",
            color = Color(0xFF03DAC6),
            onClick = { onTypeSelected("bluetooth") }
        )
        
        Spacer(modifier = Modifier.height(15.dp))
        
        // Hotspot
        ConnectionTypeCard(
            icon = Icons.Filled.WifiTethering,
            title = "نقطة الاتصال",
            description = "إنشاء شبكة خاصة للمشاركة\nسرعة عالية جداً",
            isSelected = selectedType == "hotspot",
            color = Color(0xFFFF9800),
            onClick = { onTypeSelected("hotspot") }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF)
            )
        ) {
            Text("بحث عن الأجهزة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Icon(Icons.Filled.Search, contentDescription = null)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // توقيع المطور
        DeveloperSignatureSmall()
    }
}

@Composable
fun ConnectionTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    color,
                    RoundedCornerShape(20.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.1f) else Color(0xFF161B22)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(30.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(15.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else Color.White
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ==================== خطوة البحث عن الأجهزة ====================
@Composable
fun DeviceSearchStep(
    isSearching: Boolean,
    devices: List<DeviceInfo>,
    connectionType: String,
    onDeviceSelected: (DeviceInfo) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSearching) {
            Spacer(modifier = Modifier.height(50.dp))
            
            // رسوم متحركة للبحث
            SearchingAnimation()
            
            Spacer(modifier = Modifier.height(30.dp))
            
            Text(
                "جاري البحث عن الأجهزة القريبة...",
                fontSize = 16.sp,
                color = Color.Gray
            )
            
            Text(
                "تأكد من تفعيل ${if (connectionType == "bluetooth") "البلوتوث" else "الواي فاي"} على الجهاز الآخر",
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "الأجهزة المتاحة (${devices.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "تحديث",
                        tint = Color(0xFF6C63FF)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(15.dp))
            
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.DevicesOther,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(15.dp))
                        Text(
                            "لم يتم العثور على أجهزة",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onRefresh) {
                            Text("إعادة البحث", color = Color(0xFF6C63FF))
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceSelected(device) }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // توقيع المطور
        DeveloperSignatureSmall()
    }
}

@Composable
fun SearchingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "search")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(150.dp)
    ) {
        // الدوائر المتحركة
        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size((80 + i * 30).dp * scale)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF6C63FF).copy(alpha = alpha / (i + 1))
                    )
            )
        }
        
        // أيقونة المركز
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color(0xFF6C63FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(35.dp)
            )
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C63FF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (device.type) {
                        "phone" -> Icons.Filled.PhoneAndroid
                        "tablet" -> Icons.Filled.Tablet
                        else -> Icons.Filled.Devices
                    },
                    contentDescription = null,
                    tint = Color(0xFF6C63FF)
                )
            }
            
            Spacer(modifier = Modifier.width(15.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    device.address,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            // قوة الإشارة
            Icon(
                when {
                    device.signalStrength > 75 -> Icons.Filled.SignalWifi4Bar
                    device.signalStrength > 50 -> Icons.Filled.NetworkWifi3Bar
                    device.signalStrength > 25 -> Icons.Filled.NetworkWifi2Bar
                    else -> Icons.Filled.NetworkWifi1Bar
                },
                contentDescription = null,
                tint = Color(0xFF03DAC6)
            )
        }
    }
}

// ==================== خطوة تقدم النقل ====================
@Composable
fun TransferProgressStep(
    progress: Float,
    status: String,
    files: List<SelectedFile>,
    device: DeviceInfo?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        
        // دائرة التقدم
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(200.dp),
                color = Color(0xFF6C63FF),
                trackColor = Color(0xFF2D333B),
                strokeWidth = 12.dp
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6C63FF)
                )
                Text(
                    status,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // معلومات النقل
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF6C63FF)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("الإرسال إلى", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            device?.name ?: "جهاز غير معروف",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Divider(
                    color = Color.Gray.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 15.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color(0xFF03DAC6)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("عدد الملفات", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            "${files.size} ملف (${formatFileSize(files.sumOf { it.size })})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // زر الإلغاء
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("إلغاء")
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        DeveloperSignatureSmall()
    }
}

// ==================== مكونات مساعدة ====================
@Composable
fun CategoryChip(
    category: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) category.color else Color(0xFF161B22)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else category.color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                category.name,
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun SelectedFileCard(
    file: SelectedFile,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    file.icon,
                    contentDescription = null,
                    tint = file.color,
                    modifier = Modifier.size(30.dp)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "حذف",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                file.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                formatFileSize(file.size),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DeveloperSignatureSmall() {
    val context = LocalContext.current
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "تم تصميم التطبيق بواسطة ",
            fontSize = 12.sp,
            color = Color.Gray.copy(alpha = 0.7f)
        )
        Text(
            "عمر سنجق",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                context.startActivity(intent)
            }
        )
    }
}

// ==================== نماذج البيانات ====================
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val type: String,
    val icon: ImageVector,
    val color: Color
)

data class CategoryItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color
)

data class DeviceInfo(
    val name: String,
    val address: String,
    val type: String,
    val signalStrength: Int
)

// ==================== دوال مساعدة ====================
fun getFileInfo(context: Context, uri: Uri): SelectedFile? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "ملف"
                val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                val type = context.contentResolver.getType(uri) ?: ""
                
                val (icon, color) = when {
                    type.startsWith("image/") -> Icons.Filled.Image to Color(0xFF4CAF50)
                    type.startsWith("video/") -> Icons.Filled.VideoLibrary to Color(0xFFE91E63)
                    type.startsWith("audio/") -> Icons.Filled.MusicNote to Color(0xFFFF9800)
                    type.contains("pdf") -> Icons.Filled.PictureAsPdf to Color(0xFFF44336)
                    type.contains("apk") || type.contains("android") -> Icons.Filled.Android to Color(0xFF03DAC6)
                    else -> Icons.Filled.InsertDriveFile to Color(0xFF2196F3)
                }
                
                SelectedFile(uri, name, size, type, icon, color)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size >= 1073741824 -> String.format("%.2f GB", size / 1073741824.0)
        size >= 1048576 -> String.format("%.2f MB", size / 1048576.0)
        size >= 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> "$size B"
    }
}

fun searchForDevices(
    context: Context,
    connectionType: String,
    wifiP2pManager: WifiP2pManager?,
    channel: WifiP2pManager.Channel?,
    bluetoothAdapter: BluetoothAdapter?,
    onDevicesFound: (List<DeviceInfo>) -> Unit
) {
    // محاكاة البحث عن الأجهزة
    Handler(Looper.getMainLooper()).postDelayed({
        val mockDevices = listOf(
            DeviceInfo("Samsung Galaxy S21", "AA:BB:CC:DD:EE:FF", "phone", 85),
            DeviceInfo("Xiaomi Note 10", "11:22:33:44:55:66", "phone", 72),
            DeviceInfo("OnePlus 9 Pro", "77:88:99:AA:BB:CC", "phone", 60)
        )
        onDevicesFound(mockDevices)
    }, 3000)
}

fun startFileTransfer(
    context: Context,
    device: DeviceInfo,
    files: List<SelectedFile>,
    connectionType: String,
    onProgress: (Float, String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    // محاكاة نقل الملفات
    CoroutineScope(Dispatchers.Main).launch {
        var progress = 0f
        val totalFiles = files.size
        
        files.forEachIndexed { index, file ->
            val fileProgress = (index.toFloat() / totalFiles)
            
            // محاكاة تقدم الملف
            for (i in 0..100 step 5) {
                delay(50)
                progress = fileProgress + (i / 100f / totalFiles)
                onProgress(progress, "إرسال: ${file.name}")
            }
        }
        
        onProgress(1f, "اكتمل الإرسال!")
        delay(500)
        onComplete()
    }
}