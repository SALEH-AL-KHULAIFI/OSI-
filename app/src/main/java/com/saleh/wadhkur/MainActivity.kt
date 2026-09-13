package com.saleh.wadhkur

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private val green = Color(0xFF39FF8F)
    private val cyan = Color(0xFF55DCFF)
    private val purple = Color(0xFFB56CFF)
    private val bg = Color(0xFF061016)
    private val card = Color(0xFF0D1B23)
    private val card2 = Color(0xFF101F28)
    private val muted = Color(0xFF9AAFB8)

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            saveBestLocation()

            Toast.makeText(
                this,
                "تم تحديث موقع مواقيت الصلاة",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "لم يتم السماح بالموقع",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotifications()
        requestLocation()

        val requestedDhikr =
            intent?.getStringExtra("show_dhikr")

        setContent {
            WadhkurTheme {
                WadhkurApp(
                    requestedDhikr = requestedDhikr,
                    onRequestLocation = {
                        requestLocation()
                    }
                )
            }
        }

        ReminderScheduler.scheduleAll(this)
    }

    private fun requestNotifications() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                9001
            )
        }
    }

    private fun requestLocation() {

        val fine =
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {

            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

        } else {
            saveBestLocation()
        }
    }

    /*
     * الحصول على موقع الهاتف بشكل موثوق.
     *
     * لا نعتمد فقط على LastKnownLocation.
     * نستخدم الموقع المحفوظ كاستجابة سريعة إن وجد،
     * ثم نطلب موقعًا حديثًا من النظام لتحديث الإحداثيات.
     */
    private fun saveBestLocation() {

        val manager =
            getSystemService(Context.LOCATION_SERVICE)
                    as LocationManager

        val hasFine =
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse =
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return
        }

        val prefs =
            getSharedPreferences(
                "wadhkur_location",
                MODE_PRIVATE
            )

        var locationSaved = false

        fun saveLocation(location: Location) {

            if (
                !location.latitude.isFinite() ||
                !location.longitude.isFinite()
            ) {
                return
            }

            if (
                location.latitude !in -90.0..90.0 ||
                location.longitude !in -180.0..180.0
            ) {
                return
            }

            prefs.edit()
                .putFloat(
                    "lat",
                    location.latitude.toFloat()
                )
                .putFloat(
                    "lon",
                    location.longitude.toFloat()
                )
                .apply()

            locationSaved = true

            runOnUiThread {
                recreate()
            }
        }

        try {

            /*
             * التأكد أولًا من أن خدمة الموقع مفعلة.
             */
            val locationEnabled =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.isLocationEnabled
                } else {
                    manager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                    ) ||
                        manager.isProviderEnabled(
                            LocationManager.NETWORK_PROVIDER
                        )
                }

            if (!locationEnabled) {

                Toast.makeText(
                    this,
                    "فعّل خدمة الموقع في الهاتف",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            /*
             * نحصل على آخر موقع متاح من جميع المزودين.
             * هذا يعطي نتيجة سريعة إذا كان النظام يملك موقعًا محفوظًا.
             */
            var bestLocation: Location? = null

            val providers =
                manager.getProviders(true)

            for (provider in providers) {

                try {

                    val location =
                        manager.getLastKnownLocation(provider)

                    if (location != null) {

                        if (
                            bestLocation == null ||
                            location.accuracy <
                            bestLocation!!.accuracy
                        ) {
                            bestLocation = location
                        }
                    }

                } catch (_: SecurityException) {
                }
            }

            if (bestLocation != null) {
                saveLocation(bestLocation!!)
            }

            /*
             * بعد الموقع المحفوظ، نطلب موقعًا حديثًا أيضًا.
             *
             * Android 11 وما بعده:
             * نستخدم getCurrentLocation للحصول على قراءة حديثة
             * بدل الاعتماد على موقع قديم.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val provider =
                    when {
                        manager.isProviderEnabled(
                            LocationManager.NETWORK_PROVIDER
                        ) ->
                            LocationManager.NETWORK_PROVIDER

                        manager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER
                        ) ->
                            LocationManager.GPS_PROVIDER

                        else -> null
                    }

                if (provider == null) {
                    if (!locationSaved) {
                        Toast.makeText(
                            this,
                            "تعذر تحديد موقع الهاتف",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }

                manager.getCurrentLocation(
                    provider,
                    null,
                    mainExecutor
                ) { location ->

                    if (location != null) {
                        saveLocation(location)
                    } else if (!locationSaved) {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "تعذر الحصول على الموقع الحالي",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

            } else {

                /*
                 * للأجهزة الأقدم من Android 11:
                 * نستخدم LocationListener للحصول على موقع حديث.
                 */
                val provider =
                    when {
                        manager.isProviderEnabled(
                            LocationManager.NETWORK_PROVIDER
                        ) ->
                            LocationManager.NETWORK_PROVIDER

                        manager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER
                        ) ->
                            LocationManager.GPS_PROVIDER

                        else -> null
                    }

                if (provider == null) {

                    if (!locationSaved) {
                        Toast.makeText(
                            this,
                            "تعذر تحديد موقع الهاتف",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    return
                }

                val listener =
                    object : android.location.LocationListener {

                        override fun onLocationChanged(
                            location: Location
                        ) {
                            saveLocation(location)

                            try {
                                manager.removeUpdates(this)
                            } catch (_: Exception) {
                            }
                        }
                    }

                manager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    android.os.Looper.getMainLooper()
                )
            }

        } catch (_: SecurityException) {

            Toast.makeText(
                this,
                "لا يوجد إذن للوصول إلى الموقع",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "تعذر الحصول على الموقع",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @Composable
    private fun WadhkurApp(
        requestedDhikr: String?,
        onRequestLocation: () -> Unit
    ) {

        var screen by remember {
            mutableStateOf("home")
        }

        var popup by remember {
            mutableStateOf(requestedDhikr)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
        ) {

            when (screen) {

                "home" -> HomeScreen(
                    onDhikr = {
                        screen = "dhikr"
                    },
                    onReminders = {
                        screen = "reminders"
                    },
                    onPrayer = {
                        screen = "prayer"
                    },
                    onAbout = {
                        screen = "about"
                    },
                    onTasbeeh = {
                        screen = "tasbeeh"
                    }
                )

                "dhikr" -> DhikrScreen {
                    screen = "home"
                }

                "reminders" -> ReminderScreen {
                    screen = "home"
                }

                "prayer" -> PrayerScreen(
                    onBack = {
                        screen = "home"
                    },
                    onLocation = onRequestLocation
                )

                "tasbeeh" -> TasbeehScreen {
                    screen = "home"
                }

                "about" -> AboutScreen {
                    screen = "home"
                }
            }

            if (popup != null) {

                DhikrPopup(
                    text = popup!!,
                    onDismiss = {
                        popup = null
                    }
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        onDhikr: () -> Unit,
        onReminders: () -> Unit,
        onPrayer: () -> Unit,
        onAbout: () -> Unit,
        onTasbeeh: () -> Unit
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Header()
            }

            item {
                RamadanCounter()
            }

            item {
                NextPrayerCard()
            }

            item {
                MainCard(
                    icon = "🤲",
                    title = "الأدعية والأذكار",
                    subtitle = "أذكار عامة وصباح ومساء",
                    action = onDhikr
                )
            }

            item {
                MainCard(
                    icon = "🔔",
                    title = "تذكير الذكر",
                    subtitle = "تذكيرات عامة وأذكار الصباح والمساء",
                    action = onReminders
                )
            }

            item {
                MainCard(
                    icon = "🕌",
                    title = "مواقيت الصلاة",
                    subtitle = "حساب محلي حسب موقع الهاتف",
                    action = onPrayer
                )
            }

            item {
                MainCard(
                    icon = "📿",
                    title = "المسبحة",
                    subtitle = "عداد تسبيح مع أهداف متعددة",
                    action = onTasbeeh
                )
            }

            item {
                MainCard(
                    icon = "ℹ️",
                    title = "عن التطبيق",
                    subtitle = "وٌ ذکْــر 3.0.0 • صالح الخليفي",
                    action = onAbout
                )
            }
        }
    }

    @Composable
    private fun Header() {

        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "وٌ ذکْــر",
                color = green,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Text(
                text = "وَاذْكُر رَّبَّكَ إِذَا نَسِيتَ",
                color = muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun RamadanCounter() {

        val remaining = remember {
            mutableStateOf(
                getRamadanRemaining()
            )
        }

        LaunchedEffect(Unit) {

            while (true) {

                remaining.value =
                    getRamadanRemaining()

                delay(1000L)
            }
        }

        val data = remaining.value

        NeonCard(
            borderColor = purple
        ) {

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "🌙  رمضان",
                    color = purple,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(5.dp)
                )

                Text(
                    "متبقي على رمضان القادم",
                    color = muted,
                    fontSize = 13.sp
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceEvenly
                ) {

                    CountdownUnit(
                        value = data.days,
                        label = "يوم"
                    )

                    CountdownUnit(
                        value = data.hours,
                        label = "ساعة"
                    )

                    CountdownUnit(
                        value = data.minutes,
                        label = "دقيقة"
                    )

                    CountdownUnit(
                        value = data.seconds,
                        label = "ثانية"
                    )
                }
            }
        }
    }

    private data class RamadanRemaining(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long
    )

    private fun getRamadanRemaining(): RamadanRemaining {

        val target = Calendar.getInstance().apply {

            set(
                Calendar.YEAR,
                2027
            )

            set(
                Calendar.MONTH,
                Calendar.FEBRUARY
            )

            set(
                Calendar.DAY_OF_MONTH,
                8
            )

            set(
                Calendar.HOUR_OF_DAY,
                0
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
        }

        val now =
            Calendar.getInstance()

        var difference =
            target.timeInMillis -
                now.timeInMillis

        if (difference < 0) {
            difference = 0
        }

        val totalSeconds =
            difference / 1000L

        val days =
            totalSeconds / 86400L

        val hours =
            (totalSeconds % 86400L) / 3600L

        val minutes =
            (totalSeconds % 3600L) / 60L

        val seconds =
            totalSeconds % 60L

        return RamadanRemaining(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
    }

    @Composable
    private fun CountdownUnit(
        value: Long,
        label: String
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "%02d".format(
                    Locale.US,
                    value
                ),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = muted,
                fontSize = 10.sp
            )
        }
    }

    @Composable
    private fun NextPrayerCard() {

        val locationPrefs =
            getSharedPreferences(
                "wadhkur_location",
                MODE_PRIVATE
            )

        val lat =
            locationPrefs.getFloat(
                "lat",
                Float.NaN
            )

        val lon =
            locationPrefs.getFloat(
                "lon",
                Float.NaN
            )

        if (lat.isNaN() || lon.isNaN()) {

            NeonCard(
                borderColor = cyan
            ) {

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        "🕌  الصلاة القادمة",
                        color = cyan,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "اسمح بالموقع لحساب الصلاة القادمة",
                        color = muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            return
        }

        var nowMillis by remember {
            mutableLongStateOf(
                System.currentTimeMillis()
            )
        }

        LaunchedEffect(Unit) {

            while (true) {

                nowMillis =
                    System.currentTimeMillis()

                delay(1000L)
            }
        }

        val info =
            remember(
                lat,
                lon,
                nowMillis / 1000L
            ) {
                getNextPrayerInfo(
                    lat.toDouble(),
                    lon.toDouble()
                )
            }

        NeonCard(
            borderColor = green
        ) {

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "🕌  الصلاة القادمة",
                    color = green,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    info.name,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(2.dp)
                )

                Text(
                    info.timeText,
                    color = cyan,
                    fontSize = 16.sp
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    formatDuration(
                        info.remainingMillis
                    ),
                    color = green,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(3.dp)
                )

                Text(
                    "الوقت المتبقي",
                    color = muted,
                    fontSize = 11.sp
                )
            }
        }
    }

    private data class NextPrayerInfo(
        val name: String,
        val timeText: String,
        val remainingMillis: Long
    )

    private fun getNextPrayerInfo(
        latitude: Double,
        longitude: Double
    ): NextPrayerInfo {

        val now =
            Calendar.getInstance()

        val todayTimes =
            PrayerCalculator.calculate(
                latitude,
                longitude,
                now
            )

        val todayList =
            prayerList(todayTimes)

        for (item in todayList) {

            val time =
                parsePrayerTime(
                    item.second,
                    now
                )

            if (
                time != null &&
                time.timeInMillis > now.timeInMillis
            ) {

                return NextPrayerInfo(
                    name = item.first,
                    timeText = item.second,
                    remainingMillis =
                        time.timeInMillis -
                            now.timeInMillis
                )
            }
        }

        val tomorrow =
            Calendar.getInstance().apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }

        val tomorrowTimes =
            PrayerCalculator.calculate(
                latitude,
                longitude,
                tomorrow
            )

        val fajrTime =
            parsePrayerTime(
                tomorrowTimes.fajr,
                tomorrow
            )

        val remaining =
            if (fajrTime != null) {
                fajrTime.timeInMillis -
                    now.timeInMillis
            } else {
                0L
            }

        return NextPrayerInfo(
            name = "الفجر",
            timeText = tomorrowTimes.fajr,
            remainingMillis =
                max(
                    0L,
                    remaining
                )
        )
    }

    private fun prayerList(
        times: PrayerTimes
    ): List<Pair<String, String>> {

        return listOf(
            "الفجر" to times.fajr,
            "الظهر" to times.dhuhr,
            "العصر" to times.asr,
            "المغرب" to times.maghrib,
            "العشاء" to times.isha
        )
    }

    private fun parsePrayerTime(
        value: String,
        base: Calendar
    ): Calendar? {

        return try {

            val parts =
                value.trim()
                    .split(" ")

            if (parts.size < 2) {
                return null
            }

            val hm =
                parts[0].split(":")

            if (hm.size != 2) {
                return null
            }

            var hour =
                hm[0].toInt()

            val minute =
                hm[1].toInt()

            val suffix =
                parts[1]

            if (suffix == "م" && hour < 12) {
                hour += 12
            }

            if (suffix == "ص" && hour == 12) {
                hour = 0
            }

            Calendar.getInstance().apply {

                timeInMillis =
                    base.timeInMillis

                set(
                    Calendar.HOUR_OF_DAY,
                    hour
                )

                set(
                    Calendar.MINUTE,
                    minute
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

        } catch (_: Exception) {
            null
        }
    }

    private fun formatDuration(
        millis: Long
    ): String {

        val total =
            max(
                0L,
                millis
            ) / 1000L

        val hours =
            total / 3600L

        val minutes =
            (total % 3600L) / 60L

        val seconds =
            total % 60L

        return "%02d:%02d:%02d".format(
            Locale.US,
            hours,
            minutes,
            seconds
        )
    }

    @Composable
    private fun MainCard(
        icon: String,
        title: String,
        subtitle: String,
        action: () -> Unit
    ) {

        Card(
            onClick = action,
            colors = CardDefaults.cardColors(
                containerColor = card
            ),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                Modifier.padding(18.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    icon,
                    fontSize = 30.sp
                )

                Spacer(
                    Modifier.width(16.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        subtitle,
                        color = muted,
                        fontSize = 13.sp
                    )
                }

                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = green
                )
            }
        }
    }

    @Composable
    private fun NeonCard(
        borderColor: Color,
        content: @Composable ColumnScope.() -> Unit
    ) {

        Card(
            colors = CardDefaults.cardColors(
                containerColor = card
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = borderColor.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(24.dp)
                ),
            content = content
        )
    }

    @Composable
    private fun DhikrScreen(
        onBack: () -> Unit
    ) {

        var selectedTab by rememberSaveable {
            mutableIntStateOf(0)
        }

        var index by rememberSaveable {
            mutableIntStateOf(0)
        }

        val lists = listOf(
            DhikrRepository.main,
            DhikrRepository.morning,
            DhikrRepository.evening
        )

        val titles = listOf(
            "الأذكار العامة",
            "أذكار الصباح",
            "أذكار المساء"
        )

        val currentList =
            lists[selectedTab]

        if (index >= currentList.size) {
            index = 0
        }

        val currentDhikr =
            currentList.getOrNull(index)

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            TopBar(
                titles[selectedTab],
                onBack
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = card,
                contentColor = green
            ) {

                titles.forEachIndexed { tabIndex, _ ->

                    Tab(
                        selected =
                            selectedTab == tabIndex,

                        onClick = {
                            selectedTab = tabIndex
                            index = 0
                        },

                        text = {
                            Text(
                                when (tabIndex) {
                                    0 -> "عامة"
                                    1 -> "الصباح"
                                    else -> "المساء"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(
                Modifier.height(20.dp)
            )

            if (currentDhikr != null) {

                Text(
                    "${index + 1} / ${currentList.size}",
                    color = cyan,
                    fontSize = 14.sp,
                    modifier =
                        Modifier.fillMaxWidth(),
                    textAlign =
                        TextAlign.Center
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                NeonCard(
                    borderColor = green
                ) {

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 300.dp,
                                max = 500.dp
                            )
                            .padding(24.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {

                        Text(
                            when (selectedTab) {
                                1 -> "☀️"
                                2 -> "🌙"
                                else -> "🤲"
                            },
                            fontSize = 42.sp
                        )

                        Spacer(
                            Modifier.height(20.dp)
                        )

                        Text(
                            currentDhikr.title,
                            color = green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            Modifier.height(18.dp)
                        )

                        Text(
                            currentDhikr.text,
                            color = Color.White,
                            fontSize = 23.sp,
                            lineHeight = 38.sp,
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                Spacer(
                    Modifier.height(18.dp)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedButton(
                        onClick = {
                            if (index > 0) {
                                index--
                            }
                        },
                        enabled = index > 0,
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription =
                                "السابق"
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Text("السابق")
                    }

                    Button(
                        onClick = {
                            if (
                                index <
                                currentList.lastIndex
                            ) {
                                index++
                            } else {
                                index = 0
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            if (
                                index ==
                                currentList.lastIndex
                            ) {
                                "من البداية"
                            } else {
                                "التالي"
                            }
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription =
                                "التالي"
                        )
                    }
                }

            } else {

                Text(
                    "لا توجد أذكار في هذا القسم",
                    color = Color.White,
                    modifier =
                        Modifier.fillMaxWidth(),
                    textAlign =
                        TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun ReminderScreen(
        onBack: () -> Unit
    ) {

        val prefs =
            getSharedPreferences(
                ReminderScheduler.PREFS,
                Context.MODE_PRIVATE
            )

        var generalEnabled by remember {
            mutableStateOf(
                prefs.getBoolean(
                    ReminderScheduler.ENABLED,
                    true
                )
            )
        }

        var morningEnabled by remember {
            mutableStateOf(
                prefs.getBoolean(
                    "morning_enabled",
                    true
                )
            )
        }

        var eveningEnabled by remember {
            mutableStateOf(
                prefs.getBoolean(
                    "evening_enabled",
                    true
                )
            )
        }

        var interval by remember {
            mutableIntStateOf(
                prefs.getInt(
                    ReminderScheduler.INTERVAL,
                    30
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {
                TopBar(
                    "إعدادات التذكيرات",
                    onBack
                )
            }

            item {
                ReminderCard(
                    icon = "🔔",
                    title = "التذكير العام",
                    description =
                        "ذكر عام يظهر لك حسب الفاصل الزمني الذي تختاره.",
                    enabled = generalEnabled,
                    onEnabledChange = { enabled ->

                        generalEnabled = enabled

                        prefs.edit()
                            .putBoolean(
                                ReminderScheduler.ENABLED,
                                enabled
                            )
                            .apply()

                        if (enabled) {
                            ReminderScheduler.scheduleGeneral(
                                this@MainActivity
                            )
                        } else {
                            ReminderScheduler.cancelGeneral(
                                this@MainActivity
                            )
                        }
                    }
                ) {

                    Text(
                        "الفاصل الزمني",
                        color = cyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    val choices =
                        listOf(
                            1,
                            3,
                            5,
                            10,
                            15,
                            30,
                            60
                        )

                    choices.chunked(3)
                        .forEach { row ->

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {

                                row.forEach { value ->

                                    FilterChip(
                                        selected =
                                            interval == value,

                                        onClick = {

                                            interval = value

                                            prefs.edit()
                                                .putInt(
                                                    ReminderScheduler.INTERVAL,
                                                    value
                                                )
                                                .apply()

                                            if (
                                                generalEnabled
                                            ) {
                                                ReminderScheduler
                                                    .scheduleGeneral(
                                                        this@MainActivity
                                                    )
                                            }
                                        },

                                        label = {
                                            Text(
                                                if (
                                                    value == 60
                                                ) {
                                                    "ساعة"
                                                } else {
                                                    "$value د"
                                                }
                                            )
                                        },

                                        modifier =
                                            Modifier.weight(1f)
                                    )
                                }

                                repeat(
                                    3 - row.size
                                ) {
                                    Spacer(
                                        Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(
                                Modifier.height(8.dp)
                            )
                        }
                }
            }

            item {
                ReminderCard(
                    icon = "🌅",
                    title = "أذكار الصباح",
                    description =
                        "تذكير مستقل بأذكار الصباح يوميًا الساعة 06:00.",
                    enabled = morningEnabled,
                    onEnabledChange = { enabled ->

                        morningEnabled = enabled

                        prefs.edit()
                            .putBoolean(
                                "morning_enabled",
                                enabled
                            )
                            .apply()

                        if (enabled) {
                            ReminderScheduler.scheduleMorning(
                                this@MainActivity
                            )
                        } else {
                            ReminderScheduler.cancelMorning(
                                this@MainActivity
                            )
                        }
                    }
                ) {

                    Text(
                        "الوقت: 06:00 صباحًا",
                        color = cyan,
                        fontSize = 15.sp
                    )
                }
            }

            item {
                ReminderCard(
                    icon = "🌙",
                    title = "أذكار المساء",
                    description =
                        "تذكير مستقل بأذكار المساء يوميًا الساعة 05:00 عصرًا.",
                    enabled = eveningEnabled,
                    onEnabledChange = { enabled ->

                        eveningEnabled = enabled

                        prefs.edit()
                            .putBoolean(
                                "evening_enabled",
                                enabled
                            )
                            .apply()

                        if (enabled) {
                            ReminderScheduler.scheduleEvening(
                                this@MainActivity
                            )
                        } else {
                            ReminderScheduler.cancelEvening(
                                this@MainActivity
                            )
                        }
                    }
                ) {

                    Text(
                        "الوقت: 05:00 عصرًا",
                        color = cyan,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun ReminderCard(
        icon: String,
        title: String,
        description: String,
        enabled: Boolean,
        onEnabledChange: (Boolean) -> Unit,
        extra: @Composable ColumnScope.() -> Unit
    ) {

        NeonCard(
            borderColor =
                if (enabled) green else muted
        ) {

            Column(
                Modifier.padding(18.dp)
            ) {

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        icon,
                        fontSize = 30.sp
                    )

                    Spacer(
                        Modifier.width(12.dp)
                    )

                    Column(
                        Modifier.weight(1f)
                    ) {

                        Text(
                            title,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            description,
                            color = muted,
                            fontSize = 12.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange =
                            onEnabledChange
                    )
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                extra()
            }
        }
    }

    @Composable
    private fun PrayerScreen(
        onBack: () -> Unit,
        onLocation: () -> Unit
    ) {

        val locationPrefs =
            getSharedPreferences(
                "wadhkur_location",
                MODE_PRIVATE
            )

        val lat =
            locationPrefs.getFloat(
                "lat",
                Float.NaN
            )

        val lon =
            locationPrefs.getFloat(
                "lon",
                Float.NaN
            )

        val now =
            Calendar.getInstance()

        val times =
            if (
                !lat.isNaN() &&
                !lon.isNaN()
            ) {
                PrayerCalculator.calculate(
                    lat.toDouble(),
                    lon.toDouble(),
                    now
                )
            } else {
                null
            }

        val nextName =
            if (
                times != null
            ) {
                getNextPrayerInfo(
                    lat.toDouble(),
                    lon.toDouble()
                ).name
            } else {
                ""
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {

            item {
                TopBar(
                    "مواقيت الصلاة",
                    onBack
                )
            }

            if (times == null) {

                item {

                    NeonCard(
                        borderColor = cyan
                    ) {

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                "🕌",
                                fontSize = 48.sp
                            )

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            Text(
                                "نحتاج إلى موقع الهاتف",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            Text(
                                "يتم حساب مواقيت الصلاة محليًا حسب موقعك.",
                                color = muted,
                                textAlign =
                                    TextAlign.Center
                            )

                            Spacer(
                                Modifier.height(16.dp)
                            )

                            Button(
                                onClick = onLocation
                            ) {
                                Text(
                                    "السماح بالموقع"
                                )
                            }
                        }
                    }
                }

            } else {

                item {

                    NeonCard(
                        borderColor = green
                    ) {

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                "الصلاة القادمة",
                                color = green,
                                fontSize = 14.sp
                            )

                            Spacer(
                                Modifier.height(5.dp)
                            )

                            Text(
                                nextName,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(5.dp)
                            )

                            Text(
                                "العداد موجود في الصفحة الرئيسية",
                                color = muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                val rows =
                    prayerList(times)

                items(rows) { item ->

                    val isNext =
                        item.first == nextName

                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isNext) {
                                        Color(0xFF102B27)
                                    } else {
                                        card
                                    }
                            ),
                        shape =
                            RoundedCornerShape(18.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isNext) {
                                        Modifier.border(
                                            1.dp,
                                            green,
                                            RoundedCornerShape(
                                                18.dp
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                    ) {

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 18.dp,
                                    vertical = 16.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                when (item.first) {
                                    "الفجر" -> "🌅"
                                    "الظهر" -> "☀️"
                                    "العصر" -> "🌤️"
                                    "المغرب" -> "🌇"
                                    "العشاء" -> "🌙"
                                    else -> "🕌"
                                },
                                fontSize = 25.sp
                            )

                            Spacer(
                                Modifier.width(12.dp)
                            )

                            Text(
                                item.first,
                                color =
                                    if (isNext) {
                                        green
                                    } else {
                                        Color.White
                                    },
                                fontSize = 18.sp,
                                fontWeight =
                                    if (isNext) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                modifier =
                                    Modifier.weight(1f)
                            )

                            Text(
                                item.second,
                                color =
                                    if (isNext) {
                                        green
                                    } else {
                                        cyan
                                    },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {

                    TextButton(
                        onClick = onLocation,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "📍 تحديث الموقع",
                            color = cyan
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TasbeehScreen(
        onBack: () -> Unit
    ) {

        val adhkar = listOf(
            "سبحان الله",
            "الحمد لله",
            "أستغفر الله",
            "لا إله إلا الله",
            "اللهم صل وسلم وبارك على نبينا محمد",
            "لا حول ولا قوة إلا بالله"
        )

        var selectedDhikr by rememberSaveable {
            mutableStateOf(adhkar[0])
        }

        var count by rememberSaveable {
            mutableIntStateOf(0)
        }

        var target by rememberSaveable {
            mutableIntStateOf(33)
        }

        var expandedDhikr by remember {
            mutableStateOf(false)
        }

        var expandedTarget by remember {
            mutableStateOf(false)
        }

        val progress =
            if (target <= 0) {
                0f
            } else {
                (
                    count.toFloat() /
                        target.toFloat()
                    ).coerceIn(
                        0f,
                        1f
                    )
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            item {
                TopBar(
                    "المسبحة",
                    onBack
                )
            }

            item {
                Box {

                    OutlinedButton(
                        onClick = {
                            expandedDhikr = true
                        }
                    ) {

                        Text(
                            selectedDhikr,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        Text(
                            "▼",
                            color = cyan
                        )
                    }

                    DropdownMenu(
                        expanded =
                            expandedDhikr,
                        onDismissRequest = {
                            expandedDhikr = false
                        }
                    ) {

                        adhkar.forEach { dhikr ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        dhikr,
                                        textAlign =
                                            TextAlign.End,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                    )
                                },
                                onClick = {

                                    selectedDhikr =
                                        dhikr

                                    count = 0

                                    expandedDhikr =
                                        false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(
                    Modifier.height(14.dp)
                )

                Box {

                    OutlinedButton(
                        onClick = {
                            expandedTarget = true
                        }
                    ) {

                        Text(
                            when (target) {
                                33 -> "الهدف: 33"
                                100 -> "الهدف: 100"
                                else -> "بدون حد"
                            },
                            color = cyan
                        )
                    }

                    DropdownMenu(
                        expanded =
                            expandedTarget,
                        onDismissRequest = {
                            expandedTarget = false
                        }
                    ) {

                        DropdownMenuItem(
                            text = {
                                Text("33")
                            },
                            onClick = {
                                target = 33
                                count = 0
                                expandedTarget = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("100")
                            },
                            onClick = {
                                target = 100
                                count = 0
                                expandedTarget = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("بدون حد")
                            },
                            onClick = {
                                target = 0
                                expandedTarget = false
                            }
                        )
                    }
                }
            }

            item {
                Spacer(
                    Modifier.height(28.dp)
                )

                NeonCard(
                    borderColor = green
                ) {

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "$count",
                            color = green,
                            fontSize = 68.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            selectedDhikr,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight =
                                FontWeight.Bold,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            Modifier.height(18.dp)
                        )

                        if (target > 0) {

                            LinearProgressIndicator(
                                progress = {
                                    progress
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                10.dp
                                            )
                                        ),
                                color = green,
                                trackColor =
                                    Color(0xFF20333B)
                            )

                            Spacer(
                                Modifier.height(7.dp)
                            )

                            Text(
                                "$count / $target",
                                color = muted,
                                fontSize = 13.sp
                            )
                        } else {

                            Text(
                                "بدون حد",
                                color = muted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(
                    Modifier.height(22.dp)
                )

                Button(
                    onClick = {

                        if (
                            target == 0 ||
                            count < target
                        ) {
                            count++
                        }
                    },
                    modifier =
                        Modifier.size(180.dp),
                    shape = CircleShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFF12332B)
                        )
                ) {

                    Text(
                        "تسبيح",
                        color = green,
                        fontSize = 24.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(
                    Modifier.height(12.dp)
                )

                TextButton(
                    onClick = {
                        count = 0
                    }
                ) {

                    Text(
                        "تصفير العداد",
                        color = cyan,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutScreen(
        onBack: () -> Unit
    ) {

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            TopBar(
                "عن التطبيق",
                onBack
            )

            Spacer(
                Modifier.height(25.dp)
            )

            NeonCard(
                borderColor = green
            ) {

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(25.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        "وٌ ذکْــر",
                        color = green,
                        fontSize = 40.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(5.dp)
                    )

                    Text(
                        "الإصدار 3.0.0",
                        color = cyan,
                        fontSize = 16.sp
                    )

                    Spacer(
                        Modifier.height(25.dp)
                    )

                    Text(
                        "المطور صالح الخليفي",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(5.dp)
                    )

                    Text(
                        "@iSx3i",
                        color = cyan,
                        fontSize = 17.sp
                    )

                    Spacer(
                        Modifier.height(25.dp)
                    )

                    Text(
                        "تطبيق مجاني يساعدك على دوام الذكر، مع أذكار متنوعة وتذكيرات ومواقيت صلاة محسوبة محليًا ومسبحة إلكترونية.",
                        color = muted,
                        fontSize = 14.sp,
                        textAlign =
                            TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun TopBar(
        title: String,
        onBack: () -> Unit
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "رجوع",
                    tint = green
                )
            }

            Text(
                title,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun DhikrPopup(
        text: String,
        onDismiss: () -> Unit
    ) {

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = card,

            title = {

                Text(
                    "🔔 تذكير بالذكر",
                    color = green,
                    textAlign =
                        TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            },

            text = {

                Text(
                    text,
                    color = Color.White,
                    fontSize = 24.sp,
                    lineHeight = 38.sp,
                    textAlign =
                        TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            },

            confirmButton = {

                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "رددته ✓"
                    )
                }
            }
        )
    }

    @Composable
    private fun WadhkurTheme(
        content: @Composable () -> Unit
    ) {

        MaterialTheme(

            colorScheme =
                darkColorScheme(

                    primary = green,
                    secondary = cyan,
                    tertiary = purple,
                    background = bg,
                    surface = card
                ),

            content = content
        )
    }
}
