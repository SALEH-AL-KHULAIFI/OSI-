package com.saleh.wadhkur

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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

private val WadhkurArabicFont = FontFamily.Serif

class MainActivityV2 : ComponentActivity(), SensorEventListener {
    companion object {
        const val EMAIL = "saleh.mabkhot@hotmail.com"
        private const val LOCATION_PREFS = "wadhkur_location"
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> if (permissions.values.any { it }) saveBestLocation() }

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var azimuthMagnetic by mutableFloatStateOf(0f)
    private var locationVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        initializeAds()
        ReminderScheduler.scheduleAll(this)
        setContent { WadhkurTheme { WadhkurRoot(locationVersion, azimuthMagnetic, ::requestLocation, ::openEmail) } }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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
        var value = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (value < 0f) value += 360f
        azimuthMagnetic = value
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else saveBestLocation()
    }

    private fun saveBestLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            var best: Location? = null
            manager.getProviders(true).forEach { provider ->
                val location = manager.getLastKnownLocation(provider) ?: return@forEach
                if (best == null || location.accuracy < best!!.accuracy) best = location
            }
            best?.let { saveLocation(it) }
            if (Build.VERSION.SDK_INT >= 30) {
                val provider = when {
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                provider?.let { p -> manager.getCurrentLocation(p, null, mainExecutor) { it?.let(::saveLocation) } }
            }
        } catch (_: SecurityException) { }
    }

    private fun saveLocation(location: Location) {
        getSharedPreferences(LOCATION_PREFS, MODE_PRIVATE).edit()
            .putFloat("lat", location.latitude.toFloat())
            .putFloat("lon", location.longitude.toFloat())
            .apply()
        locationVersion++
    }

    private fun initializeAds() {
        val params = ConsentRequestParameters.Builder().build()
        val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(this, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { if (info.canRequestAds()) MobileAds.initialize(this) {} }
        }, { if (info.canRequestAds()) MobileAds.initialize(this) {} })
    }

    private fun openEmail() {
        startActivity(Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "استفسار حول تطبيق وذكر")
        })
    }
}

@Composable
private fun WadhkurRoot(locationVersion: Int, azimuthMagnetic: Float, requestLocation: () -> Unit, openEmail: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("home") }
    Scaffold(
        containerColor = WadhkurColors.background,
        bottomBar = {
            NavigationBar(containerColor = WadhkurColors.surface) {
                NavigationBarItem(screen == "home", { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                NavigationBarItem(screen == "prayer", { screen = "prayer" }, icon = { Icon(Icons.Default.AccessTime, null) }, label = { Text("الصلاة") })
                NavigationBarItem(screen == "qibla", { screen = "qibla" }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("القبلة") })
                NavigationBarItem(screen == "more", { screen = "more" }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("المزيد") })
            }
        }
    ) { padding ->
        when (screen) {
            "home" -> HomeV2(padding, locationVersion, requestLocation, { screen = it })
            "prayer" -> PrayerV2(padding, { screen = "home" })
            "qibla" -> QiblaV2(padding, azimuthMagnetic, { screen = "home" })
            "more" -> MoreV2(padding, { screen = it }, openEmail)
            "morning" -> DhikrV2(padding, "أذكار الصباح", DhikrRepository.morning) { screen = "home" }
            "evening" -> DhikrV2(padding, "أذكار المساء", DhikrRepository.evening) { screen = "home" }
            "general" -> DhikrV2(padding, "أذكار عامة", DhikrRepository.main) { screen = "home" }
            "tasbeeh" -> TasbeehV2(padding) { screen = "home" }
            "reminders" -> ReminderSettingsV2(padding) { screen = "home" }
            "calendar" -> CalendarV2(padding) { screen = "home" }
            "privacy" -> PrivacyV2(padding) { screen = "home" }
        }
    }
}

@Composable
private fun AppFrameV2(padding: PaddingValues, content: @Composable ColumnScopeV2.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding).background(WadhkurColors.background).padding(horizontal = 10.dp)) {
        ColumnV2(content)
    }
}

