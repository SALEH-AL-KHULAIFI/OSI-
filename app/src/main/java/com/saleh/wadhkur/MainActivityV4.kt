package com.saleh.wadhkur

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

private val WadhkurFont = FontFamily.Serif

class MainActivityV4 : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heading by mutableFloatStateOf(0f)
    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { saveLocation() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9100)
        ReminderScheduler.scheduleAll(this)
        setContent { WadhkurTheme { WadhkurV4(::requestLocation, heading) } }
    }
    override fun onResume() { super.onResume(); sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) } }
    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onSensorChanged(e: SensorEvent) { if(e.sensor.type==Sensor.TYPE_ROTATION_VECTOR){ val r=FloatArray(9); val o=FloatArray(3); SensorManager.getRotationMatrixFromVector(r,e.values); SensorManager.getOrientation(r,o); var h=Math.toDegrees(o[0].toDouble()).toFloat(); if(h<0)h+=360f; heading=h } }
    override fun onAccuracyChanged(s: Sensor?,a:Int)=Unit
    private fun requestLocation(){
        val f=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val c=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!f&&!c) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) else saveLocation()
    }
    private fun saveLocation(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        try { val lm=getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager; var best:android.location.Location?=null; lm.getProviders(true).forEach{p->lm.getLastKnownLocation(p)?.let{if(best==null||it.accuracy<best!!.accuracy)best=it}}; best?.let{getSharedPreferences("wadhkur_location",0).edit().putFloat("lat",it.latitude.toFloat()).putFloat("lon",it.longitude.toFloat()).apply()} } catch(_:Exception){}
    }
}

@Composable private fun WadhkurV4(requestLocation:()->Unit,heading:Float){
    var screen by rememberSaveable{mutableStateOf("home")}
    Scaffold(containerColor=WadhkurColors.background,bottomBar={NavigationBar(containerColor=WadhkurColors.surface){
        NavigationBarItem(screen=="home",{screen="home"},{Icon(Icons.Default.Home,null)},label={Text("الرئيسية",fontFamily=WadhkurFont)})
        NavigationBarItem(screen=="prayer",{screen="prayer"},{Icon(Icons.Default.AccessTime,null)},label={Text("الصلاة",fontFamily=WadhkurFont)})
        NavigationBarItem(screen=="qibla",{screen="qibla"},{Icon(Icons.Default.Explore,null)},label={Text("القبلة",fontFamily=WadhkurFont)})
        NavigationBarItem(screen=="more",{screen="more"},{Icon(Icons.Default.MoreHoriz,null)},label={Text("المزيد",fontFamily=WadhkurFont)})
    }}){p->when(screen){
        "home"->HomeV4(p,requestLocation){screen=it}
        "prayer"->PrayerV4(p){screen="home"}
        "qibla"->QiblaV4(p,heading){screen="home"}
        "more"->MoreV4(p){screen=it}
        "reminders"->ReminderSettingsV4(p){screen="more"}
        "morning"->DhikrPageV4(p,"أذكار الصباح",DhikrRepository.morning){screen="home"}
        "evening"->DhikrPageV4(p,"أذكار المساء",DhikrRepository.evening){screen="home"}
        "general"->DhikrPageV4(p,"أذكار عامة",DhikrRepository.main){screen="home"}
        "tasbeeh"->TasbeehV4(p){screen="home"}
    }} }
}

