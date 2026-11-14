package io.github.manrajsingh1.tsundoku.core.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.manrajsingh1.tsundoku.core.data.LibraryRepository
import io.github.manrajsingh1.tsundoku.core.data.OfflineLibraryRepository
import io.github.manrajsingh1.tsundoku.core.data.OfflineReadingRepository
import io.github.manrajsingh1.tsundoku.core.data.ReadingRepository
import io.github.manrajsingh1.tsundoku.core.data.importing.ImportRepository
import io.github.manrajsingh1.tsundoku.core.data.importing.ImportScheduler
import io.github.manrajsingh1.tsundoku.core.data.importing.OfflineImportRepository
import io.github.manrajsingh1.tsundoku.core.data.importing.PublicationInspector
import io.github.manrajsingh1.tsundoku.core.data.importing.ReadiumPublicationInspector
import io.github.manrajsingh1.tsundoku.core.data.importing.WorkManagerImportScheduler
import io.github.manrajsingh1.tsundoku.core.database.BookDao
import io.github.manrajsingh1.tsundoku.core.database.ImportQueueDao
import io.github.manrajsingh1.tsundoku.core.database.ReadingProgressDao
import io.github.manrajsingh1.tsundoku.core.database.ShelfDao
import io.github.manrajsingh1.tsundoku.core.database.TsundokuDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TsundokuDatabase = TsundokuDatabase.create(context)

    @Provides
    fun provideBookDao(database: TsundokuDatabase): BookDao = database.bookDao()

    @Provides
    fun provideShelfDao(database: TsundokuDatabase): ShelfDao = database.shelfDao()

    @Provides
    fun provideReadingProgressDao(database: TsundokuDatabase): ReadingProgressDao = database.readingProgressDao()

    @Provides
    fun provideImportQueueDao(database: TsundokuDatabase): ImportQueueDao = database.importQueueDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(implementation: OfflineLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindImportRepository(implementation: OfflineImportRepository): ImportRepository

    @Binds
    abstract fun bindImportScheduler(implementation: WorkManagerImportScheduler): ImportScheduler

    @Binds
    @Singleton
    abstract fun bindReadingRepository(implementation: OfflineReadingRepository): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindPublicationInspector(implementation: ReadiumPublicationInspector): PublicationInspector
}
