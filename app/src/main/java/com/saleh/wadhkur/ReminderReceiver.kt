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
        private const val NOTIFICATION_ID = 8800
        private const val PENDING_INTENT_REQUEST_CODE = 7400
    }

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        val prefs =
            context.getSharedPreferences(
                ReminderScheduler.PREFS,
                Context.MODE_PRIVATE
            )

        /*
         * إذا كان المستخدم أوقف التذكير،
         * لا نعرض شيئًا ولا نعيد الجدولة.
         */
        if (
            !prefs.getBoolean(
                ReminderScheduler.ENABLED,
                true
            )
        ) {
            return
        }

        /*
         * جمع جميع أنواع الأذكار.
         */
        val allDhikr =
            DhikrRepository.main +
            DhikrRepository.morning +
            DhikrRepository.evening

        if (allDhikr.isEmpty()) {
            ReminderScheduler.schedule(context)
            return
        }

        /*
         * اختيار ذكر عشوائي.
         */
        val dhikr =
            allDhikr.random(Random.Default)

        /*
         * الواجهة المخصصة للتذكير.
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
                PENDING_INTENT_REQUEST_CODE,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        /*
         * قناة جديدة لضمان أن مستوى الأهمية
         * High حتى لو كانت القناة القديمة
         * قد تم حفظ إعداداتها من النظام.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات وذكر",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "تنبيهات تذكير الأذكار من تطبيق وذكر"

                    enableVibration(true)

                    setSound(
                        null,
                        null
                    )

                    lockscreenVisibility =
                        android.app.Notification.VISIBILITY_PUBLIC
                }

            manager.createNotificationChannel(channel)
        }

        /*
         * بناء الإشعار كوسيلة احتياطية.
         *
         * عند السماح بخاصية Full Screen Intent
         * سيظهر ReminderActivity مباشرة.
         *
         * وإذا لم يسمح النظام بذلك، يبقى الإشعار
         * ظاهرًا ويمكن للمستخدم الضغط عليه لفتح
         * شاشة التذكير المخصصة.
         */
        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
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
                    NotificationCompat.CATEGORY_REMINDER
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setAutoCancel(true)
                .setContentIntent(
                    fullScreenPendingIntent
                )
                .setFullScreenIntent(
                    fullScreenPendingIntent,
                    true
                )
                .build()

        manager.notify(
            NOTIFICATION_ID,
            notification
        )

        /*
         * جدولة التذكير التالي بنفس الفاصل
         * الذي اختاره المستخدم.
         */
        ReminderScheduler.schedule(context)
    }
}
