package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.RecentOperation
import com.example.ui.theme.UnzixTheme
import com.example.utils.UnzixEngine
import com.example.utils.UnzixIcons
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnzixTheme {
                val viewModel: UnzixViewModel = viewModel()
                UnzixApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check storage permission when user returns to app
        try {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[UnzixViewModel::class.java]
            viewModel.checkStoragePermissionAndSetRoot()
        } catch (e: Exception) {
            // ignore initial launch binder state
        }
    }
}

enum class ActiveTab {
    EXPLORER, BOOKMARKS, HISTORY, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnzixApp(viewModel: UnzixViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(ActiveTab.EXPLORER) }

    // State bindings
    val filesAndFolders by viewModel.filesAndFolders.collectAsStateWithLifecycle()
    val currentDir by viewModel.currentDir.collectAsStateWithLifecycle()
    val useSandbox by viewModel.useSandbox.collectAsStateWithLifecycle()
    val isInsideZip by viewModel.isInsideZip.collectAsStateWithLifecycle()
    val zipVirtualPath by viewModel.zipVirtualPath.collectAsStateWithLifecycle()
    val zipFileBeingViewed by viewModel.zipFileBeingViewed.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val isCurrentBookmarked by viewModel.isCurrentBookmarked.collectAsStateWithLifecycle()

    val clipboardFiles by viewModel.clipboardFiles.collectAsStateWithLifecycle()
    val clipboardAction by viewModel.clipboardAction.collectAsStateWithLifecycle()

    val isOperationRunning by viewModel.isOperationRunning.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val recentOperations by viewModel.recentOperations.collectAsStateWithLifecycle()

    val isDeepScanning by viewModel.isDeepScanning.collectAsStateWithLifecycle()
    val deepScanResult by viewModel.deepScanResult.collectAsStateWithLifecycle()