private class ColumnScopeV2
@Composable private fun ColumnV2(content: @Composable ColumnScopeV2.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), content = { ColumnScopeV2().content() })
}

@Composable
private fun HomeV2(padding: PaddingValues, locationVersion: Int, requestLocation: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble()
    val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val hasLocation = lat.isFinite() && lon.isFinite()
    val prayers = if (hasLocation) PrayerCalculator.calculate(lat, lon) else null
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(locationVersion) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val next = prayers?.let { nextPrayerV2(it, now) }

    AppFrameV2(padding) {
        LazyColumn(contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("وَذَكِّرْ", fontFamily = WadhkurArabicFont, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
                    Text("رفيقك اليومي للذكر والصلاة", fontFamily = WadhkurArabicFont, color = WadhkurColors.muted)
                }
            }
            item { RamadanCardV2() }
            item {
                SectionV2("الصلاة القادمة", Icons.Default.AccessTime) {
                    if (next == null) {
                        Text("فعّل الموقع لحساب الصلاة القادمة", fontFamily = WadhkurArabicFont, color = WadhkurColors.muted)
                        Button(onClick = requestLocation) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("تحديد الموقع") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(next.first, fontFamily = WadhkurArabicFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text(next.second, fontFamily = WadhkurArabicFont, color = WadhkurColors.muted) }
                            Text(countdownV2(next.third - now), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                        }
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { HomeTileV2("أذكار الصباح", "☀️", { go("morning") }); HomeTileV2("أذكار المساء", "🌙", { go("evening") }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { HomeTileV2("تسبيح سريع", "📿", { go("tasbeeh") }); HomeTileV2("أذكار عامة", "🤲", { go("general") }) } }
            item {
                SectionV2("التاريخ الهجري", Icons.Default.CalendarMonth) {
                    Text(islamicDateV2(Calendar.getInstance()), fontFamily = WadhkurArabicFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text)
                    Text(gregorianDateV2(), fontFamily = WadhkurArabicFont, color = WadhkurColors.muted)
                }
            }
            item {
                SectionV2("مواقيت الصلاة", Icons.Default.AccessTime) {
                    if (prayers == null) Text("فعّل الموقع لعرض المواقيت", color = WadhkurColors.muted)
                    else prayers.forEach { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(it.name); Text(it.time, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) } }
                }
            }
            item { AdBannerV2() }
        }
    }
}

@Composable private fun RamadanCardV2() {
    var remaining by remember { mutableStateOf(ramadanCountdownV2()) }
    LaunchedEffect(Unit) { while (true) { remaining = ramadanCountdownV2(); delay(1000) } }
    SectionV2("كم باقي على رمضان؟", Icons.Default.Brightness4) {
        Text(remaining, fontFamily = WadhkurArabicFont, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("التاريخ المتوقع لبداية 1 رمضان 1448 هو 8 فبراير 2027، وقد يتغير حسب ثبوت رؤية الهلال.", fontFamily = WadhkurArabicFont, fontSize = 13.sp, color = WadhkurColors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun HomeTileV2(title: String, icon: String, onClick: () -> Unit) {
    Card(Modifier.weight(1f).height(105.dp).clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(icon, fontSize = 28.sp); Text(title, fontFamily = WadhkurArabicFont, fontWeight = FontWeight.Bold, color = WadhkurColors.text) }
    }
}

@Composable private fun SectionV2(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = WadhkurColors.primary); Spacer(Modifier.width(8.dp)); Text(title, fontFamily = WadhkurArabicFont, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text) }; content() }
    }
}

@Composable private fun DhikrV2(padding: PaddingValues, title: String, list: List<Dhikr>, back: () -> Unit) {
    AppFrameV2(padding) {
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TextButton(onClick = back) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("رجوع") } }
            item { Text(title, fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }
            items(list) { dhikr -> SectionV2(dhikr.title, Icons.Default.Info) { Text(dhikr.text, fontFamily = WadhkurArabicFont, fontSize = 21.sp, lineHeight = 34.sp, color = WadhkurColors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        }
    }
}

@Composable private fun TasbeehV2(padding: PaddingValues, back: () -> Unit) {
    val options = listOf("سبحان الله", "الحمد لله", "الله أكبر", "لا إله إلا الله", "أستغفر الله العظيم", "لا حول ولا قوة إلا بالله")
    var selected by rememberSaveable { mutableStateOf(options.first()) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    AppFrameV2(padding) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = back) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("رجوع") }
            Text("التسبيح السريع", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Text(selected, fontFamily = WadhkurArabicFont, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.text, textAlign = TextAlign.Center)
            Text(count.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary)
            Button(onClick = { count++ }, modifier = Modifier.size(180.dp)) { Text("اضغط للذكر", fontFamily = WadhkurArabicFont, fontSize = 21.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.take(3).forEach { DhikrChoiceV2(it, selected == it) { selected = it; count = 0 } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.drop(3).forEach { DhikrChoiceV2(it, selected == it) { selected = it; count = 0 } } }
            TextButton(onClick = { count = 0 }) { Text("تصفير العداد") }
        }
    }
}

@Composable private fun DhikrChoiceV2(text: String, selected: Boolean, onClick: () -> Unit) { Card(Modifier.weight(1f).height(72.dp).clickable(onClick = onClick), border = BorderStroke(1.dp, if (selected) WadhkurColors.primary else WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = if (selected) WadhkurColors.surface2 else WadhkurColors.surface)) { Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) { Text(text, fontFamily = WadhkurArabicFont, fontSize = 13.sp, textAlign = TextAlign.Center) } } }

@Composable private fun PrayerV2(padding: PaddingValues, back: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val prayers = if (lat.isFinite() && lon.isFinite()) PrayerCalculator.calculate(lat, lon) else null
    AppFrameV2(padding) { LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { TextButton(onClick = back) { Text("رجوع") } }; item { Text("مواقيت الصلاة", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; if (prayers == null) item { Text("حدّد الموقع من الصفحة الرئيسية.", color = WadhkurColors.muted) } else items(prayers) { p -> SectionV2(p.name, Icons.Default.AccessTime) { Text(p.time, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) } } } }
}

@Composable private fun QiblaV2(padding: PaddingValues, azimuthMagnetic: Float, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble()
    val qiblaTrue = if (lat.isFinite() && lon.isFinite()) qiblaBearingTrueV2(lat, lon) else null
    val magneticDeclination = if (qiblaTrue != null) GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination else 0f
    val trueHeading = normalizeV2(azimuthMagnetic + magneticDeclination)
    val rotation = if (qiblaTrue != null) normalizeV2(qiblaTrue - trueHeading) else 0f
    AppFrameV2(padding) { Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { TextButton(onClick = back) { Text("رجوع") }; Text("مؤشر القبلة", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); if (qiblaTrue == null) { Text("حدّد موقعك أولًا لتحديد القبلة.", color = WadhkurColors.muted) } else { Text("اتجاه القبلة ${qiblaTrue.toInt()}°", fontFamily = WadhkurArabicFont, fontSize = 20.sp, color = WadhkurColors.text); Text("حرّك الهاتف ببطء على شكل دائرة لمعايرة البوصلة", fontFamily = WadhkurArabicFont, fontSize = 14.sp, color = WadhkurColors.muted, textAlign = TextAlign.Center); Text("➤", fontSize = 120.sp, color = WadhkurColors.primary, modifier = Modifier.graphicsLayer(rotationZ = rotation)); Text("اتجاه الهاتف الآن ${trueHeading.toInt()}°", color = WadhkurColors.muted) } } }
}

@Composable private fun MoreV2(padding: PaddingValues, go: (String) -> Unit, openEmail: () -> Unit) {
    AppFrameV2(padding) { LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("المزيد", fontFamily = WadhkurArabicFont, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; item { MenuV2("🔔", "إدارة التذكيرات", "أذكار الصباح والمساء والتذكير الدوري") { go("reminders") } }; item { MenuV2("🗓️", "التقويم الهجري", "التاريخ الهجري اليوم") { go("calendar") } }; item { MenuV2("🔒", "الخصوصية", "بيانات الموقع تستخدم محليًا") { go("privacy") } }; item { MenuV2("✉️", "تواصل مع المطور", EMAIL) { openEmail() } }; item { SectionV2("حول وذكر", Icons.Default.Info) { Text("المطور صالح الخليفي", fontWeight = FontWeight.Bold); Text("${MainActivityV2.EMAIL}", color = WadhkurColors.muted) } } } }
}

@Composable private fun MenuV2(icon: String, title: String, subtitle: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 28.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontFamily = WadhkurArabicFont, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontFamily = WadhkurArabicFont, color = WadhkurColors.muted) }; Icon(Icons.Default.ArrowBack, null, tint = WadhkurColors.primary) } } }

