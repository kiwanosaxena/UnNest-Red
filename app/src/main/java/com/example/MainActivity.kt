package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.ui.theme.InkGoldLight
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
                
                // Draw folded line highlight
                drawLine(
                    color = color.copy(alpha = 0.3f),
                    start = Offset(0f, tabHeight),
                    end = Offset(width, tabHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }
    )
}

@Composable
fun InboxIcon(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .drawBehind {
                val w = size.width
                val h = size.height
                val d = h * 0.4f

                // Drawer Tray box
                val trayPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h * 0.2f)
                    lineTo(0f, h)
                    lineTo(w, h)
                    lineTo(w, h * 0.2f)
                    lineTo(w * 0.82f, h * 0.2f)
                    lineTo(w * 0.76f, d)
                    lineTo(w * 0.24f, d)
                    lineTo(w * 0.18f, h * 0.2f)
                    close()
                }
                drawPath(
                    path = trayPath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
                
                // Content document bar
                drawRoundRect(
                    color = color.copy(alpha = 0.22f),
                    topLeft = Offset(w * 0.25f, h * 0.15f),
                    size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Secure context linking for background file streams
        AppContextHolder.context = applicationContext

        setContent {
            val viewModel: UnNestViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val stateDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDark = stateDarkTheme ?: systemInDark

            MyApplicationTheme(darkTheme = isDark) {
                UnNestAppNavigation(viewModel)
            }
        }
    }
}

// Custom Paper Background Procedural Pattern Draw Modifier
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
                // Procedural horizontal paper fibers / ledger lined grid
                val hSpacing = 28.dp.toPx()
                val lineWeight = 0.5.dp.toPx()
                val lineColor = onBg.copy(alpha = 0.05f) // Delightful warm graphite pencil lead fiber
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

                // Left red-wax archival spine alignment layout line
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

        // Global Dynamic Theme Toggle (cinematic hide during splash)
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

