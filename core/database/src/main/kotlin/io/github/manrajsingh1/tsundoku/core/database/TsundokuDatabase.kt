package io.github.manrajsingh1.tsundoku.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The library's single source of truth. Schema version 1; every later version needs a
 * migration here and a test that runs it against the exported schema in `schemas/`.
 */
@Database(
    entities = [
        BookEntity::class,
        ShelfEntity::class,
        ShelfBookEntity::class,
        ReadingProgressEntity::class,
        ImportItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TsundokuDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun shelfDao(): ShelfDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun importQueueDao(): ImportQueueDao

    companion object {
        const val FILE_NAME = "tsundoku.db"

        // The platform's own SQLite, through Room's driver API: nothing native is bundled.
        fun create(context: Context): TsundokuDatabase =
            Room.databaseBuilder<TsundokuDatabase>(context.applicationContext, FILE_NAME)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        /** A database that lives only as long as the process. For tests. */
        fun inMemory(context: Context): TsundokuDatabase =
            Room.inMemoryDatabaseBuilder<TsundokuDatabase>(context.applicationContext)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