@Composable private fun ReminderSettingsV2(padding: PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE)
    var general by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.ENABLED, true)) }
    var morning by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.MORNING_ENABLED, true)) }
    var evening by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.EVENING_ENABLED, true)) }
    var interval by remember { mutableIntStateOf(prefs.getInt(ReminderScheduler.INTERVAL, 30).coerceIn(1, 60)) }
    val options = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60)
    fun save() { prefs.edit().putBoolean(ReminderScheduler.ENABLED, general).putBoolean(ReminderScheduler.MORNING_ENABLED, morning).putBoolean(ReminderScheduler.EVENING_ENABLED, evening).putInt(ReminderScheduler.INTERVAL, interval).apply(); ReminderScheduler.scheduleAll(context) }
    AppFrameV2(padding) { LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { TextButton(onClick = back) { Text("رجوع") } }; item { Text("إدارة التذكيرات", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; item { ReminderRowV2("التذكير العام", "كل $interval دقيقة", general) { general = it; save() } }; item { SectionV2("فاصل التذكير العام", Icons.Default.Notifications) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.take(5).forEach { n -> ChoiceV2("$n د", interval == n) { interval = n; save() } } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.drop(5).forEach { n -> ChoiceV2(if (n == 60) "ساعة" else "$n د", interval == n) { interval = n; save() } } } } }; item { ReminderRowV2("أذكار الصباح", "كل يوم الساعة 06:00", morning) { morning = it; save() } }; item { ReminderRowV2("أذكار المساء", "كل يوم الساعة 17:00", evening) { evening = it; save() } }; item { SectionV2("ملاحظة", Icons.Default.Info) { Text("فعّل إشعارات التطبيق من إعدادات الهاتف. وعلى Android 12+ اسمح للتطبيق بالتنبيهات والمنبهات الدقيقة حتى تكون المواعيد أقرب للوقت المحدد.", fontFamily = WadhkurArabicFont, color = WadhkurColors.muted) } } } }
}

