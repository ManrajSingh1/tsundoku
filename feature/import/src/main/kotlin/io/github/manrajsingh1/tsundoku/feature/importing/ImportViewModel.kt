package io.github.manrajsingh1.tsundoku.feature.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.manrajsingh1.tsundoku.core.data.importing.ImportRepository
import io.github.manrajsingh1.tsundoku.core.model.ImportStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importRepository: ImportRepository,
) : ViewModel() {

    val status: StateFlow<ImportStatus> = importRepository.observeStatus().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportStatus(),
    )

    fun onDocumentsPicked(uris: List<Uri>) {
        viewModelScope.launch { importRepository.importDocuments(uris) }
    }

    fun onFolderPicked(tree: Uri) {
        viewModelScope.launch { importRepository.importFolder(tree) }
    }

    fun onDismissResult() {
        viewModelScope.launch { importRepository.dismissFinished() }
    }
}
