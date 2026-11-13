package io.github.manrajsingh1.tsundoku.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.LibraryRepository
import io.github.manrajsingh1.tsundoku.core.model.ShelfWithBooks
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the library screen draws, as one immutable value. */
sealed interface LibraryUiState {

    /** The first read of the database has not come back yet. */
    data object Loading : LibraryUiState

    data class Loaded(val shelves: List<ShelfWithBooks>) : LibraryUiState {
        val isEmpty: Boolean get() = shelves.all { it.books.isEmpty() }
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = libraryRepository.observeShelves()
        .map<List<ShelfWithBooks>, LibraryUiState> { LibraryUiState.Loaded(it) }
        .stateIn(
            scope = viewModelScope,
            // Keeps the database query alive across a rotation, but not while the app is away.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )
}
