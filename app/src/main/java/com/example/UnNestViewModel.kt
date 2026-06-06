package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

sealed class ProcessStatus {
    object Idle : ProcessStatus()
    data class Scanning(val currentFile: String, val treeOutput: String) : ProcessStatus()
    data class Conflict(val conflictedName: String, val duplicates: List<ScannedFileInfo>) : ProcessStatus()
    data class ProcessingFiles(val filesWritten: Int, val totalFiles: Int) : ProcessStatus()
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
    val destName: String
)

class UnNestViewModel : ViewModel() {

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

    // Mockup-aligned extended metrics
    private val _sessionLogs = MutableStateFlow<List<String>>(emptyList())
    val sessionLogs: StateFlow<List<String>> = _sessionLogs.asStateFlow()

    private val _maxScannedDepth = MutableStateFlow(1)
    val maxScannedDepth: StateFlow<Int> = _maxScannedDepth.asStateFlow()

    private val _duplicatesCount = MutableStateFlow(0)
    val duplicatesCount: StateFlow<Int> = _duplicatesCount.asStateFlow()

    private val _conflictsResolvedCount = MutableStateFlow(0)
    val conflictsResolvedCount: StateFlow<Int> = _conflictsResolvedCount.asStateFlow()

    fun addLog(message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = sdf.format(java.util.Date())
        _sessionLogs.value = _sessionLogs.value + "[$timeStr] $message"
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
        countHeavyFilesRecursively(rootDoc)
    }

    private fun countHeavyFilesRecursively(dirDoc: DocumentFile): Int {
        if (!dirDoc.isDirectory) return 0
        var count = 0
        val children = try { dirDoc.listFiles() } catch (e: Exception) { emptyArray() }
        for (child in children) {
            if (child.isDirectory) {
                count += countHeavyFilesRecursively(child)
            } else if (child.isFile) {
                val name = child.name.orEmpty().lowercase()
                if (isHeavyExtension(name)) {
                    count++
                }
            }
        }
        return count
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
        _sourceDirectoryUri.value = null
        _destinationDirectoryUri.value = null
        _flattenMode.value = FlattenMode.DIRECT
        _processStatus.value = ProcessStatus.Idle
        _sessionStats.value = null
        _sessionLogs.value = emptyList()
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
        navigateTo(AppScreen.Hub)
    }

