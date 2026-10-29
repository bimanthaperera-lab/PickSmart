package lk.picksmart.glance

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import lk.picksmart.R
import lk.picksmart.domain.usecase.OfferChime

/**
 * Plays the offer reader's chimes (CLAUDE.md section 9b, `design/sounds/`). They play as a
 * notification sound, so they follow the phone's notification volume and are silent when the
 * phone is on silent, vibrate or Do Not Disturb. Each is under a second and needs no audio focus.
 */
object OfferChimes {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(context: Context, chime: OfferChime) {
        val sound = when (chime) {
            OfferChime.PROFIT -> R.raw.offer_profit
            OfferChime.LOSS -> R.raw.offer_loss
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val player = MediaPlayer.create(context, sound, attributes, audio.generateAudioSessionId()) ?: return
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            true
        }
        player.start()
    }
}
