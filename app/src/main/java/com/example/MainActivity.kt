package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PaperSurface
import com.example.ui.theme.PaperSurfaceDarker
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkSage
import com.example.ui.theme.InkSageLight
import com.example.ui.theme.InkRedWax
import com.example.ui.theme.InkRedWaxLight
import com.example.ui.theme.InkGold
import com.example.ui.theme.ThemeRed
import com.example.ui.theme.ThemeRedLight
import com.example.ui.theme.VelvetRed
import com.example.ui.theme.VelvetRedLight

@Composable
fun FolderIcon(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .drawBehind {
                val width = size.width
                val height = size.height
                val tabWidth = width * 0.45f
                val tabHeight = height * 0.25f

                val folderPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, tabHeight)
                    lineTo(tabWidth * 0.8f, tabHeight)
                    lineTo(tabWidth, tabHeight * 0.4f)
                    lineTo(width - 2.dp.toPx(), tabHeight * 0.4f)
                    lineTo(width, tabHeight)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(
                    path = folderPath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )
                
                drawLine(
                    color = color.copy(alpha = 0.3f),
                    start = Offset(0f, tabHeight),
                    end = Offset(width, tabHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: UnNestViewModel = viewModel()
            val stateDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDark = stateDarkTheme ?: systemInDark

            MyApplicationTheme(darkTheme = isDark) {
                UnNestAppNavigation(viewModel)
            }
        }
    }
}

@Composable
fun PaperCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                val hSpacing = 28.dp.toPx()
                val lineWeight = 0.5.dp.toPx()
                val lineColor = onBg.copy(alpha = 0.05f)
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = lineWeight
                    )
                    y += hSpacing
                }

                drawLine(
                    color = InkRedWax.copy(alpha = 0.08f),
                    start = Offset(36.dp.toPx(), 0f),
                    end = Offset(36.dp.toPx(), size.height),
                    strokeWidth = 1.2f.dp.toPx()
                )
            }
    ) {
        content()
    }
}

