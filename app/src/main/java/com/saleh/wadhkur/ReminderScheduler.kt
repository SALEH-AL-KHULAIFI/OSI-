package com.saleh.wadhkur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {

    const val PREFS = "wadhkur_settings"

    // التذكير العام
    const val ENABLED = "reminder_enabled"
    const val INTERVAL = "reminder_interval_minutes"

    // التذكيرات المستقلة
    const val MORNING_ENABLED = "morning_enabled"
    const val EVENING_ENABLED = "evening_enabled"

    const val TYPE_GENERAL = "GENERAL"
    const val TYPE_MORNING = "MORNING"
    const val TYPE_EVENING = "EVENING"

    private const val REQUEST_GENERAL = 7300
    private const val REQUEST_MORNING = 7301
    private const val REQUEST_EVENING = 7302

    fun scheduleAll(context: Context) {

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val generalEnabled = prefs.getBoolean(
            ENABLED,
            true
        )

        val morningEnabled = prefs.getBoolean(
            MORNING_ENABLED,
            true
        )

        val eveningEnabled = prefs.getBoolean(
            EVENING_ENABLED,
            true
        )

        if (generalEnabled) {
            scheduleGeneral(context)
        } else {
            cancelGeneral(context)
        }

        if (morningEnabled) {
            scheduleMorning(context)
        } else {
            cancelMorning(context)
        }

        if (eveningEnabled) {
            scheduleEvening(context)
        } else {
            cancelEvening(context)
        }
    }

    // ---------------------------------------------------------
    // التذكير العام
    // ---------------------------------------------------------

    fun scheduleGeneral(context: Context) {

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(ENABLED, true)) {
            cancelGeneral(context)
            return
        }

        val interval = prefs.getInt(
            INTERVAL,
            30
        ).coerceIn(1, 60)

        val triggerAt =
            System.currentTimeMillis() +
                    interval * 60_000L

        schedule(
            context = context,
            type = TYPE_GENERAL,
            requestCode = REQUEST_GENERAL,
            triggerAtMillis = triggerAt
        )
    }

    fun cancelGeneral(context: Context) {

        cancel(
            context = context,
            type = TYPE_GENERAL,
            requestCode = REQUEST_GENERAL
        )
    }

    // ---------------------------------------------------------
    // أذكار الصباح
    // ---------------------------------------------------------

    fun scheduleMorning(context: Context) {

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(MORNING_ENABLED, true)) {
            cancelMorning(context)
            return
        }

        val calendar = Calendar.getInstance().apply {

            set(
                Calendar.HOUR_OF_DAY,
                6
            )

            set(
                Calendar.MINUTE,
                0
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )

            if (
                timeInMillis <=
                System.currentTimeMillis()
            ) {
                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }
        }

        schedule(
            context = context,
            type = TYPE_MORNING,
            requestCode = REQUEST_MORNING,
            triggerAtMillis = calendar.timeInMillis
        )
    }

    fun cancelMorning(context: Context) {

        cancel(
            context = context,
            type = TYPE_MORNING,
            requestCode = REQUEST_MORNING
        )
    }

    // ---------------------------------------------------------
    // أذكار المساء
    // ---------------------------------------------------------

    fun scheduleEvening(context: Context) {

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(EVENING_ENABLED, true)) {
            cancelEvening(context)
            return
        }

        val calendar = Calendar.getInstance().apply {

            set(
                Calendar.HOUR_OF_DAY,
                17
            )

            set(
                Calendar.MINUTE,
                0
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )

            if (
                timeInMillis <=
                System.currentTimeMillis()
            ) {
                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }
        }

        schedule(
            context = context,
            type = TYPE_EVENING,
            requestCode = REQUEST_EVENING,
            triggerAtMillis = calendar.timeInMillis
        )
    }

    fun cancelEvening(context: Context) {

        cancel(
            context = context,
            type = TYPE_EVENING,
            requestCode = REQUEST_EVENING
        )
    }

    // ---------------------------------------------------------
    // جدولة المنبه
    // ---------------------------------------------------------

    private fun schedule(
        context: Context,
        type: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {

        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                putExtra(
                    "type",
                    type
                )
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    // ---------------------------------------------------------
    // إلغاء المنبه
    // ---------------------------------------------------------

    private fun cancel(
        context: Context,
        type: String,
        requestCode: Int
    ) {

        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                putExtra(
                    "type",
                    type
                )
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(
            pendingIntent
        )

        pendingIntent.cancel()
    }
}