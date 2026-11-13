package io.github.manrajsingh1.tsundoku.core.model.importing

import java.io.InputStream
import java.security.MessageDigest

/** The result of reading a file once from start to end. */
class FileDigest(
    /** SHA-256 of every byte, as lowercase hex. */
    val sha256: String,
    val size: Long,
    /** The first [FormatSniffer.HEAD_SIZE] bytes, or fewer if the file is shorter. */
    val head: ByteArray,
)

object ContentHash {

    /**
     * Reads [input] to the end in one pass, producing the hash that identifies the book, the
     * size, and the leading bytes used to tell what format it is. The stream is not closed.
     */
    fun digest(input: InputStream): FileDigest {
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        val head = ByteArray(FormatSniffer.HEAD_SIZE)
        var headLength = 0
        var size = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (headLength < head.size) {
                val take = minOf(read, head.size - headLength)
                System.arraycopy(buffer, 0, head, headLength, take)
                headLength += take
            }
            sha.update(buffer, 0, read)
            size += read
        }
        val hex = sha.digest().joinToString("") { "%02x".format(it) }
        return FileDigest(sha256 = hex, size = size, head = head.copyOf(headLength))
    }
}
