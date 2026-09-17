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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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

private val ArabicFont = FontFamily.Serif

class MainActivityV3 : ComponentActivity(), SensorEventListener {
    companion object { const val EMAIL = "saleh.mabkhot@hotmail.com"; private const val LOCATION_PREFS = "wadhkur_location" }
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var magneticHeading by mutableFloatStateOf(0f)
    private var locationVersion by mutableIntStateOf(0)
    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { ok -> ok }) saveBestLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        initializeAds(); ReminderScheduler.scheduleAll(this)
        setContent { WadhkurTheme { WadhkurAppV3(locationVersion, magneticHeading, ::requestLocation, ::openEmail) } }
    }
    override fun onResume() { super.onResume(); rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val r = FloatArray(9); val o = FloatArray(3); SensorManager.getRotationMatrixFromVector(r, event.values); SensorManager.getOrientation(r, o)
        var d = Math.toDegrees(o[0].toDouble()).toFloat(); if (d < 0) d += 360f; magneticHeading = d
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else saveBestLocation()
    }
    private fun saveBestLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            var best: Location? = null
            manager.getProviders(true).forEach { p -> manager.getLastKnownLocation(p)?.let { if (best == null || it.accuracy < best!!.accuracy) best = it } }
            best?.let(::saveLocation)
            if (Build.VERSION.SDK_INT >= 30) {
                val provider = when { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER; manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER; else -> null }
                provider?.let { p -> manager.getCurrentLocation(p, null, mainExecutor) { it?.let(::saveLocation) } }
            }
        } catch (_: SecurityException) { }
    }
    private fun saveLocation(location: Location) { getSharedPreferences(LOCATION_PREFS, MODE_PRIVATE).edit().putFloat("lat", location.latitude.toFloat()).putFloat("lon", location.longitude.toFloat()).apply(); locationVersion++ }
    private fun initializeAds() {
        val params = ConsentRequestParameters.Builder().build(); val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(this, params, { UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { if (info.canRequestAds()) MobileAds.initialize(this) {} } }, { if (info.canRequestAds()) MobileAds.initialize(this) {} })
    }
    private fun openEmail() { startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:$EMAIL"); putExtra(Intent.EXTRA_SUBJECT, "استفسار حول تطبيق وذكر") }) }
}

@Composable
private fun WadhkurAppV3(locationVersion: Int, magneticHeading: Float, requestLocation: () -> Unit, openEmail: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("home") }
    Scaffold(containerColor = WadhkurColors.background, bottomBar = {
        NavigationBar(containerColor = WadhkurColors.surface) {
            NavigationBarItem(screen == "home", { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
            NavigationBarItem(screen == "prayer", { screen = "prayer" }, icon = { Icon(Icons.Default.AccessTime, null) }, label = { Text("الصلاة") })
            NavigationBarItem(screen == "qibla", { screen = "qibla" }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("القبلة") })
            NavigationBarItem(screen == "more", { screen = "more" }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("المزيد") })
        }
    }) { p ->
        when (screen) {
            "home" -> HomeV3(p, locationVersion, requestLocation) { screen = it }
            "prayer" -> PrayerV3(p) { screen = "home" }
            "qibla" -> QiblaV3(p, magneticHeading) { screen = "home" }
            "more" -> MoreV3(p, { screen = it }, openEmail)
            "morning" -> DhikrV3(p, "أذكار الصباح", DhikrRepository.morning) { screen = "home" }
            "evening" -> DhikrV3(p, "أذكار المساء", DhikrRepository.evening) { screen = "home" }
            "general" -> DhikrV3(p, "أذكار عامة", DhikrRepository.main) { screen = "home" }
            "tasbeeh" -> TasbeehV3(p) { screen = "home" }
            "reminders" -> ReminderSettingsV3(p) { screen = "home" }
            "calendar" -> CalendarV3(p) { screen = "home" }
            "privacy" -> PrivacyV3(p) { screen = "home" }
        }
    }
}

