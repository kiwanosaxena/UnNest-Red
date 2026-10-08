package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UnNest:Red", appName)
    }

    @Test
    fun `category classification works correctly`() {
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("photo.JPG"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("image.png"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("shot.heic"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("clip.mp4"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("movie.MKV"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("song.mp3"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("voice.wav"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("report.pdf"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("sheet.xlsx"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("backup.zip"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("archive.7z"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("script.py"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("binary.bin"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("unknown"))
    }

    @Test
    fun `conflict renaming produces unique names with auto-sequence`() {
        val files = listOf(
            ScannedFileInfo(
                name = "invoice.pdf",
                parentName = "January",
                size = 100L,
                uri = Uri.parse("content://dummy/1"),
                relativePath = "January"
            ),
            ScannedFileInfo(
                name = "invoice.pdf",
                parentName = "February",
                size = 200L,
                uri = Uri.parse("content://dummy/2"),
                relativePath = "February"
            ),
            ScannedFileInfo(
                name = "invoice.pdf",
                parentName = "March",
                size = 300L,
                uri = Uri.parse("content://dummy/3"),
                relativePath = "March"
            )
        )

        val uniqueNames = UnNestViewModel.resolveFileNames(files, strategy = 1)
        assertEquals(3, uniqueNames.size)
        // Ensure all generated destination names are distinct
        val distinctCount = uniqueNames.values.distinct().size
        assertEquals(3, distinctCount)
        assertEquals("invoice.pdf", uniqueNames[files[0]])
        assertEquals("invoice_1.pdf", uniqueNames[files[1]])
        assertEquals("invoice_2.pdf", uniqueNames[files[2]])
    }

    @Test
    fun `conflict renaming produces unique names with parent-prefix`() {
        val files = listOf(
            ScannedFileInfo(
                name = "invoice.pdf",
                parentName = "January",
                size = 100L,
                uri = Uri.parse("content://dummy/1"),
                relativePath = "January"
            ),
            ScannedFileInfo(
                name = "invoice.pdf",
                parentName = "February",
                size = 200L,
                uri = Uri.parse("content://dummy/2"),
                relativePath = "February"
            )
        )

        val uniqueNames = UnNestViewModel.resolveFileNames(files, strategy = 2)
        assertEquals(2, uniqueNames.size)
        val distinctCount = uniqueNames.values.distinct().size
        assertEquals(2, distinctCount)
        assertEquals("invoice_(January).pdf", uniqueNames[files[0]])
        assertEquals("invoice_(February).pdf", uniqueNames[files[1]])
    }

    @Test
    fun `selected count and size calculation without and with filters`() {
        val files = listOf(
            ScannedFileInfo(
                name = "pic.jpg",
                parentName = "Trip",
                size = 1000L,
                uri = Uri.parse("content://dummy/1"),
                relativePath = "Trip",
                lastModified = 1000L
            ),
            ScannedFileInfo(
                name = "doc.pdf",
                parentName = "Work",
                size = 2000L,
                uri = Uri.parse("content://dummy/2"),
                relativePath = "Work",
                lastModified = 2000L
            ),
            ScannedFileInfo(
                name = "song.mp3",
                parentName = "Trip",
                size = 5000L,
                uri = Uri.parse("content://dummy/3"),
                relativePath = "Trip",
                lastModified = 5000L
            )
        )

        // 1. All categories and folders selected, no size/date filters
        val allCats = FileCategory.entries.toSet()
        val allFolders = setOf("Trip", "Work")
        val (countAll, bytesAll) = UnNestViewModel.calculateSelectionStats(files, allCats, allFolders)
        assertEquals(3, countAll)
        assertEquals(8000L, bytesAll)

        // 2. Filter out audio category
        val noAudio = setOf(FileCategory.PHOTOS, FileCategory.DOCUMENTS)
        val (countNoAudio, bytesNoAudio) = UnNestViewModel.calculateSelectionStats(files, noAudio, allFolders)
        assertEquals(2, countNoAudio)
        assertEquals(3000L, bytesNoAudio)

        // 3. Filter out Work folder
        val onlyTripFolder = setOf("Trip")
        val (countTrip, bytesTrip) = UnNestViewModel.calculateSelectionStats(files, allCats, onlyTripFolder)
        assertEquals(2, countTrip)
        assertEquals(6000L, bytesTrip)

        // 4. Min size filter >= 1500 bytes
        val (countMinSize, bytesMinSize) = UnNestViewModel.calculateSelectionStats(
            files, allCats, allFolders,
            minSizeBytes = 1500L
        )
        assertEquals(2, countMinSize)
        assertEquals(7000L, bytesMinSize)

        // 5. Max size filter <= 3000 bytes
        val (countMaxSize, bytesMaxSize) = UnNestViewModel.calculateSelectionStats(
            files, allCats, allFolders,
            maxSizeBytes = 3000L
        )
        assertEquals(2, countMaxSize)
        assertEquals(3000L, bytesMaxSize)

        // 6. Date range filter
        val (countDateRange, bytesDateRange) = UnNestViewModel.calculateSelectionStats(
            files, allCats, allFolders,
            minDateMillis = 1500L,
            maxDateMillis = 4000L
        )
        assertEquals(1, countDateRange)
        assertEquals(2000L, bytesDateRange)
    }
}
