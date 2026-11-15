package io.github.manrajsingh1.tsundoku.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.ReadingRepository
import io.github.manrajsingh1.tsundoku.core.data.SettingsRepository
import io.github.manrajsingh1.tsundoku.core.data.reading.PublicationSource
import io.github.manrajsingh1.tsundoku.core.model.Book
import io.github.manrajsingh1.tsundoku.core.model.BookFormat
import io.github.manrajsingh1.tsundoku.core.model.ColourTheme
import io.github.manrajsingh1.tsundoku.core.model.PageTheme
import io.github.manrajsingh1.tsundoku.core.model.ReadingDirection
import io.github.manrajsingh1.tsundoku.core.model.ReadingProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

/** The book being read. */
sealed interface ReaderUiState {

    data object Loading : ReaderUiState

    /** The file has gone or cannot be read. [title] is null if the book itself has gone. */
    data class Failed(val title: String?) : ReaderUiState

    /**
     * @param pageCount pages in a comic or PDF; Readium positions in an EPUB. Zero if unknown.
     */
    class Ready(val book: Book, val publication: Publication, val pageCount: Int) : ReaderUiState
}

/**
 * Where the reader is, for the controls to show.
 *
 * @param page counts from zero; null in an EPUB, which has no fixed pages.
 */
data class ReaderLocation(val page: Int? = null, val pageCount: Int = 0, val fraction: Double = 0.0)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reading: ReadingRepository,
    private val publications: PublicationSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val bookId: Long = savedStateHandle.get<Long>(ReaderActivity.EXTRA_BOOK_ID) ?: -1L
    private val restart: Boolean = savedStateHandle.get<Boolean>(ReaderActivity.EXTRA_RESTART) ?: false

    // Settings cannot change while a book is open, so one reading of them is enough.
    private val preferences = settings.preferencesNow()

    /** The colours the bars wear, the same as the rest of the app. */
    val colourTheme: ColourTheme = preferences.colourTheme

    /** The page colour chosen for EPUBs. */
    val epubPageTheme: PageTheme = preferences.epubPageTheme

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _location = MutableStateFlow(ReaderLocation())
    val location: StateFlow<ReaderLocation> = _location.asStateFlow()

    private val _rightToLeft = MutableStateFlow(true)
    val rightToLeft: StateFlow<Boolean> = _rightToLeft.asStateFlow()

    private val _fontSize = MutableStateFlow(1.0)
    val fontSize: StateFlow<Double> = _fontSize.asStateFlow()

    private val _pageRequests = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** Pages asked for from the controls, for the comic pager to go to. */
    val pageRequests: SharedFlow<Int> = _pageRequests.asSharedFlow()

    /**
     * The latest position, kept so the page can be rebuilt in place (when the theme changes,
     * say) without a round trip through the database.
     */
    var position: String? = null
        private set

    private var publication: Publication? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = reading.findBook(bookId)
        val opened = book?.let { publications.open(it) }
        if (book == null || opened == null) {
            _state.value = ReaderUiState.Failed(book?.title)
            return
        }
        publication = opened
        position = if (restart) null else reading.position(bookId)
        val pageCount = when (book.format) {
            BookFormat.CBZ -> opened.readingOrder.size
            else -> runCatching { opened.positions().size }.getOrDefault(0)
        }
        _rightToLeft.value = reading.readingDirection(book) == ReadingDirection.RIGHT_TO_LEFT
        _fontSize.value = settings.epubFontSize.first()
        if (book.format == BookFormat.CBZ) {
            val page = ReadingProgress.pageOf(position, pageCount)
            _location.value = ReaderLocation(page, pageCount, ReadingProgress.ofPage(page, pageCount))
        }
        reading.markOpened(bookId)
        _state.value = ReaderUiState.Ready(book, opened, pageCount)
    }

    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    fun showControls() {
        _controlsVisible.value = true
    }

    /** The comic pager has come to rest on a page. */
    fun onComicPage(page: Int) {
        val ready = _state.value as? ReaderUiState.Ready ?: return
        val fraction = ReadingProgress.ofPage(page, ready.pageCount)
        position = page.toString()
        _location.value = ReaderLocation(page, ready.pageCount, fraction)
        reading.recordProgress(bookId, page.toString(), fraction)
    }

    /** An EPUB or PDF navigator has moved. [locatorJson] is Readium's locator, serialised. */
    fun onLocator(locatorJson: String, positionNumber: Int?, totalProgression: Double?) {
        val ready = _state.value as? ReaderUiState.Ready ?: return
        val paged = ready.book.format == BookFormat.PDF
        val fraction = ReaderMath.locatorFraction(paged, positionNumber, ready.pageCount, totalProgression)
        position = locatorJson
        _location.value = ReaderLocation(
            page = if (paged) positionNumber?.minus(1) else null,
            pageCount = ready.pageCount,
            fraction = fraction,
        )
        reading.recordProgress(bookId, locatorJson, fraction)
    }

    fun seekToPage(page: Int) {
        _pageRequests.tryEmit(page)
    }

    /** Applies to this comic and, if it is part of a series, to every volume of it. */
    fun setRightToLeft(rightToLeft: Boolean) {
        val ready = _state.value as? ReaderUiState.Ready ?: return
        _rightToLeft.value = rightToLeft
        val direction = if (rightToLeft) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT
        viewModelScope.launch { reading.setReadingDirection(ready.book, direction) }
    }

    fun changeFontSize(steps: Int) {
        val next = (_fontSize.value + steps * FONT_STEP)
            .coerceIn(SettingsRepository.MIN_FONT_SIZE, SettingsRepository.MAX_FONT_SIZE)
        _fontSize.value = next
        viewModelScope.launch { settings.setEpubFontSize(next) }
    }

    suspend fun comicPage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val open = publication ?: return null
        return publications.comicPage(open, index, maxWidth, maxHeight)
    }

    override fun onCleared() {
        publication?.close()
        publication = null
    }

    private companion object {
        const val FONT_STEP = 0.1
    }
}
