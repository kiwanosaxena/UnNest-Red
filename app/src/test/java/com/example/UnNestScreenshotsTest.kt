package com.example

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class UnNestScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createPreviewViewModel(): UnNestViewModel {
        val vm = UnNestViewModel()
        val summary = ScanSummary(
            totalFiles = 42,
            totalFolders = 4,
            maxDepth = 3,
            totalSize = 1024L * 1024L * 18L,
            skippedJunkCount = 2,
            categoryCounts = mapOf(
                FileCategory.PHOTOS to 20,
                FileCategory.DOCUMENTS to 15,
                FileCategory.ARCHIVES to 7
            ),
            categorySizes = mapOf(
                FileCategory.PHOTOS to 1024L * 1024L * 10L,
                FileCategory.DOCUMENTS to 1024L * 1024L * 5L,
                FileCategory.ARCHIVES to 1024L * 1024L * 3L
            ),
            subfolders = listOf("Documents/2024", "Pictures/Vacation", "Downloads/Old")
        )
        vm.setScanSummaryForTesting(summary)
        return vm
    }

    private fun createWorkbenchConflictViewModel(): UnNestViewModel {
        val vm = UnNestViewModel()
        val conflict = ProcessStatus.Conflict(
            conflictedName = "report.pdf",
            duplicates = listOf(
                ScannedFileInfo("report.pdf", "Finance", 1500L, Uri.parse("content://dummy/1"), "2023/Finance"),
                ScannedFileInfo("report.pdf", "HR", 1800L, Uri.parse("content://dummy/2"), "2024/HR")
            )
        )
        vm.setProcessStatusForTesting(conflict)
        return vm
    }

    private fun createWorkbenchErrorViewModel(): UnNestViewModel {
        val vm = UnNestViewModel()
        val error = ProcessStatus.Error("Failed to write to destination directory: Permission denied.")
        vm.setProcessStatusForTesting(error)
        return vm
    }

    private fun createSuccessViewModel(): UnNestViewModel {
        val vm = UnNestViewModel()
        val stats = SessionStats(
            filesProcessed = 42,
            totalSize = 1024L * 1024L * 18L,
            elapsedMs = 4500L,
            destName = "Flattened_Output",
            filesSkipped = 1,
            skippedFiles = listOf(SkippedFileInfo("thumbs.db", "Ignored system file"))
        )
        vm.setSessionStatsForTesting(stats)
        vm.addLogForTesting("Flattened invoice.pdf -> invoice_1.pdf")
        vm.addLogForTesting("Operation completed successfully.")
        return vm
    }

    // --- Hub Screen ---
    @Test
    fun hub_screen_light() {
        val vm = UnNestViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                UnNestHubScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/hub_light.png")
    }

    @Test
    fun hub_screen_dark() {
        val vm = UnNestViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                UnNestHubScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/hub_dark.png")
    }

    // --- Preview Screen ---
    @Test
    fun preview_screen_light() {
        val vm = createPreviewViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                UnNestPreviewScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/preview_light.png")
    }

    @Test
    fun preview_screen_dark() {
        val vm = createPreviewViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                UnNestPreviewScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/preview_dark.png")
    }

    // --- Workbench Screen (Conflict State) ---
    @Test
    fun workbench_conflict_light() {
        val vm = createWorkbenchConflictViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                UnNestWorkbenchScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/workbench_conflict_light.png")
    }

    @Test
    fun workbench_conflict_dark() {
        val vm = createWorkbenchConflictViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                UnNestWorkbenchScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/workbench_conflict_dark.png")
    }

    // --- Workbench Screen (Error State) ---
    @Test
    fun workbench_error_light() {
        val vm = createWorkbenchErrorViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                UnNestWorkbenchScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/workbench_error_light.png")
    }

    @Test
    fun workbench_error_dark() {
        val vm = createWorkbenchErrorViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                UnNestWorkbenchScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/workbench_error_dark.png")
    }

    // --- Success Screen ---
    @Test
    fun success_screen_light() {
        val vm = createSuccessViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                UnNestSuccessScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/success_light.png")
    }

    @Test
    fun success_screen_dark() {
        val vm = createSuccessViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                UnNestSuccessScreen(viewModel = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/success_dark.png")
    }
}
