package com.example

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun UnNestPreviewScreen(viewModel: UnNestViewModel) {
    val context = LocalContext.current
    BackHandler {
        viewModel.navigateTo(AppScreen.Hub)
    }

    val colors = UnNestTheme.colors
    val inkCharcoalColor = colors.textPrimary
    val inkSubtleColor = colors.textSecondary

    val summary by viewModel.scanSummary.collectAsStateWithLifecycle()
    val selectedCats by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val selectedFolds by viewModel.selectedFolders.collectAsStateWithLifecycle()
    val conflictStrategy by viewModel.conflictStrategy.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val destUri by viewModel.destinationDirectoryUri.collectAsStateWithLifecycle()
    val destAvailBytes by viewModel.destinationAvailableBytes.collectAsStateWithLifecycle()
    val flattenMode by viewModel.flattenMode.collectAsStateWithLifecycle()
    val duplicatesCount by viewModel.duplicatesCount.collectAsStateWithLifecycle()

    val isDestInsideSrc = viewModel.isDestinationInsideSource()

    val destinationChooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val oldUri = destUri
                if (oldUri != null && oldUri != uri) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            oldUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (ignored: Exception) {}
                }
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_folder_access_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                viewModel.setDestinationDirectory(uri, context)
            }
        }
    )

    val skipIdenticalDuplicates by viewModel.skipIdenticalDuplicates.collectAsStateWithLifecycle()
    val filterMinSize by viewModel.filterMinSizeBytes.collectAsStateWithLifecycle()
    val filterMaxSize by viewModel.filterMaxSizeBytes.collectAsStateWithLifecycle()
    val filterMinDate by viewModel.filterMinDateMillis.collectAsStateWithLifecycle()
    val filterMaxDate by viewModel.filterMaxDateMillis.collectAsStateWithLifecycle()

    var minSizeInput by remember { mutableStateOf("") }
    var maxSizeInput by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val (selectedCount, selectedBytes) = viewModel.getSelectedFilesStats()
    val hasSpace = viewModel.hasSufficientSpace()
    val conflictExamples = remember(conflictStrategy, selectedCats, selectedFolds) {
        viewModel.getConflictExamples(conflictStrategy)
    }

    val allFiles = viewModel.getSelectedFiles()
    val filteredFiles = remember(allFiles, searchQuery) {
        if (searchQuery.isBlank()) allFiles else allFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val groupedFiles = remember(filteredFiles) {
        filteredFiles.groupBy { it.relativePath.ifEmpty { "Root" } }
    }

    // Permission handling for notifications
    val onStartExtraction = {
        viewModel.startExtractionWithSelectedFiles(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> onStartExtraction() }
    )

    val onExtractClicked = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onStartExtraction()
            }
        } else {
            onStartExtraction()
        }
    }

    // Set of collapsed subfolders
    val collapsedFolders = remember { mutableStateMapOf<String, Boolean>() }

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("preview_screen")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.title_preview),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = inkCharcoalColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(id = R.string.subtitle_preview),
                            style = MaterialTheme.typography.bodyMedium,
                            color = inkSubtleColor,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 3.a Summary Cards
                item {
                    val s = summary ?: ScanSummary()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryBadge(
                                    label = stringResource(id = R.string.summary_total_files),
                                    value = "${s.totalFiles}",
                                    color = colors.accent
                                )
                                SummaryBadge(
                                    label = stringResource(id = R.string.summary_folders),
                                    value = "${s.totalFolders}",
                                    color = colors.folderArt
                                )
                                SummaryBadge(
                                    label = stringResource(id = R.string.summary_max_depth),
                                    value = "${s.maxDepth}",
                                    color = colors.warning
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryBadge(
                                    label = stringResource(id = R.string.summary_total_size),
                                    value = viewModel.formatSize(s.totalSize),
                                    color = colors.success
                                )
                                SummaryBadge(
                                    label = stringResource(id = R.string.summary_junk_skipped),
                                    value = "${s.skippedJunkCount}",
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Output Section (Mode toggle, Destination selection, Inside-source warning, and Space check)
                item {
                    val reqBytes = viewModel.estimateRequiredSpace(viewModel.getSelectedFiles(), flattenMode)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                            .testTag("output_section_box")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = stringResource(id = R.string.section_output),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )

                            // Plain Copy & Flatten / Compress to .ZIP toggle (moved from Hub)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.segmentTrack)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isDirect = flattenMode == FlattenMode.DIRECT
                                val isZip = flattenMode == FlattenMode.ZIP

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDirect) colors.accent else Color.Transparent)
                                        .clickable { viewModel.setFlattenMode(FlattenMode.DIRECT) }
                                        .testTag("mode_plain_copy_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.mode_plain_copy),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isDirect) colors.onAccent else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isZip) colors.accent else Color.Transparent)
                                        .clickable { viewModel.setFlattenMode(FlattenMode.ZIP) }
                                        .testTag("mode_compress_zip_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.mode_compress_zip),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isZip) colors.onAccent else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Choose destination card (same style as old destination card)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .shadow(
                                        elevation = 2.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        clip = false,
                                        ambientColor = colors.textSecondary.copy(alpha = 0.2f),
                                        spotColor = colors.textSecondary.copy(alpha = 0.3f)
                                    )
                                    .background(colors.card, RoundedCornerShape(12.dp))
                                    .clickable { destinationChooser.launch(null) }
                                    .border(1.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("destination_picker_box"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FolderIcon(
                                        color = if (destUri != null) colors.accent else colors.folderArtMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.btn_select_destination),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (destUri != null) getDisplayPath(destUri!!) else stringResource(id = R.string.status_none_selected),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (destUri != null) colors.textPrimary else colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }

                            // Warning if destination is inside source folder
                            if (isDestInsideSrc) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.warningTint)
                                        .border(1.5.dp, colors.warning, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                        .testTag("dest_inside_source_warning")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = colors.warning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.warning_dest_inside_source),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.warning
                                        )
                                    }
                                }
                            }

                            // Space check
                            if (destUri == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.segmentTrack)
                                        .padding(12.dp)
                                        .testTag("storage_choose_dest_hint"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.storage_choose_dest_first),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                val availStr = destAvailBytes?.let { viewModel.formatSize(it) } ?: "Checking…"
                                val reqStr = viewModel.formatSize(reqBytes)

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.storage_available, availStr),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = stringResource(id = R.string.storage_required, reqStr),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (hasSpace) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = colors.success,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(id = R.string.storage_sufficient),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.success
                                            )
                                        }
                                    } else {
                                        // Red Stamp Warning
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.errorTint)
                                                .border(1.5.dp, colors.error, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                                .testTag("storage_insufficient_warning")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = colors.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = stringResource(id = R.string.storage_insufficient),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp,
                                                        color = colors.error
                                                    )
                                                    Text(
                                                        text = stringResource(id = R.string.storage_warning_desc, reqStr, availStr),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3.b File-Type Breakdown
                item {
                    val s = summary ?: ScanSummary()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.section_file_types),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            FileCategory.entries.forEach { cat ->
                                val count = s.categoryCounts[cat] ?: 0
                                val size = s.categorySizes[cat] ?: 0L
                                val isChecked = selectedCats.contains(cat)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleCategory(cat) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { viewModel.toggleCategory(cat) },
                                        colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                                        modifier = Modifier.testTag("cat_checkbox_${cat.name.lowercase()}")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        if (cat.extensions.isNotEmpty()) {
                                            Text(
                                                text = cat.extensions.joinToString(", "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$count files • ${viewModel.formatSize(size)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isChecked) colors.accent else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3.c Name Conflicts Section
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.section_conflicts),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (duplicatesCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = colors.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.conflicts_detected, duplicatesCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.error
                                    )
                                }

                                // Segmented Control
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.segmentTrack)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isSeq = conflictStrategy == 1
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSeq) colors.accent else Color.Transparent)
                                            .clickable { viewModel.setConflictStrategy(1) }
                                            .padding(vertical = 8.dp)
                                            .testTag("conflict_auto_sequence_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Auto-Sequence",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSeq) colors.onAccent else colors.textSecondary
                                        )
                                    }

                                    val isParent = conflictStrategy == 2
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isParent) colors.accent else Color.Transparent)
                                            .clickable { viewModel.setConflictStrategy(2) }
                                            .padding(vertical = 8.dp)
                                            .testTag("conflict_parent_prefix_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Parent Prefix",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isParent) colors.onAccent else colors.textSecondary
                                        )
                                    }
                                }

                                // Rename Examples
                                if (conflictExamples.isNotEmpty()) {
                                    Text(
                                        text = "Sample renames:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.segmentTrack)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        conflictExamples.forEach { (from, to) ->
                                            Text(
                                                text = "$from → $to",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.success,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.conflicts_none),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Feature: Skip Identical Duplicates Toggle
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(id = R.string.toggle_skip_duplicates),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(id = R.string.toggle_skip_duplicates_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = skipIdenticalDuplicates,
                                onCheckedChange = { viewModel.setSkipIdenticalDuplicates(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccent,
                                    checkedTrackColor = colors.accent,
                                    uncheckedTrackColor = colors.segmentTrack
                                ),
                                modifier = Modifier.testTag("skip_identical_duplicates_toggle")
                            )
                        }
                    }
                }

                // Feature: Size and Date Filters
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.section_filters),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )
                                val hasAnyFilter = filterMinSize != null || filterMaxSize != null || filterMinDate != null || filterMaxDate != null
                                if (hasAnyFilter) {
                                    Text(
                                        text = stringResource(id = R.string.filter_reset),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent,
                                        modifier = Modifier.clickable {
                                            viewModel.resetFilters()
                                            minSizeInput = ""
                                            maxSizeInput = ""
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Size range filters (in MB)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = minSizeInput,
                                    onValueChange = { input ->
                                        minSizeInput = input.filter { it.isDigit() }
                                        val mb = minSizeInput.toLongOrNull()
                                        viewModel.setFilterMinSizeBytes(mb?.let { it * 1024L * 1024L })
                                    },
                                    label = { Text(stringResource(id = R.string.filter_size_min), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f).testTag("filter_min_size_field"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = maxSizeInput,
                                    onValueChange = { input ->
                                        maxSizeInput = input.filter { it.isDigit() }
                                        val mb = maxSizeInput.toLongOrNull()
                                        viewModel.setFilterMaxSizeBytes(mb?.let { it * 1024L * 1024L })
                                    },
                                    label = { Text(stringResource(id = R.string.filter_size_max), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f).testTag("filter_max_size_field"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Date range pickers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // From Date Picker
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.segmentTrack)
                                        .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val cal = Calendar.getInstance()
                                            if (filterMinDate != null) cal.timeInMillis = filterMinDate!!
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    val selectedCal = Calendar.getInstance()
                                                    selectedCal.set(year, month, dayOfMonth, 0, 0, 0)
                                                    selectedCal.set(Calendar.MILLISECOND, 0)
                                                    viewModel.setFilterMinDateMillis(selectedCal.timeInMillis)
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("filter_min_date_picker")
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.filter_date_start),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                            fontSize = 10.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dateText = filterMinDate?.let { dateFormat.format(Date(it)) } ?: stringResource(id = R.string.filter_date_any)
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (filterMinDate != null) colors.accent else colors.textPrimary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // To Date Picker
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.segmentTrack)
                                        .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val cal = Calendar.getInstance()
                                            if (filterMaxDate != null) cal.timeInMillis = filterMaxDate!!
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    val selectedCal = Calendar.getInstance()
                                                    selectedCal.set(year, month, dayOfMonth, 23, 59, 59)
                                                    selectedCal.set(Calendar.MILLISECOND, 999)
                                                    viewModel.setFilterMaxDateMillis(selectedCal.timeInMillis)
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("filter_max_date_picker")
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.filter_date_end),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                            fontSize = 10.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dateText = filterMaxDate?.let { dateFormat.format(Date(it)) } ?: stringResource(id = R.string.filter_date_any)
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (filterMaxDate != null) colors.accent else colors.textPrimary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3.e File List Grouped by Subfolder & Search
                item {
                    val s = summary ?: ScanSummary()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(14.dp))
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.section_files_by_folder),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )

                                val allSelected = selectedFolds.size == s.subfolders.size && s.subfolders.isNotEmpty()
                                Text(
                                    text = if (allSelected) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    modifier = Modifier.clickable {
                                        viewModel.selectAllFolders(!allSelected)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Search Box
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_files_field"),
                                placeholder = {
                                    Text(
                                        text = stringResource(id = R.string.search_files_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Subfolders Items
                val subfoldersList = summary?.subfolders.orEmpty()
                items(subfoldersList) { folder ->
                    val filesInFolder = groupedFiles[folder].orEmpty()
                    val isFolderSelected = selectedFolds.contains(folder)
                    val isCollapsed = collapsedFolders[folder] ?: false

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(10.dp))
                            .background(colors.card, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isFolderSelected,
                                    onCheckedChange = { viewModel.toggleFolder(folder) },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                                    modifier = Modifier.testTag("folder_checkbox_$folder")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FolderIcon(
                                    color = if (isFolderSelected) colors.folderArt else colors.folderArtMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${filesInFolder.size} files match",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { collapsedFolders[folder] = !isCollapsed },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                        tint = colors.textSecondary
                                    )
                                }
                            }

                            AnimatedVisibility(visible = !isCollapsed && filesInFolder.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp, top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    filesInFolder.take(20).forEach { file ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = viewModel.formatSize(file.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                    if (filesInFolder.size > 20) {
                                        Text(
                                            text = "+ ${filesInFolder.size - 20} more files…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accent,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Space for sticky bottom bar
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            // 4. Sticky Bottom Bar
            val isDestChosen = destUri != null
            val canExtract = isDestChosen && hasSpace && selectedCount > 0
            val disabledReason = when {
                !isDestChosen -> stringResource(id = R.string.btn_reason_choose_dest)
                !hasSpace -> stringResource(id = R.string.btn_reason_insufficient_space)
                selectedCount == 0 -> "No files selected"
                else -> null
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(8.dp)
                    .background(colors.card)
                    .border(BorderStroke(1.dp, colors.cardBorder))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.Hub) },
                            modifier = Modifier
                                .weight(0.35f)
                                .height(50.dp)
                                .testTag("preview_back_button"),
                            border = BorderStroke(1.5.dp, colors.cardBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.btn_back),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Button(
                            onClick = { onExtractClicked() },
                            enabled = canExtract,
                            modifier = Modifier
                                .weight(0.65f)
                                .height(50.dp)
                                .testTag("preview_extract_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                                disabledContainerColor = colors.segmentTrack,
                                disabledContentColor = colors.textSecondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.btn_extract_format,
                                    selectedCount,
                                    viewModel.formatSize(selectedBytes)
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (disabledReason != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = disabledReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!hasSpace && isDestChosen) colors.error else colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extract_disabled_reason")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBadge(
    label: String,
    value: String,
    color: Color
) {
    val colors = UnNestTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            fontSize = 10.sp
        )
    }
}
