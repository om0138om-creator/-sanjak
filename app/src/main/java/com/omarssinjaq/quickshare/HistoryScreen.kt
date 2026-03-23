// HistoryScreen.kt
// شاشة سجل النقل والملفات المستلمة
// تم تصميم التطبيق بواسطة عمر سنجق

package com.omarssinjaq.quickshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==================== شاشة السجل الكاملة ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(SortOption.DATE_DESC) }
    var filterType by remember { mutableStateOf(FilterType.ALL) }
    
    // بيانات السجل (في التطبيق الحقيقي تأتي من قاعدة البيانات)
    var transferHistory by remember { mutableStateOf(getSampleHistory()) }
    var receivedFiles by remember { mutableStateOf(getSampleReceivedFiles()) }
    
    val tabs = listOf("سجل النقل", "الملفات المستلمة")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "السجل",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    // زر الترتيب
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, "ترتيب")
                        }
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sortBy = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortBy == option) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF6C63FF)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // زر الحذف
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = transferHistory.isNotEmpty() || receivedFiles.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            "مسح السجل",
                            tint = if (transferHistory.isNotEmpty() || receivedFiles.isNotEmpty())
                                Color.Red.copy(alpha = 0.8f)
                            else Color.Gray
                        )
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
            // التبويبات
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF161B22),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF6C63FF),
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    if (index == 0) Icons.Filled.History else Icons.Filled.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(title)
                            }
                        },
                        selectedContentColor = Color(0xFF6C63FF),
                        unselectedContentColor = Color.Gray
                    )
                }
            }
            
            // فلاتر سريعة
            FilterChips(
                selectedFilter = filterType,
                onFilterChange = { filterType = it }
            )
            
            // المحتوى
            when (selectedTab) {
                0 -> TransferHistoryContent(
                    history = filterAndSortHistory(transferHistory, filterType, sortBy),
                    onItemClick = { /* فتح التفاصيل */ },
                    onDeleteItem = { record ->
                        transferHistory = transferHistory.filter { it.id != record.id }
                    }
                )
                1 -> ReceivedFilesContent(
                    files = filterAndSortFiles(receivedFiles, filterType, sortBy),
                    onFileClick = { file ->
                        openFile(context, file)
                    },
                    onShareFile = { file ->
                        shareFile(context, file)
                    },
                    onDeleteFile = { file ->
                        receivedFiles = receivedFiles.filter { it.path != file.path }
                    }
                )
            }
        }
    }
    
    // نافذة تأكيد الحذف
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "مسح السجل",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "هل أنت متأكد من حذف جميع السجلات؟",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "لن يتم حذف الملفات المستلمة من الجهاز",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        transferHistory = emptyList()
                        showClearDialog = false
                        Toast.makeText(context, "تم مسح السجل", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("مسح")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}

// ==================== شرائح الفلتر ====================
@Composable
fun FilterChips(
    selectedFilter: FilterType,
    onFilterChange: (FilterType) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FilterType.values().toList()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        filter.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF6C63FF),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = Color(0xFF161B22),
                    labelColor = Color.Gray,
                    iconColor = Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                    enabled = true,
                    selected = selectedFilter == filter
                )
            )
        }
    }
}

