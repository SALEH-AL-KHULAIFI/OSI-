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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
        Text("العدّاد يعتمد على التقويم الهجري المدني وقد يختلف بدء رمضان حسب الرؤية المحلية.", color = WadhkurColors.muted)
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, WadhkurColors.edge),
        colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, emoji: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, WadhkurColors.edge),
        colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                Text(subtitle, color = WadhkurColors.muted)
            }
            Icon(Icons.Default.ArrowBack, null, tint = WadhkurColors.primary)
        }
    }
}

@Composable
private fun PrayerPreview(prayers: List<PrayerCalculator.PrayerTime>?, hasLocation: Boolean, onClick: () -> Unit) {
    SectionCard("مواقيت الصلاة", Icons.Default.AccessTime) {
        if (!hasLocation || prayers == null) {
            Text("فعّل الموقع لعرض مواقيت الصلاة حسب مكانك.", color = WadhkurColors.muted)
        } else {
            prayers.take(5).forEach { prayer ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(prayer.name, color = WadhkurColors.text)
                    Text(prayer.time, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
                }
            }
        }
        TextButton(onClick = onClick) { Text("عرض جميع المواقيت") }
    }
}

@Composable
private fun AdBanner() {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        factory = {
            AdView(it).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
private fun PrayerScreen(padding: PaddingValues, locationVersion: Int, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble()
    val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val prayers = if (lat.isFinite() && lon.isFinite()) PrayerCalculator.calculate(lat, lon) else null
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("مواقيت الصلاة", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            if (prayers == null) {
                Text("حدّد موقعك من الصفحة الرئيسية لعرض المواقيت.", color = WadhkurColors.muted)
            } else {
                prayers.forEach { prayer ->
                    SectionCard(prayer.name, Icons.Default.AccessTime) {
                        Text(prayer.time, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun QiblaScreen(padding: PaddingValues, locationVersion: Int, azimuth: Float, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble()
    val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val qibla = if (lat.isFinite() && lon.isFinite()) qiblaBearing(lat, lon) else null
    val rotation = if (qibla != null) qibla - azimuth else 0f
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("اتجاه القبلة", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            if (qibla == null) {
                Text("حدّد موقعك أولًا لحساب اتجاه القبلة.", color = WadhkurColors.muted)
            } else {
                Text("${qibla.toInt()}°", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                Text("وجّه السهم نحو القبلة", color = WadhkurColors.muted)
                Text("➤", fontSize = 100.sp, color = WadhkurColors.primary, modifier = Modifier.graphicsLayer(rotationZ = rotation))
            }
        }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, go: (String) -> Unit, openEmail: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("المزيد", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }
            item { QuickTile("التقويم الهجري", "عرض التاريخ الهجري", "🗓️") { go("calendar") } }
            item { QuickTile("التذكيرات", "إدارة تنبيهات الأذكار والصلاة", "🔔") { go("reminders") } }
            item { QuickTile("الخصوصية", "كيف يتعامل التطبيق مع بياناتك", "🔒") { go("privacy") } }
            item { QuickTile("تواصل مع المطور", "saleh.mabkhot@hotmail.com", "✉️") { openEmail() } }
            item {
                SectionCard("حول وذكر", Icons.Default.Info) {
                    Text("المطور صالح الخليفي", fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                    Text("البريد: ${MainActivity.EMAIL}", color = WadhkurColors.muted)
                    Text("تطبيق وذكر — أدوات يومية للذكر والصلاة والقبلة والتقويم الهجري.", color = WadhkurColors.muted)
                }
            }
        }
    }
}

@Composable
private fun DhikrScreen(padding: PaddingValues, back: () -> Unit) {
    val items = listOf("سبحان الله", "الحمد لله", "الله أكبر", "لا إله إلا الله", "أستغفر الله")
    AppFrame(Modifier.padding(padding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TextButton(onClick = back) { Text("رجوع") } }
            item { Text("الأذكار", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }
            itemsIndexed(items) { index, text ->
                SectionCard("ذكر ${index + 1}", Icons.Default.Favorite) {
                    Text(text, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TasbeehScreen(padding: PaddingValues, back: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("تسبيح سريع", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text(count.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
            Button(onClick = { count++ }, modifier = Modifier.size(180.dp)) { Text("تسبيح", fontSize = 24.sp) }
            TextButton(onClick = { count = 0 }) { Text("تصفير العداد") }
        }
    }
}

@Composable
private fun CalendarScreen(padding: PaddingValues, back: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("التقويم الهجري", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text(islamicDate(Calendar.getInstance()), fontSize = 24.sp, color = WadhkurColors.text)
            Text("التاريخ الميلادي: ${gregorianDate()}", color = WadhkurColors.muted)
        }
    }
}

@Composable
private fun ReminderSettingsScreen(padding: PaddingValues, back: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("التذكيرات", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text("إدارة التذكيرات تتم محليًا من خلال نظام التنبيهات في التطبيق.", color = WadhkurColors.muted)
        }
    }
}

@Composable
private fun PrivacyScreen(padding: PaddingValues, back: () -> Unit) {
    AppFrame(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = back) { Text("رجوع") }
            Text("الخصوصية", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text("الموقع يُستخدم محليًا لحساب مواقيت الصلاة والقبلة. التطبيق لا يتطلب حسابًا. الإعلانات وإدارة الموافقة تُدار عبر مكتبات Google المعتمدة.", color = WadhkurColors.text)
        }
    }
}

private fun gregorianDate(): String = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date())

private fun islamicDate(calendar: Calendar): String {
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    val jd = (367 * year - (7 * (year + (month + 9) / 12)) / 4 + (275 * month) / 9 + day + 1721013.5)
    val l = jd.toLong() - 1948440 + 10632
    val n = (l - 1) / 10631
    val ll = l - 10631 * n + 354
    val j = ((10985 - ll) / 5316) * ((50 * ll) / 17719) + (ll / 5670) * ((43 * ll) / 15238)
    val monthH = (24 * j) / 709
    val dayH = ll - (709 * monthH) / 24
    val yearH = 30 * n + j - 30
    val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    return "$dayH ${months[(monthH - 1).coerceIn(0, 11)]} $yearH هـ"
}

private fun ramadanCountdown(): String {
    val now = Calendar.getInstance()
    var target = Calendar.getInstance().apply {
        set(Calendar.MONTH, Calendar.FEBRUARY)
        set(Calendar.DAY_OF_MONTH, 18)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (!target.after(now)) target.add(Calendar.YEAR, 1)
    val days = ((target.timeInMillis - now.timeInMillis) / 86_400_000L).coerceAtLeast(0)
    return "باقي تقريبًا $days يومًا"
}

private fun countdown(millis: Long): String {
    if (millis <= 0) return "الآن"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun nextPrayer(prayers: List<PrayerCalculator.PrayerTime>, now: Long): Triple<String, String, Long>? {
    val format = SimpleDateFormat("HH:mm", Locale.US)
    val current = Calendar.getInstance().apply { timeInMillis = now }
    val currentMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE)
    val next = prayers.mapNotNull { prayer ->
        val parts = prayer.time.split(":")
        if (parts.size != 2) return@mapNotNull null
        val minutes = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null
        Triple(prayer.name, prayer.time, minutes)
    }.firstOrNull { it.third > currentMinutes }
    return if (next != null) {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, next.third / 60)
            set(Calendar.MINUTE, next.third % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        Triple(next.first, format.format(target.time), target.timeInMillis)
    } else prayers.firstOrNull()?.let {
        val parts = it.time.split(":")
        if (parts.size != 2) null else {
            val target = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                set(Calendar.MINUTE, parts[1].toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            Triple(it.name, it.time, target.timeInMillis)
        }
    }
}

private fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val kaabaLat = Math.toRadians(21.4225)
    val kaabaLon = Math.toRadians(39.8262)
    val lat = Math.toRadians(latitude)
    val lon = Math.toRadians(longitude)
    val dLon = kaabaLon - lon
    val y = sin(dLon)
    val x = cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(dLon)
    var bearing = Math.toDegrees(atan2(y, x)).toFloat()
    if (bearing < 0) bearing += 360f
    return bearing
}
