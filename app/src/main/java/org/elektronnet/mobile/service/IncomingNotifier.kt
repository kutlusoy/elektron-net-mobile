package org.elektronnet.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.elektronnet.mobile.MainActivity
import org.elektronnet.mobile.R
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.IncomingDetector
import org.elektronnet.mobile.wallet.SeenStore
import org.elektronnet.mobile.wallet.TxItem

class IncomingNotifier(private val context: Context, private val settings: SettingsStore) {
    fun check(wallet: String, recent: List<TxItem>) {
        val detector = IncomingDetector(object : SeenStore {
            override var ids: List<String>?
                get() = settings.seenIncoming(wallet)
                set(value) = settings.setSeenIncoming(wallet, value)
        })
        val fresh = detector.poll(recent)
        if (!settings.notifyIncoming || fresh.isEmpty()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_incoming_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val total = fresh.sumOf { it.netLep }
        val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val hidden = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentTitle(context.getString(R.string.notify_incoming_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentTitle(context.getString(R.string.notify_incoming_title))
            .setContentText(context.getString(R.string.notify_incoming_text, Amount.format(total), context.getString(R.string.balance_unit)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .build()
        manager.notify(ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "incoming"
        const val ID = 2
    }
}
