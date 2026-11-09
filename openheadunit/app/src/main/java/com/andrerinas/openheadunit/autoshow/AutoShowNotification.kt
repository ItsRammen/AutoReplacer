package com.andrerinas.openheadunit.autoshow

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.MainActivity

/**
 * The one notification AutoReplacer shows while a car session runs. Two services need a foreground
 * notification (the car link and Android Auto's own service); both post this one under the same id,
 * so the phone shows a single entry that says what is working.
 */
object AutoShowNotification {
    const val ID = 1

    fun build(context: Context): Notification {
        val snapshot = AutoShowState.state.value
        val title = AutoShowStatusCard.title(snapshot, cableIn = true)
        val details = buildString {
            append(snapshot.headline.takeIf { it.isNotBlank() && it != title } ?: "")
            if (isNotEmpty()) append('\n')
            append("Touch: ").append(snapshot.touch).append('\n')
            append("Bluetooth: ").append(snapshot.bluetooth)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, App.defaultChannel)
        else @Suppress("DEPRECATION") Notification.Builder(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return builder.setSmallIcon(R.drawable.ic_stat_aa)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(title)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_aa), "Save log",
                PendingIntent.getService(context, 1,
                    Intent(context, AutoShowService::class.java).setAction(AutoShowService.ACTION_SAVE_LOG),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            .setOngoing(true)
            .build()
    }
}
