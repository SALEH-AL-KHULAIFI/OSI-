package com.saleh.wadhkur

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

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

        /*
         * حماية من الإحداثيات غير الصالحة.
         */
        val safeLatitude = latitude.coerceIn(-90.0, 90.0)
        val safeLongitude = longitude.coerceIn(-180.0, 180.0)

        /*
         * اليوم من السنة.
         */
        val day = date.get(Calendar.DAY_OF_YEAR)

        /*
         * معامل اليوم الفلكي.
         */
        val gamma =
            2.0 * Math.PI / 365.0 * (day - 1)

        /*
         * معادلة الزمن Equation of Time
         * بالدقائق.
         */
        val equationOfTime =
            229.18 * (
                0.000075 +
                    0.001868 * cos(gamma) -
                    0.032077 * sin(gamma) -
                    0.014615 * cos(2.0 * gamma) -
                    0.040849 * sin(2.0 * gamma)
                )

        /*
         * ميل الشمس Solar Declination
         * بالراديان.
         */
        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) +
                0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) +
                0.001480 * sin(3.0 * gamma)

        /*
         * فرق التوقيت المحلي عن UTC بالساعات.
         *
         * استخدام getOffset() أفضل من الاعتماد على رقم
         * ثابت، لأنه يأخذ المنطقة الزمنية الحالية للجهاز.
         */
        val timezone =
            date.timeZone.getOffset(date.timeInMillis) /
                3_600_000.0

        /*
         * الظهر الشمسي Solar Noon
         * بالدقائق منذ منتصف الليل المحلي.
         */
        val solarNoon =
            720.0 -
                4.0 * safeLongitude -
                equationOfTime +
                60.0 * timezone

        /*
         * زاوية الشروق والغروب.
         *
         * -0.833 درجة تأخذ انكسار الضوء وحجم قرص الشمس
         * في الاعتبار بصورة تقريبية.
         */
        val sunriseHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -0.833
            )

        /*
         * الفجر:
         * زاوية الشمس -18 درجة.
         */
        val fajrHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -18.0
            )

        /*
         * العشاء:
         * زاوية الشمس -17 درجة.
         */
        val ishaHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -17.0
            )

        /*
         * العصر - طريقة الظل 1.
         *
         * طول الظل = ظل الزوال + طول الجسم.
         */
        val latitudeRadians =
            Math.toRadians(safeLatitude)

        val asrShadowFactor = 1.0

        val asrAltitude =
            Math.toDegrees(
                atan(
                    1.0 /
                        (
                            asrShadowFactor +
                                tan(
                                    abs(
                                        latitudeRadians -
                                            declination
                                    )
                                )
                            )
                )
            )

        val asrHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = asrAltitude
            )

        /*
         * تحويل النتائج إلى أوقات محلية.
         */
        return PrayerTimes(
            /*
             * الفجر قبل الظهر.
             */
            fajr = formatTime(
                solarNoon -
                    4.0 * fajrHourAngle
            ),

            /*
             * الشروق قبل الظهر.
             */
            sunrise = formatTime(
                solarNoon -
                    4.0 * sunriseHourAngle
            ),

            /*
             * الظهر الشمسي.
             */
            dhuhr = formatTime(
                solarNoon
            ),

            /*
             * العصر بعد الظهر.
             */
            asr = formatTime(
                solarNoon +
                    4.0 * asrHourAngle
            ),

            /*
             * المغرب بعد الغروب.
             */
            maghrib = formatTime(
                solarNoon +
                    4.0 * sunriseHourAngle
            ),

            /*
             * العشاء بعد المغرب.
             */
            isha = formatTime(
                solarNoon +
                    4.0 * ishaHourAngle
            )
        )
    }

    /**
     * حساب زاوية الساعة للشمس.
     *
     * latitude:
     * خط العرض بالدرجات.
     *
     * declination:
     * ميل الشمس بالراديان.
     *
     * altitude:
     * ارتفاع الشمس المطلوب بالدرجات.
     */
    private fun hourAngle(
        latitude: Double,
        declination: Double,
        altitude: Double
    ): Double {

        val latitudeRadians =
            Math.toRadians(latitude)

        val altitudeRadians =
            Math.toRadians(altitude)

        val denominator =
            cos(latitudeRadians) *
                cos(declination)

        /*
         * حماية من القسمة على صفر.
         */
        if (abs(denominator) < 1e-10) {
            return 0.0
        }

        var cosineHourAngle =
            (
                sin(altitudeRadians) -
                    sin(latitudeRadians) *
                    sin(declination)
                ) / denominator

        /*
         * منع أخطاء الفاصلة العائمة من إنتاج قيمة
         * خارج المجال [-1, 1].
         */
        cosineHourAngle =
            cosineHourAngle.coerceIn(-1.0, 1.0)

        return Math.toDegrees(
            acos(cosineHourAngle)
        )
    }

    /**
     * تحويل الدقائق منذ منتصف الليل إلى
     * نظام 12 ساعة باللغة العربية.
     */
    private fun formatTime(
        minutes: Double
    ): String {

        /*
         * تطبيع الوقت إلى نطاق 24 ساعة.
         */
        var normalized =
            minutes % 1440.0

        if (normalized < 0.0) {
            normalized += 1440.0
        }

        /*
         * تقريب الوقت إلى أقرب دقيقة.
         *
         * هذا أفضل من قص الثواني فقط.
         */
        var totalMinutes =
            normalized.roundToInt()

        /*
         * حماية إضافية إذا أصبح الناتج 1440
         * بعد التقريب.
         */
        totalMinutes %= 1440

        val hour24 =
            totalMinutes / 60

        val minute =
            totalMinutes % 60

        /*
         * صباح / مساء.
         */
        val suffix =
            if (hour24 >= 12) {
                "م"
            } else {
                "ص"
            }

        /*
         * تحويل 24 ساعة إلى 12 ساعة.
         */
        val displayHour =
            when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }

        return String.format(
            Locale("ar"),
            "%02d:%02d %s",
            displayHour,
            minute,
            suffix
        )
    }
}
