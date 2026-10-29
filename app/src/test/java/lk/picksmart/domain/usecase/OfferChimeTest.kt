package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ResultBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferChimeTest {

    @Test
    fun `a profit with or without a target plays the profit chime`() {
        assertEquals(OfferChime.PROFIT, offerChime(ResultBand.PROFIT))
        assertEquals(OfferChime.PROFIT, offerChime(ResultBand.GOOD))
    }

    @Test
    fun `a loss plays the loss chime`() {
        assertEquals(OfferChime.LOSS, offerChime(ResultBand.LOSS))
    }

    @Test
    fun `below target and break even stay silent`() {
        assertNull(offerChime(ResultBand.BELOW_TARGET))
        assertNull(offerChime(ResultBand.BREAK_EVEN))
    }
}
