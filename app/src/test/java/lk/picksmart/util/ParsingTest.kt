package lk.picksmart.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {

    @Test
    fun `rupees parse to cents`() {
        assertEquals(30_000L, parseRupeesToCents("300"))
        assertEquals(29_750L, parseRupeesToCents("297.5"))
        assertEquals(125_000L, parseRupeesToCents("1250.00"))
        assertEquals(0L, parseRupeesToCents("0"))
        assertEquals(1_200L, parseRupeesToCents(" 12 "))
    }

    @Test
    fun `sub-cent amounts round half up`() {
        assertEquals(1L, parseRupeesToCents("0.005"))
        assertEquals(0L, parseRupeesToCents("0.004"))
    }

    @Test
    fun `bad rupee text is rejected`() {
        assertNull(parseRupeesToCents(""))
        assertNull(parseRupeesToCents("."))
        assertNull(parseRupeesToCents("abc"))
        assertNull(parseRupeesToCents("-5"))
        assertNull(parseRupeesToCents("99999999999999999999"))
    }

    @Test
    fun `decimals parse and reject bad text`() {
        assertEquals(40.0, parseDecimal("40")!!, 0.0)
        assertEquals(37.5, parseDecimal("37.5")!!, 0.0)
        assertEquals(12.0, parseDecimal("12.")!!, 0.0)
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("-1"))
        assertNull(parseDecimal("x"))
        assertNull(parseDecimal("1e999"))
    }

    @Test
    fun `values convert back to editable text`() {
        assertEquals("300.00", centsToInput(30_000))
        assertEquals("0.05", centsToInput(5))
        assertEquals("40", decimalToInput(40.0))
        assertEquals("37.5", decimalToInput(37.5))
        assertEquals("0.1", decimalToInput(0.1))
    }

    @Test
    fun `hundredths become short editable text`() {
        assertEquals("200", hundredthsToInput(20_000))
        assertEquals("12.5", hundredthsToInput(1_250))
        assertEquals("0.05", hundredthsToInput(5))
        assertEquals("0", hundredthsToInput(0))
    }
}
