package org.elektronnet.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.elektronnet.mobile.MainActivity
import org.elektronnet.mobile.R
import org.elektronnet.mobile.policy.PolicyReason
import org.elektronnet.mobile.runtime.Headline

object NodeNotification {
    const val CHANNEL_ID = "node"
    const val ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun build(context: Context, headline: Headline?, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text(context, headline))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, context.getString(if (paused) R.string.action_resume else R.string.action_pause), action(context, if (paused) NodeService.ACTION_RESUME else NodeService.ACTION_PAUSE, 1))
            .addAction(0, context.getString(R.string.action_stop), action(context, NodeService.ACTION_STOP, 2))
        return builder.build()
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context,
            code,
            Intent(context, NodeService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun text(context: Context, headline: Headline?): String = when (headline) {
        null, Headline.Stopped -> context.getString(R.string.headline_stopped)
        Headline.Starting -> context.getString(R.string.headline_starting)
        Headline.Stopping -> context.getString(R.string.headline_stopping)
        is Headline.Connecting -> context.getString(R.string.headline_connecting)
        is Headline.Paused -> context.getString(R.string.headline_paused, reason(context, headline.reasons))
        is Headline.Syncing -> context.getString(R.string.headline_syncing, headline.blocks, headline.headers, headline.peers.toInt())
        is Headline.Synchronized -> context.getString(R.string.headline_synchronized, headline.blocks, headline.peers.toInt())
        is Headline.Crashed -> context.getString(R.string.headline_crashed)
    }

    private fun reason(context: Context, reasons: Set<PolicyReason>): String {
        val first = reasons.firstOrNull() ?: return context.getString(R.string.reason_unknown)
        return context.getString(
            when (first) {
                PolicyReason.UserPaused -> R.string.reason_user_paused
                PolicyReason.NoNetwork -> R.string.reason_no_network
                PolicyReason.MeteredNotAllowed -> R.string.reason_metered
                PolicyReason.RoamingNotAllowed -> R.string.reason_roaming
                PolicyReason.DataSaver -> R.string.reason_data_saver
                PolicyReason.DataCapReached -> R.string.reason_data_cap
                PolicyReason.NeedsCharging -> R.string.reason_needs_charging
                PolicyReason.BatteryLow -> R.string.reason_battery
                PolicyReason.ThermalPause -> R.string.reason_thermal
                PolicyReason.OutsideSchedule -> R.string.reason_schedule
                else -> R.string.reason_unknown
            },
        )
    }
}
