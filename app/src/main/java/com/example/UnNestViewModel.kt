package com.example

import android.content.Context
import android.net.Uri
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
    Workbench,
    Success
}

enum class FlattenMode {
    DIRECT,
    ZIP
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
    var resolvedName: String = name
)

data class SessionStats(
    val filesProcessed: Int,
    val totalSize: Long,
    val elapsedMs: Long,
    val destName: String,
    val filesSkipped: Int = 0,
    val skippedFiles: List<SkippedFileInfo> = emptyList()
)

class UnNestViewModel : ViewModel() {

    companion object {
        private const val TREE_LINES_CAP = 300
        private const val SCAN_PUBLISH_INTERVAL_MS = 150L
        private const val SESSION_LOGS_CAP = 500
    }

    private val _isDarkTheme = MutableStateFlow<Boolean?>(null) // null = follow system
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

    // Bounded scan tree output (capped at 300 lines, published at most every 150ms)
    private val treeLinesDeque = ArrayDeque<String>()
    private var lastScanPublishTime = 0L

    private fun addTreeLine(currentFile: String, line: String, forcePublish: Boolean = false) {
        synchronized(treeLinesDeque) {
            if (treeLinesDeque.size >= TREE_LINES_CAP) {
                treeLinesDeque.removeFirst()
            }
            treeLinesDeque.addLast(line)

            val now = System.currentTimeMillis()
            if (forcePublish || now - lastScanPublishTime >= SCAN_PUBLISH_INTERVAL_MS) {
                lastScanPublishTime = now
                _processStatus.value = ProcessStatus.Scanning(currentFile, treeLinesDeque.joinToString(""))
            }
        }
    }

    private fun flushTreeScan(currentFile: String = "") {
        synchronized(treeLinesDeque) {
            val fileToReport = currentFile.ifEmpty {
                (_processStatus.value as? ProcessStatus.Scanning)?.currentFile.orEmpty()
            }
            lastScanPublishTime = System.currentTimeMillis()
            _processStatus.value = ProcessStatus.Scanning(fileToReport, treeLinesDeque.joinToString(""))
        }
    }

    // Full list of all scanned files
    private val allScannedFiles = mutableListOf<ScannedFileInfo>()
    
    // Remaining conflicts to resolve
    private val pendingConflictsGroup = mutableListOf<MutableList<ScannedFileInfo>>()
    
    // Current active duplicate group in conflict screen
    private val _activeConflictGroup = MutableStateFlow<List<ScannedFileInfo>?>(null)
    val activeConflictGroup: StateFlow<List<ScannedFileInfo>?> = _activeConflictGroup.asStateFlow()

    // Session memory selection for conflict resolution (0 = None, 1 = Auto-Sequence, 2 = Parent Reference)
    private var rememberedStrategy = 0 
    private var isApplyToAllChecked = false

    private val _showOptimizationDialog = MutableStateFlow(false)
    val showOptimizationDialog: StateFlow<Boolean> = _showOptimizationDialog.asStateFlow()

    private val _detectedHeavyFilesCount = MutableStateFlow(0)
    val detectedHeavyFilesCount: StateFlow<Int> = _detectedHeavyFilesCount.asStateFlow()

    private val _isCheckingPreCompressed = MutableStateFlow(false)
    val isCheckingPreCompressed: StateFlow<Boolean> = _isCheckingPreCompressed.asStateFlow()

    var isOptimizationEnabled = false

    private var sessionStartTime = 0L

    private var scanJob: Job? = null
    private var workObserverJob: Job? = null
    private var currentWorkId: UUID? = null

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSourceDirectory(uri: Uri?) {
        _sourceDirectoryUri.value = uri
    }

    fun setDestinationDirectory(uri: Uri?) {
        _destinationDirectoryUri.value = uri
    }

    fun setFlattenMode(mode: FlattenMode) {
        _flattenMode.value = mode
    }

    fun setOptimizationDialogVisibility(visible: Boolean) {
        _showOptimizationDialog.value = visible
    }

    fun setCheckingState(checking: Boolean) {
        _isCheckingPreCompressed.value = checking
    }

