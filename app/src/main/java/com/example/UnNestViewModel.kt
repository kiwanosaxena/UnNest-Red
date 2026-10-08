package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

enum class AppScreen {
    Splash,
    Hub,
    Preview,
    Workbench,
    Success
}

enum class FlattenMode {
    DIRECT,
    ZIP
}

enum class FileCategory(val displayName: String, val extensions: Set<String>) {
    PHOTOS("Photos", setOf("jpg", "jpeg", "png", "heic", "webp", "gif")),
    VIDEOS("Videos", setOf("mp4", "mov", "mkv", "3gp", "webm")),
    AUDIO("Audio", setOf("mp3", "m4a", "wav", "aac", "ogg", "opus")),
    DOCUMENTS("Documents", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")),
    ARCHIVES("Archives", setOf("zip", "rar", "7z", "gz")),
    OTHER("Other", emptySet());

    companion object {
        fun fromFileName(name: String): FileCategory {
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex == -1 || dotIndex == name.length - 1) return OTHER
            val ext = name.substring(dotIndex + 1).lowercase()
            return entries.firstOrNull { it != OTHER && it.extensions.contains(ext) } ?: OTHER
        }
    }
}

data class SkippedFileInfo(
    val fileName: String,
    val reason: String
)

sealed class ProcessStatus {
    object Idle : ProcessStatus()
    data class Scanning(val currentFile: String, val treeOutput: String) : ProcessStatus()
    data class Conflict(val conflictedName: String, val duplicates: List<ScannedFileInfo>) : ProcessStatus()
    data class ProcessingFiles(val filesWritten: Int, val totalFiles: Int, val currentFile: String = "") : ProcessStatus()
    data class Cancelled(val filesWritten: Int, val totalFiles: Int, val message: String) : ProcessStatus()
    data class Error(val message: String) : ProcessStatus()
}

data class ScannedFileInfo(
    val name: String,
    val parentName: String,
    val size: Long,
    val uri: Uri,
    val relativePath: String,
    var resolvedName: String = name,
    val lastModified: Long = 0L,
    val mimeType: String = ""
)

data class ScanSummary(
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val maxDepth: Int = 1,
    val totalSize: Long = 0L,
    val skippedJunkCount: Int = 0,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val categorySizes: Map<FileCategory, Long> = emptyMap(),
    val subfolders: List<String> = emptyList()
)

data class SessionStats(
    val filesProcessed: Int,
    val totalSize: Long,
    val elapsedMs: Long,
    val destName: String,
    val filesSkipped: Int = 0,
    val skippedFiles: List<SkippedFileInfo> = emptyList(),
    val zipUri: Uri? = null,
    val destTreeUri: Uri? = null
)

class UnNestViewModel : ViewModel() {