    fun startExtraction(context: Context) {
        val srcUri = _sourceDirectoryUri.value ?: return
        val destUri = _destinationDirectoryUri.value ?: return

        navigateTo(AppScreen.Workbench)
        _processStatus.value = ProcessStatus.Scanning("Starting scanner...", "")
        _sessionLogs.value = emptyList()
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

        viewModelScope.launch {
            try {
                // Step 1: Scan files recursively
                val resultTreeStringBuilder = StringBuilder("└── source_root\n")
                scanDirectoryRecursively(context, srcUri, "", resultTreeStringBuilder)

                delay(1000) // Aesthetic delay for tree visual scannability

                if (allScannedFiles.isEmpty()) {
                    _processStatus.value = ProcessStatus.Error("The Selected Source folder is empty. No files to flatten.")
                    return@launch
                }

                // Step 2: Identify Duplicate Conflicts
                detectAndHandleConflicts()

            } catch (e: Exception) {
                _processStatus.value = ProcessStatus.Error("Scan failed: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun scanDirectoryRecursively(
        context: Context,
        directoryUri: Uri,
        indentation: String,
        treeBuilder: StringBuilder
    ) = withContext(Dispatchers.IO) {
        val rootDoc = try {
            if (directoryUri == _sourceDirectoryUri.value) {
                DocumentFile.fromTreeUri(context, directoryUri)
            } else {
                // If it is a nested subdirectory, tree URI might need child document parsing
                DocumentFile.fromSingleUri(context, directoryUri) 
            }
        } catch (e: Exception) {
            null
        } ?: return@withContext

        // Double fallback if fromSingleUri doesn't resolve nested children correctly,
        // we can construct hierarchical tree traversals using SAF helper.
        // In Android SAF, tree traversal can be executed from the root DocumentFile
        traverseTreeWithDoc(context, DocumentFile.fromTreeUri(context, _sourceDirectoryUri.value!!), "", indentation)
    }

    private suspend fun traverseTreeWithDoc(
        context: Context,
        dirDoc: DocumentFile?,
        currentRelativePath: String,
        indentation: String
    ): Unit = withContext(Dispatchers.IO) {
        if (dirDoc == null || !dirDoc.isDirectory) return@withContext

        val children = dirDoc.listFiles()
        val count = children.size
        for (index in 0 until count) {
            val child = children[index]
            val isLast = index == count - 1
            val branchSymbol = if (isLast) "└── " else "├── "
            val nextIndent = indentation + (if (isLast) "    " else "│   ")

            if (child.isDirectory) {
                val nextRelative = if (currentRelativePath.isEmpty()) child.name.orEmpty() else "$currentRelativePath/${child.name.orEmpty()}"
                
                // Track dynamic layout depth
                val currentDepth = nextRelative.split('/').size + 1
                if (currentDepth > _maxScannedDepth.value) {
                    _maxScannedDepth.value = currentDepth
                }

                // Track directory log
                val dirLine = "$indentation$branchSymbol[${child.name.orEmpty()}]\n"
                
                // Update terminal log live
                val currentScanState = _processStatus.value as? ProcessStatus.Scanning
                val prevLogs = currentScanState?.treeOutput.orEmpty()
                _processStatus.value = ProcessStatus.Scanning(child.name.orEmpty(), prevLogs + dirLine)
                
                delay(80) // Visual pacing animation
                
                traverseTreeWithDoc(context, child, nextRelative, nextIndent)
            } else if (child.isFile) {
                val fileName = child.name.orEmpty()
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

                // Track file log
                val fileLine = "$indentation$branchSymbol$fileName (${formatSize(fileSize)})\n"
                val currentScanState = _processStatus.value as? ProcessStatus.Scanning
                val prevLogs = currentScanState?.treeOutput.orEmpty()
                _processStatus.value = ProcessStatus.Scanning(fileName, prevLogs + fileLine)
                
                delay(40) // Responsive layout flow delay
            }
        }
    }

    private fun detectAndHandleConflicts() {
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
            
            // Resolve conflicts state
            resolveNextConflict()
        } else {
            addLog("Zero conflicts detected. Proceeding to extraction immediately.")
            _duplicatesCount.value = 0
            _conflictsResolvedCount.value = 0
            // Proceed to direct extraction
            performExtractionToDestination()
        }
    }

    private fun resolveNextConflict() {
        if (pendingConflictsGroup.isEmpty()) {
            // All conflicts fully resolved! Proceed to flattening extraction
            performExtractionToDestination()
            return
        }

        val nextGroup = pendingConflictsGroup.first()
        
        // If remembered strategy is active and user checked "apply to all", enforce here
        if (isApplyToAllChecked && rememberedStrategy > 0) {
            applyConflictResolutionToGroup(nextGroup, rememberedStrategy)
            pendingConflictsGroup.removeAt(0)
            resolveNextConflict()
        } else {
            _activeConflictGroup.value = nextGroup
            _processStatus.value = ProcessStatus.Conflict(
                conflictedName = nextGroup.first().name,
                duplicates = nextGroup
            )
        }
    }

    fun submitConflictResolution(strategy: Int, applyToAll: Boolean) {
        // Strategy: 1 = Auto-sequence numbering (e.g., photo_1.jpg), 2 = Parent reference (e.g., photo_(Trip).jpg)
        isApplyToAllChecked = applyToAll
        if (applyToAll) {
            rememberedStrategy = strategy
        }

        val currentGroup = _activeConflictGroup.value ?: return
        applyConflictResolutionToGroup(currentGroup, strategy)

        pendingConflictsGroup.removeAt(0)
        _activeConflictGroup.value = null
        
        resolveNextConflict()
    }

    private fun applyConflictResolutionToGroup(group: List<ScannedFileInfo>, strategy: Int) {
        group.forEachIndexed { index, file ->
            val extIndex = file.name.lastIndexOf('.')
            val baseName = if (extIndex != -1) file.name.substring(0, extIndex) else file.name
            val extension = if (extIndex != -1) file.name.substring(extIndex) else ""

            if (strategy == 1) {
                // Auto-sequence numbering
                val suffix = if (index > 0) "_$index" else ""
                file.resolvedName = "$baseName$suffix$extension"
            } else {
                // Parent location referencing
                file.resolvedName = "${baseName}_(${file.parentName})$extension"
            }
            addLog("Resolved name clash: ${file.relativePath}/${file.name} -> Renamed to: ${file.resolvedName}")
        }
    }

    private fun performExtractionToDestination() {
        val destUri = _destinationDirectoryUri.value ?: return
        _processStatus.value = ProcessStatus.ProcessingFiles(0, allScannedFiles.size)

        viewModelScope.launch {
            try {
                addLog("Starting file extraction process...")
                val stats = withContext(Dispatchers.IO) {
                    val context = AppContextHolder.context ?: throw Exception("Context not in memory")
                    val destDirDoc = DocumentFile.fromTreeUri(context, destUri) 
                        ?: throw Exception("Could not open Destination folder")

                    val totalBytes = allScannedFiles.sumOf { it.size }
                    val totalFiles = allScannedFiles.size

                    when (_flattenMode.value) {
                        FlattenMode.DIRECT -> {
                            addLog("Flatten Mode: DIRECT extraction chosen. Creating local copy stream...")
                            allScannedFiles.forEachIndexed { idx, fileInfo ->
                                withContext(Dispatchers.Main) {
                                    _processStatus.value = ProcessStatus.ProcessingFiles(idx + 1, totalFiles)
                                }

                                addLog("Copying: ${fileInfo.name}")
                                if (fileInfo.resolvedName != fileInfo.name) {
                                    addLog("- Renamed due to clash: original: [${fileInfo.name}] -> resolution: [${fileInfo.resolvedName}]")
                                }

                                // Create direct document
                                val mimeType = context.contentResolver.getType(fileInfo.uri) ?: "application/octet-stream"
                                val createdFile = destDirDoc.createFile(mimeType, fileInfo.resolvedName)
                                    ?: throw Exception("Failed to write direct document: ${fileInfo.resolvedName}")

                                // Stream copy
                                copyStream(context, fileInfo.uri, createdFile.uri)
                            }
                            addLog("Successfully extracted $totalFiles files to destination.")
                            
                            val endTime = System.currentTimeMillis()
                            SessionStats(
                                filesProcessed = totalFiles,
                                totalSize = totalBytes,
                                elapsedMs = endTime - sessionStartTime,
                                destName = destDirDoc.name.orEmpty().ifEmpty { "destination_root" }
                            )
                        }
                        
                        FlattenMode.ZIP -> {
                            val zipFilename = "UnNest_Flattened_${System.currentTimeMillis() / 1000}.zip"
                            addLog("Flatten Mode: ZIP Archive compilation. Writing to: $zipFilename")
                            val createdZipFile = destDirDoc.createFile("application/zip", zipFilename)
                                ?: throw Exception("Failed to create flattened ZIP output document inside Destination directory.")
                            
                            context.contentResolver.openOutputStream(createdZipFile.uri)?.use { out ->
                                ZipOutputStream(BufferedOutputStream(out)).use { zipOut ->
                                    allScannedFiles.forEachIndexed { idx, fileInfo ->
                                        withContext(Dispatchers.Main) {
                                            _processStatus.value = ProcessStatus.ProcessingFiles(idx + 1, totalFiles)
                                        }

                                        val nameLower = fileInfo.resolvedName.lowercase()
                                        val isHeavy = isHeavyExtension(nameLower)

                                        val entry = ZipEntry(fileInfo.resolvedName)
                                        if (isOptimizationEnabled && isHeavy) {
                                            addLog("Bypassing compression for pre-compressed item (STORED): ${fileInfo.name}")
                                            if (fileInfo.resolvedName != fileInfo.name) {
                                                addLog("- Applying rename prefix: ${fileInfo.resolvedName}")
                                            }

                                            val fileBytes = context.contentResolver.openInputStream(fileInfo.uri)?.use { input ->
                                                input.readBytes()
                                            } ?: byteArrayOf()

                                            val crc = java.util.zip.CRC32()
                                            crc.update(fileBytes)

                                            entry.method = ZipEntry.STORED
                                            entry.size = fileBytes.size.toLong()
                                            entry.compressedSize = fileBytes.size.toLong()
                                            entry.crc = crc.value

                                            zipOut.putNextEntry(entry)
                                            zipOut.write(fileBytes)
                                        } else {
                                            addLog("Compressing entry: ${fileInfo.name}")
                                            if (fileInfo.resolvedName != fileInfo.name) {
                                                addLog("- Applying rename prefix: ${fileInfo.resolvedName}")
                                            }
                                            zipOut.putNextEntry(entry)
                                            context.contentResolver.openInputStream(fileInfo.uri)?.use { input ->
                                                input.copyTo(zipOut)
                                            }
                                        }
                                        zipOut.closeEntry()
                                    }
                                }
                            }
                            addLog("Successfully compiled all nested elements into ZIP container.")
                            
                            val endTime = System.currentTimeMillis()
                            SessionStats(
                                filesProcessed = totalFiles,
                                totalSize = totalBytes,
                                elapsedMs = endTime - sessionStartTime,
                                destName = zipFilename
                            )
                        }
                    }
                }

                _sessionStats.value = stats
                addLog("Processing completed successfully. Operation summary compiled.")
                // Safe progress simulation end delay
                delay(600)
                navigateTo(AppScreen.Success)

            } catch (e: Exception) {
                _processStatus.value = ProcessStatus.Error("Extraction failed: ${e.localizedMessage}")
            }
        }
    }

    private fun copyStream(context: Context, sourceUri: Uri, destinationUri: Uri) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                input.copyTo(output)
            }
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

// Global reference holder so background dispatchers can securely access Application Context without leaking Activity
object AppContextHolder {
    var context: Context? = null
}
