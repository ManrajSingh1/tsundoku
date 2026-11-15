package io.github.manrajsingh1.tsundoku.core.model

/** The orders a shelf can be put in. */
object ShelfOrder {

    /**
     * Books by title from A to Z, the way a person would shelve them: case does not matter,
     * and numbers count as numbers, so "Volume 2" comes before "Volume 10". Books with the
     * same title go by volume, then by which was added first.
     */
    fun alphabetical(books: List<Book>): List<Book> = books.sortedWith(
        Comparator<Book> { a, b -> compareNaturally(a.title, b.title) }
            .thenBy { it.volume ?: Double.MAX_VALUE }
            .thenBy { it.id },
    )

    fun isAlphabetical(books: List<Book>): Boolean {
        val sorted = alphabetical(books)
        return books.indices.all { books[it].id == sorted[it].id }
    }

    /** Compares text ignoring case, treating each run of digits as one number. */
    fun compareNaturally(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val x = a[i]
            val y = b[j]
            if (x.isDigit() && y.isDigit()) {
                val endA = digitsEnd(a, i)
                val endB = digitsEnd(b, j)
                val byNumber = compareNumbers(a, i, endA, b, j, endB)
                if (byNumber != 0) return byNumber
                i = endA
                j = endB
            } else {
                val byLetter = x.lowercaseChar().compareTo(y.lowercaseChar())
                if (byLetter != 0) return byLetter
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun digitsEnd(text: String, from: Int): Int {
        var end = from
        while (end < text.length && text[end].isDigit()) end++
        return end
    }

    /** Compares two runs of digits as numbers of any length, without parsing them. */
    private fun compareNumbers(a: String, startA: Int, endA: Int, b: String, startB: Int, endB: Int): Int {
        var i = startA
        var j = startB
        while (i < endA - 1 && a[i] == '0') i++
        while (j < endB - 1 && b[j] == '0') j++
        val byLength = (endA - i).compareTo(endB - j)
        if (byLength != 0) return byLength
        while (i < endA) {
            val byDigit = a[i].compareTo(b[j])
            if (byDigit != 0) return byDigit
            i++
            j++
        }
        return 0
    }
}
