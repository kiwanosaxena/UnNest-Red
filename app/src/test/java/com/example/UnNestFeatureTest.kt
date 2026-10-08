package com.example

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnNestFeatureTest {

    @Test
    fun testCategoryClassification() {
        // Photos
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("photo.jpg"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("image.PNG"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("sample.HEIC"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("graphic.webp"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("anim.gif"))

        // Videos
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("clip.mp4"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("movie.mov"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("recording.mkv"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("stream.webm"))

        // Audio
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("song.mp3"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("track.m4a"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("voice.wav"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("podcast.opus"))

        // Documents
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("invoice.pdf"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("report.docx"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("sheet.xlsx"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("notes.txt"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("data.csv"))

        // Archives
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("bundle.zip"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("package.tar.gz"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("backup.7z"))

        // Other
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("unknown.bin"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("no_extension"))
    }

    @Test
    fun testConflictRenamingAutoSequenceProducesUniqueNames() {
        val dummyUri = Uri.parse("content://dummy/1")
        val files = listOf(
            ScannedFileInfo(name = "file.txt", parentName = "FolderA", size = 100L, uri = dummyUri, relativePath = "FolderA"),
            ScannedFileInfo(name = "file.txt", parentName = "FolderB", size = 200L, uri = dummyUri, relativePath = "FolderB"),
            ScannedFileInfo(name = "file.txt", parentName = "FolderC", size = 300L, uri = dummyUri, relativePath = "FolderC")
        )

        val resolved = UnNestViewModel.resolveFileNames(files, strategy = 1)
        val names = files.map { resolved[it] ?: it.name }

        assertEquals(3, names.distinctBy { it.lowercase() }.size)
        assertTrue(names.contains("file.txt"))
        assertTrue(names.contains("file_1.txt"))
        assertTrue(names.contains("file_2.txt"))
    }

    @Test
    fun testConflictRenamingParentPrefixProducesUniqueNames() {
        val dummyUri = Uri.parse("content://dummy/1")
        val files = listOf(
            ScannedFileInfo(name = "invoice.pdf", parentName = "June", size = 100L, uri = dummyUri, relativePath = "June"),
            ScannedFileInfo(name = "invoice.pdf", parentName = "July", size = 200L, uri = dummyUri, relativePath = "July")
        )

        val resolved = UnNestViewModel.resolveFileNames(files, strategy = 2)
        val names = files.map { resolved[it] ?: it.name }

        assertEquals(2, names.distinctBy { it.lowercase() }.size)
        assertTrue(names.any { it.contains("June") })
        assertTrue(names.any { it.contains("July") })
    }

    @Test
    fun testSelectedStatsCalculationWithSizeAndDateFilters() {
        val dummyUri = Uri.parse("content://dummy/1")
        val f1 = ScannedFileInfo(name = "photo.jpg", parentName = "P", size = 1_000_000L, uri = dummyUri, relativePath = "Photos", lastModified = 1000L)
        val f2 = ScannedFileInfo(name = "video.mp4", parentName = "V", size = 5_000_000L, uri = dummyUri, relativePath = "Videos", lastModified = 2000L)
        val f3 = ScannedFileInfo(name = "doc.pdf", parentName = "D", size = 500_000L, uri = dummyUri, relativePath = "Docs", lastModified = 3000L)

        val allFiles = listOf(f1, f2, f3)
        val allCats = FileCategory.entries.toSet()
        val allFolders = setOf("Photos", "Videos", "Docs")

        // 1. All selected without filters
        val (countAll, sizeAll) = UnNestViewModel.calculateSelectionStats(allFiles, allCats, allFolders)
        assertEquals(3, countAll)
        assertEquals(6_500_000L, sizeAll)

        // 2. Minimum size filter (>= 1,000,000 bytes)
        val (countMin, sizeMin) = UnNestViewModel.calculateSelectionStats(
            allFiles, allCats, allFolders,
            minSizeBytes = 1_000_000L
        )
        assertEquals(2, countMin)
        assertEquals(6_000_000L, sizeMin)

        // 3. Maximum size filter (<= 2,000,000 bytes)
        val (countMax, sizeMax) = UnNestViewModel.calculateSelectionStats(
            allFiles, allCats, allFolders,
            maxSizeBytes = 2_000_000L
        )
        assertEquals(2, countMax)
        assertEquals(1_500_000L, sizeMax)

        // 4. Date range filter (1500L to 3500L)
        val (countDate, sizeDate) = UnNestViewModel.calculateSelectionStats(
            allFiles, allCats, allFolders,
            minDateMillis = 1500L,
            maxDateMillis = 3500L
        )
        assertEquals(2, countDate)
        assertEquals(5_500_000L, sizeDate)
    }

    @Test
    fun testCsvReportEscaping() {
        assertEquals("normal.txt", ExtractionWorker.escapeCsv("normal.txt"))
        assertEquals("\"name,with,comma.txt\"", ExtractionWorker.escapeCsv("name,with,comma.txt"))
        assertEquals("\"name\"\"with\"\"quote.txt\"", ExtractionWorker.escapeCsv("name\"with\"quote.txt"))
    }
}
