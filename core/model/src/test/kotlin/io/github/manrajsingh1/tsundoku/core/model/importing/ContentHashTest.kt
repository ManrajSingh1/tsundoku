package io.github.manrajsingh1.tsundoku.core.model.importing

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentHashTest {

    @Test
    fun `matches the published SHA-256 of a known input`() {
        val digest = ContentHash.digest(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest.sha256)
        assertEquals(3L, digest.size)
        assertArrayEquals("abc".toByteArray(), digest.head)
    }

    @Test
    fun `an empty file has the empty hash and no head`() {
        val digest = ContentHash.digest(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest.sha256)
        assertEquals(0L, digest.size)
        assertEquals(0, digest.head.size)
    }

    @Test
    fun `a large file is read whole and its head is the first bytes only`() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val digest = ContentHash.digest(ByteArrayInputStream(bytes))
        assertEquals(300_000L, digest.size)
        assertEquals(FormatSniffer.HEAD_SIZE, digest.head.size)
        assertArrayEquals(bytes.copyOf(FormatSniffer.HEAD_SIZE), digest.head)
    }

    @Test
    fun `one changed byte changes the hash`() {
        val a = ByteArray(100_000)
        val b = ByteArray(100_000).also { it[99_999] = 1 }
        assertNotEquals(
            ContentHash.digest(ByteArrayInputStream(a)).sha256,
            ContentHash.digest(ByteArrayInputStream(b)).sha256,
        )
    }
}
