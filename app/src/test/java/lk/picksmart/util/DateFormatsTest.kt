package lk.picksmart.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DateFormatsTest {

    private val sinhala = Locale.forLanguageTag("si")
    private val day = LocalDate.of(2026, 10, 26)

    @Test
    fun `Sinhala dates use the calendar month names`() {
        assertEquals("2026 ඔක් 26", dateFormatter("yyyy MMM d", sinhala).format(day))
        assertEquals("සැප් 7", dateFormatter("MMM d", sinhala).format(LocalDate.of(2026, 9, 7)))
        assertEquals("2026 නොවැ", dateFormatter("yyyy MMM", sinhala).format(LocalDate.of(2026, 11, 1)))
        assertEquals("ඔක්තෝබර් 26", dateFormatter("MMMM d", sinhala).format(day))
    }

    @Test
    fun `every Sinhala month has its short name`() {
        val names = (1..12).map { dateFormatter("MMM", sinhala).format(LocalDate.of(2026, it, 1)) }
        assertEquals(
            listOf("ජන", "පෙබ", "මාර්තු", "අප්‍රේල්", "මැයි", "ජූනි", "ජූලි", "අගෝ", "සැප්", "ඔක්", "නොවැ", "දෙසැ"),
            names,
        )
    }

    @Test
    fun `the rest of a Sinhala pattern is unchanged`() {
        val expected = java.time.format.DateTimeFormatter.ofPattern("EEE", sinhala).format(day)
        assertEquals("ඔක් 26 $expected", dateFormatter("MMM d EEE", sinhala).format(day))
        assertEquals("ඔක් 26, at", dateFormatter("MMM d, 'at'", sinhala).format(day))
        assertEquals("09:05", dateFormatter("HH:mm", sinhala).format(LocalDateTime.of(2026, 10, 26, 9, 5)))
    }

    @Test
    fun `other languages use Android's own names`() {
        assertEquals("26 Oct 2026", dateFormatter("d MMM yyyy", Locale.ENGLISH).format(day))
        val tamil = Locale.forLanguageTag("ta")
        assertEquals(
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", tamil).format(day),
            dateFormatter("d MMM yyyy", tamil).format(day),
        )
    }
}