@Composable private fun FrameV3(p: PaddingValues, content: @Composable () -> Unit) { Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 10.dp), content = { content() }) }

@Composable private fun HomeV3(p: PaddingValues, locationVersion: Int, requestLocation: () -> Unit, go: (String) -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE); val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble(); val prayers = if (lat.isFinite() && lon.isFinite()) PrayerCalculator.calculate(lat, lon) else null
    var now by remember { mutableStateOf(System.currentTimeMillis()) }; LaunchedEffect(locationVersion) { while (true) { now = System.currentTimeMillis(); delay(1000) } }; val next = prayers?.let { nextPrayerV3(it, now) }
    FrameV3(p) { LazyColumn(contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("وَذَكِّرْ", fontFamily = ArabicFont, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text("رفيقك اليومي للذكر والصلاة", fontFamily = ArabicFont, color = WadhkurColors.muted) } }
        item { RamadanV3() }
        item { SectionV3("الصلاة القادمة", Icons.Default.AccessTime) { if (next == null) { Text("فعّل الموقع لحساب الصلاة القادمة", fontFamily = ArabicFont, color = WadhkurColors.muted); Button(onClick = requestLocation) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(5.dp)); Text("تحديد الموقع") } } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(next.first, fontFamily = ArabicFont, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text(next.second, color = WadhkurColors.muted) }; Text(countdownV3(next.third - now), fontSize = 23.sp, fontWeight = FontWeight.Bold) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { TileV3("أذكار الصباح", "☀️", Modifier.weight(1f)) { go("morning") }; TileV3("أذكار المساء", "🌙", Modifier.weight(1f)) { go("evening") } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { TileV3("تسبيح سريع", "📿", Modifier.weight(1f)) { go("tasbeeh") }; TileV3("أذكار عامة", "🤲", Modifier.weight(1f)) { go("general") } } }
        item { SectionV3("التاريخ الهجري", Icons.Default.CalendarMonth) { Text(islamicDateV3(Calendar.getInstance()), fontFamily = ArabicFont, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(gregorianV3(), color = WadhkurColors.muted) } }
        item { SectionV3("مواقيت الصلاة", Icons.Default.AccessTime) { if (prayers == null) Text("فعّل الموقع لعرض المواقيت", color = WadhkurColors.muted) else prayers.forEach { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(it.name); Text(it.time, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) } } } }
        item { AdV3() }
    } }
}

@Composable private fun RamadanV3() { var text by remember { mutableStateOf(ramadanV3()) }; LaunchedEffect(Unit) { while (true) { text = ramadanV3(); delay(1000) } }; SectionV3("كم باقي على رمضان؟", Icons.Default.Brightness4) { Text(text, fontFamily = ArabicFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()); Text("الموعد المتوقع: 8 فبراير 2027، وقد يتغير حسب رؤية الهلال.", fontFamily = ArabicFont, fontSize = 13.sp, color = WadhkurColors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }

@Composable private fun TileV3(title: String, icon: String, modifier: Modifier, onClick: () -> Unit) { Card(modifier.height(105.dp).clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(icon, fontSize = 28.sp); Text(title, fontFamily = ArabicFont, fontWeight = FontWeight.Bold) } } }

@Composable private fun SectionV3(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) { Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) { Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = WadhkurColors.primary); Spacer(Modifier.width(7.dp)); Text(title, fontFamily = ArabicFont, fontSize = 19.sp, fontWeight = FontWeight.Bold) }; content() } } }

