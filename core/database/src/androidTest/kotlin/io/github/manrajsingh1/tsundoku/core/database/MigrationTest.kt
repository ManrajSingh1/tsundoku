package io.github.manrajsingh1.tsundoku.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.manrajsingh1.tsundoku.core.model.ImportState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database exactly as an older version of the app left it, from that version's
 * exported schema, fills it, and opens it with today's code. Room checks the migrated
 * tables against what it expects when it opens the file, so a wrong migration fails here
 * and not on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FILE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FILE)
    }

    /** Creates the database as schema [version] defined it and leaves it open to be filled. */
    private fun createAtVersion(version: Int): SQLiteDatabase {
        // A copy of schemas/<version>.json, kept in this test's assets. Past schemas never
        // change, so the copy cannot drift.
        val schema = context.assets.open("schemas/$version.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val file = context.getDatabasePath(FILE)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                database.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
        database.version = version
        return database
    }

    @Test
    fun aVersion1LibraryOpensWithItsBooksShelvesAndQueueIntact() = runBlocking {
        createAtVersion(1).use { old ->
            val shelf = old.insertOrThrow("shelves", null, values("name" to "Manga", "position" to 0))
            val book = old.insertOrThrow(
                "books",
                null,
                values(
                    "documentUri" to "content://test/a",
                    "contentHash" to "a",
                    "format" to "CBZ",
                    "title" to "Kept",
                    "fileSize" to 10L,
                    "spineColor" to 0xFF223344.toInt(),
                    "readingDirection" to "RIGHT_TO_LEFT",
                    "addedAt" to 1L,
                    "isMissing" to 0,
                ),
            )
            old.insertOrThrow("shelf_books", null, values("shelfId" to shelf, "bookId" to book, "position" to 0))
            old.insertOrThrow(
                "reading_progress",
                null,
                values("bookId" to book, "position" to "4", "percent" to 0.4, "updatedAt" to 2L),
            )
            old.insertOrThrow(
                "import_queue",
                null,
                values("uri" to "content://test/b", "isTree" to 0, "state" to "PENDING", "createdAt" to 3L),
            )
        }

        val migrated = TsundokuDatabase.create(context, FILE)
        try {
            val shelved = migrated.bookDao().observeShelved().first().single()
            assertEquals("Kept", shelved.book.title)
            assertEquals(listOf("Manga"), migrated.shelfDao().observeAll().first().map { it.name })
            assertEquals("4", migrated.readingProgressDao().find(shelved.book.id)?.position)

            val pending = migrated.importQueueDao().nextPending()!!
            assertEquals("content://test/b", pending.uri)
            assertEquals(ImportState.PENDING, pending.state)
            // Queued before shelves could be chosen: shelved by format, as it always was.
            assertNull(pending.targetShelfId)
        } finally {
            migrated.close()
        }
    }

    private fun values(vararg pairs: Pair<String, Any>) = ContentValues().apply {
        for ((key, value) in pairs) {
            when (value) {
                is String -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Double -> put(key, value)
                else -> error("Unsupported value for $key")
            }
        }
    }

    private companion object {
        const val FILE = "migration-test.db"
    }
}
