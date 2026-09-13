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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val green = Color(0xFF39FF8F)
    private val cyan = Color(0xFF55DCFF)
    private val bg = Color(0xFF061016)
    private val card = Color(0xFF0D1B23)

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

        /*
         * تشغيل جميع أنظمة التذكير:
         *
         * 1. الأذكار العامة حسب الفاصل المختار.
         * 2. أذكار الصباح الساعة 06:00.
         * 3. أذكار المساء الساعة 17:00.
         */
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

    private fun saveBestLocation() {

        val lm =
            getSystemService(LocationManager::class.java)

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        var best: Location? = null

        for (provider in providers) {

            try {

                val location =
                    lm.getLastKnownLocation(provider)
                        ?: continue

                if (
                    best == null ||
                    location.accuracy < best!!.accuracy
                ) {
                    best = location
                }

            } catch (_: SecurityException) {
            }
        }

        best?.let {

            getSharedPreferences(
                "wadhkur_location",
                MODE_PRIVATE
            )
                .edit()
                .putFloat(
                    "lat",
                    it.latitude.toFloat()
                )
                .putFloat(
                    "lon",
                    it.longitude.toFloat()
                )
                .apply()
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
    private fun Header() {

        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                "وٌ ذکْــر",
                color = green,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "وَذَكِّرْ فَإِنَّ الذِّكْرَىٰ تَنفَعُ الْمُؤْمِنِينَ",
                color = Color(0xFF9AAFB8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Header()
            }

            item {
                MainCard(
                    "🤲",
                    "الأدعية والأذكار",
                    "أذكار عامة وصباح ومساء",
                    onDhikr
                )
            }

            item {
                MainCard(
                    "🔔",
                    "تذكير الذكر",
                    "اختر كل كم دقيقة يظهر لك ذكر عام جديد",
                    onReminders
                )
            }

            item {
                MainCard(
                    "🕌",
                    "مواقيت الصلاة",
                    "حساب محلي حسب موقع الهاتف",
                    onPrayer
                )
            }

            item {
                MainCard(
                    "📿",
                    "المسبحة",
                    "عداد بسيط للتسبيح",
                    onTasbeeh
                )
            }

            item {
                MainCard(
                    "ℹ️",
                    "عن التطبيق",
                    "وٌ ذکْــر 3.0.0 • صالح الخليفي",
                    onAbout
                )
            }
        }
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
                verticalAlignment = Alignment.CenterVertically
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        subtitle,
                        color = Color(0xFF9AAFB8),
                        fontSize = 13.sp
                    )
                }

                Icon(
                    Icons.Default.ChevronLeft,
                    null,
                    tint = green
                )
            }
        }
    }

    /*
     * شاشة الأذكار
     *
     * ثلاثة أقسام:
     * 1 - عامة
     * 2 - الصباح
     * 3 - المساء
     *
     * يعرض ذكرًا واحدًا في كل مرة.
     */
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

        val currentList = lists[selectedTab]

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
                        selected = selectedTab == tabIndex,
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
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = card
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
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
                            Modifier.height(24.dp)
                        )

                        Text(
                            currentDhikr.title,
                            color = green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            Modifier.height(20.dp)
                        )

                        Text(
                            currentDhikr.text,
                            color = Color.White,
                            fontSize = 24.sp,
                            lineHeight = 40.sp,
                            textAlign = TextAlign.Center
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
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "السابق"
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Text("السابق")
                    }

                    Button(
                        onClick = {
                            if (index < currentList.lastIndex) {
                                index++
                            } else {
                                index = 0
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            if (index == currentList.lastIndex)
                                "من البداية"
                            else
                                "التالي"
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "التالي"
                        )
                    }
                }

            } else {

                Text(
                    "لا توجد أذكار في هذا القسم",
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
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

        var enabled by remember {
            mutableStateOf(
                prefs.getBoolean(
                    ReminderScheduler.ENABLED,
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            TopBar(
                "تذكير الذكر",
                onBack
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = card
                ),
                shape = RoundedCornerShape(24.dp)
            ) {

                Column(
                    Modifier.padding(20.dp)
                ) {

                    Text(
                        "التذكير الدوري للأذكار العامة",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "يتم تذكيرك بذكر عام حسب الفاصل الذي تختاره. أذكار الصباح والمساء لها مواعيد ثابتة.",
                        color = Color(0xFF9AAFB8),
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            "تشغيل التذكير",
                            color = Color.White
                        )

                        Switch(
                            checked = enabled,
                            onCheckedChange = {

                                enabled = it

                                prefs.edit()
                                    .putBoolean(
                                        ReminderScheduler.ENABLED,
                                        it
                                    )
                                    .apply()

                                /*
                                 * تشغيل أو إيقاف جميع أنواع
                                 * التذكيرات.
                                 */
                                ReminderScheduler.scheduleAll(
                                    this@MainActivity
                                )
                            }
                        )
                    }

                    Text(
                        "الفاصل الزمني للأذكار العامة",
                        color = cyan,
                        fontSize = 16.sp
                    )

                    Spacer(
                        Modifier.height(8.dp)
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

                    choices
                        .chunked(3)
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

                                            /*
                                             * تغيير الفاصل يؤثر
                                             * على الأذكار العامة فقط.
                                             */
                                            ReminderScheduler.scheduleGeneral(
                                                this@MainActivity
                                            )
                                        },

                                        label = {
                                            Text(
                                                if (value == 60)
                                                    "ساعة"
                                                else
                                                    "$value د"
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

                    Text(
                        "أذكار الصباح: يوميًا الساعة 06:00 صباحًا.\nأذكار المساء: يوميًا الساعة 05:00 عصرًا.",
                        color = Color(0xFF9AAFB8),
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                }
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

        val times =
            if (!lat.isNaN() && !lon.isNaN()) {
                PrayerCalculator.calculate(
                    lat.toDouble(),
                    lon.toDouble()
                )
            } else {
                null
            }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            TopBar(
                "مواقيت الصلاة",
                onBack
            )

            if (times == null) {

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = card
                        )
                ) {

                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "نحتاج إلى موقع الهاتف لحساب المواقيت محليًا.",
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            Modifier.height(12.dp)
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

            } else {

                val rows =
                    listOf(
                        "الفجر" to times.fajr,
                        "الشروق" to times.sunrise,
                        "الظهر" to times.dhuhr,
                        "العصر" to times.asr,
                        "المغرب" to times.maghrib,
                        "العشاء" to times.isha
                    )

                rows.forEach { (name, time) ->

                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = card
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                    ) {

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                name,
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            Text(
                                time,
                                color = green,
                                fontSize = 18.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onLocation,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "تحديث الموقع",
                        color = cyan
                    )
                }
            }
        }
    }

    @Composable
    private fun TasbeehScreen(
        onBack: () -> Unit
    ) {

        var count by remember {
            mutableIntStateOf(0)
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            TopBar(
                "المسبحة",
                onBack
            )

            Spacer(
                Modifier.height(50.dp)
            )

            Text(
                "$count",
                color = green,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "سبحان الله",
                color = Color.White,
                fontSize = 24.sp
            )

            Spacer(
                Modifier.height(30.dp)
            )

            Button(
                onClick = {
                    count++
                },
                modifier = Modifier.size(180.dp),
                shape = RoundedCornerShape(90.dp)
            ) {

                Text(
                    "تسبيح",
                    fontSize = 22.sp
                )
            }

            TextButton(
                onClick = {
                    count = 0
                }
            ) {

                Text("تصفير")
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
                Modifier.height(30.dp)
            )

            Text(
                "وٌ ذکْــر",
                color = green,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "الإصدار 3.0.0",
                color = cyan,
                fontSize = 16.sp
            )

            Spacer(
                Modifier.height(20.dp)
            )

            Text(
                "المطور صالح الخليفي",
                color = Color.White,
                fontSize = 18.sp
            )

            Text(
                "@iSx3i",
                color = Color(0xFF9AAFB8),
                fontSize = 16.sp
            )

            Spacer(
                Modifier.height(30.dp)
            )

            Text(
                "تطبيق مجاني يساعدك على دوام الذكر، مع تذكيرات دورية ومواقيت صلاة محسوبة محليًا.",
                color = Color(0xFF9AAFB8),
                textAlign = TextAlign.Center,
                lineHeight = 25.sp
            )
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
                .padding(bottom = 14.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.ArrowForward,
                    "رجوع",
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },

            text = {

                Text(
                    text,
                    color = Color.White,
                    fontSize = 24.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },

            confirmButton = {

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
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

                    background = bg,

                    surface = card
                ),

            content = content
        )
    }
}
