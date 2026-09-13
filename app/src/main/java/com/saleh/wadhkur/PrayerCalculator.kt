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

        val safeLatitude = latitude.coerceIn(-90.0, 90.0)
        val safeLongitude = longitude.coerceIn(-180.0, 180.0)

        val day = date.get(Calendar.DAY_OF_YEAR)

        val gamma =
            2.0 * Math.PI / 365.0 * (day - 1)

        val equationOfTime =
            229.18 * (
                0.000075 +
                    0.001868 * cos(gamma) -
                    0.032077 * sin(gamma) -
                    0.014615 * cos(2.0 * gamma) -
                    0.040849 * sin(2.0 * gamma)
                )

        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) +
                0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) +
                0.001480 * sin(3.0 * gamma)

        val timezone =
            date.timeZone.getOffset(date.timeInMillis) /
                3_600_000.0

        val solarNoon =
            720.0 -
                4.0 * safeLongitude -
                equationOfTime +
                60.0 * timezone

        val sunriseHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -0.833
            )

        val fajrHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -18.0
            )

        val ishaHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = -17.0
            )

        /*
         * العصر — طريقة الظل 1
         */
        val latitudeRadians =
            Math.toRadians(safeLatitude)

        val solarNoonAltitude =
            abs(
                latitudeRadians - declination
            )

        val asrAltitudeRadians =
            atan(
                1.0 /
                    (
                        1.0 +
                            tan(solarNoonAltitude)
                        )
            )

        val asrAltitude =
            Math.toDegrees(asrAltitudeRadians)

        val asrHourAngle =
            hourAngle(
                latitude = safeLatitude,
                declination = declination,
                altitude = asrAltitude
            )

        return PrayerTimes(
            fajr = formatTime(
                solarNoon -
                    4.0 * fajrHourAngle
            ),

            sunrise = formatTime(
                solarNoon -
                    4.0 * sunriseHourAngle
            ),

            dhuhr = formatTime(
                solarNoon
            ),

            asr = formatTime(
                solarNoon +
                    4.0 * asrHourAngle
            ),

            maghrib = formatTime(
                solarNoon +
                    4.0 * sunriseHourAngle
            ),

            isha = formatTime(
                solarNoon +
                    4.0 * ishaHourAngle
            )
        )
    }

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

        if (abs(denominator) < 1e-10) {
            return 0.0
        }

        var cosineHourAngle =
            (
                sin(altitudeRadians) -
                    sin(latitudeRadians) *
                    sin(declination)
                ) / denominator

        cosineHourAngle =
            cosineHourAngle.coerceIn(
                -1.0,
                1.0
            )

        return Math.toDegrees(
            acos(cosineHourAngle)
        )
    }

    private fun formatTime(
        minutes: Double
    ): String {

        var normalized =
            minutes % 1440.0

        if (normalized < 0.0) {
            normalized += 1440.0
        }

        var totalMinutes =
            normalized.roundToInt()

        totalMinutes %= 1440

        val hour24 =
            totalMinutes / 60

        val minute =
            totalMinutes % 60

        val suffix =
            if (hour24 >= 12) {
                "م"
            } else {
                "ص"
            }

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
