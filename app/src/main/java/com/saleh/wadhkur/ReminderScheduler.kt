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

    /*
     * أنواع التذكيرات الثلاثة:
     *
     * GENERAL = الأذكار العامة
     * MORNING = أذكار الصباح
     * EVENING = أذكار المساء
     */
    const val TYPE_GENERAL = "GENERAL"
    const val TYPE_MORNING = "MORNING"
    const val TYPE_EVENING = "EVENING"

    private const val EXTRA_REMINDER_TYPE = "reminder_type"

    /*
     * أرقام مختلفة لكل تذكير،
     * حتى لا يلغي تذكيرٌ تذكيرًا آخر.
     */
    private const val REQUEST_CODE_GENERAL = 7300
    private const val REQUEST_CODE_MORNING = 7301
    private const val REQUEST_CODE_EVENING = 7302

    /*
     * جدولة جميع أنواع التذكيرات.
     */
    fun scheduleAll(context: Context) {

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
            cancelAll(context)
            return
        }

        scheduleGeneral(context)
        scheduleMorning(context)
        scheduleEvening(context)
    }

    /*
     * تذكير الأذكار العامة.
     *
     * يعتمد فقط على الفاصل الذي اختاره المستخدم:
     * 1، 3، 5، 10، 15، 30 أو 60 دقيقة.
     */
    fun scheduleGeneral(context: Context) {

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
            cancelGeneral(context)
            return
        }

        val minutes =
            prefs.getInt(
                INTERVAL,
                30
            ).coerceIn(1, 60)

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

        scheduleAlarm(
            context = context,
            type = TYPE_GENERAL,
            requestCode = REQUEST_CODE_GENERAL,
            triggerAtMillis = first.timeInMillis
        )
    }

    /*
     * تذكير أذكار الصباح.
     *
     * الموعد ثابت يوميًا الساعة 06:00 صباحًا.
     */
    fun scheduleMorning(context: Context) {

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
            cancelMorning(context)
            return
        }

        val nextMorning =
            Calendar.getInstance().apply {

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

                /*
                 * إذا كانت الساعة 06:00 قد مرت اليوم،
                 * ننتقل إلى الساعة 06:00 من اليوم التالي.
                 */
                if (
                    timeInMillis <=
                    Calendar.getInstance().timeInMillis
                ) {
                    add(
                        Calendar.DAY_OF_YEAR,
                        1
                    )
                }
            }

        scheduleAlarm(
            context = context,
            type = TYPE_MORNING,
            requestCode = REQUEST_CODE_MORNING,
            triggerAtMillis = nextMorning.timeInMillis
        )
    }

    /*
     * تذكير أذكار المساء.
     *
     * الموعد ثابت يوميًا الساعة 05:00 عصرًا.
     */
    fun scheduleEvening(context: Context) {

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
            cancelEvening(context)
            return
        }

        val nextEvening =
            Calendar.getInstance().apply {

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

                /*
                 * إذا كانت الساعة 05:00 عصرًا قد مرت اليوم،
                 * ننتقل إلى الساعة 05:00 من اليوم التالي.
                 */
                if (
                    timeInMillis <=
                    Calendar.getInstance().timeInMillis
                ) {
                    add(
                        Calendar.DAY_OF_YEAR,
                        1
                    )
                }
            }

        scheduleAlarm(
            context = context,
            type = TYPE_EVENING,
            requestCode = REQUEST_CODE_EVENING,
            triggerAtMillis = nextEvening.timeInMillis
        )
    }

    /*
     * إنشاء Alarm مستقل لكل نوع.
     */
    private fun scheduleAlarm(
        context: Context,
        type: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                putExtra(
                    EXTRA_REMINDER_TYPE,
                    type
                )
            }

        val pending =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        /*
         * إلغاء الموعد السابق لنفس النوع فقط.
         */
        alarmManager.cancel(pending)

        /*
         * تشغيل التذكير حتى أثناء وضع توفير الطاقة.
         */
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pending
        )
    }

    /*
     * إلغاء جميع التذكيرات.
     */
    fun cancelAll(context: Context) {

        cancelGeneral(context)
        cancelMorning(context)
        cancelEvening(context)
    }

    /*
     * إلغاء تذكير الأذكار العامة فقط.
     */
    fun cancelGeneral(context: Context) {

        cancelAlarm(
            context,
            TYPE_GENERAL,
            REQUEST_CODE_GENERAL
        )
    }

    /*
     * إلغاء تذكير أذكار الصباح فقط.
     */
    fun cancelMorning(context: Context) {

        cancelAlarm(
            context,
            TYPE_MORNING,
            REQUEST_CODE_MORNING
        )
    }

    /*
     * إلغاء تذكير أذكار المساء فقط.
     */
    fun cancelEvening(context: Context) {

        cancelAlarm(
            context,
            TYPE_EVENING,
            REQUEST_CODE_EVENING
        )
    }

    /*
     * إلغاء Alarm محدد.
     */
    private fun cancelAlarm(
        context: Context,
        type: String,
        requestCode: Int
    ) {

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                putExtra(
                    EXTRA_REMINDER_TYPE,
                    type
                )
            }

        val pending =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pending)
    }
}