@Composable private fun DhikrV3(p: PaddingValues, title: String, list: List<Dhikr>, back: () -> Unit) { FrameV3(p) { LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { TextButton(onClick = back) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("رجوع") } }; item { Text(title, fontFamily = ArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; items(list) { d -> SectionV3(d.title, Icons.Default.Info) { Text(d.text, fontFamily = ArabicFont, fontSize = 21.sp, lineHeight = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } } } } }

@Composable private fun TasbeehV3(p: PaddingValues, back: () -> Unit) { val options = listOf("سبحان الله", "الحمد لله", "الله أكبر", "لا إله إلا الله", "أستغفر الله العظيم", "لا حول ولا قوة إلا بالله"); var selected by rememberSaveable { mutableStateOf(options[0]) }; var count by rememberSaveable { mutableIntStateOf(0) }; FrameV3(p) { Column(Modifier.fillMaxSize().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = back) { Text("رجوع") }; Text("التسبيح السريع", fontFamily = ArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Text(selected, fontFamily = ArabicFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(count.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); Button(onClick = { count++ }, modifier = Modifier.size(180.dp)) { Text("اضغط للذكر", fontFamily = ArabicFont, fontSize = 20.sp) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.take(3).forEach { ChoiceV3(it, it == selected, Modifier.weight(1f)) { selected = it; count = 0 } } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.drop(3).forEach { ChoiceV3(it, it == selected, Modifier.weight(1f)) { selected = it; count = 0 } } }; TextButton(onClick = { count = 0 }) { Text("تصفير العداد") } } } }

@Composable private fun ChoiceV3(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) { Card(modifier.height(72.dp).clickable(onClick = onClick), border = BorderStroke(1.dp, if (selected) WadhkurColors.primary else WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = if (selected) WadhkurColors.surface2 else WadhkurColors.surface)) { Box(Modifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.Center) { Text(text, fontFamily = ArabicFont, fontSize = 12.sp, textAlign = TextAlign.Center) } } }

@Composable private fun PrayerV3(p: PaddingValues, back: () -> Unit) { val prefs = LocalContext.current.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE); val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble(); val prayers = if (lat.isFinite() && lon.isFinite()) PrayerCalculator.calculate(lat, lon) else null; FrameV3(p) { LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { TextButton(onClick = back) { Text("رجوع") } }; item { Text("مواقيت الصلاة", fontFamily = ArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; if (prayers == null) item { Text("حدّد موقعك من الصفحة الرئيسية.", color = WadhkurColors.muted) } else items(prayers) { x -> SectionV3(x.name, Icons.Default.AccessTime) { Text(x.time, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) } } } } }

@Composable private fun QiblaV3(p: PaddingValues, magneticHeading: Float, back: () -> Unit) { val c = LocalContext.current; val prefs = c.getSharedPreferences("wadhkur_location", Context.MODE_PRIVATE); val lat = prefs.getFloat("lat", Float.NaN).toDouble(); val lon = prefs.getFloat("lon", Float.NaN).toDouble(); val qibla = if (lat.isFinite() && lon.isFinite()) qiblaV3(lat, lon) else null; val declination = if (qibla != null) GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination else 0f; val trueHeading = normV3(magneticHeading + declination); val rotation = if (qibla != null) normV3(qibla - trueHeading) else 0f; FrameV3(p) { Column(Modifier.fillMaxSize().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) { TextButton(onClick = back) { Text("رجوع") }; Text("مؤشر القبلة", fontFamily = ArabicFont, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary); if (qibla == null) Text("حدّد موقعك أولًا لتحديد القبلة.", color = WadhkurColors.muted) else { Text("اتجاه القبلة ${qibla.toInt()}°", fontFamily = ArabicFont, fontSize = 21.sp); Text("حرّك الهاتف ببطء لمعايرة البوصلة", fontFamily = ArabicFont, color = WadhkurColors.muted); Text("➤", fontSize = 120.sp, color = WadhkurColors.primary, modifier = Modifier.graphicsLayer(rotationZ = rotation)); Text("اتجاه الهاتف ${trueHeading.toInt()}°", color = WadhkurColors.muted) } } } }

@Composable private fun MoreV3(p: PaddingValues, go: (String) -> Unit, email: () -> Unit) { FrameV3(p) { LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("المزيد", fontFamily = ArabicFont, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WadhkurColors.primary) }; item { MenuV3("🔔", "إدارة التذكيرات", "الصباح 06:00 • المساء 17:00 • تذكير دوري") { go("reminders") } }; item { MenuV3("🗓️", "التقويم الهجري", "التاريخ الهجري اليوم") { go("calendar") } }; item { MenuV3("🔒", "الخصوصية", "الموقع يستخدم محليًا") { go("privacy") } }; item { MenuV3("✉️", "تواصل مع المطور", EMAIL, email) }; item { SectionV3("حول وذكر", Icons.Default.Info) { Text("المطور صالح الخليفي", fontWeight = FontWeight.Bold); Text(EMAIL, color = WadhkurColors.muted) } } } } }
@Composable private fun MenuV3(icon: String, title: String, subtitle: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), border = BorderStroke(1.dp, WadhkurColors.edge), colors = CardDefaults.cardColors(containerColor = WadhkurColors.surface)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 28.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontFamily = ArabicFont, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontFamily = ArabicFont, color = WadhkurColors.muted) }; Text("‹", fontSize = 28.sp, color = WadhkurColors.primary) } } }

