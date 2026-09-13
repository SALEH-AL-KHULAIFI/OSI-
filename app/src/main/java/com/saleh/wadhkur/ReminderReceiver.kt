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

        private const val EXTRA_REMINDER_TYPE = "reminder_type"
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
         * إذا كان المستخدم أوقف التذكيرات،
         * لا نعرض أي تذكير.
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
         * معرفة نوع التذكير الذي تم تشغيله.
         */
        val reminderType =
            intent?.getStringExtra(
                EXTRA_REMINDER_TYPE
            ) ?: ReminderScheduler.TYPE_GENERAL

        /*
         * اختيار قائمة الأذكار حسب نوع التذكير.
         *
         * GENERAL  -> الأذكار العامة فقط
         * MORNING  -> أذكار الصباح فقط
         * EVENING  -> أذكار المساء فقط
         */
        val dhikrList =
            when (reminderType) {

                ReminderScheduler.TYPE_MORNING ->
                    DhikrRepository.morning

                ReminderScheduler.TYPE_EVENING ->
                    DhikrRepository.evening

                else ->
                    DhikrRepository.main
            }

        /*
         * إذا لم توجد أذكار في القائمة المطلوبة،
         * نعيد جدولة نفس النوع فقط.
         */
        if (dhikrList.isEmpty()) {

            when (reminderType) {

                ReminderScheduler.TYPE_MORNING ->
                    ReminderScheduler.scheduleMorning(context)

                ReminderScheduler.TYPE_EVENING ->
                    ReminderScheduler.scheduleEvening(context)

                else ->
                    ReminderScheduler.scheduleGeneral(context)
            }

            return
        }

        /*
         * اختيار ذكر عشوائي من القائمة الخاصة
         * بهذا النوع فقط.
         */
        val dhikr =
            dhikrList.random(Random.Default)

        /*
         * تحديد عنوان مناسب للتذكير.
         */
        val reminderTitle =
            when (reminderType) {

                ReminderScheduler.TYPE_MORNING ->
                    "أذكار الصباح"

                ReminderScheduler.TYPE_EVENING ->
                    "أذكار المساء"

                else ->
                    "ذكر"
            }

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
                    reminderTitle
                )

                putExtra(
                    EXTRA_REMINDER_TYPE,
                    reminderType
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
         * إنشاء قناة الإشعارات.
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
         * بناء الإشعار.
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
                    reminderTitle
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
         * إعادة جدولة نفس نوع التذكير فقط.
         *
         * لا يتم خلط الأذكار العامة
         * مع أذكار الصباح أو المساء.
         */
        when (reminderType) {

            ReminderScheduler.TYPE_MORNING ->
                ReminderScheduler.scheduleMorning(context)

            ReminderScheduler.TYPE_EVENING ->
                ReminderScheduler.scheduleEvening(context)

            else ->
                ReminderScheduler.scheduleGeneral(context)
        }
    }
}