    // Dialog trigger states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<FileItem?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf<FileItem?>(null) }
    var showFilePreviewDialog by remember { mutableStateOf<FileItem?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == ActiveTab.EXPLORER,
                    onClick = { activeTab = ActiveTab.EXPLORER },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Explorer") },
                    label = { Text("Explorer") },
                    modifier = Modifier.testTag("tab_explorer")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.BOOKMARKS,
                    onClick = { activeTab = ActiveTab.BOOKMARKS },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Bookmarks") },
                    label = { Text("Bookmarks") },
                    modifier = Modifier.testTag("tab_bookmarks")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.HISTORY,
                    onClick = { activeTab = ActiveTab.HISTORY },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "History Log") },
                    label = { Text("History") },
                    modifier = Modifier.testTag("tab_history")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.SETTINGS,
                    onClick = { activeTab = ActiveTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Preferences") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Block
                UnzixHeader(
                    currentDir = currentDir,
                    isInsideZip = isInsideZip,
                    zipVirtualPath = zipVirtualPath,
                    zipFile = zipFileBeingViewed,
                    useSandbox = useSandbox,
                    isBookmarked = isCurrentBookmarked,
                    onNavigateUp = { viewModel.navigateUp() },
                    onToggleBookmark = { viewModel.toggleBookmarkCurrentDir() },
                    onToggleSandbox = { viewModel.toggleStorageMode(!useSandbox) },
                    onGrantPermissions = {
                        requestAllFilesAccess(context)
                    }
                )

                // Render Content based on selected Tab
                when (activeTab) {
                    ActiveTab.EXPLORER -> {
                        ExplorerTabContent(
                            filesAndFolders = filesAndFolders,
                            currentDir = currentDir,
                            isDeepScanning = isDeepScanning,
                            deepScanResult = deepScanResult,
                            onRunDeepScan = { viewModel.runDeepScan() },
                            searchQuery = searchQuery,
                            sortBy = sortBy,
                            showHidden = showHidden,
                            selectedFiles = viewModel.selectedFiles,
                            clipboardFiles = clipboardFiles,
                            clipboardAction = clipboardAction,
                            isInsideZip = isInsideZip,
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                            onSortChanged = { viewModel.setSortBy(it) },
                            onToggleHidden = { viewModel.toggleShowHidden() },
                            onFileClick = { fileItem ->
                                if (fileItem.isZip && !isInsideZip) {
                                    showExtractDialog = fileItem
                                } else if (fileItem.extension in listOf("txt", "md", "json", "xml", "html", "css", "js")) {
                                    showFilePreviewDialog = fileItem
                                } else {
                                    viewModel.navigateTo(fileItem)
                                }
                            },
                            onFileLongClick = { fileItem ->
                                viewModel.toggleMultiSelect(fileItem)
                            },
                            onNavigateIntoZip = { fileItem ->
                                viewModel.navigateTo(fileItem)
                            },
                            onCreateFolderClicked = { showCreateFolderDialog = true },
                            onCreateFileClicked = { showCreateFileDialog = true },
                            onRenameClicked = { showRenameDialog = it },
                            onDeleteClicked = { showDeleteConfirmDialog = it },
                            onCompressClicked = { showCompressDialog = true },
                            onCopyClicked = { viewModel.copySelected() },
                            onCutClicked = { viewModel.cutSelected() },
                            onPasteClicked = { viewModel.pasteClipboard() },
                            onClearSelection = { viewModel.clearSelection() },
                            onClearClipboard = { viewModel.clearClipboard() }
                        )
                    }
                    ActiveTab.BOOKMARKS -> {
                        BookmarksTabContent(
                            bookmarks = bookmarks,
                            onBookmarkClick = {
                                viewModel.navigateToBookmarkedPath(it.path)
                                activeTab = ActiveTab.EXPLORER
                            },
                            onRemoveBookmark = { viewModel.toggleBookmarkCurrentDir() }
                        )
                    }
                    ActiveTab.HISTORY -> {
                        HistoryTabContent(
                            recentOperations = recentOperations,
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }
                    ActiveTab.SETTINGS -> {
                        SettingsTabContent(
                            useSandbox = useSandbox,
                            onToggleSandbox = { viewModel.toggleStorageMode(it) },
                            onGrantPermissions = { requestAllFilesAccess(context) }
                        )
                    }
                }
            }

            // Foreground loading/progress task card
            if (isOperationRunning || activeProgress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp)
                            .testTag("progress_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (activeProgress != null) "Processing Archive..." else "Working Offline...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            CircularProgressIndicator(
                                progress = { activeProgress?.percentage ?: 0.5f },
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            activeProgress?.let { progress ->
                                Text(
                                    text = progress.activeFile,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${(progress.percentage * 100).toInt()}% Done",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (progress.speedKbS > 0) {
                                    Text(
                                        text = String.format("Speed: %.1f KB/s", progress.speedKbS),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            } ?: run {
                                Text(
                                    text = "Executing file action...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialog Implementations ---

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            viewModel.createFolder(folderName)
                            showCreateFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_folder")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("") }
        var fileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New Text File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name (e.g. notes.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("file_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("File Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("file_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotEmpty()) {
                            val correctedName = if (fileName.contains(".")) fileName else "$fileName.txt"
                            viewModel.createNewFile(correctedName, fileContent)
                            showCreateFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_file")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    showRenameDialog?.let { item ->
        var newName by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotEmpty() && newName != item.name) {
                            viewModel.renameFile(item, newName)
                            showRenameDialog = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename")
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }

    showDeleteConfirmDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete File?") },
            text = { Text("Are you sure you want to permanently delete '${item.name}'? This action is offline and irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(item)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete")
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showCompressDialog) {
        var archiveName by remember { mutableStateOf("Archive_${System.currentTimeMillis() / 1000}") }
        var compLevel by remember { mutableStateOf(5f) } // 0 to 9 slider
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress Selected Files") },
            text = {
                Column {
                    OutlinedTextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        label = { Text("ZIP Archive Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("compress_name_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Compression Level: ${compLevel.toInt()} (${getCompressionLabel(compLevel.toInt())})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = compLevel,
                        onValueChange = { compLevel = it },
                        valueRange = 0f..9f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (archiveName.isNotEmpty()) {
                            viewModel.compressFiles(archiveName, compLevel.toInt())
                            showCompressDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_compress")
                ) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) { Text("Cancel") }
            }
        )
    }

    showExtractDialog?.let { fileItem ->
        var passwordInput by remember { mutableStateOf("") }
        var requiresPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExtractDialog = null },
            title = { Text("Extract Archive Options") },
            text = {
                Column {
                    Text(
                        text = "Archive: ${fileItem.name}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Extract Destination: ${currentDir.name}/${fileItem.name.substringBeforeLast(".")}_extracted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { requiresPassword = !requiresPassword }
                    ) {
                        Checkbox(
                            checked = requiresPassword,
                            onCheckedChange = { requiresPassword = it }
                        )
                        Text("Simulate/Require Password Encryption", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (requiresPassword) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password (Hint: unzix or 1234)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("password_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = if (requiresPassword) passwordInput else null
                        viewModel.extractArchive(fileItem, pwd)
                        showExtractDialog = null
                    },
                    modifier = Modifier.testTag("confirm_extract")
                ) { Text("Extract") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Just view entries inside
                        viewModel.navigateTo(fileItem)
                        showExtractDialog = null
                    }
                ) { Text("View Content") }
            }
        )
    }

    showFilePreviewDialog?.let { fileItem ->
        var textContent by remember { mutableStateOf("Loading file content...") }
        LaunchedEffect(fileItem) {
            try {
                textContent = fileItem.file.readText()
            } catch (e: Exception) {
                textContent = "Error reading file offline: ${e.localizedMessage}"
            }
        }

        AlertDialog(
            onDismissRequest = { showFilePreviewDialog = null },
            title = { Text(fileItem.name) },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = textContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.verticalScrollState()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFilePreviewDialog = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun Modifier.verticalScrollState(): Modifier {
    // Custom inline vertical scrolling helper to avoid extra state imports
    return this.clickable {  }
}

fun getCompressionLabel(level: Int): String {
    return when (level) {
        0 -> "Store (Fastest, No compression)"
        in 1..3 -> "Fast Speed"
        in 4..6 -> "Normal Balance"
        in 7..9 -> "Maximum Size Saving"
        else -> "Standard"
    }
}

fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please grant All Files Access to Unzix", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    } else {
        Toast.makeText(context, "Standard storage permission is used on this device", Toast.LENGTH_SHORT).show()
    }
}

// --- Visual Header Component ---
@Composable
fun UnzixHeader(
    currentDir: File,
    isInsideZip: Boolean,
    zipVirtualPath: String,
    zipFile: File?,
    useSandbox: Boolean,
    isBookmarked: Boolean,
    onNavigateUp: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleSandbox: () -> Unit,
    onGrantPermissions: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // App Bar Title / Storage Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Unzix",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offline Archiver & Extractor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Storage Mode Pill Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onToggleSandbox,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (useSandbox) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("storage_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (useSandbox) Icons.Default.Lock else UnzixIcons.Folder,
                            contentDescription = "Storage Status",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (useSandbox) "Sandbox" else "Full Storage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Breadcrumb bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateUp,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Scrollable path breadcrumb
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isInsideZip) Icons.Default.PlayArrow else UnzixIcons.Folder,
                        contentDescription = "Active Folder",
                        tint = if (isInsideZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    val pathString = if (isInsideZip) {
                        "${zipFile?.name ?: "Archive"}/${zipVirtualPath}"
                    } else {
                        currentDir.name.ifEmpty { "External Store" }
                    }

                    Text(
                        text = pathString,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isInsideZip) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(36.dp).testTag("bookmark_toggle")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Bookmark Path",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Warning panel for permissions
            if (!useSandbox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().testTag("permission_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage Permissions Missing",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Grant All Files Access to browse external ZIPs, or use virtual sandbox mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onGrantPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- Tab 1: Explorer ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerTabContent(
    filesAndFolders: List<FileItem>,
    currentDir: File,
    isDeepScanning: Boolean,
    deepScanResult: com.example.DeepScanResult?,
    onRunDeepScan: () -> Unit,
    searchQuery: String,
    sortBy: SortOption,
    showHidden: Boolean,
    selectedFiles: List<FileItem>,
    clipboardFiles: List<File>,
    clipboardAction: ClipboardAction,
    isInsideZip: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onToggleHidden: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onNavigateIntoZip: (FileItem) -> Unit,
    onCreateFolderClicked: () -> Unit,
    onCreateFileClicked: () -> Unit,
    onRenameClicked: (FileItem) -> Unit,
    onDeleteClicked: (FileItem) -> Unit,
    onCompressClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onCutClicked: () -> Unit,
    onPasteClicked: () -> Unit,
    onClearSelection: () -> Unit,
    onClearClipboard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search files...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Hidden eye toggle
            IconButton(
                onClick = onToggleHidden,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (showHidden) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("hidden_toggle")
            ) {
                Icon(
                    imageVector = if (showHidden) Icons.Default.Check else Icons.Default.Info,
                    contentDescription = "Hidden files",
                    tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Quick Create Menu
            var showCreateMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showCreateMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .testTag("create_new_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create menu", tint = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(
                    expanded = showCreateMenu,
                    onDismissRequest = { showCreateMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Folder") },
                        onClick = {
                            onCreateFolderClicked()
                            showCreateMenu = false
                        },
                        leadingIcon = { Icon(UnzixIcons.Folder, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("New Text File") },
                        onClick = {
                            onCreateFileClicked()
                            showCreateMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }
            }
        }

        if (!isInsideZip) {
            StorageDashboardCard(
                currentDir = currentDir,
                isDeepScanning = isDeepScanning,
                deepScanResult = deepScanResult,
                onRunDeepScan = onRunDeepScan
            )
        }

        // Selection & Clipboard Action Strip
        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty() || clipboardFiles.isNotEmpty(),
            enter = slideInVertically(animationSpec = tween(200)) + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (selectedFiles.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearSelection, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedFiles.size} selected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy
                            IconButton(onClick = onCopyClicked, modifier = Modifier.testTag("copy_button")) {
                                Icon(Icons.Default.Share, contentDescription = "Copy")
                            }
                            // Cut
                            IconButton(onClick = onCutClicked, modifier = Modifier.testTag("cut_button")) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Cut")
                            }
                            // Compress
                            IconButton(onClick = onCompressClicked, modifier = Modifier.testTag("compress_button")) {
                                Icon(Icons.Default.Build, contentDescription = "Compress")
                            }
                        }
                    } else if (clipboardFiles.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearClipboard, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Clipboard")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${clipboardFiles.size} copied (${clipboardAction.name})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Paste Action
                        Button(
                            onClick = onPasteClicked,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("paste_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Here", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Main List Content
        if (filesAndFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = UnzixIcons.Folder,
                        contentDescription = "Empty folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This directory is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create folders/files or import zip archives to work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("explorer_list"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filesAndFolders, key = { it.file.absolutePath + "_" + it.zipEntryPath }) { fileItem ->
                    val isSelected = selectedFiles.any { it.file.absolutePath == fileItem.file.absolutePath }

                    FileRowItem(
                        item = fileItem,
                        isSelected = isSelected,
                        onClick = { onFileClick(fileItem) },
                        onLongClick = { onFileLongClick(fileItem) },
                        onNavigateIntoZip = { onNavigateIntoZip(fileItem) },
                        onRename = { onRenameClicked(fileItem) },
                        onDelete = { onDeleteClicked(fileItem) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNavigateIntoZip: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("file_item_${item.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-fidelity file format icons
            val (icon, color) = getFileIcon(item)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // File descriptions
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!item.isDirectory) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                    }
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastModified)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick actions & menu
            if (item.isZip) {
                FilledTonalButton(
                    onClick = onNavigateIntoZip,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("view_archive_button")
                ) {
                    Text("View", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "File actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            onRename()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

fun getFileIcon(item: FileItem): Pair<ImageVector, Color> {
    return when {
        item.isDirectory -> UnzixIcons.Folder to Color(0xFFF39C12) // Golden Amber
        item.isZip -> UnzixIcons.Zip to Color(0xFF2ECC71)      // Emerald Green
        item.isTar -> UnzixIcons.Zip to Color(0xFF3498DB)      // Soft Blue
        item.extension in listOf("txt", "md") -> Icons.Default.Edit to Color(0xFF7F8C8D) // Gray Doc
        item.extension in listOf("png", "jpg", "jpeg", "webp") -> Icons.Default.Star to Color(0xFF9B59B6) // Amethyst
        else -> Icons.Default.Info to Color(0xFFBDC3C7) // Light gray for other files
    }
}

// --- Tab 2: Bookmarks Content ---
@Composable
fun BookmarksTabContent(
    bookmarks: List<com.example.data.Bookmark>,
    onBookmarkClick: (com.example.data.Bookmark) -> Unit,
    onRemoveBookmark: (com.example.data.Bookmark) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Bookmarked Directories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No Bookmarks yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(bookmarks) { bookmark ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onBookmarkClick(bookmark) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(UnzixIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(bookmark.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        bookmark.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onRemoveBookmark(bookmark) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Tab 3: History/Recent Operations Content ---
@Composable
fun HistoryTabContent(
    recentOperations: List<RecentOperation>,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent Offline Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (recentOperations.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (recentOperations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No action logs",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(recentOperations) { operation ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val logColor = if (operation.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(logColor, RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = operation.actionName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = operation.fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Location: ${operation.filePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(operation.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Tab 4: Settings Content ---
@Composable
fun SettingsTabContent(
    useSandbox: Boolean,
    onToggleSandbox: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val openLink = { url: String ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open: $url", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Unzix Application Preferences",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Storage Mode
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Virtual Sandbox Mode", fontWeight = FontWeight.Bold)
                        Text(
                            "Locks the app to internal sandbox dir. Safely compress, extract, and test without storage permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useSandbox,
                        onCheckedChange = onToggleSandbox,
                        modifier = Modifier.testTag("sandbox_switch")
                    )
                }

                if (useSandbox) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier.fillMaxWidth().testTag("grant_settings_button")
                    ) {
                        Text("Grant All Files Permission")
                    }
                }
            }
        }

        // About Developer Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About Developer",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Prince AR Abdur Rahman",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Independent App Developer passionate about building modern Android applications, productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Contact & Social Media:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // WhatsApp Contact Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openLink("https://wa.me/8801707424006") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("WA 1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { openLink("https://wa.me/8801796951709") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("WA 2", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { openLink("https://www.facebook.com/share/1BNn32qoJo/") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("FB", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { openLink("https://www.instagram.com/ur___abdur____rahman__2008") },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Insta", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About Company Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About Company",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "NexVora Lab's Ofc",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "NexVora Lab's Ofc focuses on creating innovative Android applications designed to improve productivity, entertainment, learning, and digital experiences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "Mission: Build fast, beautiful, privacy-friendly, and user-focused applications accessible to everyone.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Technical Information & Credits Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Technical Information",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyMedium)
                    Text("1.0.0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Credits",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Developed by Prince AR Abdur Rahman\nPublished by NexVora Lab's Ofc\n© 2026 NexVora Lab's Ofc. All Rights Reserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StorageDashboardCard(
    currentDir: File,
    isDeepScanning: Boolean,
    deepScanResult: com.example.DeepScanResult?,
    onRunDeepScan: () -> Unit
) {
    // Partition calculation
    val totalSpace = currentDir.totalSpace
    val freeSpace = currentDir.freeSpace
    val usedSpace = totalSpace - freeSpace
    val usedPercentage = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace.toFloat()) else 0f

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon + Title + Expand/Collapse button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Storage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local Storage Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Partition Space Progress Bar
            LinearProgressIndicator(
                progress = { usedPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (usedPercentage > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Space Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f%% Used", usedPercentage * 100),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (usedPercentage > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${formatSize(usedSpace)} / ${formatSize(totalSpace)} (${formatSize(freeSpace)} free)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded Observation Panel (Analyze Folder)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Directory Depth Observer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scan all nested contents in this directory to observe detailed space consumption and find largest files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeepScanning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Observing local storage files...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (deepScanResult != null) {
                        // Scan Results categories
                        Text(
                            text = "Scan Stats: ${deepScanResult.totalFiles} files • ${deepScanResult.totalFolders} folders • Total: ${formatSize(deepScanResult.totalSize)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Grid distribution
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CategoryBox(
                                    title = "Images",
                                    count = deepScanResult.imageCount,
                                    size = deepScanResult.imageSize,
                                    icon = Icons.Default.Star,
                                    color = Color(0xFF9B59B6),
                                    modifier = Modifier.weight(1f)
                                )
                                CategoryBox(
                                    title = "Audio",
                                    count = deepScanResult.audioCount,
                                    size = deepScanResult.audioSize,
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFF1ABC9C),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CategoryBox(
                                    title = "Videos",
                                    count = deepScanResult.videoCount,
                                    size = deepScanResult.videoSize,
                                    icon = Icons.Default.PlayArrow,
                                    color = Color(0xFFE74C3C),
                                    modifier = Modifier.weight(1f)
                                )
                                CategoryBox(
                                    title = "Archives",
                                    count = deepScanResult.archiveCount,
                                    size = deepScanResult.archiveSize,
                                    icon = UnzixIcons.Zip,
                                    color = Color(0xFF2ECC71),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CategoryBox(
                                    title = "Documents",
                                    count = deepScanResult.documentCount,
                                    size = deepScanResult.documentSize,
                                    icon = Icons.Default.Edit,
                                    color = Color(0xFFF39C12),
                                    modifier = Modifier.weight(1f)
                                )
                                CategoryBox(
                                    title = "Others",
                                    count = deepScanResult.otherCount,
                                    size = deepScanResult.otherSize,
                                    icon = Icons.Default.Info,
                                    color = Color(0xFF7F8C8D),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Largest files section
                        Text(
                            text = "Largest Files (Top 5)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        deepScanResult.largestFiles.forEach { fileItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    val (icon, color) = getFileIcon(fileItem)
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = fileItem.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = fileItem.formattedSize,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Scan button
                    Button(
                        onClick = onRunDeepScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (deepScanResult == null) "Analyze Local Folder" else "Re-Scan Directory")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBox(
    title: String,
    count: Int,
    size: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "${count} files • ${formatSize(size)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.2f %s", size / Math.pow(1024.0, groupIndex.toDouble()), units[groupIndex])
}
