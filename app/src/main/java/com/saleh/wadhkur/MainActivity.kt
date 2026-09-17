package com.saleh.wadhkur

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {
        const val EMAIL = "saleh.mabkhot@hotmail.com"
        private const val LOCATION_PREFS = "wadhkur_location"
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) saveBestLocation()
    }

    private var locationVersion by mutableIntStateOf(0)
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var azimuth by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        requestNotificationsIfNeeded()
        initializeAdsAndConsent()
        ReminderScheduler.scheduleAll(this)

        setContent {
            WadhkurTheme {
                WadhkurApp(
                    locationVersion = locationVersion,
                    requestLocation = { requestLocationWithDisclosureAlreadyShown() },
                    openEmail = { openEmail() },
                    azimuth = azimuth
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0) degrees += 360f
        azimuth = degrees
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
    }

    private fun requestLocationWithDisclosureAlreadyShown() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else saveBestLocation()
    }

    private fun saveBestLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            var best: Location? = null
            for (provider in manager.getProviders(true)) {
                val value = manager.getLastKnownLocation(provider) ?: continue
                if (best == null || value.accuracy < best!!.accuracy) best = value
            }
            best?.let {
                getSharedPreferences(LOCATION_PREFS, MODE_PRIVATE).edit()
                    .putFloat("lat", it.latitude.toFloat())
                    .putFloat("lon", it.longitude.toFloat())
                    .apply()
                locationVersion++
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val provider = when {
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                        if (location != null) {
                            getSharedPreferences(LOCATION_PREFS, MODE_PRIVATE).edit()
                                .putFloat("lat", location.latitude.toFloat())
                                .putFloat("lon", location.longitude.toFloat())
                                .apply()
                            locationVersion++
                        }
                    }
                }
            }
        } catch (_: SecurityException) { }
    }

    private fun initializeAdsAndConsent() {
        val params = ConsentRequestParameters.Builder().build()
        val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(this, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                if (info.canRequestAds()) MobileAds.initialize(this) {}
            }
        }, {
            if (info.canRequestAds()) MobileAds.initialize(this) {}
        })
    }

    private fun openEmail() {
        startActivity(Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "استفسار حول تطبيق وذكر")
        })
    }
}

@Composable
private fun WadhkurApp(
    locationVersion: Int,
    requestLocation: () -> Unit,
    openEmail: () -> Unit,
    azimuth: Float
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var showLocationDisclosure by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WadhkurColors.background,
        bottomBar = {
            NavigationBar(containerColor = WadhkurColors.surface) {
                NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                NavigationBarItem(selected = screen == "prayer", onClick = { screen = "prayer" }, icon = { Icon(Icons.Default.AccessTime, null) }, label = { Text("الصلاة") })
                NavigationBarItem(selected = screen == "qibla", onClick = { screen = "qibla" }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("القبلة") })
                NavigationBarItem(selected = screen == "more", onClick = { screen = "more" }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("المزيد") })
            }
        }
    ) { padding ->
        when (screen) {
            "home" -> HomeScreen(
                padding = padding,
                locationVersion = locationVersion,
                go = { screen = it },
                requestLocation = { showLocationDisclosure = true }
            )
            "prayer" -> PrayerScreen(padding, locationVersion) { screen = "home" }
            "qibla" -> QiblaScreen(padding, locationVersion, azimuth) { screen = "home" }
            "more" -> MoreScreen(padding, go = { screen = it }, openEmail = openEmail)
            "dhikr" -> DhikrScreen(padding) { screen = "home" }
            "tasbeeh" -> TasbeehScreen(padding) { screen = "home" }
            "calendar" -> CalendarScreen(padding) { screen = "home" }
            "reminders" -> ReminderSettingsScreen(padding) { screen = "home" }
            "privacy" -> PrivacyScreen(padding) { screen = "home" }
            else -> HomeScreen(padding, locationVersion, { screen = it }, { showLocationDisclosure = true })
        }
    }

    if (showLocationDisclosure) {
        AlertDialog(
            onDismissRequest = { showLocationDisclosure = false },
            title = { Text("الموقع لمواقيت الصلاة والقبلة") },
            text = { Text("يستخدم التطبيق موقع جهازك فقط لحساب مواقيت الصلاة واتجاه القبلة محليًا. لا نحتاج إلى إنشاء حساب أو إرسال موقعك إلى خادم التطبيق.") },
            confirmButton = {
                Button(onClick = { showLocationDisclosure = false; requestLocation() }) { Text("السماح بالموقع") }
            },
            dismissButton = { TextButton(onClick = { showLocationDisclosure = false }) { Text("لاحقًا") } }
        )
    }
}

