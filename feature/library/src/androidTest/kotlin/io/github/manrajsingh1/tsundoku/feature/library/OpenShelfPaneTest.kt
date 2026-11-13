package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenShelfPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = ArrayList<String>()

    private fun book(id: Long, title: String, author: String? = null, volume: Double? = null) = Book(
        id = id,
        documentUri = "content://test/$id",
        contentHash = "hash$id",
        format = BookFormat.CBZ,
        title = title,
        author = author,
        series = null,
        volume = volume,
        pageCount = 180,
        fileSize = 1L,
        coverPath = null,
        spineColor = 0xFF2B3A55.toInt(),
        readingDirection = ReadingDirection.AUTO,
        addedAt = 0L,
        lastOpenedAt = null,
        isMissing = false,
    )

    private val shelf = ShelfWithBooks(
        Shelf(id = 1, name = "Manga", position = 0),
        listOf(book(1, "The Third Volume", author = "A. Author", volume = 3.0), book(2, "Quiet Shelf")),
    )

    private fun show(place: BookPlace?, withBack: Boolean = true) {
        compose.setContent {
            TsundokuTheme {
                OpenShelfPane(
                    place = place,
                    onShowBook = { calls += "show $it" },
                    onBack = if (withBack) ({ calls += "back" }) else null,
                )
            }
        }
    }

    @Test
    fun theOpenBookIsNamedWithItsPlaceOnTheShelf() {
        show(BookPlace(shelf, 0))

        compose.onNodeWithText("Manga").assertIsDisplayed()
        compose.onNodeWithText("A. Author · vol. 3 · CBZ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Book 1 of 2", substring = true).assertIsDisplayed()
    }

    @Test
    fun theArrowsStepToTheNeighboursAndStopAtTheEnds() {
        show(BookPlace(shelf, 0))

        compose.onNodeWithContentDescription("Previous book").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next book").assertIsEnabled().performClick()

        assertEquals(listOf("show 2"), calls)
    }

    @Test
    fun backReturnsToTheLibraryWhenThePaneCoversIt() {
        show(BookPlace(shelf, 1))

        compose.onNodeWithContentDescription("Back to library").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun besideTheLibraryThereIsNoBackAndAnEmptyPaneSaysWhatToDo() {
        show(place = null, withBack = false)

        compose.onNodeWithContentDescription("Back to library").assertDoesNotExist()
        compose.onNodeWithText("Pick a book").assertIsDisplayed()
    }
}
