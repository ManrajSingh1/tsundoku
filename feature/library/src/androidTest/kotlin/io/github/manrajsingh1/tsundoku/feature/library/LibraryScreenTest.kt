package io.github.manrajsingh1.tsundoku.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.Shelf
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import io.github.manrajsingh1.tsundoku.core.ui.theme.TsundokuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library screen driven through its semantics tree, which is what TalkBack reads and
 * acts on. If a book can be found, opened, selected, moved and removed here, it can be with
 * a screen reader.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** What the screen asked for, in order. */
    private val calls = ArrayList<String>()

    private fun book(
        id: Long,
        title: String,
        format: BookFormat = BookFormat.CBZ,
        volume: Double? = null,
    ) = Book(
        id = id,
        documentUri = "content://test/$id",
        contentHash = "hash$id",
        format = format,
        title = title,
        author = null,
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

    private val manga = ShelfWithBooks(
        Shelf(id = 1, name = "Manga", position = 0),
        listOf(book(1, "The Third Volume", volume = 3.0), book(2, "Quiet Shelf")),
    )
    private val novels = ShelfWithBooks(
        Shelf(id = 2, name = "Novels", position = 1),
        listOf(book(3, "A Test Novel", format = BookFormat.EPUB)),
    )

    /** Shows the screen, keeping edit mode and the selection the way the app's route does. */
    private fun show(shelves: List<ShelfWithBooks>, editing: Boolean = false) {
        compose.setContent {
            TsundokuTheme {
                var isEditing by remember { mutableStateOf(editing) }
                var selected by remember { mutableStateOf<Long?>(null) }
                val actions = remember {
                    LibraryActions(
                        onAddFiles = { calls += "add files" },
                        onAddFolder = { calls += "add folder" },
                        onAddFilesToShelf = { calls += "add files to shelf $it" },
                        onOpenBook = { calls += "open $it" },
                        onSetEditing = {
                            isEditing = it
                            selected = null
                        },
                        onSelectBook = { selected = it },
                        onMoveBook = { id, delta -> calls += "move $id by $delta" },
                        onMoveBookToNextShelf = { calls += "next shelf $it" },
                        onRemoveBook = { calls += "remove ${it.id}" },
                        onAddShelf = { calls += "add shelf" },
                        onRenameShelf = { id, name -> calls += "rename $id to $name" },
                        onDeleteShelf = { calls += "delete shelf ${it.id}" },
                        onSortShelf = { calls += "sort shelf ${it.id}" },
                    )
                }
                LibraryScreen(
                    uiState = LibraryUiState.Loaded(shelves),
                    editing = isEditing,
                    selectedBookId = selected,
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun everySpineIsAButtonThatSaysWhichBookItIsAndOpensIt() {
        show(listOf(manga, novels))

        compose.onNodeWithContentDescription("The Third Volume, volume 3, CBZ")
            .assertIsDisplayed()
            .assertHasClickAction()
        compose.onNodeWithContentDescription("A Test Novel, EPUB").assertIsDisplayed()

        compose.onNodeWithContentDescription("Quiet Shelf, CBZ").performClick()
        assertEquals(listOf("open 2"), calls)
    }

    @Test
    fun eachShelfIsAHeadingThatGivesItsNameAndCount() {
        show(listOf(manga, novels))

        compose.onNode(isHeading() and hasText("Manga") and hasText("2 books")).assertIsDisplayed()
        compose.onNode(isHeading() and hasText("Novels") and hasText("1 book")).assertIsDisplayed()
    }

    @Test
    fun anEmptyLibraryOffersBothWaysToAddBooks() {
        show(emptyList())

        compose.onNodeWithText("Your shelves are empty").assertIsDisplayed()
        compose.onNodeWithText("Add folder").performClick()
        compose.onNodeWithText("Add files").performClick()
        assertEquals(listOf("add folder", "add files"), calls)
    }

    @Test
    fun editModeIsEnteredFromTheMenuAndLeftWithDone() {
        show(listOf(manga))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Edit shelves").performClick()
        compose.onNodeWithText("Tap a book to move or remove it").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Add books").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun inEditModeATappedSpineIsSelectedAndTheBarMovesIt() {
        show(listOf(manga, novels), editing = true)
        val first = compose.onNodeWithContentDescription("The Third Volume, volume 3, CBZ")
        first.assertIsNotSelected()
        // Nothing to act on yet.
        compose.onNodeWithText("Move right").assertIsNotEnabled()

        first.performClick()

        first.assertIsSelected()
        assertTrue("Selecting must not open the book", calls.isEmpty())
        compose.onNodeWithText("1 of 2 · Manga").assertIsDisplayed()
        // The first book on a shelf cannot go further left.
        compose.onNodeWithText("Move left").assertIsNotEnabled()
        compose.onNodeWithText("Move right").assertIsEnabled().performClick()
        compose.onNodeWithText("Next shelf").assertIsEnabled().performClick()
        assertEquals(listOf("move 1 by 1", "next shelf 1"), calls)

        // Tapping it again puts it down.
        first.performClick()
        first.assertIsNotSelected()
    }

    @Test
    fun withOneShelfThereIsNoNextShelfAndTheShelfCannotBeDeleted() {
        show(listOf(manga), editing = true)
        compose.onNodeWithContentDescription("Quiet Shelf, CBZ").performClick()

        compose.onNodeWithText("Next shelf").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Delete shelf Manga").assertIsNotEnabled()
    }

    @Test
    fun aSpineOffersItsMovesAsScreenReaderActions() {
        show(listOf(manga, novels))

        val first = compose.onNodeWithContentDescription("The Third Volume, volume 3, CBZ").fetchSemanticsNode()
        val firstActions = first.config[SemanticsActions.CustomActions]
        // No "Move left": it is already first on its shelf.
        assertEquals(
            listOf("Move right", "Move to next shelf", "Remove from library"),
            firstActions.map { it.label },
        )
        val last = compose.onNodeWithContentDescription("Quiet Shelf, CBZ").fetchSemanticsNode()
        assertEquals(
            listOf("Move left", "Move to next shelf", "Remove from library"),
            last.config[SemanticsActions.CustomActions].map { it.label },
        )

        compose.runOnUiThread {
            firstActions.first { it.label == "Move right" }.action()
            firstActions.first { it.label == "Move to next shelf" }.action()
        }
        assertEquals(listOf("move 1 by 1", "next shelf 1"), calls)
    }

    @Test
    fun removingABookIsAskedAboutFirstAndSaysTheFileIsKept() {
        show(listOf(manga), editing = true)
        compose.onNodeWithContentDescription("Quiet Shelf, CBZ").performClick()

        compose.onNodeWithText("Remove").performClick()

        compose.onNodeWithText("Remove from library?").assertIsDisplayed()
        compose.onNodeWithText("The file itself is not deleted.", substring = true).assertIsDisplayed()
        assertTrue("Nothing is removed until the user confirms", calls.isEmpty())

        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("remove 2"), calls)
    }

    @Test
    fun cancellingARemovalRemovesNothing() {
        show(listOf(manga), editing = true)
        compose.onNodeWithContentDescription("Quiet Shelf, CBZ").performClick()
        compose.onNodeWithText("Remove").performClick()

        compose.onNodeWithText("Cancel").performClick()

        assertTrue(calls.isEmpty())
        compose.onNodeWithContentDescription("Quiet Shelf, CBZ").assertIsDisplayed()
    }

    @Test
    fun deletingAShelfSaysWhereItsBooksGoBeforeItHappens() {
        show(listOf(manga, novels), editing = true)

        compose.onNodeWithTag(SHELVES_TEST_TAG)
            .performScrollToNode(hasText("Novels") and hasSetTextAction())
        compose.onNodeWithContentDescription("Delete shelf Novels").performClick()

        compose.onNodeWithText("Delete Novels?").assertIsDisplayed()
        compose.onNodeWithText("Its 1 book moves to Manga. No files are deleted.").assertIsDisplayed()
        assertTrue(calls.isEmpty())

        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete shelf 2"), calls)
    }

    @Test
    fun aShelfIsRenamedByTypingInItsNameField() {
        show(listOf(manga), editing = true)

        compose.onNode(hasSetTextAction() and hasText("Manga")).performTextReplacement("Shonen")
        compose.onNode(hasSetTextAction() and hasText("Shonen")).performImeAction()

        assertEquals(listOf("rename 1 to Shonen"), calls)
    }

    @Test
    fun aNameFieldLeftEmptyKeepsTheShelfsName() {
        show(listOf(manga), editing = true)

        compose.onNode(hasSetTextAction() and hasText("Manga")).performTextReplacement("   ")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertTrue(calls.isEmpty())
        compose.onNode(hasSetTextAction() and hasText("Manga")).assertIsDisplayed()
    }

    @Test
    fun editModeOffersASlotOnEachShelfAndAButtonForANewShelf() {
        show(listOf(manga), editing = true)

        compose.onNodeWithContentDescription("Add files to Manga").assertHasClickAction().performClick()
        compose.onNodeWithTag(SHELVES_TEST_TAG).performScrollToNode(hasText("Add shelf"))
        compose.onNodeWithText("Add shelf").performClick()

        assertEquals(listOf("add files to shelf 1", "add shelf"), calls)
    }

    @Test
    fun aShelfOutOfOrderOffersToBeSortedAndOneInOrderDoesNot() {
        show(listOf(manga, novels), editing = true)

        compose.onNodeWithContentDescription("Sort Novels A to Z").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Sort Manga A to Z").assertIsEnabled().performClick()

        assertEquals(listOf("sort shelf 1"), calls)
    }

    @Test
    fun aFingerRunAlongTheShelfOpensTheBookItLeavesFrom() {
        show(listOf(manga, novels))

        // Down on the first spine, along to the second, and off.
        val first = compose.onNodeWithContentDescription("The Third Volume, volume 3, CBZ").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Quiet Shelf, CBZ").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(first.center)
            val steps = 12
            for (step in 1..steps) {
                moveTo(first.center + (second.center - first.center) * (step / steps.toFloat()))
            }
            up()
        }

        assertEquals(listOf("open 2"), calls)
    }

    @Test
    fun aFingerThatWandersOffTheShelfOpensNothing() {
        show(listOf(manga, novels))

        val first = compose.onNodeWithContentDescription("The Third Volume, volume 3, CBZ").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Quiet Shelf, CBZ").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(first.center)
            moveTo((first.center + second.center) / 2f)
            moveTo(second.center)
            // Well above the shelf: the book is let go.
            moveTo(second.center.copy(y = second.top - 300f))
            up()
        }

        assertEquals(emptyList<String>(), calls)
    }
}