@Composable
private fun AppFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(WadhkurColors.background)) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 7.dp)
                .border(BorderStroke(1.dp, WadhkurColors.edge), RoundedCornerShape(24.dp))
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 15.dp), content = content)
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    locationVersion: Int,
    go: (String) -> Unit,
    requestLocation: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble()
    val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val hasLocation = lat.isFinite() && lon.isFinite()
    val prayers = if (hasLocation) PrayerCalculator.calculate(lat, lon) else null
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(locationVersion) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val next = prayers?.let { nextPrayer(it, now) }
    val hijri = islamicDate(Calendar.getInstance())

    AppFrame(Modifier.padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("وَذَكِّرْ", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
                    Text("عبادتك اليومية في مكان واحد", color = WadhkurColors.muted)
                }
            }
            item { RamadanCard() }
            item {
                SectionCard("الصلاة القادمة", Icons.Default.AccessTime) {
                    if (next != null) {
                        Text(next.first, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
                        Text("الوقت: ${next.second}", color = WadhkurColors.muted)
                        Text(countdown(next.third - now), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                    } else {
                        Text("حدّد موقعك لعرض الصلاة القادمة", color = WadhkurColors.muted)
                        Button(onClick = requestLocation) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("تحديد الموقع") }
                    }
                }
            }
            item {
                SectionCard("التاريخ الهجري", Icons.Default.CalendarMonth) {
                    Text(hijri, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                    Text(gregorianDate(), color = WadhkurColors.muted)
                }
            }
            item { QuickTile("أذكار الصباح", "ابدأ يومك بالذكر", "☀️") { go("dhikr") } }
            item { QuickTile("أذكار المساء", "اختم يومك بالذكر", "🌙") { go("dhikr") } }
            item { QuickTile("تسبيح سريع", "عداد يعمل دون اتصال", "📿") { go("tasbeeh") } }
            item { PrayerPreview(prayers, hasLocation) { go("prayer") } }
            item { AdBanner() }
            item {
                Text("مميزات وذكر", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                Text("القبلة • التقويم الهجري • التذكيرات • الأذكار • التسبيح • مواقيت الصلاة • الخصوصية", color = WadhkurColors.muted)
            }
        }
    }
}

@Composable
private fun RamadanCard() {
    val remaining = remember { mutableStateOf(ramadanCountdown()) }
    LaunchedEffect(Unit) { while (true) { remaining.value = ramadanCountdown(); delay(60_000) } }
    SectionCard("كم باقي على رمضان؟", Icons.Default.Brightness4) {
        Text(remaining.value, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
        Text("العدّاد يعتمد على التقويم الهجري المدني وقد يختلف بدء رمضان رسميًا حسب الرؤية المحلية.", color = WadhkurColors.muted, fontSize = 12.sp)
    }
}

@Composable
private fun PrayerPreview(prayers: PrayerTimes?, hasLocation: Boolean, open: () -> Unit) {
    SectionCard("مواقيت الصلاة", Icons.Default.AccessTime) {
        if (!hasLocation || prayers == null) {
            Text("فعّل الموقع لحساب المواقيت حسب موقعك.", color = WadhkurColors.muted)
        } else {
            val values = listOf("الفجر" to prayers.fajr, "الشروق" to prayers.sunrise, "الظهر" to prayers.dhuhr, "العصر" to prayers.asr, "المغرب" to prayers.maghrib, "العشاء" to prayers.isha)
            values.forEach { (name, time) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text(time, fontWeight = FontWeight.Bold) } }
        }
        TextButton(onClick = open) { Text("عرض تفاصيل الصلاة") }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, emoji: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface),
        border = BorderStroke(1.dp, WadhkurColors.edge)
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(WadhkurColors.surface2), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
            Spacer(Modifier.width(14.dp))
            Column { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = WadhkurColors.muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface),
        border = BorderStroke(1.dp, WadhkurColors.edge)
    ) {
        Column(Modifier.padding(17.dp), content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = WadhkurColors.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        })
    }
}

@Composable
private fun AdBanner() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface), border = BorderStroke(1.dp, WadhkurColors.edge)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("إعلان", color = WadhkurColors.muted, fontSize = 10.sp)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                factory = { context -> AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } }
            )
        }
    }
}