@Composable private fun ReminderRowV2(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontFamily = WadhkurArabicFont, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontFamily = WadhkurArabicFont, color = WadhkurColors.muted) }; Switch(checked = checked, onCheckedChange = onChecked) } } }

@Composable private fun ChoiceV2(text: String, selected: Boolean, onClick: () -> Unit) { Card(Modifier.weight(1f).height(48.dp).clickable(onClick = onClick), border = BorderStroke(1.dp, if (selected) WadhkurColors.primary else WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = if (selected) WadhkurColors.surface2 else WadhkurColors.surface)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, fontFamily = WadhkurArabicFont, fontSize = 13.sp) } } }

@Composable private fun CalendarV2(padding: PaddingValues, back: () -> Unit) { AppFrameV2(padding) { Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = back) { Text("رجوع") }; Text("التقويم الهجري", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text(islamicDateV2(Calendar.getInstance()), fontFamily = WadhkurArabicFont, fontSize = 23.sp); Text(gregorianDateV2(), color = WadhkurColors.muted) } } }
@Composable private fun PrivacyV2(padding: PaddingValues, back: () -> Unit) { AppFrameV2(padding) { Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = back) { Text("رجوع") }; Text("الخصوصية", fontFamily = WadhkurArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text("الموقع يستخدم محليًا لحساب مواقيت الصلاة واتجاه القبلة. لا يحتاج التطبيق إلى حساب.", fontFamily = WadhkurArabicFont, color = WadhkurColors.text) } } }

