package lk.picksmart.data.account

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePhotoSampleTest {

    @Test
    fun `a small photo is decoded at full size`() {
        assertEquals(1, sampleSizeFor(800, 600, 512))
        assertEquals(1, sampleSizeFor(300, 300, 512))
    }

    @Test
    fun `a camera photo is decoded smaller but keeps its short side above the target`() {
        assertEquals(4, sampleSizeFor(4000, 3000, 512)) // 1000 × 750
        assertEquals(2, sampleSizeFor(2048, 1024, 512)) // 1024 × 512
    }

    @Test
    fun `a long panorama is decoded small enough to fit in memory`() {
        assertEquals(8, sampleSizeFor(30_000, 1_000, 512)) // 3750 × 125
    }
}