    companion object {
        private const val SCAN_PUBLISH_INTERVAL_MS = 150L
        private const val SESSION_LOGS_CAP = 500

        fun isIgnoredOrSystemJunk(name: String): Boolean {
            if (name.startsWith(".")) return true
            if (name.equals("Thumbs.db", ignoreCase = true)) return true
            if (name.equals("desktop.ini", ignoreCase = true)) return true
            return false
        }

        fun isHeavyExtension(name: String): Boolean {
            val cat = FileCategory.fromFileName(name)
            return cat == FileCategory.PHOTOS ||
                    cat == FileCategory.VIDEOS ||
                    cat == FileCategory.AUDIO ||
                    cat == FileCategory.ARCHIVES ||
                    name.endsWith(".pdf", ignoreCase = true)
        }

        fun resolveFileNames(
            files: List<ScannedFileInfo>,
            strategy: Int, // 1 = Auto-Sequence, 2 = Parent Prefix
            existingDestinationNames: Set<String> = emptySet()
        ): Map<ScannedFileInfo, String> {
            val result = mutableMapOf<ScannedFileInfo, String>()
            val dupGroups = files.groupBy { it.name.lowercase() }

            // Step 1: initial resolution for duplicate groups
            for ((_, group) in dupGroups) {
                if (group.size == 1) {
                    result[group[0]] = group[0].name
                } else {
                    val parentCounts = group.groupBy { it.parentName.lowercase() }
                    group.forEachIndexed { index, file ->
                        val extIndex = file.name.lastIndexOf('.')
                        val baseName = if (extIndex != -1) file.name.substring(0, extIndex) else file.name
                        val extension = if (extIndex != -1) file.name.substring(extIndex) else ""

                        if (strategy == 1 || strategy == 0) {
                            val suffix = if (index > 0) "_$index" else ""
                            result[file] = "$baseName$suffix$extension"
                        } else {
                            val hasDuplicateParents = (parentCounts[file.parentName.lowercase()]?.size ?: 0) > 1
                            if (hasDuplicateParents) {
                                val segments = file.relativePath.split('/').filter { it.isNotEmpty() }
                                if (segments.size >= 2) {
                                    val grandparent = segments[segments.size - 2]
                                    val parent = segments[segments.size - 1]
                                    result[file] = "${baseName}_(${grandparent}-${parent})$extension"
                                } else {
                                    val parent = file.parentName.ifEmpty { "Root" }
                                    result[file] = "${baseName}_(${parent})$extension"
                                }
                            } else {
                                val parent = file.parentName.ifEmpty { "Root" }
                                result[file] = "${baseName}_(${parent})$extension"
                            }
                        }
                    }
                }
            }

            // Step 2: guarantee uniqueness across all files + existing destination names
            val usedNamesLower = existingDestinationNames.map { it.lowercase() }.toMutableSet()

            for (file in files) {
                var candidate = result[file] ?: file.name
                val extIndex = candidate.lastIndexOf('.')
                val extension = if (extIndex != -1) candidate.substring(extIndex) else ""
                val rawExtIndex = file.name.lastIndexOf('.')
                val rawBase = if (rawExtIndex != -1) file.name.substring(0, rawExtIndex) else file.name

                if (usedNamesLower.contains(candidate.lowercase())) {
                    if (strategy == 2) {
                        val segments = file.relativePath.split('/').filter { it.isNotEmpty() }
                        if (segments.size >= 2) {
                            val grandparent = segments[segments.size - 2]
                            val parent = segments[segments.size - 1]
                            val gpCandidate = "${rawBase}_(${grandparent}-${parent})$extension"
                            if (!usedNamesLower.contains(gpCandidate.lowercase())) {
                                candidate = gpCandidate
                            }
                        }
                    }

                    if (usedNamesLower.contains(candidate.lowercase())) {
                        var counter = 2
                        val candExtIndex = candidate.lastIndexOf('.')
                        val candBase = if (candExtIndex != -1) candidate.substring(0, candExtIndex) else candidate
                        val cleanBase = candBase.replace(Regex("_\\d+$"), "")

                        var uniqueCandidate = "${cleanBase}_$counter$extension"
                        while (usedNamesLower.contains(uniqueCandidate.lowercase())) {
                            counter++
                            uniqueCandidate = "${cleanBase}_$counter$extension"
                        }
                        candidate = uniqueCandidate
                    }
                }

                usedNamesLower.add(candidate.lowercase())
                result[file] = candidate
            }

            return result
        }

        fun calculateSelectionStats(
            allFiles: List<ScannedFileInfo>,
            selectedCats: Set<FileCategory>,
            selectedFolds: Set<String>,
            minSizeBytes: Long? = null,
            maxSizeBytes: Long? = null,
            minDateMillis: Long? = null,
            maxDateMillis: Long? = null
        ): Pair<Int, Long> {
            var count = 0
            var bytes = 0L
            for (f in allFiles) {
                val cat = FileCategory.fromFileName(f.name)
                val folder = f.relativePath.ifEmpty { "Root" }
                if (selectedCats.contains(cat) && selectedFolds.contains(folder)) {
                    if (minSizeBytes != null && f.size < minSizeBytes) continue
                    if (maxSizeBytes != null && f.size > maxSizeBytes) continue
                    if (minDateMillis != null && f.lastModified > 0 && f.lastModified < minDateMillis) continue
                    if (maxDateMillis != null && f.lastModified > 0 && f.lastModified > maxDateMillis) continue
                    count++
                    bytes += f.size
                }
            }
            return Pair(count, bytes)
        }

        fun estimateRequiredSpace(
            selectedFiles: List<ScannedFileInfo>,
            mode: FlattenMode
        ): Long {
            if (mode == FlattenMode.DIRECT) {
                return selectedFiles.sumOf { it.size }
            }
            // ZIP estimation: 0.95 for pre-compressed types, 0.60 for others
            var totalEst = 0L
            for (f in selectedFiles) {
                val cat = FileCategory.fromFileName(f.name)
                val isPreCompressed = cat == FileCategory.PHOTOS ||
                        cat == FileCategory.VIDEOS ||
                        cat == FileCategory.AUDIO ||
                        cat == FileCategory.ARCHIVES ||
                        f.name.endsWith(".pdf", ignoreCase = true)
                totalEst += if (isPreCompressed) {
                    (f.size * 0.95).toLong()
                } else {
                    (f.size * 0.60).toLong()
                }
            }
            return totalEst
        }
    }

    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }

    private val _currentScreen = MutableStateFlow(AppScreen.Splash)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _sourceDirectoryUri = MutableStateFlow<Uri?>(null)
    val sourceDirectoryUri: StateFlow<Uri?> = _sourceDirectoryUri.asStateFlow()

    private val _destinationDirectoryUri = MutableStateFlow<Uri?>(null)
    val destinationDirectoryUri: StateFlow<Uri?> = _destinationDirectoryUri.asStateFlow()

    private val _flattenMode = MutableStateFlow(FlattenMode.DIRECT)
    val flattenMode: StateFlow<FlattenMode> = _flattenMode.asStateFlow()

    private val _processStatus = MutableStateFlow<ProcessStatus>(ProcessStatus.Idle)
    val processStatus: StateFlow<ProcessStatus> = _processStatus.asStateFlow()

    private val _sessionStats = MutableStateFlow<SessionStats?>(null)
    val sessionStats: StateFlow<SessionStats?> = _sessionStats.asStateFlow()

    // Bounded session logs (capped at 500)
    private val _sessionLogs = MutableStateFlow<List<String>>(emptyList())
    val sessionLogs: StateFlow<List<String>> = _sessionLogs.asStateFlow()
    private val sessionLogsDeque = ArrayDeque<String>()

    private val _maxScannedDepth = MutableStateFlow(1)
    val maxScannedDepth: StateFlow<Int> = _maxScannedDepth.asStateFlow()

    private val _duplicatesCount = MutableStateFlow(0)
    val duplicatesCount: StateFlow<Int> = _duplicatesCount.asStateFlow()

    private val _conflictsResolvedCount = MutableStateFlow(0)
    val conflictsResolvedCount: StateFlow<Int> = _conflictsResolvedCount.asStateFlow()

    // Preview and Selection states
    private val allScannedFiles = mutableListOf<ScannedFileInfo>()

    private val _scanSummary = MutableStateFlow<ScanSummary?>(null)
    val scanSummary: StateFlow<ScanSummary?> = _scanSummary.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<FileCategory>>(FileCategory.entries.toSet())
    val selectedCategories: StateFlow<Set<FileCategory>> = _selectedCategories.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _conflictStrategy = MutableStateFlow(1) // 1 = Auto-Sequence, 2 = Parent Prefix
    val conflictStrategy: StateFlow<Int> = _conflictStrategy.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Additional Features: Skip identical files and Preview filters
    private val _skipIdenticalDuplicates = MutableStateFlow(false)
    val skipIdenticalDuplicates: StateFlow<Boolean> = _skipIdenticalDuplicates.asStateFlow()

    private val _filterMinSizeBytes = MutableStateFlow<Long?>(null)
    val filterMinSizeBytes: StateFlow<Long?> = _filterMinSizeBytes.asStateFlow()

    private val _filterMaxSizeBytes = MutableStateFlow<Long?>(null)
    val filterMaxSizeBytes: StateFlow<Long?> = _filterMaxSizeBytes.asStateFlow()

    private val _filterMinDateMillis = MutableStateFlow<Long?>(null)
    val filterMinDateMillis: StateFlow<Long?> = _filterMinDateMillis.asStateFlow()

    private val _filterMaxDateMillis = MutableStateFlow<Long?>(null)
    val filterMaxDateMillis: StateFlow<Long?> = _filterMaxDateMillis.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressMessage = MutableStateFlow("")
    val scanProgressMessage: StateFlow<String> = _scanProgressMessage.asStateFlow()

    private val _destinationAvailableBytes = MutableStateFlow<Long?>(null)
    val destinationAvailableBytes: StateFlow<Long?> = _destinationAvailableBytes.asStateFlow()

    private val _showOptimizationDialog = MutableStateFlow(false)
    val showOptimizationDialog: StateFlow<Boolean> = _showOptimizationDialog.asStateFlow()

    private val _detectedHeavyFilesCount = MutableStateFlow(0)
    val detectedHeavyFilesCount: StateFlow<Int> = _detectedHeavyFilesCount.asStateFlow()

    var isOptimizationEnabled = false

    private var scanJob: Job? = null
    private var workObserverJob: Job? = null
    private var currentWorkId: UUID? = null

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSourceDirectory(uri: Uri?) {
        if (_sourceDirectoryUri.value != uri) {
            _sourceDirectoryUri.value = uri
            discardScanResults()
        }
    }

    fun setDestinationDirectory(uri: Uri?, context: Context? = null) {
        if (_destinationDirectoryUri.value != uri) {
            _destinationDirectoryUri.value = uri
            _destinationAvailableBytes.value = null
            if (uri != null && context != null) {
                checkDestinationFreeSpace(context, uri)
            }
        }
    }

    fun isDestinationInsideSource(): Boolean {
        val src = _sourceDirectoryUri.value ?: return false
        val dest = _destinationDirectoryUri.value ?: return false
        if (src == dest || src.toString() == dest.toString()) return true

        val srcDocId = try {
            DocumentsContract.getTreeDocumentId(src)
        } catch (e: Exception) {
            try { DocumentsContract.getDocumentId(src) } catch (e2: Exception) { null }
        }
        val destDocId = try {
            DocumentsContract.getTreeDocumentId(dest)
        } catch (e: Exception) {
            try { DocumentsContract.getDocumentId(dest) } catch (e2: Exception) { null }
        }

        if (srcDocId != null && destDocId != null) {
            if (destDocId == srcDocId || destDocId.startsWith("$srcDocId/") || destDocId.startsWith("$srcDocId:")) {
                return true
            }
        }

        val srcPath = src.path
        val destPath = dest.path
        if (!srcPath.isNullOrEmpty() && !destPath.isNullOrEmpty()) {
            if (destPath == srcPath || destPath.startsWith("$srcPath/")) {
                return true
            }
        }

        return false
    }

    fun isFileInsideDestination(file: ScannedFileInfo): Boolean {
        val dest = _destinationDirectoryUri.value ?: return false
        val src = _sourceDirectoryUri.value ?: return false
        if (!isDestinationInsideSource()) return false

        if (dest == src || dest.toString() == src.toString()) {
            if (file.name.startsWith("UnNest_report_") && file.name.endsWith(".csv")) return true
            if (file.name.startsWith("UnNest_Flattened_") && file.name.endsWith(".zip")) return true
            return false
        }

        val srcDocId = try {
            DocumentsContract.getTreeDocumentId(src)
        } catch (e: Exception) {
            try { DocumentsContract.getDocumentId(src) } catch (e2: Exception) { null }
        }
        val destDocId = try {
            DocumentsContract.getTreeDocumentId(dest)
        } catch (e: Exception) {
            try { DocumentsContract.getDocumentId(dest) } catch (e2: Exception) { null }
        }

        if (destDocId != null) {
            val fileDocId = try {
                DocumentsContract.getDocumentId(file.uri)
            } catch (e: Exception) { null }
            if (fileDocId != null && (fileDocId == destDocId || fileDocId.startsWith("$destDocId/"))) {
                return true
            }

            if (srcDocId != null && destDocId.startsWith("$srcDocId/")) {
                val destRelative = destDocId.removePrefix("$srcDocId/")
                if (file.relativePath == destRelative || file.relativePath.startsWith("$destRelative/")) {
                    return true
                }
            }
        }

        val destPath = dest.path
        val filePath = file.uri.path
        if (!destPath.isNullOrEmpty() && !filePath.isNullOrEmpty()) {
            if (filePath.startsWith("$destPath/")) return true
        }

        return false
    }

    fun setFlattenMode(mode: FlattenMode) {
        _flattenMode.value = mode
    }

    fun setOptimizationDialogVisibility(visible: Boolean) {
        _showOptimizationDialog.value = visible
    }

    fun addLog(message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = sdf.format(java.util.Date())
        val logLine = "[$timeStr] $message"
        synchronized(sessionLogsDeque) {
            if (sessionLogsDeque.size >= SESSION_LOGS_CAP) {
                sessionLogsDeque.removeFirst()
            }
            sessionLogsDeque.addLast(logLine)
            _sessionLogs.value = sessionLogsDeque.toList()
        }
    }

    fun discardScanResults() {
        allScannedFiles.clear()
        _scanSummary.value = null
        _selectedCategories.value = FileCategory.entries.toSet()
        _selectedFolders.value = emptySet()
        _duplicatesCount.value = 0
        _destinationAvailableBytes.value = null
        _searchQuery.value = ""
        _skipIdenticalDuplicates.value = false
        _filterMinSizeBytes.value = null
        _filterMaxSizeBytes.value = null
        _filterMinDateMillis.value = null
        _filterMaxDateMillis.value = null
    }

    fun resetSession() {
        scanJob?.cancel()
        workObserverJob?.cancel()
        _sourceDirectoryUri.value = null
        _destinationDirectoryUri.value = null
        _flattenMode.value = FlattenMode.DIRECT
        _processStatus.value = ProcessStatus.Idle
        _sessionStats.value = null
        synchronized(sessionLogsDeque) {
            sessionLogsDeque.clear()
            _sessionLogs.value = emptyList()
        }
        _maxScannedDepth.value = 1
        _duplicatesCount.value = 0
        _conflictsResolvedCount.value = 0
        discardScanResults()
        _showOptimizationDialog.value = false
        _detectedHeavyFilesCount.value = 0
        isOptimizationEnabled = false
        currentWorkId = null
        navigateTo(AppScreen.Hub)
    }

    // Toggle Category in Preview
    fun toggleCategory(category: FileCategory) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _selectedCategories.value = current
    }

    // Toggle Folder in Preview
    fun toggleFolder(folder: String) {
        val current = _selectedFolders.value.toMutableSet()
        if (current.contains(folder)) {
            current.remove(folder)
        } else {
            current.add(folder)
        }
        _selectedFolders.value = current
    }

    // Select/Deselect All Folders
    fun selectAllFolders(select: Boolean) {
        val summary = _scanSummary.value ?: return
        if (select) {
            _selectedFolders.value = summary.subfolders.toSet()
        } else {
            _selectedFolders.value = emptySet()
        }
    }

    fun setConflictStrategy(strategy: Int) {
        _conflictStrategy.value = strategy
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSkipIdenticalDuplicates(skip: Boolean) {
        _skipIdenticalDuplicates.value = skip
    }

    fun setFilterMinSizeBytes(bytes: Long?) {
        _filterMinSizeBytes.value = bytes
    }

    fun setFilterMaxSizeBytes(bytes: Long?) {
        _filterMaxSizeBytes.value = bytes
    }

    fun setFilterMinDateMillis(millis: Long?) {
        _filterMinDateMillis.value = millis
    }

    fun setFilterMaxDateMillis(millis: Long?) {
        _filterMaxDateMillis.value = millis
    }

    fun resetFilters() {
        _filterMinSizeBytes.value = null
        _filterMaxSizeBytes.value = null
        _filterMinDateMillis.value = null
        _filterMaxDateMillis.value = null
    }

    // Live selected files calculation with filters
    fun getSelectedFiles(): List<ScannedFileInfo> {
        val cats = _selectedCategories.value
        val folds = _selectedFolders.value
        val minSize = _filterMinSizeBytes.value
        val maxSize = _filterMaxSizeBytes.value
        val minDate = _filterMinDateMillis.value
        val maxDate = _filterMaxDateMillis.value
        val destInsideSrc = isDestinationInsideSource()

        return allScannedFiles.filter { f ->
            if (destInsideSrc && isFileInsideDestination(f)) return@filter false
            val cat = FileCategory.fromFileName(f.name)
            val folder = f.relativePath.ifEmpty { "Root" }
            if (!cats.contains(cat) || !folds.contains(folder)) return@filter false
            if (minSize != null && f.size < minSize) return@filter false
            if (maxSize != null && f.size > maxSize) return@filter false
            if (minDate != null && f.lastModified > 0 && f.lastModified < minDate) return@filter false
            if (maxDate != null && f.lastModified > 0 && f.lastModified > maxDate) return@filter false
            true
        }
    }

    fun getSelectedFilesStats(): Pair<Int, Long> {
        val files = if (isDestinationInsideSource()) {
            allScannedFiles.filterNot { isFileInsideDestination(it) }
        } else {
            allScannedFiles
        }
        return calculateSelectionStats(
            allFiles = files,
            selectedCats = _selectedCategories.value,
            selectedFolds = _selectedFolders.value,
            minSizeBytes = _filterMinSizeBytes.value,
            maxSizeBytes = _filterMaxSizeBytes.value,
            minDateMillis = _filterMinDateMillis.value,
            maxDateMillis = _filterMaxDateMillis.value
        )
    }

    fun hasSufficientSpace(): Boolean {
        val dest = _destinationDirectoryUri.value ?: return false
        val avail = _destinationAvailableBytes.value ?: return true
        val selected = getSelectedFiles()
        val required = estimateRequiredSpace(selected, _flattenMode.value)
        return avail >= required
    }

    fun estimateRequiredSpace(selectedFiles: List<ScannedFileInfo>, mode: FlattenMode): Long {
        return Companion.estimateRequiredSpace(selectedFiles, mode)
    }

    fun getConflictExamples(strategy: Int): List<Pair<String, String>> {
        val selected = getSelectedFiles()
        val dupGroups = selected.groupBy { it.name.lowercase() }.filter { it.value.size > 1 }
        if (dupGroups.isEmpty()) return emptyList()

        val examples = mutableListOf<Pair<String, String>>()
        val simulated = resolveFileNames(selected, strategy)

        for ((_, group) in dupGroups) {
            for (file in group) {
                val from = if (file.relativePath.isNotEmpty()) "${file.relativePath}/${file.name}" else file.name
                val to = simulated[file] ?: file.name
                if (from != to) {
                    examples.add(Pair(from, to))
                    if (examples.size >= 5) return examples
                }
            }
        }
        return examples
    }

    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
        _processStatus.value = ProcessStatus.Idle
    }

    fun cancelJob(context: Context) {
        scanJob?.cancel()
        currentWorkId?.let { workId ->
            try {
                WorkManager.getInstance(context).cancelWorkById(workId)
            } catch (ignored: Exception) {}
        }

        val currentStatus = _processStatus.value
        val (written, total) = when (currentStatus) {
            is ProcessStatus.ProcessingFiles -> Pair(currentStatus.filesWritten, currentStatus.totalFiles)
            else -> Pair(0, allScannedFiles.size)
        }

        val cancelMsg = "Cancelled – $written of $total files were copied"
        _processStatus.value = ProcessStatus.Cancelled(
            filesWritten = written,
            totalFiles = total,
            message = cancelMsg
        )
        addLog(cancelMsg)
    }

    // Faster Scan using DocumentsContract queries
    fun startScan(context: Context) {
        val srcUri = _sourceDirectoryUri.value ?: return
        val destUri = _destinationDirectoryUri.value
        val appContext = context.applicationContext

        _isScanning.value = true
        _scanProgressMessage.value = "Scanning…"
        _processStatus.value = ProcessStatus.Scanning("Scanning directory…", "")
        _maxScannedDepth.value = 1
        _duplicatesCount.value = 0
        _conflictsResolvedCount.value = 0
        allScannedFiles.clear()

        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            var totalFolders = 0
            var maxDepth = 1
            var skippedJunk = 0
            var lastPublishTime = 0L

            try {
                val rootDocId = try {
                    DocumentsContract.getTreeDocumentId(srcUri)
                } catch (e: Exception) {
                    try { DocumentsContract.getDocumentId(srcUri) } catch (e2: Exception) { null }
                }

                val destDocId = try {
                    destUri?.let {
                        try { DocumentsContract.getTreeDocumentId(it) }
                        catch (e: Exception) { DocumentsContract.getDocumentId(it) }
                    }
                } catch (e: Exception) { null }

                if (rootDocId != null) {
                    // Fast query using DocumentsContract
                    suspend fun scanDirectoryFast(
                        dirDocId: String,
                        dirName: String,
                        relativePath: String,
                        depth: Int
                    ) {
                        coroutineContext.ensureActive()
                        totalFolders++
                        if (depth > maxDepth) maxDepth = depth

                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(srcUri, dirDocId)
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        )

                        var cursor: android.database.Cursor? = null
                        try {
                            cursor = appContext.contentResolver.query(childrenUri, projection, null, null, null)
                            if (cursor != null) {
                                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                                while (cursor.moveToNext()) {
                                    coroutineContext.ensureActive()
                                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                                    val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                                    if (name.isEmpty() || isIgnoredOrSystemJunk(name)) {
                                        skippedJunk++
                                        continue
                                    }

                                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                        // Skip destination folder if inside source
                                        if (destDocId != null && (docId == destDocId || docId.startsWith("$destDocId/"))) {
                                            continue
                                        }
                                        val nextRelative = if (relativePath.isEmpty()) name else "$relativePath/$name"
                                        scanDirectoryFast(docId, name, nextRelative, depth + 1)
                                    } else {
                                        if (name.startsWith("UnNest_Flattened_") && name.endsWith(".zip")) {
                                            continue
                                        }
                                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(srcUri, docId)
                                        val parentDisplay = dirName.ifEmpty { "Source" }
                                        val item = ScannedFileInfo(
                                            name = name,
                                            parentName = parentDisplay,
                                            size = size,
                                            uri = fileUri,
                                            relativePath = relativePath,
                                            resolvedName = name,
                                            lastModified = mod,
                                            mimeType = mime
                                        )
                                        allScannedFiles.add(item)

                                        val now = System.currentTimeMillis()
                                        if (now - lastPublishTime >= SCAN_PUBLISH_INTERVAL_MS) {
                                            lastPublishTime = now
                                            val msg = "Scanned ${allScannedFiles.size} files…"
                                            _scanProgressMessage.value = msg
                                            _processStatus.value = ProcessStatus.Scanning(name, msg)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        } finally {
                            cursor?.close()
                        }
                    }

                    scanDirectoryFast(rootDocId, "", "", 1)
                } else {
                    // Fallback to DocumentFile
                    val rootDoc = DocumentFile.fromTreeUri(appContext, srcUri)
                    if (rootDoc != null) {
                        suspend fun scanDocFileFallback(dirDoc: DocumentFile, relativePath: String, depth: Int) {
                            coroutineContext.ensureActive()
                            totalFolders++
                            if (depth > maxDepth) maxDepth = depth

                            val children = dirDoc.listFiles()
                            for (child in children) {
                                coroutineContext.ensureActive()
                                val name = child.name.orEmpty()
                                if (name.isEmpty() || isIgnoredOrSystemJunk(name)) {
                                    skippedJunk++
                                    continue
                                }
                                if (child.isDirectory) {
                                    val nextRelative = if (relativePath.isEmpty()) name else "$relativePath/$name"
                                    scanDocFileFallback(child, nextRelative, depth + 1)
                                } else if (child.isFile) {
                                    if (name.startsWith("UnNest_Flattened_") && name.endsWith(".zip")) continue
                                    val item = ScannedFileInfo(
                                        name = name,
                                        parentName = dirDoc.name.orEmpty().ifEmpty { "Source" },
                                        size = child.length(),
                                        uri = child.uri,
                                        relativePath = relativePath,
                                        resolvedName = name,
                                        lastModified = child.lastModified(),
                                        mimeType = child.type.orEmpty()
                                    )
                                    allScannedFiles.add(item)
                                    val now = System.currentTimeMillis()
                                    if (now - lastPublishTime >= SCAN_PUBLISH_INTERVAL_MS) {
                                        lastPublishTime = now
                                        val msg = "Scanned ${allScannedFiles.size} files…"
                                        _scanProgressMessage.value = msg
                                        _processStatus.value = ProcessStatus.Scanning(name, msg)
                                    }
                                }
                            }
                        }
                        scanDocFileFallback(rootDoc, "", 1)
                    }
                }

                if (allScannedFiles.isEmpty()) {
                    _isScanning.value = false
                    _processStatus.value = ProcessStatus.Error("The Selected Source folder is empty. No files to flatten.")
                    return@launch
                }

                // Compute scan summary
                val catCounts = mutableMapOf<FileCategory, Int>()
                val catSizes = mutableMapOf<FileCategory, Long>()
                val subfolderSet = mutableSetOf<String>()
                var totalBytes = 0L

                for (f in allScannedFiles) {
                    val cat = FileCategory.fromFileName(f.name)
                    catCounts[cat] = (catCounts[cat] ?: 0) + 1
                    catSizes[cat] = (catSizes[cat] ?: 0L) + f.size
                    subfolderSet.add(f.relativePath.ifEmpty { "Root" })
                    totalBytes += f.size
                }

                val summary = ScanSummary(
                    totalFiles = allScannedFiles.size,
                    totalFolders = totalFolders,
                    maxDepth = maxDepth,
                    totalSize = totalBytes,
                    skippedJunkCount = skippedJunk,
                    categoryCounts = catCounts,
                    categorySizes = catSizes,
                    subfolders = subfolderSet.sorted()
                )

                _scanSummary.value = summary
                _selectedCategories.value = FileCategory.entries.toSet()
                _selectedFolders.value = subfolderSet
                _maxScannedDepth.value = maxDepth

                // Count duplicates for Preview
                val dupGroups = allScannedFiles.groupBy { it.name.lowercase() }.filter { it.value.size > 1 }
                _duplicatesCount.value = dupGroups.values.sumOf { it.size }

                // Check destination storage
                if (destUri != null) {
                    checkDestinationFreeSpace(appContext, destUri)
                } else {
                    _destinationAvailableBytes.value = null
                }

                _isScanning.value = false
                _processStatus.value = ProcessStatus.Idle
                navigateTo(AppScreen.Preview)
            } catch (e: Exception) {
                _isScanning.value = false
                if (e is kotlinx.coroutines.CancellationException) {
                    _processStatus.value = ProcessStatus.Idle
                } else {
                    _processStatus.value = ProcessStatus.Error("Scan failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun checkDestinationFreeSpace(context: Context, destUri: Uri?) {
        if (destUri == null) {
            _destinationAvailableBytes.value = null
            return
        }
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val freeBytes = getDestinationFreeSpace(appContext, destUri)
            _destinationAvailableBytes.value = freeBytes
        }
    }

    private fun getDestinationFreeSpace(context: Context, destUri: Uri): Long {
        // 1. Try DocumentsContract query on Root.COLUMN_AVAILABLE_BYTES
        try {
            val authority = destUri.authority
            if (authority != null) {
                val rootsUri = DocumentsContract.buildRootsUri(authority)
                val projection = arrayOf(
                    DocumentsContract.Root.COLUMN_ROOT_ID,
                    DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
                )
                context.contentResolver.query(rootsUri, projection, null, null, null)?.use { cursor ->
                    val rootIdIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID)
                    val availIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                    if (availIdx >= 0) {
                        val targetRootId = try { DocumentsContract.getRootId(destUri) } catch (e: Exception) { null }
                        while (cursor.moveToNext()) {
                            val rootId = if (rootIdIdx >= 0) cursor.getString(rootIdIdx) else null
                            if (targetRootId == null || rootId == targetRootId) {
                                if (!cursor.isNull(availIdx)) {
                                    val avail = cursor.getLong(availIdx)
                                    if (avail > 0) return avail
                                }
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        // 2. Fallback to StatFs on primary external storage
        try {
            val extDir = Environment.getExternalStorageDirectory()
            val stat = StatFs(extDir.path)
            val avail = stat.availableBytes
            if (avail > 0) return avail
        } catch (ignored: Exception) {}

        // 3. Fallback to app internal storage
        try {
            val stat = StatFs(context.filesDir.path)
            return stat.availableBytes
        } catch (ignored: Exception) {}

        return Long.MAX_VALUE
    }

    // Start extraction with selected files and chosen conflict strategy
    fun startExtractionWithSelectedFiles(context: Context) {
        val destUri = _destinationDirectoryUri.value ?: return

        val selectedFiles = getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            _processStatus.value = ProcessStatus.Error("No files selected for extraction.")
            return
        }

        // Feature 5: Massive Files Detected ZIP optimization check
        if (_flattenMode.value == FlattenMode.ZIP && !isOptimizationEnabled) {
            val heavyCount = selectedFiles.count { isHeavyExtension(it.name.lowercase()) }
            if (heavyCount > 0) {
                _detectedHeavyFilesCount.value = heavyCount
                _showOptimizationDialog.value = true
                return
            }
        }

        executeExtractionInternal(context)
    }

    fun proceedWithOptimization(context: Context) {
        isOptimizationEnabled = true
        _showOptimizationDialog.value = false
        executeExtractionInternal(context)
    }

    fun proceedWithoutOptimization(context: Context) {
        isOptimizationEnabled = false
        _showOptimizationDialog.value = false
        executeExtractionInternal(context)
    }

    private fun executeExtractionInternal(context: Context) {
        val destUri = _destinationDirectoryUri.value ?: return
        val appContext = context.applicationContext

        val selectedFiles = getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            _processStatus.value = ProcessStatus.Error("No files selected for extraction.")
            return
        }

        // Check if destination pre-reading is needed in DIRECT mode
        val existingDestNames = mutableSetOf<String>()
        if (_flattenMode.value == FlattenMode.DIRECT) {
            try {
                val destDoc = DocumentFile.fromTreeUri(appContext, destUri)
                if (destDoc != null && destDoc.isDirectory) {
                    for (child in destDoc.listFiles()) {
                        if (child.isFile) {
                            child.name?.let { existingDestNames.add(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                addLog("Destination pre-read notice: ${e.localizedMessage}")
            }
        }

        // Apply conflict renaming rule once
        val resolvedMap = resolveFileNames(selectedFiles, _conflictStrategy.value, existingDestNames)
        for (f in selectedFiles) {
            f.resolvedName = resolvedMap[f] ?: f.name
        }

        val dupCount = selectedFiles.groupBy { it.name.lowercase() }.filter { it.value.size > 1 }.values.sumOf { it.size }
        _duplicatesCount.value = dupCount
        _conflictsResolvedCount.value = selectedFiles.count { it.resolvedName != it.name }

        navigateTo(AppScreen.Workbench)
        synchronized(sessionLogsDeque) {
            sessionLogsDeque.clear()
            _sessionLogs.value = emptyList()
        }
        _processStatus.value = ProcessStatus.ProcessingFiles(0, selectedFiles.size, "")
        addLog("Initializing UnNest Flattener Sandbox...")
        addLog("Selected Files to Extract: ${selectedFiles.size}")
        addLog("Conflict Renaming Strategy: ${if (_conflictStrategy.value == 1) "Auto-Sequence" else "Parent Prefix"}")

        try {
            val jobId = UUID.randomUUID().toString()
            val itemsFile = File(appContext.cacheDir, "unnest_job_${jobId}.json")
            val skippedFile = File(appContext.cacheDir, "unnest_skipped_${jobId}.json")

            val jsonArray = JSONArray()
            for (f in selectedFiles) {
                val o = JSONObject().apply {
                    put("name", f.name)
                    put("parentName", f.parentName)
                    put("size", f.size)
                    put("uriString", f.uri.toString())
                    put("relativePath", f.relativePath)
                    put("resolvedName", f.resolvedName)
                }
                jsonArray.put(o)
            }
            itemsFile.writeText(jsonArray.toString())

            val inputData = workDataOf(
                ExtractionWorker.KEY_DEST_URI to destUri.toString(),
                ExtractionWorker.KEY_FLATTEN_MODE to _flattenMode.value.name,
                ExtractionWorker.KEY_OPTIMIZATION to isOptimizationEnabled,
                ExtractionWorker.KEY_SKIP_IDENTICAL_DUPLICATES to _skipIdenticalDuplicates.value,
                ExtractionWorker.KEY_ITEMS_JSON_PATH to itemsFile.absolutePath,
                ExtractionWorker.KEY_SKIPPED_JSON_PATH to skippedFile.absolutePath
            )

            val workRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .build()

            currentWorkId = workRequest.id
            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueue(workRequest)

            workObserverJob?.cancel()
            workObserverJob = viewModelScope.launch {
                workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                    if (workInfo == null) return@collect
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val written = workInfo.progress.getInt(ExtractionWorker.KEY_FILES_WRITTEN, 0)
                            val total = workInfo.progress.getInt(ExtractionWorker.KEY_TOTAL_FILES, selectedFiles.size)
                            val current = workInfo.progress.getString(ExtractionWorker.KEY_CURRENT_FILE).orEmpty()
                            _processStatus.value = ProcessStatus.ProcessingFiles(written, total, current)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val written = workInfo.outputData.getInt(ExtractionWorker.KEY_FILES_WRITTEN, selectedFiles.size)
                            val totalBytes = workInfo.outputData.getLong(ExtractionWorker.KEY_TOTAL_SIZE, 0L)
                            val elapsed = workInfo.outputData.getLong(ExtractionWorker.KEY_ELAPSED_MS, 0L)
                            val destName = workInfo.outputData.getString(ExtractionWorker.KEY_DEST_NAME) ?: "Destination"
                            val skippedCount = workInfo.outputData.getInt(ExtractionWorker.KEY_SKIPPED_COUNT, 0)
                            val zipUriStr = workInfo.outputData.getString(ExtractionWorker.KEY_ZIP_URI)
                            val zipUri = if (!zipUriStr.isNullOrEmpty()) Uri.parse(zipUriStr) else null

                            val skippedList = mutableListOf<SkippedFileInfo>()
                            if (skippedFile.exists()) {
                                try {
                                    val arr = JSONArray(skippedFile.readText())
                                    for (i in 0 until arr.length()) {
                                        val o = arr.getJSONObject(i)
                                        skippedList.add(SkippedFileInfo(o.getString("name"), o.getString("reason")))
                                    }
                                } catch (ignored: Exception) {}
                                try { skippedFile.delete() } catch (ignored: Exception) {}
                            }

                            _sessionStats.value = SessionStats(
                                filesProcessed = written,
                                totalSize = totalBytes,
                                elapsedMs = elapsed,
                                destName = destName,
                                filesSkipped = skippedCount,
                                skippedFiles = skippedList,
                                zipUri = zipUri,
                                destTreeUri = destUri
                            )
                            addLog("Processing completed: $written files copied, $skippedCount skipped.")
                            navigateTo(AppScreen.Success)
                        }
                        WorkInfo.State.CANCELLED -> {
                            val written = workInfo.outputData.getInt(ExtractionWorker.KEY_FILES_WRITTEN, 0)
                            val total = workInfo.outputData.getInt(ExtractionWorker.KEY_TOTAL_FILES, selectedFiles.size)
                            _processStatus.value = ProcessStatus.Cancelled(
                                filesWritten = written,
                                totalFiles = total,
                                message = "Cancelled – $written of $total files were copied"
                            )
                            try { itemsFile.delete() } catch (ignored: Exception) {}
                            try { skippedFile.delete() } catch (ignored: Exception) {}
                        }
                        WorkInfo.State.FAILED -> {
                            val isCancelled = workInfo.outputData.getBoolean(ExtractionWorker.KEY_CANCELLED, false)
                            if (isCancelled) {
                                val written = workInfo.outputData.getInt(ExtractionWorker.KEY_FILES_WRITTEN, 0)
                                val total = workInfo.outputData.getInt(ExtractionWorker.KEY_TOTAL_FILES, selectedFiles.size)
                                _processStatus.value = ProcessStatus.Cancelled(
                                    filesWritten = written,
                                    totalFiles = total,
                                    message = "Cancelled – $written of $total files were copied"
                                )
                            } else {
                                val errorMsg = workInfo.outputData.getString(ExtractionWorker.KEY_ERROR_MESSAGE)
                                    ?: "Extraction failed"
                                _processStatus.value = ProcessStatus.Error(errorMsg)
                            }
                            try { itemsFile.delete() } catch (ignored: Exception) {}
                            try { skippedFile.delete() } catch (ignored: Exception) {}
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            _processStatus.value = ProcessStatus.Error("Failed to initiate background worker: ${e.localizedMessage}")
        }
    }

    // Helper to format visual sizes
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    // Testing helpers to configure exact screen states for screenshot tests
    fun setProcessStatusForTesting(status: ProcessStatus) {
        _processStatus.value = status
    }

    fun setScanSummaryForTesting(summary: ScanSummary) {
        _scanSummary.value = summary
    }

    fun setSessionStatsForTesting(stats: SessionStats) {
        _sessionStats.value = stats
    }

    fun addLogForTesting(log: String) {
        sessionLogsDeque.addLast(log)
        _sessionLogs.value = sessionLogsDeque.toList()
    }
}
