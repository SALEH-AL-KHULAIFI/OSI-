package com.saleh.wadhkur

import java.util.Calendar
import kotlin.math.*

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

object PrayerCalculator {

    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Calendar = Calendar.getInstance()
    ): PrayerTimes {

        val day = date.get(Calendar.DAY_OF_YEAR)

        val gamma = 2.0 * Math.PI / 365.0 * (day - 1)

        val eq = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
        )

        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val timezone =
            date.timeZone.getOffset(date.timeInMillis) / 3600000.0

        val noon =
            720.0 -
                4.0 * longitude -
                eq +
                60.0 * timezone

        // الشروق والغروب
        val sunriseH =
            hourAngle(latitude, decl, -0.833)

        // الفجر - زاوية 18 درجة
        val fajrH =
            hourAngle(latitude, decl, -18.0)

        // العشاء - زاوية 17 درجة
        val ishaH =
            hourAngle(latitude, decl, -17.0)

        /*
         * العصر - طريقة الظل 1
         *
         * في هذه الطريقة يكون طول ظل الجسم:
         * ظل الزوال + طول الجسم نفسه
         *
         * زاوية ارتفاع الشمس للعصر تحسب من:
         *
         * altitude = atan(
         *     1 / (shadowFactor + tan(abs(latitude - declination)))
         * )
         *
         * وهذا هو الحساب الصحيح تقريبًا لطريقة العصر
         * ذات معامل الظل 1.
         */
        val latRad = Math.toRadians(latitude)

        val shadowFactor = 1.0

        val asrAltitude = Math.toDegrees(
            atan(
                1.0 /
                    (
                        shadowFactor +
                            tan(abs(latRad - decl))
                    )
            )
        )

        val asrH =
            hourAngle(latitude, decl, asrAltitude)

        return PrayerTimes(
            fajr = format(noon - 4.0 * fajrH),

            sunrise = format(noon - 4.0 * sunriseH),

            dhuhr = format(noon),

            // العصر بعد الظهر
            asr = format(noon + 4.0 * asrH),

            // المغرب بعد الغروب
            maghrib = format(noon + 4.0 * sunriseH),

            // العشاء
            isha = format(noon + 4.0 * ishaH)
        )
    }

    /**
     * حساب زاوية الساعة للشمس.
     */
    private fun hourAngle(
        latitude: Double,
        declination: Double,
        altitude: Double
    ): Double {

        val lat = Math.toRadians(latitude)
        val alt = Math.toRadians(altitude)

        val denominator =
            cos(lat) * cos(declination)

        if (abs(denominator) < 1e-10) {
            return 0.0
        }

        var c =
            (
                sin(alt) -
                    sin(lat) * sin(declination)
                ) / denominator

        c = c.coerceIn(-1.0, 1.0)

        return Math.toDegrees(
            acos(c)
        )
    }

    /**
     * تحويل الدقائق إلى نظام 12 ساعة بالعربية.
     */
    private fun format(minutes: Double): String {

        var m = minutes % 1440.0

        if (m < 0) {
            m += 1440.0
        }

        val h = (m / 60).toInt()

        val min =
            (m - h * 60)
                .roundToInt()
                .coerceIn(0, 59)

        val hour = h % 24

        val suffix =
            if (hour >= 12) "م" else "ص"

        val display =
            when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }

        return "%02d:%02d %s".format(
            LocaleHelper.locale,
            display,
            min,
            suffix
        )
    }
}

private object LocaleHelper {

    val locale =
        java.util.Locale("ar")
}