@Composable private fun HomeV4(p:PaddingValues,requestLocation:()->Unit,go:(String)->Unit){
    val c=LocalContext.current; val prefs=c.getSharedPreferences("wadhkur_location",0); val lat=prefs.getFloat("lat",Float.NaN).toDouble(); val lon=prefs.getFloat("lon",Float.NaN).toDouble(); val prayers=if(lat.isFinite()&&lon.isFinite())PrayerCalculator.calculate(lat,lon)else null
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}; LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();delay(1000)}}
    LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp,8.dp,14.dp,28.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{Column(Modifier.fillMaxWidth().padding(top=6.dp,bottom=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("☾",fontSize=44.sp,color=WadhkurColors.primary); Text("وذكر",fontFamily=WadhkurFont,fontSize=42.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary); Text("رفيقك اليومي للذكر والصلاة",fontFamily=WadhkurFont,fontSize=15.sp,color=WadhkurColors.muted)
        }}
        item{RamadanCardV4()}
        item{if(prayers==null)PremiumCardV4("الصلاة القادمة","فعّل الموقع لعرض الصلاة القادمة",Icons.Default.LocationOn){requestLocation()}else{val n=nextV4(prayers,now);PremiumCardV4("الصلاة القادمة",n.first+"  •  "+n.second,Icons.Default.AccessTime){go("prayer")}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(11.dp)){HomeTileV4("أذكار الصباح","☀",Modifier.weight(1f)){go("morning")};HomeTileV4("أذكار المساء","☾",Modifier.weight(1f)){go("evening")}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(11.dp)){HomeTileV4("التسبيح","۞",Modifier.weight(1f)){go("tasbeeh")};HomeTileV4("أذكار عامة","✦",Modifier.weight(1f)){go("general")}}}
        item{PremiumCardV4("مواقيت الصلاة",if(prayers==null)"فعّل الموقع أولاً" else prayers.joinToString("   •   "){it.name+" "+it.time},Icons.Default.AccessTime){if(prayers==null)requestLocation()else go("prayer")}}
    }
}

@Composable private fun RamadanCardV4(){var t by remember{mutableStateOf(ramadanV4())};LaunchedEffect(Unit){while(true){t=ramadanV4();delay(1000)}};PremiumCardV4("كم باقي على رمضان؟",t,Icons.Default.Brightness4){}}
@Composable private fun PremiumCardV4(title:String,text:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=MaterialTheme.shapes.large,border=BorderStroke(1.dp,WadhkurColors.edge),colors=CardDefaults.cardColors(containerColor=WadhkurColors.surface)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=WadhkurColors.primary,modifier=Modifier.size(27.dp));Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(title,fontFamily=WadhkurFont,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(text,fontFamily=WadhkurFont,fontSize=15.sp,color=WadhkurColors.muted,lineHeight=25.sp)}}}}
@Composable private fun HomeTileV4(title:String,icon:String,modifier:Modifier,onClick:()->Unit){Card(modifier.height(112.dp).clickable(onClick=onClick),shape=MaterialTheme.shapes.large,border=BorderStroke(1.dp,WadhkurColors.edge),colors=CardDefaults.cardColors(containerColor=WadhkurColors.surface)){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(icon,fontSize=32.sp,color=WadhkurColors.primary);Text(title,fontFamily=WadhkurFont,fontSize=17.sp,fontWeight=FontWeight.Bold)}}}

@Composable private fun MoreV4(p:PaddingValues,go:(String)->Unit){Column(Modifier.fillMaxSize().padding(p).padding(horizontal=14.dp)){Text("المزيد",fontFamily=WadhkurFont,fontSize=32.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary,modifier=Modifier.padding(top=14.dp,bottom=14.dp));Menu4("🔔","إعدادات التذكير","ثلاثة أقسام مستقلة للتذكير"){go("reminders")};Menu4("☀","أذكار الصباح","يوميًا 06:00"){go("morning")};Menu4("☾","أذكار المساء","يوميًا 17:00"){go("evening")};Menu4("📿","التسبيح","ستة أذكار قابلة للاختيار"){go("tasbeeh")}}
@Composable private fun Menu4(i:String,t:String,s:String,onClick:()->Unit){Card(Modifier.fillMaxWidth().padding(bottom=10.dp).clickable(onClick=onClick),shape=MaterialTheme.shapes.large,border=BorderStroke(1.dp,WadhkurColors.edge),colors=CardDefaults.cardColors(containerColor=WadhkurColors.surface)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(i,fontSize=28.sp);Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(t,fontFamily=WadhkurFont,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(s,fontFamily=WadhkurFont,color=WadhkurColors.muted)};Text("‹",fontSize=27.sp,color=WadhkurColors.primary)}}}

