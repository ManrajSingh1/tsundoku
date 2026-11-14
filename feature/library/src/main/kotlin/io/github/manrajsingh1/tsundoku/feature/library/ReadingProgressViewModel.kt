package io.github.manrajsingh1.tsundoku.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.ReadingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** How far through each book the reader is, for the open shelf to show. */
@HiltViewModel
class ReadingProgressViewModel @Inject constructor(
    readingRepository: ReadingRepository,
) : ViewModel() {

    /** Book id to a fraction from 0 to 1. Books never opened are absent. */
    val progress: StateFlow<Map<Long, Double>> = readingRepository.observeProgress().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )
}
