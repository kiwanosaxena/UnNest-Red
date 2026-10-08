package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DEST_URI = "dest_uri"
        const val KEY_FLATTEN_MODE = "flatten_mode"
        const val KEY_OPTIMIZATION = "optimization"
        const val KEY_SKIP_IDENTICAL_DUPLICATES = "skip_identical_duplicates"
        const val KEY_ITEMS_JSON_PATH = "items_json_path"
        const val KEY_SKIPPED_JSON_PATH = "skipped_json_path"

        const val KEY_FILES_WRITTEN = "files_written"
        const val KEY_TOTAL_FILES = "total_files"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_TOTAL_SIZE = "total_size"
        const val KEY_ELAPSED_MS = "elapsed_ms"
        const val KEY_DEST_NAME = "dest_name"
        const val KEY_SKIPPED_COUNT = "skipped_count"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_ZIP_URI = "zip_uri"
        const val KEY_REPORT_URI = "report_uri"

        const val NOTIFICATION_ID = 2026
        const val CHANNEL_ID = "unnest_extraction_channel"
        private const val STREAM_BUFFER_SIZE = 64 * 1024

        fun escapeCsv(value: String): String {
            return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }
    }

    private data class ReportRecord(
        val originalRelativePath: String,
        val newName: String,
        val sizeBytes: Long,
        val status: String // "copied", "skipped", "duplicate-skipped"
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0, "UnNest: Initializing...")
    }

    private fun createForegroundInfo(written: Int, total: Int, contentText: String): ForegroundInfo {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UnNest File Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for ongoing file flattening and archiving"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("UnNest")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(total, written, total == 0)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
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

    private fun computeSha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        context.contentResolver.openInputStream(uri)?.use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        } ?: throw Exception("Cannot open stream to compute hash")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val destUriStr = inputData.getString(KEY_DEST_URI) ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "Destination folder not specified")
        )
        val modeStr = inputData.getString(KEY_FLATTEN_MODE) ?: "DIRECT"
        val isOptimizationEnabled = inputData.getBoolean(KEY_OPTIMIZATION, false)
        val skipIdenticalDuplicates = inputData.getBoolean(KEY_SKIP_IDENTICAL_DUPLICATES, false)
        val itemsJsonPath = inputData.getString(KEY_ITEMS_JSON_PATH) ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "File list configuration missing")
        )
        val skippedJsonPath = inputData.getString(KEY_SKIPPED_JSON_PATH) ?: ""

        val destUri = Uri.parse(destUriStr)
        val destDirDoc = try {
            DocumentFile.fromTreeUri(context, destUri)
        } catch (e: Exception) {
            null
        } ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "Could not open Destination folder")
        )

        val itemsFile = File(itemsJsonPath)
        if (!itemsFile.exists()) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Job item file not found in cache")
            )
        }

        val itemsJson = try {
            JSONArray(itemsFile.readText())
        } catch (e: Exception) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Failed to parse items payload: ${e.localizedMessage}")
            )
        }

        val totalFiles = itemsJson.length()
        val isZip = modeStr == "ZIP"
        val actionVerb = if (isZip) "Compressing" else "Copying"

        try {
            setForeground(createForegroundInfo(0, totalFiles, "UnNest: $actionVerb 0 of $totalFiles files"))
        } catch (e: Exception) {
            // Notification or foreground may fail if denied; work continues regardless
        }

        val reportRecords = mutableListOf<ReportRecord>()
        val skippedList = mutableListOf<JSONObject>()
        var writtenCount = 0
        var totalBytesProcessed = 0L
        var createdZipDoc: DocumentFile? = null

        // Hash cache for identical duplicates check: size -> list of seen SHA-256 hashes
        val seenHashesBySize = mutableMapOf<Long, MutableSet<String>>()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTime))
        val reportFileName = "UnNest_report_${timestamp}.csv"

        try {
            if (isZip) {
                val zipFilename = "UnNest_Flattened_${System.currentTimeMillis() / 1000}.zip"
                val zipDoc = destDirDoc.createFile("application/zip", zipFilename)
                    ?: return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "Failed to create destination ZIP file")
                    )
                createdZipDoc = zipDoc

                val outputStream = context.contentResolver.openOutputStream(zipDoc.uri)
                    ?: return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "Cannot open output stream for ZIP file")
                    )

                ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE)).use { zipOut ->
                    for (i in 0 until totalFiles) {
                        if (isStopped) {
                            try { zipOut.close() } catch (ignored: Exception) {}
                            createdZipDoc?.delete()
                            return@withContext Result.failure(
                                workDataOf(
                                    KEY_CANCELLED to true,
                                    KEY_FILES_WRITTEN to writtenCount,
                                    KEY_TOTAL_FILES to totalFiles
                                )
                            )
                        }

                        val item = itemsJson.getJSONObject(i)
                        val fileName = item.getString("name")
                        val resolvedName = item.getString("resolvedName")
                        val fileUri = Uri.parse(item.getString("uriString"))
                        val size = item.getLong("size")
                        val relativePath = item.optString("relativePath", "")
                        val fullOrigPath = if (relativePath.isNotEmpty()) "$relativePath/$fileName" else fileName

                        // SHA-256 duplicate checking if enabled
                        if (skipIdenticalDuplicates) {
                            try {
                                val sameSizeHashes = seenHashesBySize[size]
                                if (sameSizeHashes != null) {
                                    val hash = computeSha256(fileUri)
                                    if (sameSizeHashes.contains(hash)) {
                                        reportRecords.add(
                                            ReportRecord(
                                                originalRelativePath = fullOrigPath,
                                                newName = resolvedName,
                                                sizeBytes = size,
                                                status = "duplicate-skipped"
                                            )
                                        )
                                        skippedList.add(JSONObject().apply {
                                            put("name", fileName)
                                            put("reason", "Identical duplicate of existing file (SHA-256 match)")
                                        })
                                        setProgress(
                                            workDataOf(
                                                KEY_FILES_WRITTEN to writtenCount,
                                                KEY_TOTAL_FILES to totalFiles,
                                                KEY_CURRENT_FILE to resolvedName,
                                                KEY_SKIPPED_COUNT to skippedList.size
                                            )
                                        )
                                        continue
                                    } else {
                                        sameSizeHashes.add(hash)
                                    }
                                } else {
                                    val hash = computeSha256(fileUri)
                                    seenHashesBySize[size] = mutableSetOf(hash)
                                }
                            } catch (e: Exception) {
                                // If hash computation fails, proceed with normal extraction
                            }
                        }

                        try {
                            val isHeavy = isHeavyExtension(resolvedName.lowercase())
                            val entry = ZipEntry(resolvedName)

                            if (isOptimizationEnabled && isHeavy) {
                                val crc = CRC32()
                                var totalStoredBytes = 0L
                                val buffer = ByteArray(STREAM_BUFFER_SIZE)

                                context.contentResolver.openInputStream(fileUri)?.use { input ->
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        crc.update(buffer, 0, bytesRead)
                                        totalStoredBytes += bytesRead
                                    }
                                } ?: throw Exception("Failed to open input stream for $fileName")

                                entry.method = ZipEntry.STORED
                                entry.size = totalStoredBytes
                                entry.compressedSize = totalStoredBytes
                                entry.crc = crc.value

                                zipOut.putNextEntry(entry)

                                context.contentResolver.openInputStream(fileUri)?.use { input ->
                                    input.copyTo(zipOut, STREAM_BUFFER_SIZE)
                                } ?: throw Exception("Failed to reopen input stream for $fileName")
                            } else {
                                zipOut.putNextEntry(entry)
                                context.contentResolver.openInputStream(fileUri)?.use { input ->
                                    input.copyTo(zipOut, STREAM_BUFFER_SIZE)
                                } ?: throw Exception("Failed to open input stream for $fileName")
                            }
                            zipOut.closeEntry()
                            writtenCount++
                            totalBytesProcessed += size

                            reportRecords.add(
                                ReportRecord(
                                    originalRelativePath = fullOrigPath,
                                    newName = resolvedName,
                                    sizeBytes = size,
                                    status = "copied"
                                )
                            )
                        } catch (e: Exception) {
                            reportRecords.add(
                                ReportRecord(
                                    originalRelativePath = fullOrigPath,
                                    newName = resolvedName,
                                    sizeBytes = size,
                                    status = "skipped"
                                )
                            )
                            skippedList.add(JSONObject().apply {
                                put("name", fileName)
                                put("reason", e.localizedMessage ?: "Unknown stream error")
                            })
                        }

                        setProgress(
                            workDataOf(
                                KEY_FILES_WRITTEN to writtenCount,
                                KEY_TOTAL_FILES to totalFiles,
                                KEY_CURRENT_FILE to resolvedName,
                                KEY_SKIPPED_COUNT to skippedList.size
                            )
                        )
                        try {
                            setForeground(
                                createForegroundInfo(
                                    writtenCount,
                                    totalFiles,
                                    "UnNest: $actionVerb $writtenCount of $totalFiles files"
                                )
                            )
                        } catch (ignored: Exception) {}
                    }

                    // Build CSV Report content
                    val csvBuilder = StringBuilder()
                    csvBuilder.append("original_relative_path,new_name,size_bytes,status\n")
                    for (rec in reportRecords) {
                        csvBuilder.append(escapeCsv(rec.originalRelativePath)).append(",")
                        csvBuilder.append(escapeCsv(rec.newName)).append(",")
                        csvBuilder.append(rec.sizeBytes).append(",")
                        csvBuilder.append(escapeCsv(rec.status)).append("\n")
                    }
                    val csvBytes = csvBuilder.toString().toByteArray(Charsets.UTF_8)

                    // Write CSV inside the ZIP
                    val reportZipEntry = ZipEntry(reportFileName)
                    zipOut.putNextEntry(reportZipEntry)
                    zipOut.write(csvBytes)
                    zipOut.closeEntry()
                }

                // Also write CSV report to destination folder
                try {
                    val reportDoc = destDirDoc.createFile("text/csv", reportFileName)
                    if (reportDoc != null) {
                        val csvBuilder = StringBuilder()
                        csvBuilder.append("original_relative_path,new_name,size_bytes,status\n")
                        for (rec in reportRecords) {
                            csvBuilder.append(escapeCsv(rec.originalRelativePath)).append(",")
                            csvBuilder.append(escapeCsv(rec.newName)).append(",")
                            csvBuilder.append(rec.sizeBytes).append(",")
                            csvBuilder.append(escapeCsv(rec.status)).append("\n")
                        }
                        context.contentResolver.openOutputStream(reportDoc.uri)?.use { out ->
                            out.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (ignored: Exception) {}

            } else {
                // DIRECT Mode
                for (i in 0 until totalFiles) {
                    if (isStopped) {
                        return@withContext Result.failure(
                            workDataOf(
                                KEY_CANCELLED to true,
                                KEY_FILES_WRITTEN to writtenCount,
                                KEY_TOTAL_FILES to totalFiles
                            )
                        )
                    }

                    val item = itemsJson.getJSONObject(i)
                    val fileName = item.getString("name")
                    val resolvedName = item.getString("resolvedName")
                    val fileUri = Uri.parse(item.getString("uriString"))
                    val size = item.getLong("size")
                    val relativePath = item.optString("relativePath", "")
                    val fullOrigPath = if (relativePath.isNotEmpty()) "$relativePath/$fileName" else fileName

                    // SHA-256 duplicate checking if enabled
                    if (skipIdenticalDuplicates) {
                        try {
                            val sameSizeHashes = seenHashesBySize[size]
                            if (sameSizeHashes != null) {
                                val hash = computeSha256(fileUri)
                                if (sameSizeHashes.contains(hash)) {
                                    reportRecords.add(
                                        ReportRecord(
                                            originalRelativePath = fullOrigPath,
                                            newName = resolvedName,
                                            sizeBytes = size,
                                            status = "duplicate-skipped"
                                        )
                                    )
                                    skippedList.add(JSONObject().apply {
                                        put("name", fileName)
                                        put("reason", "Identical duplicate of existing file (SHA-256 match)")
                                    })
                                    setProgress(
                                        workDataOf(
                                            KEY_FILES_WRITTEN to writtenCount,
                                            KEY_TOTAL_FILES to totalFiles,
                                            KEY_CURRENT_FILE to resolvedName,
                                            KEY_SKIPPED_COUNT to skippedList.size
                                        )
                                    )
                                    continue
                                } else {
                                    sameSizeHashes.add(hash)
                                }
                            } else {
                                val hash = computeSha256(fileUri)
                                seenHashesBySize[size] = mutableSetOf(hash)
                            }
                        } catch (e: Exception) {
                            // If hash computation fails, proceed with normal copy
                        }
                    }

                    try {
                        val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                        val createdFile = destDirDoc.createFile(mimeType, resolvedName)
                        if (createdFile == null) {
                            val reason = "DocumentFile.createFile returned null for $resolvedName"
                            reportRecords.add(
                                ReportRecord(
                                    originalRelativePath = fullOrigPath,
                                    newName = resolvedName,
                                    sizeBytes = size,
                                    status = "skipped"
                                )
                            )
                            skippedList.add(JSONObject().apply {
                                put("name", fileName)
                                put("reason", reason)
                            })
                            continue
                        }

                        context.contentResolver.openInputStream(fileUri)?.use { input ->
                            context.contentResolver.openOutputStream(createdFile.uri)?.use { output ->
                                input.copyTo(output, STREAM_BUFFER_SIZE)
                            } ?: throw Exception("Failed to open destination output stream")
                        } ?: throw Exception("Failed to open source input stream")

                        writtenCount++
                        totalBytesProcessed += size

                        reportRecords.add(
                            ReportRecord(
                                originalRelativePath = fullOrigPath,
                                newName = resolvedName,
                                sizeBytes = size,
                                status = "copied"
                            )
                        )
                    } catch (e: Exception) {
                        reportRecords.add(
                            ReportRecord(
                                originalRelativePath = fullOrigPath,
                                newName = resolvedName,
                                sizeBytes = size,
                                status = "skipped"
                            )
                        )
                        skippedList.add(JSONObject().apply {
                            put("name", fileName)
                            put("reason", e.localizedMessage ?: "Unknown file copy error")
                        })
                    }

                    setProgress(
                        workDataOf(
                            KEY_FILES_WRITTEN to writtenCount,
                            KEY_TOTAL_FILES to totalFiles,
                            KEY_CURRENT_FILE to resolvedName,
                            KEY_SKIPPED_COUNT to skippedList.size
                        )
                    )
                    try {
                        setForeground(
                            createForegroundInfo(
                                writtenCount,
                                totalFiles,
                                "UnNest: $actionVerb $writtenCount of $totalFiles files"
                            )
                        )
                    } catch (ignored: Exception) {}
                }

                // Write CSV report to destination folder
                try {
                    val reportDoc = destDirDoc.createFile("text/csv", reportFileName)
                    if (reportDoc != null) {
                        val csvBuilder = StringBuilder()
                        csvBuilder.append("original_relative_path,new_name,size_bytes,status\n")
                        for (rec in reportRecords) {
                            csvBuilder.append(escapeCsv(rec.originalRelativePath)).append(",")
                            csvBuilder.append(escapeCsv(rec.newName)).append(",")
                            csvBuilder.append(rec.sizeBytes).append(",")
                            csvBuilder.append(escapeCsv(rec.status)).append("\n")
                        }
                        context.contentResolver.openOutputStream(reportDoc.uri)?.use { out ->
                            out.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            if (isZip && isStopped) {
                createdZipDoc?.delete()
                return@withContext Result.failure(
                    workDataOf(
                        KEY_CANCELLED to true,
                        KEY_FILES_WRITTEN to writtenCount,
                        KEY_TOTAL_FILES to totalFiles
                    )
                )
            }
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to (e.localizedMessage ?: "Extraction job failed"))
            )
        }

        // Save skipped files list to cacheDir JSON
        if (skippedJsonPath.isNotEmpty()) {
            try {
                val skippedArray = JSONArray(skippedList)
                File(skippedJsonPath).writeText(skippedArray.toString())
            } catch (ignored: Exception) {}
        }

        // Clean up input items file
        try { itemsFile.delete() } catch (ignored: Exception) {}

        val elapsed = System.currentTimeMillis() - startTime
        val destName = if (isZip) (createdZipDoc?.name ?: "archive.zip") else (destDirDoc.name ?: "destination")
        val zipUriStr = createdZipDoc?.uri?.toString() ?: ""

        return@withContext Result.success(
            workDataOf(
                KEY_FILES_WRITTEN to writtenCount,
                KEY_TOTAL_FILES to totalFiles,
                KEY_TOTAL_SIZE to totalBytesProcessed,
                KEY_ELAPSED_MS to elapsed,
                KEY_DEST_NAME to destName,
                KEY_SKIPPED_COUNT to skippedList.size,
                KEY_ZIP_URI to zipUriStr
            )
        )
    }
}
