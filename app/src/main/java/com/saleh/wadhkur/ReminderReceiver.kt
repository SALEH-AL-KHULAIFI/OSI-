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

    companion object {
        private const val CHANNEL_ID = "dhikr_reminders_v2"
        private const val CHANNEL_NAME = "تذكيرات الذكر"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val type =
            intent.getStringExtra("type")
                ?: ReminderScheduler.TYPE_GENERAL

        val prefs =
            context.getSharedPreferences(
                ReminderScheduler.PREFS,
                Context.MODE_PRIVATE
            )

        val enabled = when (type) {

            ReminderScheduler.TYPE_MORNING ->
                prefs.getBoolean(
                    ReminderScheduler.MORNING_ENABLED,
                    true
                )

            ReminderScheduler.TYPE_EVENING ->
                prefs.getBoolean(
                    ReminderScheduler.EVENING_ENABLED,
                    true
                )

            else ->
                prefs.getBoolean(
                    ReminderScheduler.ENABLED,
                    true
                )
        }

        if (!enabled) {
            return
        }

        val dhikr = when (type) {

            ReminderScheduler.TYPE_MORNING ->
                getMorningDhikr()

            ReminderScheduler.TYPE_EVENING ->
                getEveningDhikr()

            else ->
                getGeneralDhikr()
        }

        showNotification(
            context = context,
            type = type,
            dhikr = dhikr
        )

        when (type) {

            ReminderScheduler.TYPE_MORNING -> {
                ReminderScheduler.scheduleMorning(
                    context
                )
            }

            ReminderScheduler.TYPE_EVENING -> {
                ReminderScheduler.scheduleEvening(
                    context
                )
            }

            else -> {
                ReminderScheduler.scheduleGeneral(
                    context
                )
            }
        }
    }

    // ---------------------------------------------------------
    // اختيار الذكر
    // ---------------------------------------------------------

    private fun getGeneralDhikr(): String {

        val list =
            DhikrRepository.main

        if (list.isEmpty()) {
            return "سبحان الله والحمد لله ولا إله إلا الله والله أكبر"
        }

        return list[
            Random.nextInt(list.size)
        ].text
    }

    private fun getMorningDhikr(): String {

        val list =
            DhikrRepository.morning

        if (list.isEmpty()) {
            return "أصبحنا وأصبح الملك لله"
        }

        return list[
            Random.nextInt(list.size)
        ].text
    }

    private fun getEveningDhikr(): String {

        val list =
            DhikrRepository.evening

        if (list.isEmpty()) {
            return "أمسينا وأمسى الملك لله"
        }

        return list[
            Random.nextInt(list.size)
        ].text
    }

    // ---------------------------------------------------------
    // الإشعار
    // ---------------------------------------------------------

    private fun showNotification(
        context: Context,
        type: String,
        dhikr: String
    ) {

        createNotificationChannel(
            context
        )

        val title =
            when (type) {

                ReminderScheduler.TYPE_MORNING ->
                    "🌅 أذكار الصباح"

                ReminderScheduler.TYPE_EVENING ->
                    "🌙 أذكار المساء"

                else ->
                    "🔔 تذكير بالذكر"
            }

        val activityIntent =
            Intent(
                context,
                ReminderActivity::class.java
            ).apply {

                putExtra(
                    "dhikr",
                    dhikr
                )

                putExtra(
                    "type",
                    type
                )

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationRequestCode(type),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    dhikr
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(dhikr)
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(true)
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .build()

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            notificationRequestCode(type),
            notification
        )
    }

    private fun notificationRequestCode(
        type: String
    ): Int {

        return when (type) {

            ReminderScheduler.TYPE_MORNING ->
                8101

            ReminderScheduler.TYPE_EVENING ->
                8102

            else ->
                8100
        }
    }

    // ---------------------------------------------------------
    // قناة الإشعارات
    // ---------------------------------------------------------

    private fun createNotificationChannel(
        context: Context
    ) {

        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val existing =
            manager.getNotificationChannel(
                CHANNEL_ID
            )

        if (existing != null) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "تذكيرات الأذكار في تطبيق وٌ ذکْــر"

                enableVibration(true)

                setShowBadge(true)
            }

        manager.createNotificationChannel(
            channel
        )
    }
}
