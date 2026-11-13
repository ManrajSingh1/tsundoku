package io.github.manrajsingh1.tsundoku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.manrajsingh1.tsundoku.core.data.importing.ImportRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Staging builds only: imports every file in this app's own `files/import` folder, then
 * opens the library. It goes through the same repository, queue and background worker as a
 * pick from the library screen, so CI exercises the real import path in a minified build.
 */
@AndroidEntryPoint
class StagingImportActivity : ComponentActivity() {

    @Inject
    lateinit var importRepository: ImportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = getExternalFilesDir("import")?.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        lifecycleScope.launch {
            importRepository.importDocuments(files.map { Uri.fromFile(it) })
            startActivity(Intent(this@StagingImportActivity, MainActivity::class.java))
            finish()
        }
    }
}