    fun setHeavyFilesCount(count: Int) {
        _detectedHeavyFilesCount.value = count
    }

    suspend fun checkPreCompressedMediaFast(context: Context): Int = withContext(Dispatchers.IO) {
        val srcUri = _sourceDirectoryUri.value ?: return@withContext 0
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, srcUri)
        } catch (e: Exception) {
            null
        } ?: return@withContext 0
        countHeavyFilesRecursively(context, rootDoc, _destinationDirectoryUri.value, srcUri)
    }

    private fun countHeavyFilesRecursively(
        context: Context,
        dirDoc: DocumentFile,
        destUri: Uri?,
        srcUri: Uri?
    ): Int {
        if (!dirDoc.isDirectory) return 0
        var count = 0
        val children = try { dirDoc.listFiles() } catch (e: Exception) { emptyArray() }
        for (child in children) {
            val name = child.name.orEmpty()
            if (name.isEmpty() || isIgnoredOrSystemJunk(name)) continue
            if (child.isDirectory) {
                if (isDestinationOrInside(context, child, destUri, srcUri)) continue
                count += countHeavyFilesRecursively(context, child, destUri, srcUri)
            } else if (child.isFile) {
                if (name.startsWith("UnNest_Flattened_") && name.endsWith(".zip")) continue
                if (isHeavyExtension(name.lowercase())) {
                    count++
                }
            }
        }
        return count
    }

    private fun isIgnoredOrSystemJunk(name: String): Boolean {
        if (name.startsWith(".")) return true
        if (name.equals("Thumbs.db", ignoreCase = true)) return true
        if (name.equals("desktop.ini", ignoreCase = true)) return true
        return false
    }

    private fun isHeavyExtension(name: String): Boolean {
        return name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".mp4") ||
                name.endsWith(".pdf") ||
                name.endsWith(".png") ||
                name.endsWith(".zip") ||
                name.endsWith(".gz") ||
                name.endsWith(".mov") ||
                name.endsWith(".gif")
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
        synchronized(treeLinesDeque) {
            treeLinesDeque.clear()
            lastScanPublishTime = 0L
        }
        _maxScannedDepth.value = 1
        _duplicatesCount.value = 0
        _conflictsResolvedCount.value = 0
        allScannedFiles.clear()
        pendingConflictsGroup.clear()
        _activeConflictGroup.value = null
        rememberedStrategy = 0
        isApplyToAllChecked = false
        _showOptimizationDialog.value = false
        _detectedHeavyFilesCount.value = 0
        _isCheckingPreCompressed.value = false
        isOptimizationEnabled = false
        currentWorkId = null
        navigateTo(AppScreen.Hub)
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

    fun startExtraction(context: Context) {
        val srcUri = _sourceDirectoryUri.value ?: return
        val destUri = _destinationDirectoryUri.value ?: return
        val appContext = context.applicationContext

        navigateTo(AppScreen.Workbench)
        synchronized(sessionLogsDeque) {
            sessionLogsDeque.clear()
            _sessionLogs.value = emptyList()
        }
        synchronized(treeLinesDeque) {
            treeLinesDeque.clear()
            lastScanPublishTime = 0L
        }
        _processStatus.value = ProcessStatus.Scanning("Starting scanner...", "")
        _maxScannedDepth.value = 1
        _duplicatesCount.value = 0
        _conflictsResolvedCount.value = 0
        addLog("Initializing UnNest Flattener Sandbox...")
        addLog("Source Tree: ${srcUri.path}")
        addLog("Destination Tree: ${destUri.path}")
        allScannedFiles.clear()
        pendingConflictsGroup.clear()
        _activeConflictGroup.value = null
        rememberedStrategy = 0
        isApplyToAllChecked = false
        sessionStartTime = System.currentTimeMillis()

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                scanDirectoryRecursively(appContext, srcUri)
                flushTreeScan()

                if (allScannedFiles.isEmpty()) {
                    _processStatus.value = ProcessStatus.Error("The Selected Source folder is empty. No files to flatten.")
                    return@launch
                }

                detectAndHandleConflicts(appContext)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Handled via cancelJob
                } else {
                    _processStatus.value = ProcessStatus.Error("Scan failed: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun scanDirectoryRecursively(
        context: Context,
        sourceUri: Uri
    ) = withContext(Dispatchers.IO) {
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, sourceUri)
        } catch (e: Exception) {
            null
        } ?: return@withContext

        traverseTreeWithDoc(context, rootDoc, "", "")
    }

    private suspend fun traverseTreeWithDoc(
        context: Context,
        dirDoc: DocumentFile?,
        currentRelativePath: String,
        indentation: String
    ): Unit = withContext(Dispatchers.IO) {
        if (dirDoc == null || !dirDoc.isDirectory) return@withContext

        val destUri = _destinationDirectoryUri.value
        val srcUri = _sourceDirectoryUri.value

        val children = dirDoc.listFiles()
        val count = children.size
        for (index in 0 until count) {
            val child = children[index]
            val fileName = child.name.orEmpty()
            if (fileName.isEmpty() || isIgnoredOrSystemJunk(fileName)) {
                continue
            }

            val isLast = index == count - 1
            val branchSymbol = if (isLast) "└── " else "├── "
            val nextIndent = indentation + (if (isLast) "    " else "│   ")

            if (child.isDirectory) {
                if (isDestinationOrInside(context, child, destUri, srcUri)) {
                    addLog("Skipping destination folder from scan: $fileName")
                    continue
                }

                val nextRelative = if (currentRelativePath.isEmpty()) fileName else "$currentRelativePath/$fileName"

                val currentDepth = nextRelative.split('/').size + 1
                if (currentDepth > _maxScannedDepth.value) {
                    _maxScannedDepth.value = currentDepth
                }

                val dirLine = "$indentation$branchSymbol[$fileName]\n"
                addTreeLine(fileName, dirLine)

                traverseTreeWithDoc(context, child, nextRelative, nextIndent)
            } else if (child.isFile) {
                if (fileName.startsWith("UnNest_Flattened_") && fileName.endsWith(".zip")) {
                    addLog("Skipping previously generated archive: $fileName")
                    continue
                }

                val parentName = dirDoc.name.orEmpty().ifEmpty { "Source" }
                val fileSize = child.length()
                val childUri = child.uri

                val scannedInfo = ScannedFileInfo(
                    name = fileName,
                    parentName = parentName,
                    size = fileSize,
                    uri = childUri,
                    relativePath = currentRelativePath
                )

                allScannedFiles.add(scannedInfo)
                addLog("Scanned File: $fileName (${formatSize(fileSize)})")

                val fileLine = "$indentation$branchSymbol$fileName (${formatSize(fileSize)})\n"
                addTreeLine(fileName, fileLine)
            }
        }
    }

    private fun isDestinationOrInside(
        context: Context,
        dirDoc: DocumentFile,
        destUri: Uri?,
        srcUri: Uri?
    ): Boolean {
        if (destUri == null) return false

        if (dirDoc.uri == destUri) return true

        val destDoc = try {
            DocumentFile.fromTreeUri(context, destUri)
        } catch (e: Exception) {
            null
        }
        if (destDoc != null && dirDoc.uri == destDoc.uri) return true

        try {
            val destDocId = try {
                DocumentsContract.getTreeDocumentId(destUri)
            } catch (e: Exception) {
                try { DocumentsContract.getDocumentId(destUri) } catch (e2: Exception) { null }
            }
            val dirDocId = try {
                DocumentsContract.getDocumentId(dirDoc.uri)
            } catch (e: Exception) {
                null
            }
            val srcDocId = srcUri?.let {
                try {
                    DocumentsContract.getTreeDocumentId(it)
                } catch (e: Exception) {
                    try { DocumentsContract.getDocumentId(it) } catch (e2: Exception) { null }
                }
            }

            if (destDocId != null && dirDocId != null) {
                if (destDocId != srcDocId) {
                    if (dirDocId == destDocId || dirDocId.startsWith("$destDocId/")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return false
    }

    private fun detectAndHandleConflicts(context: Context) {
        val duplicatesGroupMap = allScannedFiles.groupBy { it.name.lowercase() }
            .filter { it.value.size > 1 }

        if (duplicatesGroupMap.isNotEmpty()) {
            val totalDupsCount = duplicatesGroupMap.values.sumOf { it.size }
            _duplicatesCount.value = totalDupsCount
            _conflictsResolvedCount.value = duplicatesGroupMap.size
            addLog("Conflicts Detected: Found ${duplicatesGroupMap.size} unique name clashes across $totalDupsCount files.")

            pendingConflictsGroup.clear()
            duplicatesGroupMap.values.forEach { groupList ->
                pendingConflictsGroup.add(groupList.toMutableList())
            }
            
            resolveNextConflict(context)
        } else {
            addLog("Zero conflicts detected. Proceeding to extraction immediately.")
            _duplicatesCount.value = 0
            _conflictsResolvedCount.value = 0
            guaranteeUniqueFileNames(context, 1)
            launchExtractionWorkManager(context)
        }
    }

    private fun resolveNextConflict(context: Context) {
        if (pendingConflictsGroup.isEmpty()) {
            guaranteeUniqueFileNames(context, if (rememberedStrategy > 0) rememberedStrategy else 1)
            launchExtractionWorkManager(context)
            return
        }

        val nextGroup = pendingConflictsGroup.first()
        
        if (isApplyToAllChecked && rememberedStrategy > 0) {
            applyConflictResolutionToGroup(nextGroup, rememberedStrategy)
            pendingConflictsGroup.removeAt(0)
            resolveNextConflict(context)
        } else {
            _activeConflictGroup.value = nextGroup
            _processStatus.value = ProcessStatus.Conflict(
                conflictedName = nextGroup.first().name,
                duplicates = nextGroup
            )
        }
    }

    fun submitConflictResolution(context: Context, strategy: Int, applyToAll: Boolean) {
        val appContext = context.applicationContext
        isApplyToAllChecked = applyToAll
        if (applyToAll) {
            rememberedStrategy = strategy
        }

        val currentGroup = _activeConflictGroup.value ?: return
        applyConflictResolutionToGroup(currentGroup, strategy)

        pendingConflictsGroup.removeAt(0)
        _activeConflictGroup.value = null
        
        resolveNextConflict(appContext)
    }

    private fun applyConflictResolutionToGroup(group: List<ScannedFileInfo>, strategy: Int) {
        // Check if multiple files in group share identical parentName
        val parentCounts = group.groupBy { it.parentName.lowercase() }

        group.forEachIndexed { index, file ->
            val extIndex = file.name.lastIndexOf('.')
            val baseName = if (extIndex != -1) file.name.substring(0, extIndex) else file.name
            val extension = if (extIndex != -1) file.name.substring(extIndex) else ""

            if (strategy == 1) {
                // Auto-sequence numbering (e.g. photo.jpg, photo_1.jpg)
                val suffix = if (index > 0) "_$index" else ""
                file.resolvedName = "$baseName$suffix$extension"
            } else {
                // Parent Prefix: if files share parent folder name, include grandparent
                val hasDuplicateParents = (parentCounts[file.parentName.lowercase()]?.size ?: 0) > 1
                if (hasDuplicateParents) {
                    val segments = file.relativePath.split('/').filter { it.isNotEmpty() }
                    if (segments.size >= 2) {
                        val grandparent = segments[segments.size - 2]
                        val parent = segments[segments.size - 1]
                        file.resolvedName = "${baseName}_(${grandparent}-${parent})$extension"
                    } else {
                        file.resolvedName = "${baseName}_(${file.parentName})$extension"
                    }
                } else {
                    file.resolvedName = "${baseName}_(${file.parentName})$extension"
                }
            }
            addLog("Resolved name clash: ${file.relativePath}/${file.name} -> ${file.resolvedName}")
        }
    }

    private fun guaranteeUniqueFileNames(context: Context, strategy: Int) {
        val usedNamesLower = mutableSetOf<String>()

        // In Plain Copy mode, read names already present in destination folder
        if (_flattenMode.value == FlattenMode.DIRECT) {
            val destUri = _destinationDirectoryUri.value
            if (destUri != null) {
                try {
                    val destDoc = DocumentFile.fromTreeUri(context, destUri)
                    if (destDoc != null && destDoc.isDirectory) {
                        for (child in destDoc.listFiles()) {
                            if (child.isFile) {
                                child.name?.let { usedNamesLower.add(it.lowercase()) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    addLog("Warning: Could not pre-read destination files: ${e.localizedMessage}")
                }
            }
        }

        for (file in allScannedFiles) {
            var candidate = file.resolvedName
            val extIndex = candidate.lastIndexOf('.')
            val extension = if (extIndex != -1) candidate.substring(extIndex) else ""
            val rawExtIndex = file.name.lastIndexOf('.')
            val rawBase = if (rawExtIndex != -1) file.name.substring(0, rawExtIndex) else file.name

            // Check if name is already taken (case-insensitive)
            if (usedNamesLower.contains(candidate.lowercase())) {
                // If strategy was Parent Prefix, try grandparent folder fallback if not already used
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

                // If still taken, append _2, _3 ... until unique
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
            file.resolvedName = candidate
        }
    }

    private fun launchExtractionWorkManager(context: Context) {
        val destUri = _destinationDirectoryUri.value ?: run {
            _processStatus.value = ProcessStatus.Error("Destination URI not set")
            return
        }

        _processStatus.value = ProcessStatus.ProcessingFiles(0, allScannedFiles.size, "")
        addLog("Launching background extraction task...")

        try {
            val jobId = UUID.randomUUID().toString()
            val itemsFile = File(context.cacheDir, "unnest_job_${jobId}.json")
            val skippedFile = File(context.cacheDir, "unnest_skipped_${jobId}.json")

            val jsonArray = JSONArray()
            for (f in allScannedFiles) {
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
                ExtractionWorker.KEY_ITEMS_JSON_PATH to itemsFile.absolutePath,
                ExtractionWorker.KEY_SKIPPED_JSON_PATH to skippedFile.absolutePath
            )

            val workRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .build()

            currentWorkId = workRequest.id
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(workRequest)

            workObserverJob?.cancel()
            workObserverJob = viewModelScope.launch {
                workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                    if (workInfo == null) return@collect
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val written = workInfo.progress.getInt(ExtractionWorker.KEY_FILES_WRITTEN, 0)
                            val total = workInfo.progress.getInt(ExtractionWorker.KEY_TOTAL_FILES, allScannedFiles.size)
                            val current = workInfo.progress.getString(ExtractionWorker.KEY_CURRENT_FILE).orEmpty()
                            _processStatus.value = ProcessStatus.ProcessingFiles(written, total, current)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val written = workInfo.outputData.getInt(ExtractionWorker.KEY_FILES_WRITTEN, allScannedFiles.size)
                            val totalBytes = workInfo.outputData.getLong(ExtractionWorker.KEY_TOTAL_SIZE, 0L)
                            val elapsed = workInfo.outputData.getLong(ExtractionWorker.KEY_ELAPSED_MS, 0L)
                            val destName = workInfo.outputData.getString(ExtractionWorker.KEY_DEST_NAME) ?: "Destination"
                            val skippedCount = workInfo.outputData.getInt(ExtractionWorker.KEY_SKIPPED_COUNT, 0)

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
                                skippedFiles = skippedList
                            )
                            addLog("Processing completed: $written files copied, $skippedCount skipped.")
                            navigateTo(AppScreen.Success)
                        }
                        WorkInfo.State.CANCELLED -> {
                            val written = workInfo.outputData.getInt(ExtractionWorker.KEY_FILES_WRITTEN, 0)
                            val total = workInfo.outputData.getInt(ExtractionWorker.KEY_TOTAL_FILES, allScannedFiles.size)
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
                                val total = workInfo.outputData.getInt(ExtractionWorker.KEY_TOTAL_FILES, allScannedFiles.size)
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
        return String.format("%.1f MB", mb)
    }
}
