package com.saleh.wadhkur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        if (
            intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            /*
             * إعادة جدولة الأنواع الثلاثة:
             *
             * 1. الأذكار العامة حسب الفاصل المختار
             * 2. أذكار الصباح الساعة 06:00
             * 3. أذكار المساء الساعة 17:00
             */
            ReminderScheduler.scheduleAll(context)
        }
    }
}
