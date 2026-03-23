// ReceiveActivity.kt
// شاشة استقبال الملفات
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.text.SimpleDateFormat
import java.util.*

class ReceiveActivity : ComponentActivity() {
    
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
                    ReceiveScreen(
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

// ==================== شاشة الاستقبال ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onBack: () -> Unit,
    wifiP2pManager: WifiP2pManager?,
    channel: WifiP2pManager.Channel?,
    bluetoothAdapter: BluetoothAdapter?
) {
    val context = LocalContext.current
    var currentState by remember { mutableStateOf(ReceiveState.IDLE) }
    var connectionType by remember { mutableStateOf("wifi") }
    var connectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var receivingFiles by remember { mutableStateOf<List<ReceivingFile>>(emptyList()) }
    var receivedFiles by remember { mutableStateOf<List<ReceivedFile>>(emptyList()) }
    var totalProgress by remember { mutableFloatStateOf(0f) }
    var currentFileName by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("QuickShare-${(1000..9999).random()}") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentState) {
                            ReceiveState.IDLE -> "استقبال الملفات"
                            ReceiveState.WAITING -> "في انتظار الاتصال"
                            ReceiveState.CONNECTED -> "متصل"
                            ReceiveState.RECEIVING -> "جاري الاستقبال"
                            ReceiveState.COMPLETED -> "اكتمل الاستقبال"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    if (currentState == ReceiveState.WAITING) {
                        IconButton(onClick = {
                            currentState = ReceiveState.IDLE
                        }) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "إيقاف",
                                tint = Color.Red
                            )
                        }
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
            when (currentState) {
                ReceiveState.IDLE -> {
                    IdleContent(
                        deviceName = deviceName,
                        onDeviceNameChange = { deviceName = it },
                        connectionType = connectionType,
                        onConnectionTypeChange = { connectionType = it },
                        onStartReceiving = {
                            currentState = ReceiveState.WAITING
                            // بدء الاستماع للاتصالات
                            startListening(
                                context = context,
                                connectionType = connectionType,
                                wifiP2pManager = wifiP2pManager,
                                channel = channel,
                                bluetoothAdapter = bluetoothAdapter,
                                onDeviceConnected = { device ->
                                    connectedDevice = device
                                    currentState = ReceiveState.CONNECTED
                                },
                                onFileReceiveStart = { files ->
                                    receivingFiles = files
                                    currentState = ReceiveState.RECEIVING
                                },
                                onProgress = { progress, fileName ->
                                    totalProgress = progress
                                    currentFileName = fileName
                                },
                                onFileReceived = { file ->
                                    receivedFiles = receivedFiles + file
                                },
                                onComplete = {
                                    currentState = ReceiveState.COMPLETED
                                },
                                onError = { error ->
                                    // معالجة الخطأ
                                }
                            )
                        }
                    )
                }
                
                ReceiveState.WAITING -> {
                    WaitingContent(
                        deviceName = deviceName,
                        connectionType = connectionType,
                        onCancel = {
                            currentState = ReceiveState.IDLE
                        }
                    )
                }
                
                ReceiveState.CONNECTED -> {
                    ConnectedContent(
                        device = connectedDevice,
                        onAccept = {
                            // قبول الملفات
                            currentState = ReceiveState.RECEIVING
                            // محاكاة استقبال الملفات
                            simulateReceiving(
                                onProgress = { progress, fileName ->
                                    totalProgress = progress
                                    currentFileName = fileName
                                },
                                onFileReceived = { file ->
                                    receivedFiles = receivedFiles + file
                                },
                                onComplete = {
                                    currentState = ReceiveState.COMPLETED
                                }
                            )
                        },
                        onReject = {
                            currentState = ReceiveState.WAITING
                            connectedDevice = null
                        }
                    )
                }
                
                ReceiveState.RECEIVING -> {
                    ReceivingContent(
                        progress = totalProgress,
                        currentFileName = currentFileName,
                        receivedFiles = receivedFiles,
                        device = connectedDevice,
                        onCancel = {
                            currentState = ReceiveState.IDLE
                            receivedFiles = emptyList()
                            totalProgress = 0f
                        }
                    )
                }
                
                ReceiveState.COMPLETED -> {
                    CompletedContent(
                        receivedFiles = receivedFiles,
                        onDone = {
                            onBack()
                        },
                        onReceiveMore = {
                            currentState = ReceiveState.WAITING
                            receivedFiles = emptyList()
                            totalProgress = 0f
                        }
                    )
                }
            }
        }
    }
}

// ==================== حالات الاستقبال ====================
enum class ReceiveState {
    IDLE,
    WAITING,
    CONNECTED,
    RECEIVING,
    COMPLETED
}

