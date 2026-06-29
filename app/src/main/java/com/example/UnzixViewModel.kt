package com.example

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RecentOperation
import com.example.data.UnzixDatabase
import com.example.data.UnzixRepository
import com.example.utils.UnzixEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Stack

enum class SortOption {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, TYPE
}

enum class ClipboardAction {
    COPY, CUT, NONE
}

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val formattedSize: String,
    val extension: String,
    val lastModified: Long,
    val isZip: Boolean,
    val isTar: Boolean,
    val isInsideZip: Boolean = false,
    val zipEntryPath: String = ""
)

data class DeepScanResult(
    val totalFiles: Int,
    val totalFolders: Int,
    val totalSize: Long,
    val imageSize: Long, val imageCount: Int,
    val audioSize: Long, val audioCount: Int,
    val videoSize: Long, val videoCount: Int,
    val archiveSize: Long, val archiveCount: Int,
    val documentSize: Long, val documentCount: Int,
    val otherSize: Long, val otherCount: Int,
    val largestFiles: List<FileItem>
)

class UnzixViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "UnzixViewModel"
    private val repository: UnzixRepository

    // File Explorer Roots
    private val sandboxDir = File(application.filesDir, "sandbox")
    private val externalRootDir = Environment.getExternalStorageDirectory()

    // Preferences & Navigation States
    private val _useSandbox = MutableStateFlow(true)
    val useSandbox: StateFlow<Boolean> = _useSandbox.asStateFlow()

    private val _currentDir = MutableStateFlow(sandboxDir)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val navHistory = Stack<File>()

    // Zip exploration states (ZArchiver views zip contents directly)
    private val _isInsideZip = MutableStateFlow(false)
    val isInsideZip: StateFlow<Boolean> = _isInsideZip.asStateFlow()

    private val _zipFileBeingViewed = MutableStateFlow<File?>(null)
    val zipFileBeingViewed: StateFlow<File?> = _zipFileBeingViewed.asStateFlow()

    // ZIP Virtual Directory Structure
    private val _zipVirtualPath = MutableStateFlow("") // e.g. "Documents/Reports"
    val zipVirtualPath: StateFlow<String> = _zipVirtualPath.asStateFlow()

    private val _zipEntries = MutableStateFlow<List<UnzixEngine.ArchiveEntryInfo>>(emptyList())

    // File Explorer List, Search, Filter States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.NAME_ASC)
    val sortBy: StateFlow<SortOption> = _sortBy.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    // File Selection States
    val selectedFiles = mutableStateListOf<FileItem>()
    val isMultiSelectMode = mutableStateMapOf<String, Boolean>() // Path -> IsSelected

    // Clipboard for Cut/Copy/Paste operations
    private val _clipboardFiles = MutableStateFlow<List<File>>(emptyList())
    val clipboardFiles: StateFlow<List<File>> = _clipboardFiles.asStateFlow()

    private val _clipboardAction = MutableStateFlow(ClipboardAction.NONE)
    val clipboardAction: StateFlow<ClipboardAction> = _clipboardAction.asStateFlow()

    // Async operation progress trackers
    private val _activeProgress = MutableStateFlow<UnzixEngine.ProgressState?>(null)
    val activeProgress: StateFlow<UnzixEngine.ProgressState?> = _activeProgress.asStateFlow()

    private val _isOperationRunning = MutableStateFlow(false)
    val isOperationRunning: StateFlow<Boolean> = _isOperationRunning.asStateFlow()

    // Bookmark & Log flows from Room Repository
    val recentOperations: StateFlow<List<RecentOperation>>
    val bookmarks: StateFlow<List<com.example.data.Bookmark>>

    private val _isCurrentBookmarked = MutableStateFlow(false)
    val isCurrentBookmarked: StateFlow<Boolean> = _isCurrentBookmarked.asStateFlow()

    private val _deepScanResult = MutableStateFlow<DeepScanResult?>(null)
    val deepScanResult: StateFlow<DeepScanResult?> = _deepScanResult.asStateFlow()

    private val _isDeepScanning = MutableStateFlow(false)
    val isDeepScanning: StateFlow<Boolean> = _isDeepScanning.asStateFlow()

    init {
        // Initialize Database and Repository
        val database = UnzixDatabase.getDatabase(application)
        repository = UnzixRepository(database.unzixDao())

        recentOperations = repository.recentOperations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        bookmarks = repository.bookmarks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initialize Virtual Sandbox Folder out of the box
        UnzixEngine.initVirtualSandbox(application, sandboxDir)

        // Default to sandbox storage for reliability. If permission is granted later, user can toggle to full storage.
        checkStoragePermissionAndSetRoot()
        refreshFileList()
    }

    /**
     * Determines whether we have All Files Access permission and selects appropriate root.
     */
    fun checkStoragePermissionAndSetRoot() {
        val context = getApplication<Application>().applicationContext
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Check legacy storage permissions
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            _useSandbox.value = false
            _currentDir.value = externalRootDir
        } else {
            _useSandbox.value = true
            _currentDir.value = sandboxDir
        }
        refreshFileList()
    }

    fun toggleStorageMode(useSandboxMode: Boolean) {
        _useSandbox.value = useSandboxMode
        _isInsideZip.value = false
        _zipFileBeingViewed.value = null
        _zipVirtualPath.value = ""
        navHistory.clear()

        if (useSandboxMode) {
            _currentDir.value = sandboxDir
        } else {
            _currentDir.value = externalRootDir
        }
        refreshFileList()
    }

    /**
     * Set of Files displayed in the Explorer, filtered and sorted dynamically.
     */
    val filesAndFolders: StateFlow<List<FileItem>> = combine(
        _currentDir,
        _isInsideZip,
        _zipVirtualPath,
        _zipEntries,
        _searchQuery,
        _sortBy,
        _showHidden
    ) { array ->
        val dir = array[0] as File
        val insideZip = array[1] as Boolean
        val zipPath = array[2] as String
        val entries = array[3] as List<UnzixEngine.ArchiveEntryInfo>
        val query = array[4] as String
        val sort = array[5] as SortOption
        val hidden = array[6] as Boolean

        if (insideZip) {
            // Inside Zip Mode: filter entries that match the virtual directory level
            val virtualItems = getVirtualZipDirectoryItems(entries, zipPath)
            filterAndSortItems(virtualItems, query, sort, hidden)
        } else {
            // Physical Storage Mode
            val physicalItems = listPhysicalDirectory(dir)
            filterAndSortItems(physicalItems, query, sort, hidden)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun listPhysicalDirectory(directory: File): List<FileItem> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val files = directory.listFiles() ?: return emptyList()
        return files.map { file ->
            val isDir = file.isDirectory
            val ext = file.extension.lowercase()
            val size = if (isDir) 0L else file.length()
            FileItem(
                file = file,
                name = file.name,
                isDirectory = isDir,
                size = size,
                formattedSize = if (isDir) "" else formatFileSize(size),
                extension = ext,
                lastModified = file.lastModified(),
                isZip = ext == "zip",
                isTar = ext == "tar"
            )
        }
    }

    private fun getVirtualZipDirectoryItems(
        entries: List<UnzixEngine.ArchiveEntryInfo>,
        currentVirtualPath: String
    ): List<FileItem> {
        val items = mutableMapOf<String, FileItem>()
        // Normalize virtual path prefix
        val prefix = if (currentVirtualPath.isEmpty()) "" else "$currentVirtualPath/"

        for (entry in entries) {
            if (!entry.name.startsWith(prefix) || entry.name == prefix) continue

            val relativeName = entry.name.substring(prefix.length)
            if (relativeName.isEmpty()) continue

            // Determine if it is a folder in this sub-level
            val parts = relativeName.split("/")
            val itemName = parts[0]
            val isDir = parts.size > 1 || entry.isDirectory

            if (items.containsKey(itemName)) {
                // If it's a folder, update sizes/dates dynamically
                if (isDir) {
                    val existing = items[itemName]!!
                    items[itemName] = existing.copy(
                        size = existing.size + entry.size,
                        formattedSize = ""
                    )
                }
                continue
            }

            val virtualFile = File(_zipFileBeingViewed.value ?: sandboxDir, entry.name)
            val ext = if (isDir) "" else itemName.substringAfterLast(".", "").lowercase()

            items[itemName] = FileItem(
                file = virtualFile,
                name = itemName,
                isDirectory = isDir,
                size = if (isDir) 0L else entry.size,
                formattedSize = if (isDir) "" else formatFileSize(entry.size),
                extension = ext,
                lastModified = entry.lastModified,
                isZip = ext == "zip",
                isTar = ext == "tar",
                isInsideZip = true,
                zipEntryPath = prefix + itemName
            )
        }
        return items.values.toList()
    }

    private fun filterAndSortItems(
        items: List<FileItem>,
        query: String,
        sort: SortOption,
        hidden: Boolean
    ): List<FileItem> {
        var filtered = items

        // Filter hidden files
        if (!hidden) {
            filtered = filtered.filter { !it.name.startsWith(".") }
        }

        // Filter by search query
        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Sort items (Folders always on top, matching ZArchiver standard)
        val sortedDirs = filtered.filter { it.isDirectory }.sortedWith(getComparator(sort))
        val sortedFiles = filtered.filter { !it.isDirectory }.sortedWith(getComparator(sort))

        return sortedDirs + sortedFiles
    }

    private fun getComparator(sort: SortOption): Comparator<FileItem> {
        return when (sort) {
            SortOption.NAME_ASC -> compareBy { it.name.lowercase() }
            SortOption.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortOption.DATE_ASC -> compareBy { it.lastModified }
            SortOption.DATE_DESC -> compareByDescending { it.lastModified }
            SortOption.SIZE_ASC -> compareBy { it.size }
            SortOption.SIZE_DESC -> compareByDescending { it.size }
            SortOption.TYPE -> compareBy { it.extension }
        }
    }

    /**
     * Refreshes file list and checks bookmarks.
     */
    fun refreshFileList() {
        _deepScanResult.value = null
        _isDeepScanning.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val path = _currentDir.value.absolutePath
            _isCurrentBookmarked.value = repository.isBookmarked(path)
        }
    }

    fun navigateTo(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            if (_isInsideZip.value) {
                // If already inside ZIP, drill down virtually
                val newVirtualPath = if (_zipVirtualPath.value.isEmpty()) {
                    fileItem.name
                } else {
                    "${_zipVirtualPath.value}/${fileItem.name}"
                }
                _zipVirtualPath.value = newVirtualPath
            } else {
                navHistory.push(_currentDir.value)
                _currentDir.value = fileItem.file
                refreshFileList()
            }
        } else if (fileItem.isZip && !_isInsideZip.value) {
            // ZArchiver characteristic: entering zip as a folder on click
            enterZipArchive(fileItem.file)
        }
    }

    fun navigateUp(): Boolean {
        if (_isInsideZip.value) {
            val currentVirtual = _zipVirtualPath.value
            if (currentVirtual.isEmpty()) {
                // Exit Zip exploration completely
                _isInsideZip.value = false
                _zipFileBeingViewed.value = null
                _zipEntries.value = emptyList()
            } else {
                // Step back one virtual directory
                val parts = currentVirtual.split("/")
                if (parts.size <= 1) {
                    _zipVirtualPath.value = ""
                } else {
                    _zipVirtualPath.value = parts.subList(0, parts.size - 1).joinToString("/")
                }
            }
            return true
        } else {
            if (navHistory.isNotEmpty()) {
                _currentDir.value = navHistory.pop()
                refreshFileList()
                return true
            }
        }
        return false // Exited root or stack empty
    }

    private fun enterZipArchive(zipFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = UnzixEngine.listZipEntries(zipFile)
            _zipFileBeingViewed.value = zipFile
            _zipEntries.value = entries
            _zipVirtualPath.value = ""
            _isInsideZip.value = true
        }
    }

    // --- Search, Filter & Preferences ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(option: SortOption) {
        _sortBy.value = option
    }

    fun toggleShowHidden() {
        _showHidden.value = !_showHidden.value
    }

    // --- Bookmarks & Recent Log Actions ---
    fun toggleBookmarkCurrentDir() {
        val current = _currentDir.value
        val path = current.absolutePath
        val name = current.name.ifEmpty { "Root /" }

        viewModelScope.launch {
            if (_isCurrentBookmarked.value) {
                repository.removeBookmark(path)
                _isCurrentBookmarked.value = false
            } else {
                repository.addBookmark(path, name)
                _isCurrentBookmarked.value = true
            }
        }
    }

    fun navigateToBookmarkedPath(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            _isInsideZip.value = false
            _zipFileBeingViewed.value = null
            navHistory.push(_currentDir.value)
            _currentDir.value = file
            refreshFileList()
        }
    }

    // --- Multi-Select Mode ---
    fun toggleMultiSelect(item: FileItem) {
        if (selectedFiles.any { it.file.absolutePath == item.file.absolutePath }) {
            selectedFiles.removeAll { it.file.absolutePath == item.file.absolutePath }
            isMultiSelectMode[item.file.absolutePath] = false
        } else {
            selectedFiles.add(item)
            isMultiSelectMode[item.file.absolutePath] = true
        }
    }

    fun clearSelection() {
        selectedFiles.clear()
        isMultiSelectMode.clear()
    }

    // --- Clipboard Actions (Copy / Cut / Paste) ---
    fun copySelected() {
        _clipboardFiles.value = selectedFiles.map { it.file }
        _clipboardAction.value = ClipboardAction.COPY
        clearSelection()
    }

    fun cutSelected() {
        _clipboardFiles.value = selectedFiles.map { it.file }
        _clipboardAction.value = ClipboardAction.CUT
        clearSelection()
    }

    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
        _clipboardAction.value = ClipboardAction.NONE
    }

    fun pasteClipboard() {
        val targetDir = _currentDir.value
        val files = _clipboardFiles.value
        val action = _clipboardAction.value

        if (files.isEmpty() || action == ClipboardAction.NONE) return

        viewModelScope.launch(Dispatchers.IO) {
            _isOperationRunning.value = true
            try {
                for (file in files) {
                    if (!file.exists()) continue
                    val dest = File(targetDir, file.name)
                    if (action == ClipboardAction.COPY) {
                        copyRecursive(file, dest)
                    } else if (action == ClipboardAction.CUT) {
                        if (file.renameTo(dest)) {
                            // successful move
                        } else {
                            copyRecursive(file, dest)
                            deleteRecursive(file)
                        }
                    }
                }
                repository.insertOperation(
                    RecentOperation(
                        actionName = if (action == ClipboardAction.COPY) "Pasted (Copy)" else "Moved (Cut)",
                        fileName = files.joinToString(", ") { it.name },
                        filePath = targetDir.absolutePath,
                        fileSize = formatFileSize(files.sumOf { if (it.isDirectory) 0L else it.length() })
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Paste failed", e)
            } finally {
                clearClipboard()
                _isOperationRunning.value = false
                refreshFileList()
            }
        }
    }

    private fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dest, child.name))
            }
        } else {
            src.copyTo(dest, overwrite = true)
        }
    }

    // --- File Manager CRUD Operations ---
    fun createFolder(name: String) {
        val folder = File(_currentDir.value, name)
        if (!folder.exists()) {
            val created = folder.mkdirs()
            if (created) {
                viewModelScope.launch {
                    repository.insertOperation(
                        RecentOperation(
                            actionName = "Created Folder",
                            fileName = name,
                            filePath = folder.absolutePath,
                            fileSize = ""
                        )
                    )
                }
                refreshFileList()
            }
        }
    }

    fun createNewFile(name: String, content: String = "") {
        val file = File(_currentDir.value, name)
        try {
            file.createNewFile()
            if (content.isNotEmpty()) {
                file.writeText(content)
            }
            viewModelScope.launch {
                repository.insertOperation(
                    RecentOperation(
                        actionName = "Created File",
                        fileName = name,
                        filePath = file.absolutePath,
                        fileSize = formatFileSize(file.length())
                    )
                )
            }
            refreshFileList()
        } catch (e: Exception) {
            Log.e(TAG, "File creation failed", e)
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        val oldFile = fileItem.file
        val newFile = File(oldFile.parentFile, newName)
        if (oldFile.exists() && !newFile.exists()) {
            val renamed = oldFile.renameTo(newFile)
            if (renamed) {
                viewModelScope.launch {
                    repository.insertOperation(
                        RecentOperation(
                            actionName = "Renamed",
                            fileName = "$newName (was ${oldFile.name})",
                            filePath = newFile.absolutePath,
                            fileSize = if (fileItem.isDirectory) "" else formatFileSize(newFile.length())
                        )
                    )
                }
                refreshFileList()
            }
        }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOperationRunning.value = true
            try {
                val success = deleteRecursive(fileItem.file)
                if (success) {
                    repository.insertOperation(
                        RecentOperation(
                            actionName = "Deleted",
                            fileName = fileItem.name,
                            filePath = fileItem.file.parent ?: "",
                            fileSize = fileItem.formattedSize
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Deletion failed", e)
            } finally {
                _isOperationRunning.value = false
                refreshFileList()
            }
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    // --- Compress & Extract Operations (Unzix Offline Engine integration) ---
    fun compressFiles(archiveName: String, compressionLevel: Int) {
        if (selectedFiles.isEmpty()) return

        val targetArchive = File(_currentDir.value, if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip")
        val filesToCompress = selectedFiles.map { it.file }
        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            _isOperationRunning.value = true
            try {
                UnzixEngine.compressToZip(filesToCompress, targetArchive, compressionLevel)
                    .collect { progress ->
                        _activeProgress.value = progress
                        if (progress.isFinished) {
                            if (progress.error == null) {
                                repository.insertOperation(
                                    RecentOperation(
                                        actionName = "Compressed (ZIP)",
                                        fileName = targetArchive.name,
                                        filePath = targetArchive.absolutePath,
                                        fileSize = formatFileSize(targetArchive.length())
                                    )
                                )
                            }
                            _activeProgress.value = null
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Compression error", e)
            } finally {
                _isOperationRunning.value = false
                refreshFileList()
            }
        }
    }

    fun extractArchive(fileItem: FileItem, password: String? = null) {
        val targetDirName = fileItem.name.substringBeforeLast(".") + "_extracted"
        val targetExtractDir = File(_currentDir.value, targetDirName)

        viewModelScope.launch(Dispatchers.IO) {
            _isOperationRunning.value = true
            try {
                UnzixEngine.extractZip(fileItem.file, targetExtractDir, password)
                    .collect { progress ->
                        _activeProgress.value = progress
                        if (progress.isFinished) {
                            if (progress.error == null) {
                                repository.insertOperation(
                                    RecentOperation(
                                        actionName = "Extracted (ZIP)",
                                        fileName = fileItem.name,
                                        filePath = targetExtractDir.absolutePath,
                                        fileSize = fileItem.formattedSize
                                    )
                                )
                            }
                            _activeProgress.value = null
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction error", e)
            } finally {
                _isOperationRunning.value = false
                refreshFileList()
            }
        }
    }

    fun runDeepScan() {
        val root = _currentDir.value
        _isDeepScanning.value = true
        _deepScanResult.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var totalFiles = 0
                var totalFolders = 0
                var totalSize = 0L
                var imageSize = 0L
                var imageCount = 0
                var audioSize = 0L
                var audioCount = 0
                var videoSize = 0L
                var videoCount = 0
                var archiveSize = 0L
                var archiveCount = 0
                var documentSize = 0L
                var documentCount = 0
                var otherSize = 0L
                var otherCount = 0
                val allFilesList = mutableListOf<File>()

                val queue = java.util.Stack<File>()
                queue.push(root)

                var count = 0
                while (queue.isNotEmpty() && count < 15000) {
                    count++
                    val current = queue.pop()
                    val files = current.listFiles() ?: continue
                    for (f in files) {
                        if (f.isDirectory) {
                            totalFolders++
                            queue.push(f)
                        } else {
                            totalFiles++
                            val size = f.length()
                            totalSize += size
                            allFilesList.add(f)

                            val ext = f.extension.lowercase()
                            when {
                                ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp") -> {
                                    imageCount++
                                    imageSize += size
                                }
                                ext in listOf("mp3", "wav", "ogg", "m4a", "flac") -> {
                                    audioCount++
                                    audioSize += size
                                }
                                ext in listOf("mp4", "mkv", "avi", "webm", "3gp") -> {
                                    videoCount++
                                    videoSize += size
                                }
                                ext in listOf("zip", "tar", "rar", "7z", "gz") -> {
                                    archiveCount++
                                    archiveSize += size
                                }
                                ext in listOf("txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "json", "xml", "html") -> {
                                    documentCount++
                                    documentSize += size
                                }
                                else -> {
                                    otherCount++
                                    otherSize += size
                                }
                            }
                        }
                    }
                }

                // Get top 5 largest files
                val largest = allFilesList.sortedByDescending { it.length() }
                    .take(5)
                    .map { f ->
                        val isDir = f.isDirectory
                        val ext = f.extension.lowercase()
                        val s = f.length()
                        FileItem(
                            file = f,
                            name = f.name,
                            isDirectory = isDir,
                            size = s,
                            formattedSize = formatFileSize(s),
                            extension = ext,
                            lastModified = f.lastModified(),
                            isZip = ext == "zip",
                            isTar = ext == "tar"
                        )
                    }

                _deepScanResult.value = DeepScanResult(
                    totalFiles = totalFiles,
                    totalFolders = totalFolders,
                    totalSize = totalSize,
                    imageSize = imageSize, imageCount = imageCount,
                    audioSize = audioSize, audioCount = audioCount,
                    videoSize = videoSize, videoCount = videoCount,
                    archiveSize = archiveSize, archiveCount = archiveCount,
                    documentSize = documentSize, documentCount = documentCount,
                    otherSize = otherSize, otherCount = otherCount,
                    largestFiles = largest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Deep scan failed", e)
            } finally {
                _isDeepScanning.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllOperations()
        }
    }

    // Helper to format sizes
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