@Composable private fun ReminderSettingsV3(p: PaddingValues, back: () -> Unit) { val c = LocalContext.current; val prefs = c.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE); var general by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.ENABLED, true)) }; var morning by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.MORNING_ENABLED, true)) }; var evening by remember { mutableStateOf(prefs.getBoolean(ReminderScheduler.EVENING_ENABLED, true)) }; var interval by remember { mutableIntStateOf(prefs.getInt(ReminderScheduler.INTERVAL, 30).coerceIn(1, 60)) }; val options = listOf(1,3,5,10,15,20,30,45,60); fun save() { prefs.edit().putBoolean(ReminderScheduler.ENABLED,general).putBoolean(ReminderScheduler.MORNING_ENABLED,morning).putBoolean(ReminderScheduler.EVENING_ENABLED,evening).putInt(ReminderScheduler.INTERVAL,interval).apply(); ReminderScheduler.scheduleAll(c) }; FrameV3(p) { LazyColumn(contentPadding = PaddingValues(top=10.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { item { TextButton(onClick=back){Text("رجوع") } }; item { Text("إدارة التذكيرات",fontFamily=ArabicFont,fontSize=29.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary) }; item { ReminderRowV3("التذكير العام","كل $interval دقيقة",general){general=it;save()} }; item { SectionV3("فاصل التذكير العام",Icons.Default.Notifications){ Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){options.take(5).forEach{n->ChoiceV3("$n د",interval==n,Modifier.weight(1f)){interval=n;save()}}}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){options.drop(5).forEach{n->ChoiceV3(if(n==60)"ساعة" else "$n د",interval==n,Modifier.weight(1f)){interval=n;save()}}} } }; item { ReminderRowV3("أذكار الصباح","كل يوم الساعة 06:00",morning){morning=it;save()} }; item { ReminderRowV3("أذكار المساء","كل يوم الساعة 17:00",evening){evening=it;save()} }; item { SectionV3("مهم",Icons.Default.Info){Text("يجب السماح بإشعارات وذكر التطبيق والمنبهات الدقيقة من إعدادات الهاتف. قد يؤخر النظام بعض المنبهات عند تفعيل أوضاع توفير البطارية.",fontFamily=ArabicFont,color=WadhkurColors.muted)} } } } }
@Composable private fun ReminderRowV3(title:String,subtitle:String,checked:Boolean,onChecked:(Boolean)->Unit){Card(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,WadhkurColors.edge),colors=CardDefaults.cardColors(containerColor=WadhkurColors.surface)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontFamily=ArabicFont,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontFamily=ArabicFont,color=WadhkurColors.muted)};Switch(checked,onCheckedChange=onChecked)}}}

