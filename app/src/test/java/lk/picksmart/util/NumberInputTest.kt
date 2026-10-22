package lk.picksmart.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberInputTest {

    @Test
    fun `keeps digits and one decimal point`() {
        assertEquals("1250.5", sanitizeDecimalInput("1250.5", 2))
        assertEquals("12.", sanitizeDecimalInput("12.", 2))
        assertEquals("12.34", sanitizeDecimalInput("12.3.4", 2))
    }

    @Test
    fun `drops separators, letters, signs and pasted currency`() {
        assertEquals("1250", sanitizeDecimalInput("1,250", 2))
        assertEquals("1000.00", sanitizeDecimalInput("Rs. 1,000.00", 2))
        assertEquals("1000", sanitizeDecimalInput("Rs.1000", 2))
        assertEquals("1250.50", sanitizeDecimalInput("LKR 1,250.50", 2))
        assertEquals("50", sanitizeDecimalInput("-50", 2))
        assertEquals("", sanitizeDecimalInput("abc", 2))
    }

    @Test
    fun `limits decimal places`() {
        assertEquals("297.50", sanitizeDecimalInput("297.509", 2))
        assertEquals("8.5", sanitizeDecimalInput("8.55", 1))
    }

    @Test
    fun `whole-number fields ignore the decimal point`() {
        assertEquals("125", sanitizeDecimalInput("12.5", 0))
    }

    @Test
    fun `a leading point gets a zero`() {
        assertEquals("0.5", sanitizeDecimalInput(".5", 2))
    }

    @Test
    fun `non-western digits are not accepted`() {
        assertEquals("", sanitizeDecimalInput("١٢٣", 2))
    }
}
