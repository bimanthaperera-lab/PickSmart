package lk.picksmart.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/** Dates are stored as ISO `yyyy-MM-dd` text, readable in backups and exports. */
class Converters {
    @TypeConverter
    fun localDateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}