// ==================== محتوى سجل النقل ====================
@Composable
fun TransferHistoryContent(
    history: List<TransferRecord>,
    onItemClick: (TransferRecord) -> Unit,
    onDeleteItem: (TransferRecord) -> Unit
) {
    val context = LocalContext.current
    
    if (history.isEmpty()) {
        EmptyHistoryView(
            icon = Icons.Outlined.History,
            title = "لا يوجد سجل",
            subtitle = "ستظهر هنا عمليات النقل السابقة"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // تجميع حسب التاريخ
            val groupedHistory = history.groupBy { record ->
                getDateGroup(record.timestamp)
            }
            
            groupedHistory.forEach { (dateGroup, records) ->
                item {
                    Text(
                        dateGroup,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6C63FF),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(records) { record ->
                    TransferHistoryItem(
                        record = record,
                        onClick = { onItemClick(record) },
                        onDelete = { onDeleteItem(record) }
                    )
                }
            }
            
            // توقيع المطور
            item {
                Spacer(modifier = Modifier.height(20.dp))
                DeveloperSignatureCard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferHistoryItem(
    record: TransferRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red, RoundedCornerShape(15.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "حذف",
                    tint = Color.White
                )
            }
        },
        content = {
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
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // أيقونة النوع
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (record.type == TransferType.SEND)
                                    Color(0xFF6C63FF).copy(alpha = 0.2f)
                                else
                                    Color(0xFF03DAC6).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (record.type == TransferType.SEND) Icons.Filled.Upload
                            else Icons.Filled.Download,
                            contentDescription = null,
                            tint = if (record.type == TransferType.SEND)
                                Color(0xFF6C63FF) else Color(0xFF03DAC6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // التفاصيل
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (record.type == TransferType.SEND) "إرسال" else "استقبال",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            
                            if (record.success) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Text(
                            "${record.files.size} ملف • ${formatFileSize(record.totalSize)}",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        
                        Text(
                            record.deviceName,
                            fontSize = 12.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // الوقت والقائمة
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            formatTime(record.timestamp),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "المزيد",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("عرض التفاصيل") },
                                    onClick = {
                                        showMenu = false
                                        onClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Info, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("حذف", color = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = Color.Red
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // قائمة الملفات المختصرة
                if (record.files.isNotEmpty()) {
                    Divider(
                        color = Color.Gray.copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 15.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        record.files.take(3).forEach { fileName ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2D333B)
                            ) {
                                Text(
                                    fileName,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .widthIn(max = 100.dp)
                                )
                            }
                        }
                        
                        if (record.files.size > 3) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6C63FF).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "+${record.files.size - 3}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6C63FF),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

// ==================== محتوى الملفات المستلمة ====================
@Composable
fun ReceivedFilesContent(
    files: List<ReceivedFileInfo>,
    onFileClick: (ReceivedFileInfo) -> Unit,
    onShareFile: (ReceivedFileInfo) -> Unit,
    onDeleteFile: (ReceivedFileInfo) -> Unit
) {
    val context = LocalContext.current
    
    if (files.isEmpty()) {
        EmptyHistoryView(
            icon = Icons.Outlined.Folder,
            title = "لا توجد ملفات",
            subtitle = "ستظهر هنا الملفات المستلمة"
        )
    } else {
        // إحصائيات
        val stats = calculateStats(files)
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // بطاقة الإحصائيات
            item {
                StatsCard(stats = stats)
            }
            
            item {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    "الملفات (${files.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            items(files) { file ->
                ReceivedFileItem(
                    file = file,
                    onClick = { onFileClick(file) },
                    onShare = { onShareFile(file) },
                    onDelete = { onDeleteFile(file) }
                )
            }
            
            // توقيع المطور
            item {
                Spacer(modifier = Modifier.height(20.dp))
                DeveloperSignatureCard()
            }
        }
    }
}

@Composable
fun StatsCard(stats: FileStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "الإحصائيات",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(15.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Filled.Folder,
                    value = "${stats.totalFiles}",
                    label = "ملف",
                    color = Color(0xFF6C63FF)
                )
                
                StatItem(
                    icon = Icons.Filled.Storage,
                    value = formatFileSize(stats.totalSize),
                    label = "الحجم",
                    color = Color(0xFF03DAC6)
                )
                
                StatItem(
                    icon = Icons.Filled.Image,
                    value = "${stats.imagesCount}",
                    label = "صورة",
                    color = Color(0xFF4CAF50)
                )
                
                StatItem(
                    icon = Icons.Filled.VideoLibrary,
                    value = "${stats.videosCount}",
                    label = "فيديو",
                    color = Color(0xFFE91E63)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun ReceivedFileItem(
    file: ReceivedFileInfo,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة الملف
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(file.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    file.icon,
                    contentDescription = null,
                    tint = file.color,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // معلومات الملف
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatFileSize(file.size),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    Text(
                        " • ",
                        color = Color.Gray
                    )
                    
                    Text(
                        formatDate(file.receivedAt),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // أزرار الإجراءات
            Row {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(35.dp)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "مشاركة",
                        tint = Color(0xFF6C63FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(35.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "المزيد",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("فتح") },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("مشاركة") },
                            onClick = {
                                showMenu = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==================== عرض فارغ ====================
@Composable
fun EmptyHistoryView(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            subtitle,
            fontSize = 14.sp,
            color = Color.Gray.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // توقيع المطور
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "تم تصميم التطبيق بواسطة ",
                fontSize = 13.sp,
                color = Color.Gray.copy(alpha = 0.5f)
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
        }
    }
}

// ==================== بطاقة توقيع المطور ====================
@Composable
fun DeveloperSignatureCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Om9r0"))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // صورة المطور
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6C63FF), Color(0xFF03DAC6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ع",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    "تم تصميم التطبيق بواسطة",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "عمر سنجق",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
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
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                Icons.Filled.Send,
                contentDescription = "تلجرام",
                tint = Color(0xFF0088CC),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== نماذج البيانات ====================
data class ReceivedFileInfo(
    val name: String,
    val size: Long,
    val path: String,
    val mimeType: String,
    val icon: ImageVector,
    val color: Color,
    val receivedAt: Date
)

data class FileStats(
    val totalFiles: Int,
    val totalSize: Long,
    val imagesCount: Int,
    val videosCount: Int,
    val audiosCount: Int,
    val documentsCount: Int,
    val appsCount: Int,
    val othersCount: Int
)

enum class SortOption(val label: String) {
    DATE_DESC("الأحدث أولاً"),
    DATE_ASC("الأقدم أولاً"),
    SIZE_DESC("الأكبر حجماً"),
    SIZE_ASC("الأصغر حجماً"),
    NAME_ASC("الاسم (أ-ي)"),
    NAME_DESC("الاسم (ي-أ)")
}

enum class FilterType(val label: String, val icon: ImageVector) {
    ALL("الكل", Icons.Filled.Apps),
    IMAGES("الصور", Icons.Filled.Image),
    VIDEOS("الفيديو", Icons.Filled.VideoLibrary),
    AUDIO("الصوت", Icons.Filled.MusicNote),
    DOCUMENTS("المستندات", Icons.Filled.Description),
    APPS("التطبيقات", Icons.Filled.Android)
}

// ==================== دوال مساعدة ====================
fun getDateGroup(date: Date): String {
    val now = Calendar.getInstance()
    val recordCal = Calendar.getInstance().apply { time = date }
    
    return when {
        now.get(Calendar.DATE) == recordCal.get(Calendar.DATE) &&
        now.get(Calendar.MONTH) == recordCal.get(Calendar.MONTH) &&
        now.get(Calendar.YEAR) == recordCal.get(Calendar.YEAR) -> "اليوم"
        
        now.get(Calendar.DATE) - 1 == recordCal.get(Calendar.DATE) &&
        now.get(Calendar.MONTH) == recordCal.get(Calendar.MONTH) &&
        now.get(Calendar.YEAR) == recordCal.get(Calendar.YEAR) -> "أمس"
        
        now.get(Calendar.WEEK_OF_YEAR) == recordCal.get(Calendar.WEEK_OF_YEAR) &&
        now.get(Calendar.YEAR) == recordCal.get(Calendar.YEAR) -> "هذا الأسبوع"
        
        now.get(Calendar.MONTH) == recordCal.get(Calendar.MONTH) &&
        now.get(Calendar.YEAR) == recordCal.get(Calendar.YEAR) -> "هذا الشهر"
        
        else -> SimpleDateFormat("MMMM yyyy", Locale("ar")).format(date)
    }
}

fun formatTime(date: Date): String {
    return SimpleDateFormat("hh:mm a", Locale("ar")).format(date)
}

fun formatDate(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    
    return when {
        diff < 60 * 1000 -> "الآن"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} دقيقة"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} ساعة"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }
}

fun calculateStats(files: List<ReceivedFileInfo>): FileStats {
    var imagesCount = 0
    var videosCount = 0
    var audiosCount = 0
    var documentsCount = 0
    var appsCount = 0
    var othersCount = 0
    
    files.forEach { file ->
        when {
            file.mimeType.startsWith("image/") -> imagesCount++
            file.mimeType.startsWith("video/") -> videosCount++
            file.mimeType.startsWith("audio/") -> audiosCount++
            file.mimeType.contains("pdf") || file.mimeType.contains("document") -> documentsCount++
            file.mimeType.contains("android") -> appsCount++
            else -> othersCount++
        }
    }
    
    return FileStats(
        totalFiles = files.size,
        totalSize = files.sumOf { it.size },
        imagesCount = imagesCount,
        videosCount = videosCount,
        audiosCount = audiosCount,
        documentsCount = documentsCount,
        appsCount = appsCount,
        othersCount = othersCount
    )
}

fun filterAndSortHistory(
    history: List<TransferRecord>,
    filter: FilterType,
    sort: SortOption
): List<TransferRecord> {
    val filtered = if (filter == FilterType.ALL) history else {
        history.filter { record ->
            record.files.any { fileName ->
                when (filter) {
                    FilterType.IMAGES -> fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg")
                    FilterType.VIDEOS -> fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi")
                    FilterType.AUDIO -> fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".m4a")
                    FilterType.DOCUMENTS -> fileName.endsWith(".pdf") || fileName.endsWith(".doc") || fileName.endsWith(".txt")
                    FilterType.APPS -> fileName.endsWith(".apk")
                    else -> true
                }
            }
        }
    }
    
    return when (sort) {
        SortOption.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
        SortOption.DATE_ASC -> filtered.sortedBy { it.timestamp }
        SortOption.SIZE_DESC -> filtered.sortedByDescending { it.totalSize }
        SortOption.SIZE_ASC -> filtered.sortedBy { it.totalSize }
        SortOption.NAME_ASC -> filtered.sortedBy { it.files.firstOrNull() ?: "" }
        SortOption.NAME_DESC -> filtered.sortedByDescending { it.files.firstOrNull() ?: "" }
    }
}

fun filterAndSortFiles(
    files: List<ReceivedFileInfo>,
    filter: FilterType,
    sort: SortOption
): List<ReceivedFileInfo> {
    val filtered = if (filter == FilterType.ALL) files else {
        files.filter { file ->
            when (filter) {
                FilterType.IMAGES -> file.mimeType.startsWith("image/")
                FilterType.VIDEOS -> file.mimeType.startsWith("video/")
                FilterType.AUDIO -> file.mimeType.startsWith("audio/")
                FilterType.DOCUMENTS -> file.mimeType.contains("pdf") || file.mimeType.contains("document")
                FilterType.APPS -> file.mimeType.contains("android")
                else -> true
            }
        }
    }
    
    return when (sort) {
        SortOption.DATE_DESC -> filtered.sortedByDescending { it.receivedAt }
        SortOption.DATE_ASC -> filtered.sortedBy { it.receivedAt }
        SortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        SortOption.SIZE_ASC -> filtered.sortedBy { it.size }
        SortOption.NAME_ASC -> filtered.sortedBy { it.name }
        SortOption.NAME_DESC -> filtered.sortedByDescending { it.name }
    }
}

fun openFile(context: Context, file: ReceivedFileInfo) {
    try {
        val fileObj = File(file.path)
        if (fileObj.exists()) {
            FileManager.openFile(context, fileObj)
        } else {
            Toast.makeText(context, "الملف غير موجود", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "فشل فتح الملف", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, file: ReceivedFileInfo) {
    try {
        val fileObj = File(file.path)
        if (fileObj.exists()) {
            FileManager.shareFile(context, fileObj)
        } else {
            Toast.makeText(context, "الملف غير موجود", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "فشل مشاركة الملف", Toast.LENGTH_SHORT).show()
    }
}

// ==================== بيانات تجريبية ====================
fun getSampleHistory(): List<TransferRecord> {
    return listOf(
        TransferRecord(
            id = "1",
            type = TransferType.SEND,
            files = listOf("photo_2024.jpg", "video_clip.mp4", "document.pdf"),
            totalSize = 15000000,
            deviceName = "Samsung Galaxy S21",
            timestamp = Date(System.currentTimeMillis() - 3600000),
            success = true
        ),
        TransferRecord(
            id = "2",
            type = TransferType.RECEIVE,
            files = listOf("app_installer.apk", "music.mp3"),
            totalSize = 45000000,
            deviceName = "Xiaomi Note 10",
            timestamp = Date(System.currentTimeMillis() - 86400000),
            success = true
        ),
        TransferRecord(
            id = "3",
            type = TransferType.SEND,
            files = listOf("presentation.pptx"),
            totalSize = 5000000,
            deviceName = "OnePlus 9",
            timestamp = Date(System.currentTimeMillis() - 172800000),
            success = false
        )
    )
}

fun getSampleReceivedFiles(): List<ReceivedFileInfo> {
    return listOf(
        ReceivedFileInfo(
            name = "photo_2024.jpg",
            size = 2500000,
            path = "/storage/emulated/0/Download/QuickShare/photo_2024.jpg",
            mimeType = "image/jpeg",
            icon = Icons.Filled.Image,
            color = Color(0xFF4CAF50),
            receivedAt = Date(System.currentTimeMillis() - 3600000)
        ),
        ReceivedFileInfo(
            name = "video_clip.mp4",
            size = 15000000,
            path = "/storage/emulated/0/Download/QuickShare/video_clip.mp4",
            mimeType = "video/mp4",
            icon = Icons.Filled.VideoLibrary,
            color = Color(0xFFE91E63),
            receivedAt = Date(System.currentTimeMillis() - 7200000)
        ),
        ReceivedFileInfo(
            name = "music_track.mp3",
            size = 5000000,
            path = "/storage/emulated/0/Download/QuickShare/music_track.mp3",
            mimeType = "audio/mp3",
            icon = Icons.Filled.MusicNote,
            color = Color(0xFFFF9800),
            receivedAt = Date(System.currentTimeMillis() - 86400000)
        ),
        ReceivedFileInfo(
            name = "app_installer.apk",
            size = 45000000,
            path = "/storage/emulated/0/Download/QuickShare/app_installer.apk",
            mimeType = "application/vnd.android.package-archive",
            icon = Icons.Filled.Android,
            color = Color(0xFF03DAC6),
            receivedAt = Date(System.currentTimeMillis() - 172800000)
        )
    )
}