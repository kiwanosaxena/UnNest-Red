package com.example

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewAndConflictRobolectricTest {

    @Test
    fun testCategoryClassification() {
        // Photos
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("image.jpg"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("photo.JPEG"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("pic.png"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("camera.heic"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("banner.webp"))
        assertEquals(FileCategory.PHOTOS, FileCategory.fromFileName("animation.gif"))

        // Videos
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("movie.mp4"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("clip.mov"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("record.mkv"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("old.3gp"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("stream.webm"))

        // Audio
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("song.mp3"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("voice.m4a"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("track.wav"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("audio.aac"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("music.ogg"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("podcast.opus"))

        // Documents
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("paper.pdf"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("resume.doc"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("notes.docx"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("sheet.xls"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("table.xlsx"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("slides.ppt"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("deck.pptx"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("readme.txt"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("data.csv"))

        // Archives
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("backup.zip"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("files.rar"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("bundle.7z"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("archive.tar.gz"))

        // Other
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("program.exe"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("script.sh"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("noextension"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("hidden."))
    }

    @Test
    fun testConflictRenamingAutoSequenceGuaranteesUniqueness() {
        val dummyUri = Uri.parse("content://dummy/file")
        val files = listOf(
            ScannedFileInfo("report.pdf", "Finance", 1000L, dummyUri, "2024/Finance"),
            ScannedFileInfo("report.pdf", "HR", 2000L, dummyUri, "2024/HR"),
            ScannedFileInfo("report.pdf", "Marketing", 3000L, dummyUri, "2024/Marketing"),
            ScannedFileInfo("unique.txt", "Notes", 500L, dummyUri, "Notes")
        )

        val resolved = UnNestViewModel.resolveFileNames(files, strategy = 1)

        // All resolved names must be strictly unique (case-insensitive)
        val resolvedNames = files.map { resolved[it] ?: it.name }
        val uniqueSet = resolvedNames.map { it.lowercase() }.toSet()
        assertEquals("All resolved file names must be unique", files.size, uniqueSet.size)

        // Verify first report remains report.pdf, and subsequent ones get _1, _2
        assertEquals("report.pdf", resolved[files[0]])
        assertEquals("report_1.pdf", resolved[files[1]])
        assertEquals("report_2.pdf", resolved[files[2]])
        assertEquals("unique.txt", resolved[files[3]])
    }

    @Test
    fun testConflictRenamingParentPrefixWithGrandparentFallback() {
        val dummyUri = Uri.parse("content://dummy/file")
        val files = listOf(
            // Two files with identical name AND identical parent folder name, but different grandparent
            ScannedFileInfo("invoice.pdf", "June", 1500L, dummyUri, "2023/June"),
            ScannedFileInfo("invoice.pdf", "June", 1800L, dummyUri, "2024/June"),
            // Third file with different parent name
            ScannedFileInfo("invoice.pdf", "July", 2100L, dummyUri, "2024/July")
        )

        val resolved = UnNestViewModel.resolveFileNames(files, strategy = 2)

        val resolvedNames = files.map { resolved[it] ?: it.name }
        val uniqueSet = resolvedNames.map { it.lowercase() }.toSet()
        assertEquals("All resolved file names must be unique", files.size, uniqueSet.size)

        // Verify grandparent fallback was applied when parents clashed
        assertTrue(
            "Grandparent should be included when parent names clash: ${resolved[files[0]]}",
            resolved[files[0]]!!.contains("2023-June") || resolved[files[0]]!!.contains("June")
        )
        assertTrue(
            "Grandparent should be included when parent names clash: ${resolved[files[1]]}",
            resolved[files[1]]!!.contains("2024-June") || resolved[files[1]]!!.contains("June")
        )
    }

    @Test
    fun testConflictRenamingWithExistingDestinationFilesCollision() {
        val dummyUri = Uri.parse("content://dummy/file")
        val files = listOf(
            ScannedFileInfo("photo.jpg", "Summer", 1000L, dummyUri, "Summer"),
            ScannedFileInfo("photo.jpg", "Winter", 2000L, dummyUri, "Winter")
        )
        val existingDestinationNames = setOf("photo.jpg", "photo_1.jpg")

        val resolved = UnNestViewModel.resolveFileNames(files, strategy = 1, existingDestinationNames)

        val resolvedNames = files.map { resolved[it] ?: it.name }
        val uniqueSet = (existingDestinationNames + resolvedNames).map { it.lowercase() }.toSet()
        assertEquals(
            "Destination files and resolved files combined must be unique",
            existingDestinationNames.size + files.size,
            uniqueSet.size
        )
    }

    @Test
    fun testSelectedCountAndSizeCalculation() {
        val dummyUri = Uri.parse("content://dummy/file")
        val allFiles = listOf(
            ScannedFileInfo("pic1.jpg", "Album", 1000L, dummyUri, "Album"), // Photos, Album
            ScannedFileInfo("pic2.png", "Album", 2000L, dummyUri, "Album"), // Photos, Album
            ScannedFileInfo("clip.mp4", "Videos", 10000L, dummyUri, "Videos"), // Videos, Videos
            ScannedFileInfo("doc.pdf", "Documents", 5000L, dummyUri, "Documents"), // Documents, Documents
            ScannedFileInfo("archive.zip", "Backup", 8000L, dummyUri, "Backup") // Archives, Backup
        )

        // When all categories and all folders are selected
        val allCats = FileCategory.entries.toSet()
        val allFolds = setOf("Album", "Videos", "Documents", "Backup")
        val (count1, size1) = UnNestViewModel.calculateSelectionStats(allFiles, allCats, allFolds)
        assertEquals(5, count1)
        assertEquals(26000L, size1)

        // When only Photos category is selected
        val onlyPhotos = setOf(FileCategory.PHOTOS)
        val (count2, size2) = UnNestViewModel.calculateSelectionStats(allFiles, onlyPhotos, allFolds)
        assertEquals(2, count2)
        assertEquals(3000L, size2)

        // When Album folder is deselected
        val withoutAlbum = setOf("Videos", "Documents", "Backup")
        val (count3, size3) = UnNestViewModel.calculateSelectionStats(allFiles, allCats, withoutAlbum)
        assertEquals(3, count3)
        assertEquals(23000L, size3)
    }

    @Test
    fun testSpaceEstimationCalculation() {
        val dummyUri = Uri.parse("content://dummy/file")
        val files = listOf(
            ScannedFileInfo("pic.jpg", "P", 10000L, dummyUri, "P"), // Pre-compressed (0.95 -> 9500)
            ScannedFileInfo("text.txt", "T", 10000L, dummyUri, "T") // Non-compressed (0.60 -> 6000)
        )

        // DIRECT mode: exact size
        val directSpace = UnNestViewModel.estimateRequiredSpace(files, FlattenMode.DIRECT)
        assertEquals(20000L, directSpace)

        // ZIP mode: 9500 + 6000 = 15500
        val zipSpace = UnNestViewModel.estimateRequiredSpace(files, FlattenMode.ZIP)
        assertEquals(15500L, zipSpace)
    }
}
