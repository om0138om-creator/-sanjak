// ConnectionManager.kt
// إدارة الاتصالات (WiFi Direct - Bluetooth - Hotspot)
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// ==================== مدير الاتصالات الرئيسي ====================
class ConnectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ConnectionManager"
        
        // ثوابت WiFi Direct
        const val WIFI_P2P_PORT = 8888
        const val WIFI_P2P_SERVICE_NAME = "QuickShare"
        const val WIFI_P2P_SERVICE_TYPE = "_quickshare._tcp"
        
        // ثوابت البلوتوث
        val BLUETOOTH_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val BLUETOOTH_SERVICE_NAME = "QuickShareBT"
        
        // ثوابت نقطة الاتصال
        const val HOTSPOT_PORT = 9999
        const val HOTSPOT_SSID_PREFIX = "QS_"
        
        // حجم البافر
        const val BUFFER_SIZE = 65536 // 64 KB للسرعة العالية
        const val SOCKET_TIMEOUT = 30000 // 30 ثانية
    }
    
    // ==================== المتغيرات ====================
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var bluetoothServerSocket: BluetoothServerSocket? = null
    private var bluetoothSocket: BluetoothSocket? = null
    
    private val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    private var isDiscovering = false
    private var isListening = false
    
    // ==================== الـ Flows ====================
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _discoveredDevicesFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevicesFlow: StateFlow<List<DiscoveredDevice>> = _discoveredDevicesFlow.asStateFlow()
    
    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()
    
    // ==================== WiFi P2P Receiver ====================
    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(EXTRA_WIFI_STATE, -1)
                    if (state == WIFI_P2P_STATE_ENABLED) {
                        Log.d(TAG, "WiFi P2P مفعل")
                    } else {
                        Log.d(TAG, "WiFi P2P غير مفعل")
                        _connectionState.value = ConnectionState.Error("WiFi P2P غير مفعل")
                    }
                }
                
                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                
                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO, android.net.NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO)
                    }
                    
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
                
                WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device: ${device?.deviceName}")
                }
            }
        }
    }
    
    // ==================== Bluetooth Receiver ====================
    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        addDiscoveredDevice(
                            DiscoveredDevice(
                                id = it.address,
                                name = it.name ?: "جهاز غير معروف",
                                address = it.address,
                                type = ConnectionType.BLUETOOTH,
                                signalStrength = calculateSignalStrength(rssi.toInt()),
                                deviceType = getDeviceType(it)
                            )
                        )
                    }
                }
                
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isDiscovering = false
                    Log.d(TAG, "انتهى البحث عن أجهزة البلوتوث")
                }
                
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> Log.d(TAG, "البلوتوث مفعل")
                        BluetoothAdapter.STATE_OFF -> {
                            _connectionState.value = ConnectionState.Error("البلوتوث غير مفعل")
                        }
                    }
                }
            }
        }
    }
    
    // ==================== التهيئة ====================
    fun initialize(): Boolean {
        return try {
            // تهيئة WiFi P2P
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
            
            // تهيئة البلوتوث
            bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            
            // تسجيل المستقبلات
            registerReceivers()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل في التهيئة: ${e.message}")
            false
        }
    }
    
    private fun registerReceivers() {
        // WiFi P2P
        val wifiP2pIntentFilter = IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(wifiP2pReceiver, wifiP2pIntentFilter)
        
        // Bluetooth
        val bluetoothIntentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, bluetoothIntentFilter)
    }
    
    // ==================== البحث عن الأجهزة ====================
    @SuppressLint("MissingPermission")
    fun startDiscovery(connectionType: ConnectionType) {
        discoveredDevices.clear()
        _discoveredDevicesFlow.value = emptyList()
        isDiscovering = true
        
        when (connectionType) {
            ConnectionType.WIFI_DIRECT -> startWifiP2pDiscovery()
            ConnectionType.BLUETOOTH -> startBluetoothDiscovery()
            ConnectionType.HOTSPOT -> startHotspotDiscovery()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startWifiP2pDiscovery() {
        wifiP2pManager?.discoverPeers(wifiP2pChannel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "بدأ البحث عن أجهزة WiFi P2P")
            }
            
            override fun onFailure(reason: Int) {
                val errorMsg = when (reason) {
                    ERROR -> "خطأ داخلي"
                    P2P_UNSUPPORTED -> "WiFi P2P غير مدعوم"
                    BUSY -> "النظام مشغول"
                    else -> "خطأ غير معروف"
                }
                Log.e(TAG, "فشل البحث: $errorMsg")
                _connectionState.value = ConnectionState.Error(errorMsg)
            }
        })
    }
    
    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.Error("البلوتوث غير مفعل")
            return
        }
        
        // إضافة الأجهزة المقترنة
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            addDiscoveredDevice(
                DiscoveredDevice(
                    id = device.address,
                    name = device.name ?: "جهاز غير معروف",
                    address = device.address,
                    type = ConnectionType.BLUETOOTH,
                    signalStrength = 100,
                    deviceType = getDeviceType(device),
                    isPaired = true
                )
            )
        }
        
        // بدء البحث عن أجهزة جديدة
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }
    
    private fun startHotspotDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // البحث عن أجهزة على الشبكة المحلية
                val localAddress = getLocalIpAddress()
                if (localAddress != null) {
                    val subnet = localAddress.substringBeforeLast(".")
                    
                    for (i in 1..254) {
                        if (!isDiscovering) break
                        
                        launch {
                            val host = "$subnet.$i"
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(host, HOTSPOT_PORT), 100)
                                socket.close()
                                
                                addDiscoveredDevice(
                                    DiscoveredDevice(
                                        id = host,
                                        name = "جهاز على $host",
                                        address = host,
                                        type = ConnectionType.HOTSPOT,
                                        signalStrength = 80,
                                        deviceType = DeviceType.PHONE
                                    )
                                )
                            } catch (e: Exception) {
                                // الجهاز غير متاح
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في البحث عن Hotspot: ${e.message}")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        wifiP2pManager?.requestPeers(wifiP2pChannel) { peerList ->
            peerList.deviceList.forEach { device ->
                addDiscoveredDevice(
                    DiscoveredDevice(
                        id = device.deviceAddress,
                        name = device.deviceName.ifEmpty { "جهاز WiFi" },
                        address = device.deviceAddress,
                        type = ConnectionType.WIFI_DIRECT,
                        signalStrength = 85,
                        deviceType = DeviceType.PHONE,
                        wifiP2pDevice = device
                    )
                )
            }
        }
    }
    
    fun stopDiscovery() {
        isDiscovering = false
        
        try {
            wifiP2pManager?.stopPeerDiscovery(wifiP2pChannel, null)
        } catch (e: Exception) { }
        
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (e: Exception) { }
    }
    
    private fun addDiscoveredDevice(device: DiscoveredDevice) {
        discoveredDevices[device.id] = device
        _discoveredDevicesFlow.value = discoveredDevices.values.toList()
            .sortedByDescending { it.signalStrength }
    }
    
    // ==================== الاتصال بجهاز ====================
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting(device)
        
        try {
            when (device.type) {
                ConnectionType.WIFI_DIRECT -> connectWifiP2p(device)
                ConnectionType.BLUETOOTH -> connectBluetooth(device)
                ConnectionType.HOTSPOT -> connectHotspot(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل الاتصال: ${e.message}")
            _connectionState.value = ConnectionState.Error("فشل الاتصال: ${e.message}")
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun connectWifiP2p(device: DiscoveredDevice): Boolean = suspendCancellableCoroutine { cont ->
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0 // نفضل أن نكون client
        }
        
        wifiP2pManager?.connect(wifiP2pChannel, config, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "بدأ الاتصال بـ WiFi P2P")
                // الاتصال الفعلي سيتم في onConnectionChanged
            }
            
            override fun onFailure(reason: Int) {
                _connectionState.value = ConnectionState.Error("فشل اتصال WiFi P2P")
                if (cont.isActive) cont.resume(false) {}
            }
        })
        
        // انتظار الاتصال
        CoroutineScope(Dispatchers.Main).launch {
            delay(10000) // timeout 10 ثواني
            if (cont.isActive) {
                _connectionState.value = ConnectionState.Error("انتهى وقت الاتصال")
                cont.resume(false) {}
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun connectBluetooth(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothAdapter?.cancelDiscovery()
            
            val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            bluetoothSocket = btDevice?.createRfcommSocketToServiceRecord(BLUETOOTH_UUID)
            bluetoothSocket?.connect()
            
            _connectionState.value = ConnectionState.Connected(device)
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل اتصال البلوتوث: ${e.message}")
            _connectionState.value = ConnectionState.Error("فشل اتصال البلوتوث")
            false
        }
    }
    
    private suspend fun connectHotspot(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            clientSocket = Socket()
            clientSocket?.connect(InetSocketAddress(device.address, HOTSPOT_PORT), SOCKET_TIMEOUT)
            
            _connectionState.value = ConnectionState.Connected(device)
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل اتصال Hotspot: ${e.message}")
            _connectionState.value = ConnectionState.Error("فشل اتصال Hotspot")
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel) { info ->
            if (info.groupFormed) {
                val device = DiscoveredDevice(
                    id = info.groupOwnerAddress?.hostAddress ?: "",
                    name = "WiFi P2P Device",
                    address = info.groupOwnerAddress?.hostAddress ?: "",
                    type = ConnectionType.WIFI_DIRECT,
                    signalStrength = 100,
                    deviceType = DeviceType.PHONE,
                    isGroupOwner = info.isGroupOwner
                )
                _connectionState.value = ConnectionState.Connected(device)
                
                // بدء الخادم أو الاتصال بالخادم
                CoroutineScope(Dispatchers.IO).launch {
                    if (info.isGroupOwner) {
                        startServer(ConnectionType.WIFI_DIRECT)
                    } else {
                        connectToServer(info.groupOwnerAddress.hostAddress!!, WIFI_P2P_PORT)
                    }
                }
            }
        }
    }
    
    // ==================== الاستماع للاتصالات ====================
    @SuppressLint("MissingPermission")
    suspend fun startListening(connectionType: ConnectionType): Boolean = withContext(Dispatchers.IO) {
        isListening = true
        _connectionState.value = ConnectionState.Listening
        
        try {
            when (connectionType) {
                ConnectionType.WIFI_DIRECT -> {
                    makeDeviceDiscoverable()
                    startServer(connectionType)
                }
                ConnectionType.BLUETOOTH -> {
                    startBluetoothServer()
                }
                ConnectionType.HOTSPOT -> {
                    startServer(connectionType)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل بدء الاستماع: ${e.message}")
            _connectionState.value = ConnectionState.Error("فشل بدء الاستماع")
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun makeDeviceDiscoverable() {
        wifiP2pManager?.discoverPeers(wifiP2pChannel, null)
    }
    
    private suspend fun startServer(connectionType: ConnectionType) {
        val port = when (connectionType) {
            ConnectionType.WIFI_DIRECT -> WIFI_P2P_PORT
            ConnectionType.HOTSPOT -> HOTSPOT_PORT
            else -> WIFI_P2P_PORT
        }
        
        serverSocket = ServerSocket(port)
        serverSocket?.soTimeout = 0 // انتظار غير محدود
        
        Log.d(TAG, "الخادم يستمع على المنفذ $port")
        
        while (isListening) {
            try {
                val client = serverSocket?.accept()
                client?.let {
                    clientSocket = it
                    val device = DiscoveredDevice(
                        id = it.inetAddress.hostAddress ?: "",
                        name = "جهاز متصل",
                        address = it.inetAddress.hostAddress ?: "",
                        type = connectionType,
                        signalStrength = 100,
                        deviceType = DeviceType.PHONE
                    )
                    _connectionState.value = ConnectionState.Connected(device)
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "خطأ في قبول الاتصال: ${e.message}")
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun startBluetoothServer() {
        bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
            BLUETOOTH_SERVICE_NAME,
            BLUETOOTH_UUID
        )
        
        Log.d(TAG, "خادم البلوتوث يستمع")
        
        while (isListening) {
            try {
                val socket = bluetoothServerSocket?.accept()
                socket?.let {
                    bluetoothSocket = it
                    val device = DiscoveredDevice(
                        id = it.remoteDevice.address,
                        name = it.remoteDevice.name ?: "جهاز بلوتوث",
                        address = it.remoteDevice.address,
                        type = ConnectionType.BLUETOOTH,
                        signalStrength = 100,
                        deviceType = DeviceType.PHONE
                    )
                    _connectionState.value = ConnectionState.Connected(device)
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "خطأ في قبول اتصال البلوتوث: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun connectToServer(host: String, port: Int) {
        try {
            clientSocket = Socket()
            clientSocket?.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT)
            Log.d(TAG, "متصل بالخادم $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الاتصال بالخادم: ${e.message}")
        }
    }
    
    // ==================== إرسال الملفات ====================
    suspend fun sendFiles(
        files: List<FileDetails>,
        onProgress: (TransferProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = getOutputStream() ?: return@withContext false
            val dataOut = DataOutputStream(BufferedOutputStream(outputStream, BUFFER_SIZE))
            
            // إرسال عدد الملفات
            dataOut.writeInt(files.size)
            dataOut.flush()
            
            var totalBytesSent = 0L
            val totalSize = files.sumOf { it.size }
            
            files.forEachIndexed { index, file ->
                // إرسال معلومات الملف
                dataOut.writeUTF(file.name)
                dataOut.writeLong(file.size)
                dataOut.writeUTF(file.mimeType)
                dataOut.flush()
                
                // إرسال محتوى الملف
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var fileBytesRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        dataOut.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalBytesSent += bytesRead
                        
                        val progress = TransferProgress(
                            currentFile = file.name,
                            currentFileIndex = index + 1,
                            totalFiles = files.size,
                            currentFileProgress = fileBytesRead.toFloat() / file.size,
                            totalProgress = totalBytesSent.toFloat() / totalSize,
                            bytesTransferred = totalBytesSent,
                            totalBytes = totalSize,
                            speed = calculateSpeed(totalBytesSent)
                        )
                        
                        withContext(Dispatchers.Main) {
                            _transferProgress.value = progress
                            onProgress(progress)
                        }
                    }
                }
                
                dataOut.flush()
            }
            
            dataOut.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال الملفات: ${e.message}")
            false
        }
    }
    
    // ==================== استقبال الملفات ====================
    suspend fun receiveFiles(
        onFileReceived: (ReceivedFile) -> Unit,
        onProgress: (TransferProgress) -> Unit
    ): List<ReceivedFile> = withContext(Dispatchers.IO) {
        val receivedFiles = mutableListOf<ReceivedFile>()
        
        try {
            val inputStream = getInputStream() ?: return@withContext emptyList()
            val dataIn = DataInputStream(BufferedInputStream(inputStream, BUFFER_SIZE))
            
            // قراءة عدد الملفات
            val fileCount = dataIn.readInt()
            var totalBytesReceived = 0L
            var estimatedTotalSize = 0L
            
            for (i in 0 until fileCount) {
                // قراءة معلومات الملف
                val fileName = dataIn.readUTF()
                val fileSize = dataIn.readLong()
                val mimeType = dataIn.readUTF()
                
                estimatedTotalSize += fileSize
                
                // حفظ الملف
                val savedFile = FileManager.saveReceivedFile(
                    context = context,
                    inputStream = object : InputStream() {
                        private var bytesRemaining = fileSize
                        
                        override fun read(): Int {
                            if (bytesRemaining <= 0) return -1
                            bytesRemaining--
                            return dataIn.read()
                        }
                        
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (bytesRemaining <= 0) return -1
                            val toRead = minOf(len.toLong(), bytesRemaining).toInt()
                            val read = dataIn.read(b, off, toRead)
                            if (read > 0) bytesRemaining -= read
                            return read
                        }
                    },
                    fileName = fileName,
                    fileSize = fileSize
                ) { fileProgress ->
                    val bytesForThisFile = (fileProgress * fileSize).toLong()
                    
                    val progress = TransferProgress(
                        currentFile = fileName,
                        currentFileIndex = i + 1,
                        totalFiles = fileCount,
                        currentFileProgress = fileProgress,
                        totalProgress = (totalBytesReceived + bytesForThisFile).toFloat() / estimatedTotalSize,
                        bytesTransferred = totalBytesReceived + bytesForThisFile,
                        totalBytes = estimatedTotalSize,
                        speed = calculateSpeed(totalBytesReceived + bytesForThisFile)
                    )
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        _transferProgress.value = progress
                        onProgress(progress)
                    }
                }
                
                totalBytesReceived += fileSize
                
                savedFile?.let { file ->
                    val receivedFile = ReceivedFile(
                        name = fileName,
                        size = fileSize,
                        type = mimeType,
                        path = file.absolutePath,
                        icon = FileManager.getFileDetails(context, android.net.Uri.fromFile(file))?.icon 
                            ?: Icons.Filled.InsertDriveFile,
                        color = FileManager.getFileDetails(context, android.net.Uri.fromFile(file))?.color 
                            ?: Color(0xFF9E9E9E)
                    )
                    
                    receivedFiles.add(receivedFile)
                    
                    withContext(Dispatchers.Main) {
                        onFileReceived(receivedFile)
                    }
                }
            }
            
            dataIn.close()
        } catch (e: Exception) {
            Log.e(TAG, "فشل استقبال الملفات: ${e.message}")
        }
        
        receivedFiles
    }
    
    // ==================== دوال مساعدة ====================
    private fun getOutputStream(): OutputStream? {
        return try {
            when (_connectionState.value) {
                is ConnectionState.Connected -> {
                    val device = (_connectionState.value as ConnectionState.Connected).device
                    when (device.type) {
                        ConnectionType.BLUETOOTH -> bluetoothSocket?.outputStream
                        else -> clientSocket?.getOutputStream()
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getInputStream(): InputStream? {
        return try {
            when (_connectionState.value) {
                is ConnectionState.Connected -> {
                    val device = (_connectionState.value as ConnectionState.Connected).device
                    when (device.type) {
                        ConnectionType.BLUETOOTH -> bluetoothSocket?.inputStream
                        else -> clientSocket?.getInputStream()
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }
    
    private var lastSpeedCheck = System.currentTimeMillis()
    private var lastBytesForSpeed = 0L
    
    private fun calculateSpeed(currentBytes: Long): Long {
        val now = System.currentTimeMillis()
        val timeDiff = now - lastSpeedCheck
        
        return if (timeDiff >= 1000) {
            val speed = ((currentBytes - lastBytesForSpeed) * 1000) / timeDiff
            lastSpeedCheck = now
            lastBytesForSpeed = currentBytes
            speed
        } else {
            0L
        }
    }
    
    private fun calculateSignalStrength(rssi: Int): Int {
        return when {
            rssi >= -50 -> 100
            rssi >= -60 -> 80
            rssi >= -70 -> 60
            rssi >= -80 -> 40
            else -> 20
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getDeviceType(device: BluetoothDevice): DeviceType {
        return when (device.bluetoothClass?.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> DeviceType.PHONE
            BluetoothClass.Device.Major.COMPUTER -> DeviceType.COMPUTER
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceType.TABLET
            else -> DeviceType.UNKNOWN
        }
    }
    
    // ==================== قطع الاتصال ====================
    @SuppressLint("MissingPermission")
    fun disconnect() {
        isListening = false
        
        try { clientSocket?.close() } catch (e: Exception) { }
        try { serverSocket?.close() } catch (e: Exception) { }
        try { bluetoothSocket?.close() } catch (e: Exception) { }
        try { bluetoothServerSocket?.close() } catch (e: Exception) { }
        
        try {
            wifiP2pManager?.removeGroup(wifiP2pChannel, null)
        } catch (e: Exception) { }
        
        clientSocket = null
        serverSocket = null
        bluetoothSocket = null
        bluetoothServerSocket = null
        
        _connectionState.value = ConnectionState.Disconnected
        _transferProgress.value = null
    }
    
    // ==================== التنظيف ====================
    fun cleanup() {
        disconnect()
        stopDiscovery()
        
        try {
            context.unregisterReceiver(wifiP2pReceiver)
        } catch (e: Exception) { }
        
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) { }
    }
}

// ==================== نماذج البيانات ====================
enum class ConnectionType {
    WIFI_DIRECT,
    BLUETOOTH,
    HOTSPOT
}

enum class DeviceType {
    PHONE,
    TABLET,
    COMPUTER,
    UNKNOWN
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Listening : ConnectionState()
    data class Connecting(val device: DiscoveredDevice) : ConnectionState()
    data class Connected(val device: DiscoveredDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val address: String,
    val type: ConnectionType,
    val signalStrength: Int,
    val deviceType: DeviceType,
    val isPaired: Boolean = false,
    val isGroupOwner: Boolean = false,
    val wifiP2pDevice: WifiP2pDevice? = null
)

data class TransferProgress(
    val currentFile: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val currentFileProgress: Float,
    val totalProgress: Float,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speed: Long // bytes per second
)