@Composable private fun AdBannerV2() { AndroidView(Modifier.fillMaxWidth().height(60.dp), factory = { AdView(it).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } }) }

private fun ramadanCountdownV2(): String {
    val now = System.currentTimeMillis()
    val target = Calendar.getInstance().apply { set(2027, Calendar.FEBRUARY, 8, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
    if (now >= target.timeInMillis) return "رمضان 1448 هـ بدأ"
    var seconds = (target.timeInMillis - now) / 1000
    val days = seconds / 86400; seconds %= 86400
    val hours = seconds / 3600; seconds %= 3600
    val minutes = seconds / 60; val secs = seconds % 60
    return String.format(Locale.US, "%d يوم  %02d ساعة  %02d دقيقة  %02d ثانية", days, hours, minutes, secs)
}

private fun countdownV2(millis: Long): String { if (millis <= 0) return "الآن"; var s = millis / 1000; val h = s / 3600; s %= 3600; val m = s / 60; val sec = s % 60; return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec) }

private fun nextPrayerV2(prayers: List<PrayerCalculator.PrayerTime>, now: Long): Triple<String, String, Long>? {
    val current = Calendar.getInstance().apply { timeInMillis = now }
    val currentMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE)
    val parsed = prayers.mapNotNull { p -> val parts = p.time.split(":"); if (parts.size != 2) null else { val h = parts[0].toIntOrNull(); val m = parts[1].toIntOrNull(); if (h == null || m == null) null else Triple(p.name, p.time, h * 60 + m) } }
    val next = parsed.firstOrNull { it.third > currentMinutes } ?: parsed.firstOrNull() ?: return null
    val target = Calendar.getInstance().apply { timeInMillis = now; if (next.third <= currentMinutes) add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, next.third / 60); set(Calendar.MINUTE, next.third % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return Triple(next.first, next.second, target.timeInMillis)
}

private fun qiblaBearingTrueV2(latitude: Double, longitude: Double): Float {
    val kaabaLat = Math.toRadians(21.4225); val kaabaLon = Math.toRadians(39.8262); val lat = Math.toRadians(latitude); val dLon = kaabaLon - Math.toRadians(longitude)
    var bearing = Math.toDegrees(atan2(sin(dLon), cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(dLon))).toFloat()
    if (bearing < 0) bearing += 360f
    return bearing
}
private fun normalizeV2(value: Float): Float { var v = value % 360f; if (v < 0) v += 360f; return v }
private fun gregorianDateV2(): String = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date())
private fun islamicDateV2(calendar: Calendar): String {
    val day = calendar.get(Calendar.DAY_OF_MONTH); val month = calendar.get(Calendar.MONTH) + 1; val year = calendar.get(Calendar.YEAR)
    val jd = 367 * year - (7 * (year + (month + 9) / 12)) / 4 + (275 * month) / 9 + day + 1721013.5
    val l = jd.toLong() - 1948440 + 10632; val n = (l - 1) / 10631; val ll = l - 10631 * n + 354
    val j = ((10985 - ll) / 5316) * ((50 * ll) / 17719) + (ll / 5670) * ((43 * ll) / 15238)
    val monthH = (24 * j) / 709; val dayH = ll - (709 * monthH) / 24; val yearH = 30 * n + j - 30
    val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    return "$dayH ${months[((monthH - 1).coerceIn(0, 11)).toInt()]} $yearH هـ"
}