@Composable private fun ReminderSettingsV4(p:PaddingValues,back:()->Unit){
    val c=LocalContext.current; val prefs=c.getSharedPreferences(ReminderScheduler.PREFS,0); var general by remember{mutableStateOf(prefs.getBoolean(ReminderScheduler.ENABLED,true))}; var morning by remember{mutableStateOf(prefs.getBoolean(ReminderScheduler.MORNING_ENABLED,true))}; var evening by remember{mutableStateOf(prefs.getBoolean(ReminderScheduler.EVENING_ENABLED,true))}; var interval by remember{mutableIntStateOf(prefs.getInt(ReminderScheduler.INTERVAL,30).coerceIn(1,60))}
    fun save(){prefs.edit().putBoolean(ReminderScheduler.ENABLED,general).putBoolean(ReminderScheduler.MORNING_ENABLED,morning).putBoolean(ReminderScheduler.EVENING_ENABLED,evening).putInt(ReminderScheduler.INTERVAL,interval).apply();ReminderScheduler.scheduleAll(c)}
    LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp,8.dp,14.dp,30.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{TextButton(onClick=back){Icon(Icons.Default.ArrowBack,null);Text("رجوع",fontFamily=WadhkurFont)}}
        item{Text("إعدادات التذكير",fontFamily=WadhkurFont,fontSize=31.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary)}
        item{ReminderSection4("١  التذكير العام بالأدعية","تذكير دوري من دقيقة واحدة إلى ساعة",general,{general=it;save()}){IntervalPickerV4(interval){interval=it;save()}}}
        item{ReminderSection4("٢  تذكير أذكار الصباح","كل يوم الساعة 06:00 صباحًا",morning,{morning=it;save()}){}}
        item{ReminderSection4("٣  تذكير أذكار المساء","كل يوم الساعة 17:00 مساءً",evening,{evening=it;save()}){}}
        item{Text("لكل تذكير زر تشغيل / إيقاف مستقل، والتغيير يُطبّق مباشرة.",fontFamily=WadhkurFont,fontSize=14.sp,color=WadhkurColors.muted,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(8.dp))}
    }
}
@Composable private fun ReminderSection4(title:String,subtitle:String,on:Boolean,set:(Boolean)->Unit,extra:@Composable()->Unit){Card(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge,border=BorderStroke(1.dp,if(on)WadhkurColors.primary else WadhkurColors.edge),colors=CardDefaults.cardColors(containerColor=WadhkurColors.surface)){Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontFamily=WadhkurFont,fontSize=19.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontFamily=WadhkurFont,color=WadhkurColors.muted)};Switch(checked=on,onCheckedChange=set)};if(on)extra()}}}
@Composable private fun IntervalPickerV4(value:Int,set:(Int)->Unit){Text("الفاصل الحالي: $value دقيقة",fontFamily=WadhkurFont,fontWeight=FontWeight.Bold);val opts=listOf(1,3,5,10,15,20,30,45,60);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){opts.take(5).forEach{n->FilterChip(n==value,{set(n)},label={Text("$n")},modifier=Modifier.weight(1f))}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){opts.drop(5).forEach{n->FilterChip(n==value,{set(n)},label={Text(if(n==60)"60" else "$n")},modifier=Modifier.weight(1f))}}}