@Composable
fun UnNestAppNavigation(viewModel: UnNestViewModel) {
    val screen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val stateDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val isDark = stateDarkTheme ?: systemInDark

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "ScreenTransitions",
            modifier = Modifier.fillMaxSize()
        ) { targetScreen ->
            when (targetScreen) {
                AppScreen.Splash -> UnNestSplashScreen(onFinished = {
                    viewModel.navigateTo(AppScreen.Hub)
                })
                AppScreen.Hub -> UnNestHubScreen(viewModel)
                AppScreen.Workbench -> UnNestWorkbenchScreen(viewModel)
                AppScreen.Success -> UnNestSuccessScreen(viewModel)
            }
        }

        if (screen != AppScreen.Splash) {
            IconButton(
                onClick = { viewModel.setDarkTheme(!isDark) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp)
                    .testTag("theme_toggle_button")
            ) {
                Text(
                    text = if (isDark) "☀️" else "🌙",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
fun SplashDocument(
    extension: String,
    tagColor: Color,
    offsetX: Float,
    offsetY: Float,
    rotation: Float,
    scale: Float,
    progress: Float
) {
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    val currentX = offsetX * (1f - progress)
    val currentY = offsetY + (150f - offsetY) * progress
    val currentRotation = rotation * (1f - progress)
    val currentScale = scale + (0.55f - scale) * progress
    val currentAlpha = if (progress > 0.85f) (1f - (progress - 0.85f) / 0.15f) else 1f

    Box(
        modifier = Modifier
            .offset(x = currentX.dp, y = currentY.dp)
            .graphicsLayer {
                rotationZ = currentRotation
                scaleX = currentScale
                scaleY = currentScale
                alpha = currentAlpha
            }
            .size(width = 46.dp, height = 58.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.5.dp, inkCharcoalColor, RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.dp).background(inkCharcoalColor.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.dp).background(inkCharcoalColor.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(inkCharcoalColor.copy(alpha = 0.2f)))
            
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(tagColor, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = extension,
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun GoldenFolderPocket(modifier: Modifier = Modifier) {
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier.size(width = 110.dp, height = 80.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -2f }
                .background(Color(0xFFCBB696), RoundedCornerShape(8.dp))
                .border(2.dp, inkCharcoalColor, RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = 10.dp, y = (-10).dp)
                .size(36.dp, 16.dp)
                .background(Color(0xFFCBB696), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .border(2.dp, inkCharcoalColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            modifier = Modifier
                .offset(y = (-6).dp)
                .fillMaxWidth(0.85f)
                .align(Alignment.Center)
                .height(72.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.5.dp, inkCharcoalColor, RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    rotationZ = 3f
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .background(Color(0xFFDECBB1), RoundedCornerShape(8.dp))
                .border(2.dp, inkCharcoalColor, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.label_unnest_folder),
                    color = inkCharcoalColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Screen 1: Splitting Sequence - Intro Splash Animation
@Composable
fun UnNestSplashScreen(onFinished: () -> Unit) {
    BackHandler {
        onFinished()
    }
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    val inkSubtleColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    var animateStart by remember { mutableStateOf(false) }
    
    // Shortened to 1.2s per specification with tap-to-skip
    LaunchedEffect(Unit) {
        animateStart = true
        delay(1200)
        onFinished()
    }

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onFinished()
            }
            .testTag("splash_screen")
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val progress by animateFloatAsState(
                    targetValue = if (animateStart) 1.0f else 0.0f,
                    animationSpec = tween(1000, easing = LinearOutSlowInEasing),
                    label = "splash_progress"
                )

                SplashDocument(".JPG", ThemeRed, -90f, -60f, -35f, 1.0f, progress)
                SplashDocument(".ZIP", InkSage, 90f, -40f, 40f, 0.9f, progress)
                SplashDocument(".TXT", InkGold, -50f, -120f, -15f, 0.95f, progress)
                SplashDocument(".PDF", InkRedWax, 45f, -110f, 25f, 0.92f, progress)
                SplashDocument(".MP4", Color(0xFF76586F), 100f, -140f, 50f, 0.88f, progress)

                GoldenFolderPocket(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(id = R.string.title_directory_flattener),
                style = MaterialTheme.typography.headlineLarge,
                color = inkCharcoalColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.subtitle_nested_file_archive),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = inkSubtleColor,
                letterSpacing = 2.sp
            )
        }
    }
}

// Screen 2: UnNest Hub (Configuration and Legal Settings Deck)
@Composable
fun UnNestHubScreen(viewModel: UnNestViewModel) {
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    val inkSubtleColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val context = LocalContext.current
    val srcUri by viewModel.sourceDirectoryUri.collectAsStateWithLifecycle()
    val destUri by viewModel.destinationDirectoryUri.collectAsStateWithLifecycle()
    val mode by viewModel.flattenMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val isChecking by viewModel.isCheckingPreCompressed.collectAsStateWithLifecycle()
    val showDlg by viewModel.showOptimizationDialog.collectAsStateWithLifecycle()
    val heavyCount by viewModel.detectedHeavyFilesCount.collectAsStateWithLifecycle()

    var showPrivacyDialog by remember { mutableStateOf(false) }

    val sourceChooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val oldUri = srcUri
                if (oldUri != null && oldUri != uri) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            oldUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (ignored: Exception) {}
                }
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_folder_access_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                viewModel.setSourceDirectory(uri)
            }
        }
    )

    val destChooser = rememberLauncherForActivityResult(
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
                viewModel.setDestinationDirectory(uri)
            }
        }
    )

    val proceedWithExtraction: () -> Unit = {
        if (mode == FlattenMode.ZIP) {
            scope.launch {
                viewModel.setCheckingState(true)
                val count = viewModel.checkPreCompressedMediaFast(context)
                viewModel.setCheckingState(false)
                if (count > 0) {
                    viewModel.setHeavyFilesCount(count)
                    viewModel.setOptimizationDialogVisibility(true)
                } else {
                    viewModel.isOptimizationEnabled = false
                    viewModel.startExtraction(context)
                }
            }
        } else {
            viewModel.isOptimizationEnabled = false
            viewModel.startExtraction(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            proceedWithExtraction()
        }
    )

    val onStartExtractionRequested: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                proceedWithExtraction()
            }
        } else {
            proceedWithExtraction()
        }
    }

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("hub_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.title_directory_flattener),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = inkCharcoalColor,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = stringResource(id = R.string.subtitle_hub_flatten),
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkSubtleColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Picker Card 1: Source
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = inkSubtleColor.copy(alpha = 0.25f),
                        spotColor = inkSubtleColor.copy(alpha = 0.35f)
                    )
                    .background(PaperSurface, RoundedCornerShape(16.dp))
                    .clickable { sourceChooser.launch(null) }
                    .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("source_picker_box"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FolderIcon(
                        color = if (srcUri != null) VelvetRed else VelvetRedLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = R.string.btn_select_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelvetRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (srcUri != null) getDisplayPath(srcUri!!) else stringResource(id = R.string.status_none_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (srcUri != null) VelvetRed else VelvetRedLight,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaperSurfaceDarker.copy(alpha = 0.5f))
                    .border(1.dp, PaperSurfaceDarker, RoundedCornerShape(24.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isDirect = mode == FlattenMode.DIRECT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDirect) ThemeRedLight else Color.Transparent)
                        .clickable { viewModel.setFlattenMode(FlattenMode.DIRECT) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.mode_plain_copy),
                        color = if (isDirect) ThemeRed else inkSubtleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                val isZip = mode == FlattenMode.ZIP
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isZip) ThemeRedLight else Color.Transparent)
                        .clickable { viewModel.setFlattenMode(FlattenMode.ZIP) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.mode_compress_zip),
                        color = if (isZip) ThemeRed else inkSubtleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Picker Card 2: Destination
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = inkSubtleColor.copy(alpha = 0.25f),
                        spotColor = inkSubtleColor.copy(alpha = 0.35f)
                    )
                    .background(PaperSurface, RoundedCornerShape(16.dp))
                    .clickable { destChooser.launch(null) }
                    .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("destination_picker_box"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FolderIcon(
                        color = if (destUri != null) VelvetRed else VelvetRedLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = R.string.btn_select_destination),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelvetRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (destUri != null) getDisplayPath(destUri!!) else stringResource(id = R.string.status_none_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (destUri != null) VelvetRed else VelvetRedLight,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Execution Button
            val workspaceReady = srcUri != null && destUri != null
            Button(
                onClick = { onStartExtractionRequested() },
                enabled = workspaceReady && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("process_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeRed,
                    contentColor = Color.White,
                    disabledContainerColor = inkSubtleColor.copy(alpha = 0.12f),
                    disabledContentColor = inkSubtleColor
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.status_analyzing_files),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.btn_analyze_folder_structure),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Specifications & Legal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.section_specifications),
                    style = MaterialTheme.typography.labelSmall,
                    color = inkSubtleColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Version Row (without 7-tap easter egg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.label_build_version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkCharcoalColor
                    )
                    Text(
                        text = "Version " + BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSubtleColor
                    )
                }

                // Company Row
                val corporateLabelStr = stringResource(id = R.string.corporate_label)
                val appLandingUrlStr = stringResource(id = R.string.landing_page_url)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appLandingUrlStr))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_site_fallback, appLandingUrlStr),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.label_corporate_publishing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkCharcoalColor
                    )
                    Text(
                        text = corporateLabelStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Support Row
                val supportMailStr = stringResource(id = R.string.support_email)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$supportMailStr")
                                    putExtra(Intent.EXTRA_SUBJECT, "UnNest Support Query")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_mail_fallback, supportMailStr),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.engineering_support),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkCharcoalColor
                    )
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = stringResource(id = R.string.contact_mail_description),
                        tint = InkSage,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Privacy Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyDialog = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.view_privacy_policy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkCharcoalColor
                    )
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(id = R.string.privacy_policy_description),
                        tint = InkSage,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        val policyUrlStr = stringResource(id = R.string.privacy_policy_url)
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(
                        text = stringResource(id = R.string.btn_close),
                        color = InkRedWax,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(policyUrlStr))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_privacy_fallback, policyUrlStr),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) {
                    Text(
                        text = stringResource(id = R.string.btn_full_privacy_policy),
                        color = InkSage,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = PaperSurface,
            title = {
                Text(
                    text = stringResource(id = R.string.title_privacy),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = inkCharcoalColor
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkCharcoalColor,
                    lineHeight = 20.sp
                )
            }
        )
    }

    UnNestOptimizationAlertDialog(
        showDialog = showDlg,
        detectedHeavyFilesCount = heavyCount,
        onDismissRequest = {
            viewModel.setOptimizationDialogVisibility(false)
        },
        onConfirmExecution = {
            viewModel.setOptimizationDialogVisibility(false)
            viewModel.isOptimizationEnabled = true
            viewModel.startExtraction(context)
        }
    )
}