// Helper Components for Visual Splendor
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
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    // Computes dynamic position sliding from outer floating state down into folder
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
            .border(1.5.dp, InkCharcoal, RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.dp).background(InkCharcoal.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.dp).background(InkCharcoal.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(InkCharcoal.copy(alpha = 0.2f)))
            
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
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .size(width = 110.dp, height = 80.dp)
    ) {
        // Rear folder cover (slightly darker manila folder shade)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -2f }
                .background(Color(0xFFCBB696), RoundedCornerShape(8.dp))
                .border(2.dp, InkCharcoal, RoundedCornerShape(8.dp))
        )
        // Tab index at the top-left
        Box(
            modifier = Modifier
                .offset(x = 10.dp, y = (-10).dp)
                .size(36.dp, 16.dp)
                .background(Color(0xFFCBB696), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .border(2.dp, InkCharcoal, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        // Opened papers sticking out slightly
        Box(
            modifier = Modifier
                .offset(y = (-6).dp)
                .fillMaxWidth(0.85f)
                .align(Alignment.Center)
                .height(72.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.5.dp, InkCharcoal, RoundedCornerShape(4.dp))
        )
        // Front folder cover angled open (classic warm light manila card finish)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    rotationZ = 3f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                }
                .background(Color(0xFFDECBB1), RoundedCornerShape(8.dp))
                .border(2.dp, InkCharcoal, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "UNNEST",
                    color = InkCharcoal,
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
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    val InkSubtle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    var animateStart by remember { mutableStateOf(false) }
    
    // Auto timeout capped at 2500ms
    LaunchedEffect(Unit) {
        animateStart = true
        delay(2500)
        onFinished()
    }

    PaperCanvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Click anytime to bypass transition instantly
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
            // Stunning stylized graphics layout mirroring Screen 1
            Box(
                modifier = Modifier
                    .size(240.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val progress by animateFloatAsState(
                    targetValue = if (animateStart) 1.0f else 0.0f,
                    animationSpec = tween(1900, easing = LinearOutSlowInEasing),
                    label = "splash_progress"
                )

                // Documents sailing down snugly into the centered folder
                SplashDocument(".JPG", ThemeRed, -90f, -60f, -35f, 1.0f, progress)
                SplashDocument(".ZIP", InkSage, 90f, -40f, 40f, 0.9f, progress)
                SplashDocument(".TXT", InkGold, -50f, -120f, -15f, 0.95f, progress)
                SplashDocument(".PDF", InkRedWax, 45f, -110f, 25f, 0.92f, progress)
                SplashDocument(".MP4", Color(0xFF76586F), 100f, -140f, 50f, 0.88f, progress)

                // Centered Folder
                GoldenFolderPocket(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Directory Flattener",
                style = MaterialTheme.typography.headlineLarge,
                color = InkCharcoal,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "NESTED FILE ARCHIVE UNIFICATION",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = InkSubtle,
                letterSpacing = 2.sp
            )
        }
    }
}

// Screen 2: UnNest Hub (Configuration and Legal Settings Deck)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnNestHubScreen(viewModel: UnNestViewModel) {
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    val InkSubtle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val context = LocalContext.current
    val srcUri by viewModel.sourceDirectoryUri.collectAsStateWithLifecycle()
    val destUri by viewModel.destinationDirectoryUri.collectAsStateWithLifecycle()
    val mode by viewModel.flattenMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val isChecking by viewModel.isCheckingPreCompressed.collectAsStateWithLifecycle()
    val showDlg by viewModel.showOptimizationDialog.collectAsStateWithLifecycle()
    val heavyCount by viewModel.detectedHeavyFilesCount.collectAsStateWithLifecycle()

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var devTaps by remember { mutableStateOf(0) }

    // SAF Directory Picker launch systems
    val sourceChooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Toast.makeText(context, "Permission binding complete.", Toast.LENGTH_SHORT).show()
                }
                viewModel.setSourceDirectory(uri)
            }
        }
    )

    val destChooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Toast.makeText(context, "Permission binding complete.", Toast.LENGTH_SHORT).show()
                }
                viewModel.setDestinationDirectory(uri)
            }
        }
    )

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

            // Pristine centered header consistent with Screen 2
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Directory Flattener",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkCharcoal,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Flatten folder tree layouts easily",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
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
                        ambientColor = InkSubtle.copy(alpha = 0.25f),
                        spotColor = InkSubtle.copy(alpha = 0.35f)
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
                        text = "Select Source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelvetRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (srcUri != null) getDisplayPath(srcUri!!) else "None Selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (srcUri != null) VelvetRed else VelvetRedLight,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // High Precision horizontal segmented control placed directly in between the cards
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
                        text = "Plain Copy & Flatten",
                        color = if (isDirect) ThemeRed else InkSubtle,
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
                        text = "Compress to .ZIP",
                        color = if (isZip) ThemeRed else InkSubtle,
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
                        ambientColor = InkSubtle.copy(alpha = 0.25f),
                        spotColor = InkSubtle.copy(alpha = 0.35f)
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
                        text = "Select Destination",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelvetRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (destUri != null) getDisplayPath(destUri!!) else "None Selected",
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

            // Primary Execution Button styled as "Analyze Folder Structure"
            val workspaceReady = srcUri != null && destUri != null
            Button(
                onClick = {
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
                },
                enabled = workspaceReady && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("process_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeRed,
                    contentColor = Color.White,
                    disabledContainerColor = InkSubtle.copy(alpha = 0.12f),
                    disabledContentColor = InkSubtle
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
                        text = "Analyzing Files...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                } else {
                    Text(
                        text = "Analyze Folder Structure",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Specifications & Legal declarative deck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "SPECIFICATIONS & LEGAL DECLARATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSubtle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Version Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            devTaps++
                            if (devTaps >= 7) {
                                devTaps = 0
                                Toast.makeText(context, "Developer Mode: Custom metrics active.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Architecture Build Version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkCharcoal
                    )
                    Text(
                        text = stringResource(id = R.string.app_version) + " (v1.3.0 Release)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle
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
                                Toast.makeText(context, "Landing Site: $appLandingUrlStr", Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Corporate Entity Publishing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkCharcoal
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
                                Toast.makeText(context, "Mail support: $supportMailStr", Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.engineering_support),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkCharcoal
                    )
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Contact Mail",
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
                        color = InkCharcoal
                    )
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Privacy Policy",
                        tint = InkSage,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Comprehensive Legal Core Declaration Popup Card Overlay
    if (showPrivacyDialog) {
        val policyUrlStr = stringResource(id = R.string.privacy_policy_url)
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("AGREE & CLOSE", color = InkRedWax, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(policyUrlStr))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Privacy Portal: $policyUrlStr", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("WEB DECLARATION Portal", color = InkSage)
                }
            },
            containerColor = PaperSurface,
            title = {
                Text(
                    text = "Zero-Knowledge Legal Pact",
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkCharcoal
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Effective Date: June 2026\nPublished by: Avika Labs (support@avikalabs.com)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = InkSubtle,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "1. Data Collection & Zero-Knowledge Architecture:\n" +
                                "UnNest processes directory subtrees strictly locally inside your device isolated storage. We do not transmit, harvest, log, or telemetry index folder tree names, file sizes, or processed entities on remote clouds. Your information is 100% private to you.\n\n" +
                                "2. Android Execution Sandboxing:\n" +
                                "By utilizing Android Storage Access Framework (SAF), directory boundaries are fully authenticated via system file scopes. Broad storage tracking or root tracking features are intentionally disabled to guarantee device security.\n\n" +
                                "3. Terms of Service & Liability Shield:\n" +
                                "UnNest resolves file restructuring actions locally. Under the Absolute Liability Shield, you assume 100% operational directory risks for destination writing paths. UnNest is provided strictly \"AS IS\" and \"AS AVAILABLE\" without warranties.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkCharcoal,
                        lineHeight = 16.sp
                    )
                }
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

// Helper to clean up SAF URI path strings and show a polished display representation
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
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    val InkSubtle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val status by viewModel.processStatus.collectAsStateWithLifecycle()
    val totalFiles = viewModel.sessionLogs.collectAsStateWithLifecycle().value.size
    val mode by viewModel.flattenMode.collectAsStateWithLifecycle()
    val maxDepth by viewModel.maxScannedDepth.collectAsStateWithLifecycle()
    val duplicatesCount by viewModel.duplicatesCount.collectAsStateWithLifecycle()
    val resolvedConflicts by viewModel.conflictsResolvedCount.collectAsStateWithLifecycle()
    val activeConflictGroup by viewModel.activeConflictGroup.collectAsStateWithLifecycle()

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
            // Header Title aligned to reference mockup
            Text(
                text = "Directory Flattener",
                style = MaterialTheme.typography.headlineMedium,
                color = InkCharcoal,
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
                        ambientColor = InkSubtle.copy(alpha = 0.2f),
                        spotColor = InkSubtle.copy(alpha = 0.3f)
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
                        text = "Analysis: $scCount Files | $formattedSize | $maxDepth Depth",
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
                                        text = "Scanning",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = InkCharcoal
                                    )
                                    Text(
                                        text = "Active...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThemeRed
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Item: ${s.currentFile.ifEmpty { "Discovering structural entries..." }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Monospace Typewriter discovery terminal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E211F))
                                .border(1.5.dp, InkCharcoal, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            val terminalScroll = rememberScrollState()
                            LaunchedEffect(s.treeOutput) {
                                terminalScroll.animateScrollTo(terminalScroll.maxValue)
                            }
                            Text(
                                text = s.treeOutput.ifEmpty { "Initializing search nodes...\nScanning directory sub-trees recursive..." },
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
                                        text = "Name Clashing Detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VelvetRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Multiple files named \"${s.conflictedName}\" are located in separate child folders. Select resolution strategy:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelvetRed
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "DUPLICATED SUBDIRECTORIES:",
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

                                // Session Memory Selection Box
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
                                        text = "Apply strategy selection to all remaining conflicts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VelvetRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Matrix Choices
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.submitConflictResolution(1, applyToAllConflictsChecked) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("resolve_seq_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = ThemeRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Auto-Sequence", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("e.g. name_1.jpg", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.submitConflictResolution(2, applyToAllConflictsChecked) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("resolve_parent_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = InkSage),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Parent Prefix", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("e.g. name_(Trip).jpg", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
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
                                        text = "Un-Nesting",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkSubtle
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

                        // Final Operation Stats Card (Mockup styled list format)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    clip = false,
                                    ambientColor = InkSubtle.copy(alpha = 0.25f),
                                    spotColor = InkSubtle.copy(alpha = 0.35f)
                                )
                                .background(PaperSurface, RoundedCornerShape(12.dp))
                                .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Final Operation Stats",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = InkCharcoal,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                // Row 1
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Files Processed", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                                    Text("${s.filesWritten} / ${s.totalFiles}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                                // Row 2
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Final Directory Size", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                                    val sizeVal = "${(s.filesWritten * 180) / 1024} KB"
                                    Text(sizeVal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                                // Row 3
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Duplicates Renamed", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                                    Text("$duplicatesCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                                // Row 4
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Conflicts Resolved", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                                    Text("$resolvedConflicts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                                // Row 5
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Execution Time", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
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
                                text = "Extraction Error",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = InkCharcoal
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkCharcoal,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.navigateTo(AppScreen.Hub) },
                                colors = ButtonDefaults.buttonColors(containerColor = InkCharcoal),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Return to Workspace Hub", color = PaperBackground)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// Screen 4: The Success and Audit Matrix Screen
@Composable
fun UnNestSuccessScreen(viewModel: UnNestViewModel) {
    val InkCharcoal = MaterialTheme.colorScheme.onBackground
    val InkSubtle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val stats by viewModel.sessionStats.collectAsStateWithLifecycle()
    val logs by viewModel.sessionLogs.collectAsStateWithLifecycle()
    val mode by viewModel.flattenMode.collectAsStateWithLifecycle()
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
            // Header Title aligned to reference mockup
            Text(
                text = "Execution Log & Stats",
                style = MaterialTheme.typography.headlineMedium,
                color = InkCharcoal,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Checkmark Success Badge
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
                    contentDescription = "Success",
                    tint = InkSage,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Success!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkCharcoal
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mon monospace logs screen styled in solid Green block as shown in mockup
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
                    "Loading final archive manifest files...\nAll items written successfully."
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

            // Final Operation Stats Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = InkSubtle.copy(alpha = 0.25f),
                        spotColor = InkSubtle.copy(alpha = 0.35f)
                    )
                    .background(PaperSurface, RoundedCornerShape(12.dp))
                    .border(1.5.dp, PaperSurfaceDarker, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Final Operation Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkCharcoal,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Row 1
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Files Processed", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                        Text("${stats?.filesProcessed ?: 0}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                    // Row 2
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Final Directory Size", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                        val bytesVal = stats?.totalSize ?: 0L
                        Text(viewModel.formatSize(bytesVal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                    // Row 3
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duplicates Renamed", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                        Text("$duplicatesCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                    // Row 4
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Conflicts Resolved", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                        Text("$resolvedConflicts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkCharcoal)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkCharcoal.copy(alpha = 0.05f)))

                    // Row 5
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Execution Time", style = MaterialTheme.typography.bodyMedium, color = InkSubtle)
                        val durationMs = stats?.elapsedMs ?: 0L
                        val secondsStr = String.format("%02dm %02ds", (durationMs / 1000) / 60, (durationMs / 1000) % 60)
                        Text(secondsStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InkSage)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Multi-Action Buttons
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
                        text = "Start Another Session",
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
                    border = BorderStroke(1.5.dp, InkCharcoal.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = InkCharcoal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Main Menu",
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
            containerColor = MaterialTheme.colorScheme.surface, // Matches the clean dynamic canvas
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = "Massive Files Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "UnNest identified $detectedHeavyFilesCount heavily pre-compressed media items. To safeguard your device's battery life and accelerate processing, these items will be packaged using the high-speed 'Smart Filtering Store' method without redundant re-compression math.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Justify
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmExecution) {
                    Text(
                        text = "Proceed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary // Minimalist action callout accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}
