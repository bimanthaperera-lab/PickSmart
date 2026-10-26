package lk.picksmart.ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import lk.picksmart.MainActivity
import lk.picksmart.R
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.usecase.OfferReading
import lk.picksmart.domain.usecase.OfferToSave
import lk.picksmart.util.MoneyFormatter
import lk.picksmart.util.decimalToInput
import java.time.Instant

/**
 * "Took this ride? Save it as a trip": posted when an offer the reader showed leaves the screen
 * (CLAUDE.md section 9b). Picksmart can't tell whether the rider accepted it, so it only asks.
 * Tapping it opens Add trip filled with the offer's numbers, where the rider checks them and saves
 * (N6). Only the latest offer is offered: a new one replaces it. It is silent, since the rider may
 * be riding, and on the lock screen it doesn't show the amounts.
 */
object OfferSaveNotification {

    private const val CHANNEL_ID = "offer_save"
    private const val NOTIFICATION_ID = 1001

    /** Long enough to save the trip at its end; an old offer then goes away on its own. */
    private const val TIMEOUT_MS = 2 * 60 * 60 * 1000L

    const val ACTION_SAVE_OFFER = "lk.picksmart.action.SAVE_OFFER"
    private const val EXTRA_FARE_CENTS = "offer_fare_cents"
    private const val EXTRA_PICKUP_KM = "offer_pickup_km"
    private const val EXTRA_TRIP_KM = "offer_trip_km"
    private const val EXTRA_PLATFORM = "offer_platform"
    private const val EXTRA_READ_AT = "offer_read_at"

    fun post(context: Context, offer: OfferToSave) {
        // Android 13+: asked for when the rider turns the reader on; without it, nothing is posted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.offer_save_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.offer_save_channel_description)
            },
        )
        val money = MoneyFormatter(context.getString(R.string.currency_prefix))
        val body = context.getString(R.string.offer_save_body, money.format(offer.reading.fareCents), decimalToInput(offer.reading.totalKm))
        val open = PendingIntent.getActivity(
            context,
            0,
            intent(context, offer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.offer_save_title)
        val lockScreen = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.offer_save_public_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.brand_yellow))
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setWhen(offer.readAt.toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun intent(context: Context, offer: OfferToSave) = Intent(context, MainActivity::class.java)
        .setAction(ACTION_SAVE_OFFER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_FARE_CENTS, offer.reading.fareCents)
        .putExtra(EXTRA_PICKUP_KM, offer.reading.pickupKm ?: -1.0)
        .putExtra(EXTRA_TRIP_KM, offer.reading.tripKm ?: -1.0)
        .putExtra(EXTRA_PLATFORM, offer.platform.name)
        .putExtra(EXTRA_READ_AT, offer.readAt.toEpochMilli())

    /** The offer in a tap on this notification, or null for any other intent. */
    fun offerFrom(intent: Intent?): OfferToSave? {
        if (intent?.action != ACTION_SAVE_OFFER) return null
        val fare = intent.getLongExtra(EXTRA_FARE_CENTS, 0)
        if (fare <= 0) return null
        val reading = OfferReading(
            fareCents = fare,
            pickupKm = intent.getDoubleExtra(EXTRA_PICKUP_KM, -1.0).takeIf { it >= 0 },
            tripKm = intent.getDoubleExtra(EXTRA_TRIP_KM, -1.0).takeIf { it >= 0 },
        )
        val platform = Platform.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PLATFORM) } ?: Platform.OTHER
        return OfferToSave(reading, platform, Instant.ofEpochMilli(intent.getLongExtra(EXTRA_READ_AT, System.currentTimeMillis())))
    }
}