@Composable
private fun PrayerScreen(padding: PaddingValues, locationVersion: Int, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val prayers = if (lat.isFinite() && lon.isFinite()) PrayerCalculator.calculate(lat, lon) else null
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("مواقيت الصلاة", back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (prayers == null) item { Text("لم يتم تحديد الموقع بعد.", color = WadhkurColors.muted) }
            prayers?.let {
                listOf("الفجر" to it.fajr, "الشروق" to it.sunrise, "الظهر" to it.dhuhr, "العصر" to it.asr, "المغرب" to it.maghrib, "العشاء" to it.isha).forEach { (name, time) -> item { Row(Modifier.fillMaxWidth().background(WadhkurColors.surface, RoundedCornerShape(16.dp)).border(1.dp, WadhkurColors.edge, RoundedCornerShape(16.dp)).padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontSize = 18.sp); Text(time, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) } } }
            }
            item { Text("طريقة الحساب المحلية مبنية على الموقع ومعادلات شمسية داخل الجهاز، دون الحاجة إلى خادم.", color = WadhkurColors.muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun QiblaScreen(padding: PaddingValues, locationVersion: Int, azimuth: Float, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val bearing = if (lat.isFinite() && lon.isFinite()) qiblaBearing(lat, lon) else null
    val rotation = if (bearing != null) bearing - azimuth else 0.0
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("اتجاه القبلة", back)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(230.dp).clip(CircleShape).background(WadhkurColors.surface).border(2.dp, WadhkurColors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("🕋", fontSize = 68.sp, modifier = Modifier.graphicsLayer(rotationZ = rotation.toFloat()))
            }
            if (bearing != null) {
                Text("اتجاه القبلة: ${bearing.toInt()}°", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("حرّك الهاتف حتى يتجه الرمز نحو القبلة", color = WadhkurColors.muted, textAlign = TextAlign.Center)
            } else {
                Text("حدد موقعك أولًا لاحتساب اتجاه القبلة", color = WadhkurColors.muted)
            }
            Text("تعتمد البوصلة على مستشعرات الجهاز؛ قد تحتاج إلى معايرة الهاتف.", fontSize = 12.sp, color = WadhkurColors.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DhikrScreen(padding: PaddingValues, back: () -> Unit) {
    var category by rememberSaveable { mutableStateOf("morning") }
    val list = if (category == "morning") DhikrRepository.morning else DhikrRepository.evening
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = list.getOrNull(index.coerceIn(0, list.lastIndex))
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("الأذكار", back)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { category = "morning"; index = 0 }, modifier = Modifier.weight(1f)) { Text("أذكار الصباح") }
            Button(onClick = { category = "evening"; index = 0 }, modifier = Modifier.weight(1f)) { Text("أذكار المساء") }
        }
        Spacer(Modifier.height(14.dp))
        if (current != null) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface), border = BorderStroke(1.dp, WadhkurColors.edge)) {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${index + 1} / ${list.size}", color = WadhkurColors.primary)
                    Text(current.text, fontSize = 25.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = index > 0, onClick = { index-- }) { Text("السابق") }
                        TextButton(enabled = index < list.lastIndex, onClick = { index++ }) { Text("التالي") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasbeehScreen(padding: PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_tasbeeh", Context.MODE_PRIVATE)
    var count by rememberSaveable { mutableIntStateOf(prefs.getInt("count", 0)) }
    var goal by rememberSaveable { mutableIntStateOf(prefs.getInt("goal", 33)) }
    fun save(value: Int) { count = value; prefs.edit().putInt("count", value).putInt("goal", goal).apply() }
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("التسبيح السريع", back)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("$count", fontSize = 70.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text("الهدف: $goal", color = WadhkurColors.muted)
            Box(Modifier.size(190.dp).clip(CircleShape).background(WadhkurColors.surface).border(3.dp, WadhkurColors.primary, CircleShape).clickable { save(count + 1) }, contentAlignment = Alignment.Center) { Text("سَبِّح", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 100, 1000).forEach { value -> TextButton(onClick = { goal = value; prefs.edit().putInt("goal", value).apply() }) { Text("$value") } }
            }
            TextButton(onClick = { save(0) }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("تصفير العداد") }
        }
    }
}

@Composable
private fun CalendarScreen(padding: PaddingValues, back: () -> Unit) {
    val cal = Calendar.getInstance()
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("التقويم الهجري", back)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionCard("اليوم", Icons.Default.CalendarMonth) { Text(islamicDate(cal), fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(gregorianDate(), color = WadhkurColors.muted) } }
            item { Text("يمكن توسيع التقويم لاحقًا لإضافة المناسبات الهجرية، بداية الأشهر، والتنبيهات الشخصية.", color = WadhkurColors.muted) }
        }
    }
}

@Composable
private fun ReminderSettingsScreen(padding: PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE)
    var general by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.ENABLED, true)) }
    var morning by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.MORNING_ENABLED, true)) }
    var evening by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.EVENING_ENABLED, true)) }
    fun update(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); ReminderScheduler.scheduleAll(context) }
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("التذكيرات", back)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SettingRow("تذكير عام", "حسب الفاصل المحفوظ", general) { general = it; update(ReminderScheduler.ENABLED, it) } }
            item { SettingRow("أذكار الصباح", "يوميًا الساعة 06:00", morning) { morning = it; update(ReminderScheduler.MORNING_ENABLED, it) } }
            item { SettingRow("أذكار المساء", "يوميًا الساعة 17:00", evening) { evening = it; update(ReminderScheduler.EVENING_ENABLED, it) } }
            item { Text("قد تختلف دقة المنبهات على بعض الأجهزة بسبب إعدادات توفير البطارية. لا يمنح التطبيق نفسه صلاحية تجاوز قيود النظام.", color = WadhkurColors.muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, go: (String) -> Unit, openEmail: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        Text("المزيد", Modifier.padding(top = 20.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        MoreRow("القبلة", "اتجاه القبلة بالبوصلة", Icons.Default.Explore) { go("qibla") }
        MoreRow("التقويم الهجري", "التاريخ الهجري اليوم", Icons.Default.CalendarMonth) { go("calendar") }
        MoreRow("التذكيرات", "أذكار الصباح والمساء والتذكير العام", Icons.Default.Notifications) { go("reminders") }
        MoreRow("الخصوصية", "سياسة الخصوصية وبيانات التطبيق", Icons.Default.PrivacyTip) { go("privacy") }
        MoreRow("عن وذكر", "المطور والتواصل", Icons.Default.Info) { go("about") }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = openEmail, modifier = Modifier.fillMaxWidth()) { Text(MainActivity.EMAIL) }
    }
}

@Composable
private fun PrivacyScreen(padding: PaddingValues, back: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("الخصوصية", back)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { Text("سياسة الخصوصية — وذكر", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item { Text("التطبيق مصمم ليعمل محليًا قدر الإمكان. موقع الجهاز، عند السماح به، يستخدم لحساب مواقيت الصلاة واتجاه القبلة داخل التطبيق. لا يطلب التطبيق إنشاء حساب.", color = WadhkurColors.text, lineHeight = 27.sp) }
            item { Text("الإعلانات: يتكامل التطبيق مع Google Mobile Ads لعرض الإعلانات. قد يعالج مزود الإعلانات معرّفات الجهاز وبيانات مرتبطة بالإعلانات وفق إعدادات الموافقة وسياساته. يجب إكمال إعدادات الخصوصية وData Safety في Play Console قبل النشر.", color = WadhkurColors.text, lineHeight = 27.sp) }
            item { Text("التخزين المحلي: تحفظ إعدادات التذكيرات والعداد وبعض الإحداثيات محليًا على الجهاز.", color = WadhkurColors.text, lineHeight = 27.sp) }
            item { Text("التواصل وحذف البيانات: لا يوجد حساب مستخدم داخل التطبيق. للاستفسارات المتعلقة بالخصوصية: ${MainActivity.EMAIL}", color = WadhkurColors.text, lineHeight = 27.sp) }
            item { Text("هذه الشاشة لا تغني عن نشر سياسة خصوصية عامة على عنوان URL وإدخاله في Play Console قبل الإصدار التجاري.", color = WadhkurColors.primary, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AboutScreen(padding: PaddingValues, back: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        ScreenHeader("عن وذكر", back)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("وَذَكِّرْ", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Spacer(Modifier.height(8.dp))
            Text("المطور صالح الخليفي", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(MainActivity.EMAIL, color = WadhkurColors.primary)
            Spacer(Modifier.height(18.dp))
            Text("تطبيق إسلامي خفيف، سريع، يعمل دون حساب، ويجمع الأذكار ومواقيت الصلاة والقبلة والتقويم والتسبيح والتذكيرات في تجربة واحدة.", textAlign = TextAlign.Center, color = WadhkurColors.muted, lineHeight = 26.sp)
            Spacer(Modifier.height(20.dp))
            Text("الإصدار 4.0.0", color = WadhkurColors.muted)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null) }
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(WadhkurColors.surface, RoundedCornerShape(16.dp)).border(1.dp, WadhkurColors.edge, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = WadhkurColors.muted, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface), border = BorderStroke(1.dp, WadhkurColors.edge)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = WadhkurColors.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(13.dp))
            Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = WadhkurColors.muted, fontSize = 12.sp) }
        }
    }
}

private fun nextPrayer(times: PrayerTimes, nowMillis: Long): Triple<String, String, Long>? {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val entries = listOf("الفجر" to times.fajr, "الظهر" to times.dhuhr, "العصر" to times.asr, "المغرب" to times.maghrib, "العشاء" to times.isha)
    for ((name, time) in entries) {
        val minute = parsePrayerMinute(time)
        val target = Calendar.getInstance().apply { timeInMillis = nowMillis; set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (target.timeInMillis > nowMillis) return Triple(name, time, target.timeInMillis)
    }
    val minute = parsePrayerMinute(times.fajr)
    val tomorrow = Calendar.getInstance().apply { timeInMillis = nowMillis; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return Triple("الفجر", times.fajr, tomorrow.timeInMillis)
}

private fun parsePrayerMinute(value: String): Int {
    val clean = value.replace("ص", "").replace("م", "").trim()
    val parts = clean.split(":")
    var hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (value.contains("م") && hour < 12) hour += 12
    if (value.contains("ص") && hour == 12) hour = 0
    return hour * 60 + minute
}

private fun countdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale("ar"), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

private fun gregorianDate(): String = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date())

private fun ramadanCountdown(): String {
    val target = islamicToGregorianMillis(1448, 9, 1)
    val diff = target - System.currentTimeMillis()
    if (diff <= 0) return "رمضان الحالي أو القادم يحتاج تحديث الرؤية الرسمية"
    val days = diff / 86_400_000L
    val hours = (diff % 86_400_000L) / 3_600_000L
    return "باقي تقريبًا $days يوم و$hours ساعة"
}

private fun islamicDate(gregorian: Calendar): String {
    val (y, m, d) = gregorianToIslamic(gregorian.get(Calendar.YEAR), gregorian.get(Calendar.MONTH) + 1, gregorian.get(Calendar.DAY_OF_MONTH))
    val months = arrayOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    return "$d ${months[m - 1]} $y هـ"
}

private fun gregorianToIslamic(y: Int, m: Int, d: Int): Triple<Int, Int, Int> {
    val jd = gregorianToJd(y, m, d)
    val l0 = jd - 1948440 + 10632
    val n = (l0 - 1) / 10631
    var l = l0 - 10631 * n + 354
    val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
    l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
    val month = (24 * l) / 709
    val day = l - (709 * month) / 24
    val year = 30 * n + j - 30
    return Triple(year, month, day)
}

private fun gregorianToJd(y: Int, m: Int, d: Int): Int {
    val a = (14 - m) / 12
    val yy = y + 4800 - a
    val mm = m + 12 * a - 3
    return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
}

private fun islamicToGregorianMillis(year: Int, month: Int, day: Int): Long {
    val jd = day + kotlin.math.ceil(29.5 * (month - 1)).toInt() + (year - 1) * 354 + ((3 + 11 * year) / 30) + 1948439
    val j = jd + 32044
    val g = j / 146097
    val dg = j % 146097
    val c = ((dg / 36524) + 1) * 3 / 4
    val dc = dg - c * 36524
    val b = dc / 1461
    val db = dc % 1461
    val a = ((db / 365) + 1) * 3 / 4
    val da = db - a * 365
    val y = g * 400 + c * 100 + b * 4 + a
    val m = (da * 5 + 308) / 153 - 2
    val d = da - (m + 4) * 153 / 5 + 122
    val yearG = y - 4800 + (m + 2) / 12
    val monthG = (m + 2) % 12 + 1
    val dayG = d + 1
    return Calendar.getInstance().apply { set(yearG, monthG - 1, dayG, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
}

private fun qiblaBearing(latitude: Double, longitude: Double): Double {
    val kaabaLat = Math.toRadians(21.422487)
    val kaabaLon = Math.toRadians(39.826206)
    val lat = Math.toRadians(latitude)
    val lon = Math.toRadians(longitude)
    val dLon = kaabaLon - lon
    val y = sin(dLon)
    val x = cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private object WadhkurColors {
    val background = Color(0xFF071116)
    val surface = Color(0xFF0E1D25)
    val surface2 = Color(0xFF142833)
    val edge = Color(0xFF235060)
    val primary = Color(0xFF4DFFAA)
    val text = Color(0xFFEAF7F1)
    val muted = Color(0xFF9EB5BD)
}

@Composable
private fun WadhkurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(primary = WadhkurColors.primary, background = WadhkurColors.background, surface = WadhkurColors.surface, onPrimary = Color.Black, onBackground = WadhkurColors.text, onSurface = WadhkurColors.text), content = content)
}
