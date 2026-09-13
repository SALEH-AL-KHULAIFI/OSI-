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

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        val prefs = context.getSharedPreferences(
            ReminderScheduler.PREFS,
            Context.MODE_PRIVATE
        )

        if (
            !prefs.getBoolean(
                ReminderScheduler.ENABLED,
                true
            )
        ) {
            return
        }

        // اختيار ذكر عشوائي من جميع الأقسام
        val allDhikr =
            DhikrRepository.main +
            DhikrRepository.morning +
            DhikrRepository.evening

        val dhikr =
            allDhikr.random(Random.Default)

        /*
         * الواجهة المخصصة للتذكير.
         *
         * Android الحديث لا يسمح عادةً بتشغيل Activity
         * مباشرة من الخلفية، لذلك نستخدم Full Screen Intent.
         */
        val fullScreenIntent =
            Intent(
                context,
                ReminderActivity::class.java
            ).apply {

                putExtra(
                    "dhikr_text",
                    dhikr.text
                )

                putExtra(
                    "dhikr_title",
                    dhikr.title
                )

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                context,
                7400,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        val channelId =
            "dhikr_reminders"

        if (Build.VERSION.SDK_INT >= 26) {

            manager.createNotificationChannel(

                NotificationChannel(
                    channelId,
                    context.getString(
                        R.string.channel_name
                    ),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        context.getString(
                            R.string.channel_description
                        )

                    enableVibration(true)

                    setSound(
                        null,
                        null
                    )
                }
            )
        }

        /*
         * إشعار احتياطي.
         *
         * إذا سمح Android بالـ Full Screen Intent
         * ستظهر ReminderActivity مباشرة.
         *
         * وإذا منعها النظام، يبقى هذا الإشعار
         * ويمكن للمستخدم الضغط عليه لفتح شاشة الذكر.
         */
        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )

                .setSmallIcon(
                    android.R.drawable.ic_popup_reminder
                )

                .setContentTitle(
                    "وٌ ذکْــر"
                )

                .setContentText(
                    dhikr.text
                )

                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(dhikr.text)
                )

                .setPriority(
                    NotificationCompat.PRIORITY_MAX
                )

                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )

                .setAutoCancel(true)

                .setOngoing(false)

                .setFullScreenIntent(
                    fullScreenPendingIntent,
                    true
                )

                .build()

        manager.notify(
            8800,
            notification
        )

        // إعادة جدولة التذكير القادم
        ReminderScheduler.schedule(context)
    }
}
