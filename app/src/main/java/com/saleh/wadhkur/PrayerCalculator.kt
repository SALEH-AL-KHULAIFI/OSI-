package com.saleh.wadhkur

import java.util.Calendar
import java.util.Locale
import kotlin.math.acos
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

        val safeLatitude = latitude.coerceIn(-90.0, 90.0)
        val safeLongitude = longitude.coerceIn(-180.0, 180.0)

        /*
         * اليوم من السنة.
         */
        val day = date.get(Calendar.DAY_OF_YEAR)

        /*
         * المعامل الفلكي.
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
         * فرق المنطقة الزمنية عن UTC بالساعات.
         *
         * نستخدم المنطقة الزمنية الفعلية للجهاز
         * مع أخذ التوقيت الصيفي إن وجد في الاعتبار.
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
         * الشروق والغروب.
         *
         * زاوية -0.833 درجة تأخذ انكسار الضوء
         * وقرص الشمس في الاعتبار.
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
         * العصر — طريقة الظل 1.
         *
         * نستخدم زاوية ارتفاع الشمس الناتجة
         * من طول الظل القياسي للمذهب الذي يعتمد
         * عامل الظل = 1.
         *
         * tan(altitude) =
         * 1 / (1 + tan(|latitude - declination|))
         */
        val latitudeRadians =
            Math.toRadians(safeLatitude)

        val solarNoonAltitude =
            kotlin.math.abs(
                latitudeRadians - declination
            )

        val asrAltitudeRadians =
            kotlin.math.atan(
                1.0 /
                    (
                        1.0 +
                            tan(solarNoonAltitude)
                        )
            )

        val asrAltitude =
            Math.toDegrees(
                asrAltitudeRadians
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
             * الفجر.
             */
            fajr = formatTime(
                solarNoon -
                    4.0 * fajrHourAngle
            ),

            /*
             * الشروق.
             */
            sunrise = formatTime(
                solarNoon -
                    4.0 * sunriseHourAngle
            ),

            /*
             * الظهر.
             */
            dhuhr = formatTime(
                solarNoon
            ),

            /*
             * العصر.
             */
            asr = formatTime(
                solarNoon +
                    4.0 * asrHourAngle
            ),

            /*
             * المغرب.
             */
            maghrib = formatTime(
                solarNoon +
                    4.0 * sunriseHourAngle
            ),

            /*
             * العشاء.
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
        if (
            kotlin.math.abs(denominator) < 1e-10
        ) {
            return 0.0
        }

        var cosineHourAngle =
            (
                sin(altitudeRadians) -
                    sin(latitudeRadians) *
                    sin(declination)
                ) / denominator

        /*
         * حماية من أخطاء الفاصلة العائمة.
         */
        cosineHourAngle =
            cosineHourAngle.coerceIn(
                -1.0,
                1.0
            )

        return Math.toDegrees(
            acos(cosineHourAngle)
        )
    }

    /**
     * تحويل الدقائق منذ منتصف الليل
     * إلى نظام 12 ساعة باللغة العربية.
     */
    private fun formatTime(
        minutes: Double
    ): String {

        /*
         * تطبيع الوقت إلى 24 ساعة.
         */
        var normalized =
            minutes % 1440.0

        if (normalized < 0.0) {
            normalized += 1440.0
        }

        /*
         * التقريب إلى أقرب دقيقة.
         */
        var totalMinutes =
            normalized.roundToInt()

        /*
         * حماية إضافية.
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
            Locale(\"ar\"),
            \"%02d:%02d %s\",
            displayHour,
            minute,
            suffix
        )
    }
            }
