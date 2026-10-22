package lk.picksmart.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    private val money = MoneyFormatter("Rs.")

    /** The formatter uses a non-breaking space; tests read better with a normal one. */
    private fun String.spaced() = replace(' ', ' ')

    @Test
    fun `formats rupees with grouping and two decimals`() {
        assertEquals("Rs. 1,250.00", money.format(125_000).spaced())
        assertEquals("Rs. 0.00", money.format(0).spaced())
        assertEquals("Rs. 0.05", money.format(5).spaced())
        assertEquals("Rs. 1,234,567.89", money.format(123_456_789).spaced())
    }

    @Test
    fun `negative amounts get a leading minus`() {
        assertEquals("-Rs. 50.00", money.format(-5_000).spaced())
    }

    @Test
    fun `signed format adds a plus only to positive amounts`() {
        assertEquals("+Rs. 705.00", money.formatSigned(70_500).spaced())
        assertEquals("-Rs. 50.00", money.formatSigned(-5_000).spaced())
        assertEquals("Rs. 0.00", money.formatSigned(0).spaced())
    }

    @Test
    fun `prefix and number never split across lines`() {
        assertEquals("Rs. 1.00", money.format(100))
    }

    @Test
    fun `extreme values format exactly`() {
        assertEquals("Rs. 92,233,720,368,547,758.07", money.format(Long.MAX_VALUE).spaced())
        assertEquals("-Rs. 92,233,720,368,547,758.08", money.format(Long.MIN_VALUE).spaced())
    }

    @Test
    fun `currency prefix is configurable for translations`() {
        assertEquals("රු. 10.00", MoneyFormatter("රු.").format(1_000).spaced())
    }

    @Test
    fun `digits stay western whatever the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("Rs. 1,250.00", money.format(125_000).spaced())
            assertEquals("1,234.5", formatDecimal(1234.5, 1))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `decimals round half up`() {
        assertEquals("81.5", formatDecimal(81.5, 1))
        assertEquals("10.0", formatDecimal(10.0, 1))
        assertEquals("0.3", formatDecimal(0.25, 1))
        assertEquals("2.68", formatDecimal(2.675, 2))
        assertEquals("1,235", formatDecimal(1234.5, 0))
    }
}
