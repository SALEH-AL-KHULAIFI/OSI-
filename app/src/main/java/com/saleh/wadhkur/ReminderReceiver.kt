package com.saleh.wadhkur

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(
            ReminderScheduler.PREFS,
            Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(ReminderScheduler.ENABLED, true)) return

        val dhikr = DhikrRepository.main.random(Random.Default)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("show_dhikr", dhikr.text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            7400,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "dhikr_reminders"

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_description)
                    enableVibration(true)
                }
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("وٌ ذکْــر")
            .setContentText(dhikr.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(dhikr.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .build()

        manager.notify(8800, notification)
        ReminderScheduler.schedule(context)
    }
}
