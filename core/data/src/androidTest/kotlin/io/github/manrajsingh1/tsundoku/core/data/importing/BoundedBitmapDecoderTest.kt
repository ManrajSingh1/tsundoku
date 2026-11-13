package io.github.manrajsingh1.tsundoku.core.data.importing

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedBitmapDecoderTest {

    @Test
    fun aLargeImageIsDecodedDownToTheRequestedBox() {
        val bytes = Fixtures.png(2400, 3600, Color.BLUE)

        val bitmap = BoundedBitmapDecoder.decode(bytes, maxWidth = 600, maxHeight = 900)

        assertNotNull(bitmap)
        assertEquals(600, bitmap!!.width)
        assertEquals(900, bitmap.height)
    }

    @Test
    fun aSmallImageIsNotEnlarged() {
        val bitmap = BoundedBitmapDecoder.decode(Fixtures.png(120, 90, Color.RED), maxWidth = 600, maxHeight = 900)

        assertEquals(120, bitmap!!.width)
        assertEquals(90, bitmap.height)
    }

    @Test
    fun aWideImageKeepsItsShape() {
        val bitmap = BoundedBitmapDecoder.decode(Fixtures.png(3000, 1000, Color.GREEN), maxWidth = 600, maxHeight = 900)

        assertEquals(600, bitmap!!.width)
        assertEquals(200, bitmap.height)
    }

    @Test
    fun anImageClaimingAnImpossibleSizeIsRefusedBeforeAnyPixelsAreAllocated() {
        val bomb = Fixtures.pngClaimingSize(30_000, 30_000)
        assertTrue("The fixture should be tiny", bomb.size < 100)

        assertNull(BoundedBitmapDecoder.decode(bomb, maxWidth = 600, maxHeight = 900))
    }

    @Test
    fun aVeryLongThinImageIsRefusedBecauseItCannotBeDownsampledEnough() {
        val strip = Fixtures.pngClaimingSize(100, 1_000_000)

        assertNull(BoundedBitmapDecoder.decode(strip, maxWidth = 600, maxHeight = 900))
    }

    @Test
    fun bytesThatAreNotAnImageGiveNull() {
        assertNull(BoundedBitmapDecoder.decode("not an image".toByteArray(), maxWidth = 600, maxHeight = 900))
        assertNull(BoundedBitmapDecoder.decode(ByteArray(0), maxWidth = 600, maxHeight = 900))
    }
}