fun getDisplayPath(uri: Uri): String {
    val decoded = Uri.decode(uri.toString())
    return if (decoded.contains(":")) {
        val sub = decoded.substringAfterLast(":")
        "/Storage/.../$sub"
    } else {
        "/Storage/.../Root"
    }
}

// Screen 3: UnNest Workbench (Processing Deck)
@Composable
fun UnNestWorkbenchScreen(viewModel: UnNestViewModel) {
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }

    val status by viewModel.processStatus.collectAsStateWithLifecycle()

    BackHandler {
        if (status is ProcessStatus.Scanning || status is ProcessStatus.ProcessingFiles) {
            showCancelDialog = true
        } else {
            viewModel.navigateTo(AppScreen.Hub)
        }
    }
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    val inkSubtleColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val totalFiles = viewModel.sessionLogs.collectAsStateWithLifecycle().value.size
    val maxDepth by viewModel.maxScannedDepth.collectAsStateWithLifecycle()
    val duplicatesCount by viewModel.duplicatesCount.collectAsStateWithLifecycle()
    val resolvedConflicts by viewModel.conflictsResolvedCount.collectAsStateWithLifecycle()

    var elapsedMs by remember { mutableStateOf(0L) }
    var applyToAllConflictsChecked by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (status is ProcessStatus.ProcessingFiles) {
            val startTime = System.currentTimeMillis()
            while (status is ProcessStatus.ProcessingFiles) {
                elapsedMs = System.currentTimeMillis() - startTime
                delay(120)
            }
        }
    }

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("workbench_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.title_directory_flattener),
                style = MaterialTheme.typography.headlineMedium,
                color = inkCharcoalColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Stats Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = inkSubtleColor.copy(alpha = 0.2f),
                        spotColor = inkSubtleColor.copy(alpha = 0.3f)
                    )
                    .background(PaperSurface, RoundedCornerShape(12.dp))
                    .border(1.2.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val scCount = when (val s = status) {
                        is ProcessStatus.ProcessingFiles -> s.totalFiles
                        else -> totalFiles
                    }
                    val formattedSize = if (scCount > 0) "${(scCount * 180) / 1024} KB" else "0 KB"
                    
                    Text(
                        text = stringResource(id = R.string.stats_analysis_header, scCount, formattedSize, maxDepth),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelvetRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (val s = status) {
                is ProcessStatus.Scanning -> {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(170.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(130.dp),
                                    color = ThemeRed,
                                    strokeWidth = 8.dp,
                                    trackColor = ThemeRedLight
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(id = R.string.status_scanning),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = inkCharcoalColor
                                    )
                                    Text(
                                        text = stringResource(id = R.string.status_active),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThemeRed
                                    )
                                }
                            }
                        }

                        val itemDisplay = s.currentFile.ifEmpty { stringResource(id = R.string.status_discovering_entries) }
                        Text(
                            text = stringResource(id = R.string.label_item_prefix, itemDisplay),
                            style = MaterialTheme.typography.bodySmall,
                            color = inkSubtleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E211F))
                                .border(1.5.dp, inkCharcoalColor, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            val terminalScroll = rememberScrollState()
                            LaunchedEffect(s.treeOutput) {
                                terminalScroll.animateScrollTo(terminalScroll.maxValue)
                            }
                            Text(
                                text = s.treeOutput.ifEmpty { stringResource(id = R.string.status_terminal_init) },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFCBE2D4),
                                modifier = Modifier
                                    .verticalScroll(terminalScroll)
                                    .fillMaxSize()
                            )
                        }
                    }
                }

                is ProcessStatus.Conflict -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    clip = false,
                                    ambientColor = InkGold.copy(alpha = 0.25f),
                                    spotColor = InkGold.copy(alpha = 0.35f)
                                )
                                .background(PaperSurface, RoundedCornerShape(12.dp))
                                .border(1.5.dp, VelvetRed, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = VelvetRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.title_duplicate_clash),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VelvetRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stringResource(id = R.string.desc_conflict_explanation, s.conflictedName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelvetRed
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = stringResource(id = R.string.label_duplicated_subdirectories),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelvetRed,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PaperSurfaceDarker.copy(alpha = 0.3f))
                                        .border(1.2.dp, PaperSurfaceDarker, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(s.duplicates) { dup ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FolderIcon(
                                                    color = ThemeRed,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "/${dup.relativePath}/${dup.name}",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = VelvetRed,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyToAllConflictsChecked = !applyToAllConflictsChecked }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = applyToAllConflictsChecked,
                                        onCheckedChange = { applyToAllConflictsChecked = it },
                                        colors = CheckboxDefaults.colors(checkedColor = VelvetRed, uncheckedColor = VelvetRed.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.checkbox_apply_all_conflicts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VelvetRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.submitConflictResolution(context, 1, applyToAllConflictsChecked) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("resolve_seq_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = ThemeRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(id = R.string.btn_auto_sequence), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(stringResource(id = R.string.desc_auto_sequence), fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.submitConflictResolution(context, 2, applyToAllConflictsChecked) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("resolve_parent_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = InkSage),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(id = R.string.btn_parent_prefix), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(stringResource(id = R.string.desc_parent_prefix), fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is ProcessStatus.ProcessingFiles -> {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val ratio = if (s.totalFiles > 0) s.filesWritten.toFloat() / s.totalFiles else 0.0f
                        val pctText = "${(ratio * 100).toInt()}%"

                        Box(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(190.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = ThemeRedLight,
                                        radius = 75.dp.toPx(),
                                        style = Stroke(width = 10.dp.toPx())
                                    )
                                    drawArc(
                                        color = ThemeRed,
                                        startAngle = -90f,
                                        sweepAngle = ratio * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                                        size = androidx.compose.ui.geometry.Size(150.dp.toPx(), 150.dp.toPx()),
                                        topLeft = Offset((size.width - 150.dp.toPx()) / 2, (size.height - 150.dp.toPx()) / 2)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(id = R.string.label_un_nesting),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = inkSubtleColor
                                    )
                                    Text(
                                        text = pctText,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = ThemeRed,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    clip = false,
                                    ambientColor = inkSubtleColor.copy(alpha = 0.25f),
                                    spotColor = inkSubtleColor.copy(alpha = 0.35f)
                                )
                                .background(PaperSurface, RoundedCornerShape(12.dp))
                                .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.title_final_operation_stats),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = inkCharcoalColor,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(id = R.string.label_total_files_processed), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                                    Text("${s.filesWritten} / ${s.totalFiles}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(id = R.string.label_final_directory_size), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                                    val sizeVal = "${(s.filesWritten * 180) / 1024} KB"
                                    Text(sizeVal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(id = R.string.label_duplicates_renamed), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                                    Text("$duplicatesCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(id = R.string.label_conflicts_resolved), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                                    Text("$resolvedConflicts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(id = R.string.label_execution_time), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                                    val elapsedSecStr = String.format("%02dm %02ds", (elapsedMs / 1000) / 60, (elapsedMs / 1000) % 60)
                                    Text(elapsedSecStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkSage)
                                }
                            }
                        }
                    }
                }

                is ProcessStatus.Error -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(InkRedWaxLight)
                            .border(1.5.dp, InkRedWax, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = InkRedWax,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(id = R.string.title_extraction_error),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = inkCharcoalColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = inkCharcoalColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.navigateTo(AppScreen.Hub) },
                                colors = ButtonDefaults.buttonColors(containerColor = inkCharcoalColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(id = R.string.btn_return_hub), color = PaperBackground)
                            }
                        }
                    }
                }

                is ProcessStatus.Cancelled -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperSurface)
                            .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = ThemeRed,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(id = R.string.title_operation_cancelled),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = inkCharcoalColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkCharcoalColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.navigateTo(AppScreen.Hub) },
                                colors = ButtonDefaults.buttonColors(containerColor = ThemeRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(id = R.string.btn_return_hub), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {}
            }

            if (status is ProcessStatus.Scanning || status is ProcessStatus.ProcessingFiles) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_button"),
                    border = BorderStroke(1.5.dp, ThemeRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.btn_cancel_job),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.title_cancel_job),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = inkCharcoalColor
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.desc_cancel_job),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkCharcoalColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCancelDialog = false
                            viewModel.cancelJob(context)
                        }
                    ) {
                        Text(stringResource(id = R.string.btn_yes_cancel), color = ThemeRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text(stringResource(id = R.string.btn_continue), color = inkSubtleColor)
                    }
                },
                containerColor = PaperSurface
            )
        }
    }
}

