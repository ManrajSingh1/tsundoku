package io.github.manrajsingh1.tsundoku.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfOrderTest {

    private fun book(id: Long, title: String, volume: Double? = null) = Book(
        id = id,
        documentUri = "content://test/$id",
        contentHash = "hash$id",
        format = BookFormat.CBZ,
        title = title,
        author = null,
        series = null,
        volume = volume,
        pageCount = 10,
        fileSize = 1L,
        coverPath = null,
        spineColor = 0,
        readingDirection = ReadingDirection.AUTO,
        addedAt = 0L,
        lastOpenedAt = null,
        isMissing = false,
    )

    private fun titles(vararg titles: String) =
        ShelfOrder.alphabetical(titles.mapIndexed { index, title -> book(index.toLong(), title) }).map { it.title }

    @Test
    fun titlesGoFromAToZWhateverTheirCase() {
        assertEquals(listOf("akira", "Berserk", "claymore", "Dorohedoro"), titles("Dorohedoro", "claymore", "akira", "Berserk"))
    }

    @Test
    fun numbersInTitlesCountAsNumbers() {
        assertEquals(
            listOf("Saga v1", "Saga v2", "Saga v10", "Saga v100"),
            titles("Saga v10", "Saga v100", "Saga v2", "Saga v1"),
        )
        assertEquals(listOf("Saga 02", "Saga 9", "Saga 010"), titles("Saga 010", "Saga 9", "Saga 02"))
    }

    @Test
    fun aTitleComesBeforeALongerOneThatStartsWithIt() {
        assertEquals(listOf("Blame", "Blame!", "Blame! 2"), titles("Blame! 2", "Blame!", "Blame"))
    }

    @Test
    fun booksWithTheSameTitleGoByVolumeThenByWhichCameFirst() {
        val sorted = ShelfOrder.alphabetical(
            listOf(book(4, "Monster"), book(3, "Monster", 2.0), book(2, "Monster", 1.0), book(1, "Monster")),
        )
        assertEquals(listOf(2L, 3L, 1L, 4L), sorted.map { it.id })
    }

    @Test
    fun aShelfKnowsWhetherItIsAlreadyInOrder() {
        assertTrue(ShelfOrder.isAlphabetical(listOf(book(1, "A"), book(2, "B"))))
        assertFalse(ShelfOrder.isAlphabetical(listOf(book(2, "B"), book(1, "A"))))
        assertTrue(ShelfOrder.isAlphabetical(emptyList()))
    }
}
