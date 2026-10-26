package lk.picksmart.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.FuelLogRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.CsvExport
import java.io.File
import java.time.LocalDate

/**
 * Writes every trip, income entry, fill-up and expense to four CSV files for the share sheet.
 * They go in the cache (never backed up), and each export first deletes the previous one, so no
 * copy lingers on the phone. Nothing is uploaded: the rider picks where the files go.
 */
class CsvExporter(
    private val context: Context,
    private val trips: TripRepository,
    private val otherIncome: OtherIncomeRepository,
    private val fuelLogs: FuelLogRepository,
    private val expenses: ExpenseRepository,
    private val vehicles: VehicleRepository,
    private val calculator: ProfitCalculator,
) {
    /** The files' content URIs, readable by whichever app the rider shares them with. */
    suspend fun export(today: LocalDate): List<Uri> = withContext(Dispatchers.IO) {
        val names = vehicles.allVehicles.first().associate { it.id to it.name }
        val files = mapOf(
            "trips" to CsvExport.trips(trips.all.first(), names, calculator),
            "other-income" to CsvExport.otherIncome(otherIncome.all.first()),
            "fuel-log" to CsvExport.fuelLog(fuelLogs.all.first(), names),
            "expenses" to CsvExport.expenses(expenses.all.first(), names),
        )
        val dir = File(context.cacheDir, DIRECTORY)
        dir.deleteRecursively()
        dir.mkdirs()
        files.map { (name, csv) ->
            val file = File(dir, "picksmart-$name-$today.csv")
            // A byte order mark lets Excel read names and notes typed in Sinhala or Tamil.
            file.writeText(BOM + csv, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.$AUTHORITY_SUFFIX", file)
        }
    }

    companion object {
        /** Under the cache directory; erased with the rider's data. */
        const val DIRECTORY = "exports"
        private const val AUTHORITY_SUFFIX = "exports"
        private val BOM = Char(0xFEFF).toString()
    }
}