// Screen 4: The Success and Audit Matrix Screen
@Composable
fun UnNestSuccessScreen(viewModel: UnNestViewModel) {
    BackHandler {
        viewModel.navigateTo(AppScreen.Hub)
    }
    val inkCharcoalColor = MaterialTheme.colorScheme.onBackground
    val inkSubtleColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val stats by viewModel.sessionStats.collectAsStateWithLifecycle()
    val logs by viewModel.sessionLogs.collectAsStateWithLifecycle()
    val duplicatesCount by viewModel.duplicatesCount.collectAsStateWithLifecycle()
    val resolvedConflicts by viewModel.conflictsResolvedCount.collectAsStateWithLifecycle()

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("success_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.title_execution_log_stats),
                style = MaterialTheme.typography.headlineMedium,
                color = inkCharcoalColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .drawBehind {
                        drawCircle(
                            color = InkSageLight,
                            radius = size.width / 2
                        )
                        drawCircle(
                            color = InkSage,
                            radius = (size.width / 2) - 6.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(id = R.string.success_icon_description),
                    tint = InkSage,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.status_success),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = inkCharcoalColor
            )

            val copiedCount = stats?.filesProcessed ?: 0
            val skippedCount = stats?.filesSkipped ?: 0
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.summary_copied_skipped, copiedCount, skippedCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = inkCharcoalColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InkSageLight.copy(alpha = 0.45f))
                    .border(1.5.dp, InkSage, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                val logScroll = rememberScrollState()
                val logLinesText = if (logs.isEmpty()) {
                    stringResource(id = R.string.status_success_manifest)
                } else {
                    logs.joinToString("\n")
                }
                
                Text(
                    text = logLinesText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = InkSage,
                    modifier = Modifier
                        .verticalScroll(logScroll)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = inkSubtleColor.copy(alpha = 0.25f),
                        spotColor = inkSubtleColor.copy(alpha = 0.35f)
                    )
                    .background(PaperSurface, RoundedCornerShape(12.dp))
                    .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.title_final_operation_stats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = inkCharcoalColor,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.label_total_files_processed), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                        Text("${stats?.filesProcessed ?: 0}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.label_final_directory_size), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                        val bytesVal = stats?.totalSize ?: 0L
                        Text(viewModel.formatSize(bytesVal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.label_duplicates_renamed), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                        Text("$duplicatesCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.label_conflicts_resolved), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                        Text("$resolvedConflicts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = inkCharcoalColor)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkCharcoalColor.copy(alpha = 0.05f)))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.label_execution_time), style = MaterialTheme.typography.bodyMedium, color = inkSubtleColor)
                        val durationMs = stats?.elapsedMs ?: 0L
                        val secondsStr = String.format("%02dm %02ds", (durationMs / 1000) / 60, (durationMs / 1000) % 60)
                        Text(secondsStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkSage)
                    }
                }
            }

            if (skippedCount > 0) {
                var isSkippedExpanded by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(12.dp),
                            clip = false,
                            ambientColor = InkRedWax.copy(alpha = 0.2f),
                            spotColor = InkRedWax.copy(alpha = 0.3f)
                        )
                        .background(PaperSurface, RoundedCornerShape(12.dp))
                        .border(1.5.dp, InkRedWaxLight, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSkippedExpanded = !isSkippedExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.label_skipped_files, skippedCount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = InkRedWax
                            )
                            Text(
                                text = stringResource(id = if (isSkippedExpanded) R.string.btn_hide else R.string.btn_show_details),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = InkRedWax
                            )
                        }

                        if (isSkippedExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                stats?.skippedFiles?.forEach { skipped ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PaperSurfaceDarker.copy(alpha = 0.5f))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = skipped.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = inkCharcoalColor
                                        )
                                        Text(
                                            text = skipped.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = inkSubtleColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.resetSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("new_session_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_start_another_session),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.Hub) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("exit_button"),
                    border = BorderStroke(1.5.dp, inkCharcoalColor.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = inkCharcoalColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_main_menu),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UnNestOptimizationAlertDialog(
    showDialog: Boolean,
    detectedHeavyFilesCount: Int,
    onDismissRequest: () -> Unit,
    onConfirmExecution: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(id = R.string.title_massive_files),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.desc_massive_files, detectedHeavyFilesCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Justify
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmExecution) {
                    Text(
                        text = stringResource(id = R.string.btn_proceed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = stringResource(id = R.string.btn_cancel),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}
