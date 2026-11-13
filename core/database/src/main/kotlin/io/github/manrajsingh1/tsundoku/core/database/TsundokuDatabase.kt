package io.github.manrajsingh1.tsundoku.core.database

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The library's single source of truth. Every schema version needs a migration here, and
 * its exported schema committed in `schemas/` so the next migration can be generated
 * against it.
 *
 * Version 2 added `import_queue.targetShelfId`.
 */
@Database(
    entities = [
        BookEntity::class,
        ShelfEntity::class,
        ShelfBookEntity::class,
        ReadingProgressEntity::class,
        ImportItemEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
        fun create(context: Context, fileName: String = FILE_NAME): TsundokuDatabase =
            Room.databaseBuilder<TsundokuDatabase>(context.applicationContext, fileName)
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