@Composable private fun DhikrPageV4(p:PaddingValues,title:String,list:List<Dhikr>,back:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp,8.dp,14.dp,28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{TextButton(onClick=back){Text("رجوع",fontFamily=WadhkurFont)}};item{Text(title,fontFamily=WadhkurFont,fontSize=30.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary)};items(list.size){i->val d=list[i];PremiumCardV4(d.title,d.text,Icons.Default.Info){}}}}
@Composable private fun TasbeehV4(p:PaddingValues,back:()->Unit){val opts=listOf("سبحان الله","الحمد لله","الله أكبر","لا إله إلا الله","أستغفر الله العظيم","لا حول ولا قوة إلا بالله");var selected by rememberSaveable{mutableStateOf(opts[0])};var count by rememberSaveable{mutableIntStateOf(0)};Column(Modifier.fillMaxSize().padding(p).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=back){Text("رجوع",fontFamily=WadhkurFont)};Text("التسبيح",fontFamily=WadhkurFont,fontSize=30.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary);Text(selected,fontFamily=WadhkurFont,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(count.toString(),fontSize=72.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary);Button(onClick={count++},modifier=Modifier.size(175.dp)){Text("اضغط للذكر",fontFamily=WadhkurFont)};opts.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{o->FilterChip(o==selected,{selected=o;count=0},label={Text(o,fontFamily=WadhkurFont,fontSize=12.sp)},modifier=Modifier.weight(1f))}}};TextButton(onClick={count=0}){Text("تصفير العداد",fontFamily=WadhkurFont)}}}
@Composable private fun PrayerV4(p:PaddingValues,back:()->Unit){val c=LocalContext.current;val pr=locationPrayer(c);LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(14.dp,8.dp,14.dp,28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{TextButton(onClick=back){Text("رجوع",fontFamily=WadhkurFont)}};item{Text("مواقيت الصلاة",fontFamily=WadhkurFont,fontSize=30.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary)};if(pr==null)item{Text("حدّد الموقع من الصفحة الرئيسية.",fontFamily=WadhkurFont,color=WadhkurColors.muted)}else pr.forEach{x->item{PremiumCardV4(x.name,x.time,Icons.Default.AccessTime){}}}}}
@Composable private fun QiblaV4(p:PaddingValues,heading:Float,back:()->Unit){val pos=location(LocalContext.current);Column(Modifier.fillMaxSize().padding(p).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=back){Text("رجوع",fontFamily=WadhkurFont)};Text("مؤشر القبلة",fontFamily=WadhkurFont,fontSize=30.sp,fontWeight=FontWeight.Bold,color=WadhkurColors.primary);if(pos==null)Text("حدّد الموقع أولًا.",fontFamily=WadhkurFont,color=WadhkurColors.muted)else{val b=qibla4(pos.first,pos.second);val d=android.hardware.GeomagneticField(pos.first.toFloat(),pos.second.toFloat(),0f,System.currentTimeMillis()).declination;Text("اتجاه القبلة ${b.toInt()}°",fontFamily=WadhkurFont,fontSize=20.sp);Text("حرّك الهاتف ببطء للمعايرة",fontFamily=WadhkurFont,color=WadhkurColors.muted);Text("➤",fontSize=120.sp,color=WadhkurColors.primary,modifier=Modifier.graphicsLayer(rotationZ=norm4(b-(heading+d))));Text("اتجاه الهاتف ${norm4(heading+d).toInt()}°",color=WadhkurColors.muted)}}}

private fun location(c:Context):Pair<Double,Double>?{val p=c.getSharedPreferences("wadhkur_location",0);val a=p.getFloat("lat",Float.NaN).toDouble();val b=p.getFloat("lon",Float.NaN).toDouble();return if(a.isFinite()&&b.isFinite())a to b else null}
private fun locationPrayer(c:Context)=location(c)?.let{PrayerCalculator.calculate(it.first,it.second)}
private fun nextV4(p:List<PrayerCalculator.PrayerTime>,now:Long):Pair<String,String>{val cur=Calendar.getInstance().apply{timeInMillis=now};val mins=cur.get(Calendar.HOUR_OF_DAY)*60+cur.get(Calendar.MINUTE);val list=p.mapNotNull{x->val z=x.time.take(5).split(":");if(z.size!=2)null else{val h=z[0].toIntOrNull();val m=z[1].toIntOrNull();if(h==null||m==null)null else Triple(x.name,x.time,h*60+m)}};val n=list.firstOrNull{it.third>mins}?:list.first();return n.first to n.second}
private fun qibla4(lat:Double,lon:Double):Float{val k=Math.toRadians(21.4225);val d=Math.toRadians(39.8262)-Math.toRadians(lon);var b=Math.toDegrees(atan2(sin(d),cos(Math.toRadians(lat))*sin(k)-sin(Math.toRadians(lat))*cos(k)*cos(d))).toFloat();if(b<0)b+=360;return b}
private fun norm4(x:Float):Float{var v=x%360;if(v<0)v+=360;return v}
private fun ramadanV4():String{val t=Calendar.getInstance().apply{set(2027,1,8,0,0,0);set(Calendar.MILLISECOND,0)};var s=(t.timeInMillis-System.currentTimeMillis())/1000;if(s<=0)return"رمضان 1448 هـ بدأ";val d=s/86400;s%=86400;val h=s/3600;s%=3600;val m=s/60;val sec=s%60;return String.format(Locale.US,"%d يوم  %02d ساعة  %02d دقيقة  %02d ثانية",d,h,m,sec)}