// ==================== محتوى الحالة الأولية ====================
@Composable
fun IdleContent(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    connectionType: String,
    onConnectionTypeChange: (String) -> Unit,
    onStartReceiving: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        // أيقونة الاستقبال
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF03DAC6), Color(0xFF00BFA5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(25.dp))
        
        Text(
            "استعد للاستقبال",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "قم بإعداد جهازك لاستقبال الملفات",
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // اسم الجهاز
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "اسم الجهاز",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF03DAC6),
                        unfocusedBorderColor = Color(0xFF2D333B),
                        cursorColor = Color(0xFF03DAC6)
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF03DAC6)
                        )
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "سيظهر هذا الاسم للأجهزة الأخرى",
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // اختيار نوع الاتصال
        Text(
            "طريقة الاتصال",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConnectionOption(
                icon = Icons.Filled.Wifi,
                title = "WiFi",
                isSelected = connectionType == "wifi",
                color = Color(0xFF6C63FF),
                modifier = Modifier.weight(1f),
                onClick = { onConnectionTypeChange("wifi") }
            )
            
            ConnectionOption(
                icon = Icons.Filled.Bluetooth,
                title = "بلوتوث",
                isSelected = connectionType == "bluetooth",
                color = Color(0xFF03DAC6),
                modifier = Modifier.weight(1f),
                onClick = { onConnectionTypeChange("bluetooth") }
            )
            
            ConnectionOption(
                icon = Icons.Filled.WifiTethering,
                title = "هوتسبوت",
                isSelected = connectionType == "hotspot",
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = { onConnectionTypeChange("hotspot") }
            )
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // تعليمات
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF03DAC6).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF03DAC6),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "تعليمات",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF03DAC6)
                    )
                    Text(
                        "• تأكد من تفعيل ${if (connectionType == "bluetooth") "البلوتوث" else "الواي فاي"}\n" +
                        "• اجعل الجهاز قريباً من المرسل\n" +
                        "• سيتم حفظ الملفات في مجلد QuickShare",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // زر البدء
        Button(
            onClick = onStartReceiving,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF03DAC6)
            )
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "بدء الاستقبال",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // توقيع المطور
        DeveloperSignatureSmall()
    }
}

@Composable
fun ConnectionOption(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, color, RoundedCornerShape(15.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color(0xFF161B22)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) color else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontSize = 13.sp,
                color = if (isSelected) color else Color.Gray
            )
        }
    }
}

// ==================== محتوى الانتظار ====================
@Composable
fun WaitingContent(
    deviceName: String,
    connectionType: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // رسوم متحركة للانتظار
        WaitingAnimation()
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            "في انتظار الاتصال",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            "جهازك مرئي الآن للأجهزة القريبة",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // معلومات الجهاز
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF03DAC6)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(15.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "متاح للاستقبال عبر ${
                            when (connectionType) {
                                "wifi" -> "WiFi Direct"
                                "bluetooth" -> "البلوتوث"
                                else -> "نقطة الاتصال"
                            }
                        }",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
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
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
        ) {
            Text("إلغاء", color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        DeveloperSignatureSmall()
    }
}

