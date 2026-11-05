package lk.picksmart.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.repository.AlertRepository
import lk.picksmart.domain.model.Alert
import lk.picksmart.domain.model.AlertLink
import lk.picksmart.domain.model.AlertType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The alerts inbox: each key is written once, opening marks them read, and 30 days are kept. */
@RunWith(AndroidJUnit4::class)
class AlertDaoTest {

    private lateinit var db: PicksmartDatabase
    private lateinit var alerts: AlertRepository

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun alert(key: String?, createdAt: Long = now) = Alert(
        type = AlertType.FUEL_PRICE,
        title = "Sample title",
        body = "Sample body",
        createdAtMillis = createdAt,
        link = AlertLink.FUEL_PRICE,
        key = key,
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PicksmartDatabase::class.java)
            .build()
        alerts = AlertRepository(db.alertDao(), Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
    }

    @After
    fun close() = db.close()

    @Test
    fun anAlertWithTheSameKeyIsWrittenOnce() = runTest {
        assertTrue(alerts.add(alert("fuel_price_new:a:2026-10-01")))
        assertFalse(alerts.add(alert("fuel_price_new:a:2026-10-01")))
        assertTrue(alerts.add(alert("fuel_price_new:a:2026-10-15")))
        // Alerts with no key are never treated as the same.
        assertTrue(alerts.add(alert(null)))
        assertTrue(alerts.add(alert(null)))
        assertEquals(4, alerts.all.first().size)
    }

    @Test
    fun openingTheInboxMarksEveryAlertRead() = runTest {
        alerts.add(alert("one"))
        alerts.add(alert("two"))
        assertEquals(2, alerts.unreadCount.first())
        alerts.markAllRead()
        assertEquals(0, alerts.unreadCount.first())
        assertTrue(alerts.all.first().all { it.read })
    }

    @Test
    fun alertsOlderThan30DaysAreDeleted() = runTest {
        alerts.add(alert("old", createdAt = now - 31 * day))
        alerts.add(alert("recent", createdAt = now - 29 * day))
        alerts.deleteOld()
        assertEquals(listOf("recent"), alerts.all.first().map { it.key })
    }

    @Test
    fun alertsReadBackNewestFirstWithTheirLink() = runTest {
        alerts.add(alert("older", createdAt = now - day))
        alerts.add(alert("newer"))
        val list = alerts.all.first()
        assertEquals(listOf("newer", "older"), list.map { it.key })
        assertEquals(AlertLink.FUEL_PRICE, list.first().link)
        assertEquals(AlertType.FUEL_PRICE, list.first().type)
    }
}
