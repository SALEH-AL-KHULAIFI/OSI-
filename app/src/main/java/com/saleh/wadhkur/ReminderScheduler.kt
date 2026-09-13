package com.saleh.wadhkur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {

    const val PREFS = "wadhkur_settings"
    const val ENABLED = "reminder_enabled"
    const val INTERVAL = "reminder_interval_minutes"

    private const val REQUEST_CODE = 7300

    fun schedule(context: Context) {

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        if (
            !prefs.getBoolean(
                ENABLED,
                true
            )
        ) {
            cancel(context)
            return
        }

        val minutes =
            prefs.getInt(
                INTERVAL,
                30
            ).coerceIn(1, 60)

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        /*
         * إلغاء أي تذكير قديم أولًا
         * حتى لا تتراكم عدة مواعيد.
         */
        alarmManager.cancel(pending)

        val first =
            Calendar.getInstance().apply {

                add(
                    Calendar.MINUTE,
                    minutes
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }

        /*
         * استخدام AlarmManager لتشغيل التذكير
         * حتى أثناء وضع توفير الطاقة.
         */
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            first.timeInMillis,
            pending
        )
    }

    fun cancel(context: Context) {

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            )

        val pending =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pending)
    }
}