@Composable
fun WaitingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waiting")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // الدوائر الخارجية
        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size((100 + i * 40).dp)
                    .scale(if (i == 0) pulse else 1f)
                    .rotate(rotation * (if (i % 2 == 0) 1 else -1))
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF03DAC6).copy(alpha = 0.6f - i * 0.15f),
                                Color(0xFF6C63FF).copy(alpha = 0.6f - i * 0.15f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        
        // الدائرة المركزية
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF03DAC6), Color(0xFF00BFA5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// ==================== محتوى الاتصال ====================
@Composable
fun ConnectedContent(
    device: DeviceInfo?,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // أيقونة الاتصال
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(50.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(25.dp))
        
        Text(
            "طلب اتصال جديد",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // معلومات الجهاز المتصل
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier.padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF6C63FF),
                        modifier = Modifier.size(35.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(15.dp))
                
                Text(
                    device?.name ?: "جهاز غير معروف",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    device?.address ?: "",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    "يريد إرسال ملفات إليك",
                    fontSize = 15.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // أزرار القبول والرفض
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier
                    .weight(1f)
                    .height(55.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("رفض", fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .height(55.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("قبول", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        DeveloperSignatureSmall()
    }
}

// ==================== محتوى الاستقبال ====================
@Composable
fun ReceivingContent(
    progress: Float,
    currentFileName: String,
    receivedFiles: List<ReceivedFile>,
    device: DeviceInfo?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // دائرة التقدم
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(180.dp),
                color = Color(0xFF03DAC6),
                trackColor = Color(0xFF2D333B),
                strokeWidth = 10.dp
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF03DAC6)
                )
                Text(
                    "جاري الاستقبال",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // الملف الحالي
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF03DAC6).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF03DAC6),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "يستقبل الآن:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        currentFileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // قائمة الملفات المستلمة
        if (receivedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الملفات المستلمة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${receivedFiles.size} ملف",
                    fontSize = 14.sp,
                    color = Color(0xFF03DAC6)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(receivedFiles) { file ->
                    ReceivedFileItem(file = file)
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        
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
            Icon(Icons.Filled.Cancel, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("إلغاء الاستقبال")
        }
        
        Spacer(modifier = Modifier.height(15.dp))
        
        DeveloperSignatureSmall()
    }
}

@Composable
fun ReceivedFileItem(file: ReceivedFile) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(file.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    file.icon,
                    contentDescription = null,
                    tint = file.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatFileSize(file.size),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ==================== محتوى الاكتمال ====================
@Composable
fun CompletedContent(
    receivedFiles: List<ReceivedFile>,
    onDone: () -> Unit,
    onReceiveMore: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        
        // أيقونة النجاح
        SuccessAnimation()
        
        Spacer(modifier = Modifier.height(25.dp))
        
        Text(
            "تم الاستقبال بنجاح! 🎉",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            "تم استقبال ${receivedFiles.size} ملف",
            fontSize = 16.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(25.dp))
        
        // ملخص الملفات
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "إجمالي الحجم",
                        color = Color.Gray
                    )
                    Text(
                        formatFileSize(receivedFiles.sumOf { it.size }),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF03DAC6)
                    )
                }
                
                Divider(
                    color = Color.Gray.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "مكان الحفظ",
                        color = Color.Gray
                    )
                    Text(
                        "Download/QuickShare",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(15.dp))
        
        // قائمة الملفات
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(receivedFiles) { file ->
                ReceivedFileItem(file = file)
            }
        }
        
        Spacer(modifier = Modifier.height(15.dp))
        
        // الأزرار
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReceiveMore,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF03DAC6))
            ) {
                Text("استقبال المزيد", color = Color(0xFF03DAC6))
            }
            
            Button(
                onClick = onDone,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF03DAC6)
                )
            ) {
                Text("تم", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // توقيع المطور الكامل
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "تم تصميم التطبيق بواسطة ",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    "عمر سنجق",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    tint = Color(0xFF1DA1F2),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessAnimation() {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(Color(0xFF4CAF50)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )
    }
}

// ==================== نماذج البيانات ====================
data class ReceivingFile(
    val name: String,
    val size: Long,
    val type: String
)

data class ReceivedFile(
    val name: String,
    val size: Long,
    val type: String,
    val path: String,
    val icon: ImageVector,
    val color: Color,
    val receivedAt: Date = Date()
)

// ==================== دوال مساعدة ====================
fun startListening(
    context: Context,
    connectionType: String,
    wifiP2pManager: WifiP2pManager?,
    channel: WifiP2pManager.Channel?,
    bluetoothAdapter: BluetoothAdapter?,
    onDeviceConnected: (DeviceInfo) -> Unit,
    onFileReceiveStart: (List<ReceivingFile>) -> Unit,
    onProgress: (Float, String) -> Unit,
    onFileReceived: (ReceivedFile) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    // محاكاة اتصال جهاز بعد 5 ثواني
    Handler(Looper.getMainLooper()).postDelayed({
        val mockDevice = DeviceInfo(
            name = "Samsung Galaxy A52",
            address = "AA:BB:CC:DD:EE:FF",
            type = "phone",
            signalStrength = 80
        )
        onDeviceConnected(mockDevice)
    }, 5000)
}

fun simulateReceiving(
    onProgress: (Float, String) -> Unit,
    onFileReceived: (ReceivedFile) -> Unit,
    onComplete: () -> Unit
) {
    val mockFiles = listOf(
        ReceivedFile(
            name = "photo_2024.jpg",
            size = 2500000,
            type = "image/jpeg",
            path = "/Download/QuickShare/photo_2024.jpg",
            icon = Icons.Filled.Image,
            color = Color(0xFF4CAF50)
        ),
        ReceivedFile(
            name = "video_clip.mp4",
            size = 15000000,
            type = "video/mp4",
            path = "/Download/QuickShare/video_clip.mp4",
            icon = Icons.Filled.VideoLibrary,
            color = Color(0xFFE91E63)
        ),
        ReceivedFile(
            name = "document.pdf",
            size = 500000,
            type = "application/pdf",
            path = "/Download/QuickShare/document.pdf",
            icon = Icons.Filled.PictureAsPdf,
            color = Color(0xFFF44336)
        )
    )
    
    CoroutineScope(Dispatchers.Main).launch {
        var progress = 0f
        val totalFiles = mockFiles.size
        
        mockFiles.forEachIndexed { index, file ->
            val fileProgress = index.toFloat() / totalFiles
            
            for (i in 0..100 step 5) {
                delay(30)
                progress = fileProgress + (i / 100f / totalFiles)
                onProgress(progress, file.name)
            }
            
            onFileReceived(file)
        }
        
        onProgress(1f, "اكتمل!")
        delay(500)
        onComplete()
    }
}