@Composable private fun CalendarV3(p: PaddingValues,back:()->Unit){FrameV3(p){Column(Modifier.fillMaxSize().padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=back){Text("رجوع")};Text("التقويم الهجري",fontFamily=ArabicFont,fontSize=29.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary);Text(islamicDateV3(Calendar.getInstance()),fontFamily=ArabicFont,fontSize=23.sp);Text(gregorianV3(),color=WadhkurColors.muted)}}}
@Composable private fun PrivacyV3(p: PaddingValues,back:()->Unit){FrameV3(p){Column(Modifier.fillMaxSize().padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=back){Text("رجوع")};Text("الخصوصية",fontFamily=ArabicFont,fontSize=29.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary);Text("الموقع يستخدم محليًا لحساب مواقيت الصلاة واتجاه القبلة. لا يحتاج التطبيق إلى حساب.",fontFamily=ArabicFont)}}}
@Composable private fun AdV3(){AndroidView(Modifier.fillMaxWidth().height(60.dp),factory={AdView(it).apply{setAdSize(AdSize.BANNER);adUnitId="ca-app-pub-3940256099942544/6300978111";loadAd(AdRequest.Builder().build())}})}

private fun ramadanV3():String{val target=Calendar.getInstance().apply{set(2027,Calendar.FEBRUARY,8,0,0,0);set(Calendar.MILLISECOND,0)};var s=(target.timeInMillis-System.currentTimeMillis())/1000;if(s<=0)return"رمضان 1448 هـ بدأ";val d=s/86400;s%=86400;val h=s/3600;s%=3600;val m=s/60;val sec=s%60;return String.format(Locale.US,"%d يوم  %02d ساعة  %02d دقيقة  %02d ثانية",d,h,m,sec)}
private fun countdownV3(ms:Long):String{if(ms<=0)return"الآن";var s=ms/1000;val h=s/3600;s%=3600;val m=s/60;val sec=s%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,sec)}
private fun nextPrayerV3(prayers:List<PrayerCalculator.PrayerTime>,now:Long):Triple<String,String,Long>?{val cur=Calendar.getInstance().apply{timeInMillis=now};val cm=cur.get(Calendar.HOUR_OF_DAY)*60+cur.get(Calendar.MINUTE);val list=prayers.mapNotNull{p->val x=p.time.split(":");if(x.size!=2)null else{val h=x[0].toIntOrNull();val m=x[1].toIntOrNull();if(h==null||m==null)null else Triple(p.name,p.time,h*60+m)}};val n=list.firstOrNull{it.third>cm}?:list.firstOrNull()?:return null;val t=Calendar.getInstance().apply{timeInMillis=now;if(n.third<=cm)add(Calendar.DAY_OF_YEAR,1);set(Calendar.HOUR_OF_DAY,n.third/60);set(Calendar.MINUTE,n.third%60);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)};return Triple(n.first,n.second,t.timeInMillis)}
private fun qiblaV3(lat:Double,lon:Double):Float{val kl=Math.toRadians(21.4225);val dl=Math.toRadians(39.8262)-Math.toRadians(lon);var b=Math.toDegrees(atan2(sin(dl),cos(Math.toRadians(lat))*sin(kl)-sin(Math.toRadians(lat))*cos(kl)*cos(dl))).toFloat();if(b<0)b+=360f;return b}
private fun normV3(v:Float):Float{var x=v%360f;if(x<0)x+=360f;return x}
private fun gregorianV3()=SimpleDateFormat("EEEE، d MMMM yyyy",Locale("ar")).format(Date())
private fun islamicDateV3(c:Calendar):String{val d=c.get(Calendar.DAY_OF_MONTH);val m=c.get(Calendar.MONTH)+1;val y=c.get(Calendar.YEAR);val jd=367*y-(7*(y+(m+9)/12))/4+(275*m)/9+d+1721013.5;val l=jd.toLong()-1948440+10632;val n=(l-1)/10631;val ll=l-10631*n+354;val j=((10985-ll)/5316)*((50*ll)/17719)+(ll/5670)*((43*ll)/15238);val mh=(24*j)/709;val dh=ll-(709*mh)/24;val yh=30*n+j-30;val months=listOf("محرم","صفر","ربيع الأول","ربيع الآخر","جمادى الأولى","جمادى الآخرة","رجب","شعبان","رمضان","شوال","ذو القعدة","ذو الحجة");return "$dh ${months[((mh-1).coerceIn(0,11)).toInt()]} $yh هـ"}
