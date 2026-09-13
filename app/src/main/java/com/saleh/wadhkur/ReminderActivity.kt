package com.saleh.wadhkur

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إظهار شاشة التذكير فوق شاشة القفل وتشغيل الشاشة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        /*
         * ReminderReceiver يرسل النص في:
         * "dhikr"
         *
         * وندعم أيضًا الاسم القديم:
         * "dhikr_text"
         */
        val dhikrText =
            intent.getStringExtra("dhikr")
                ?: intent.getStringExtra("dhikr_text")
                ?: "سبحان الله وبحمده"

        /*
         * تحديد نوع التذكير القادم من ReminderReceiver
         */
        val type = intent.getStringExtra("type")

        /*
         * عنوان الشاشة حسب نوع التذكير
         */
        val dhikrTitle = when (type) {
            ReminderScheduler.TYPE_MORNING ->
                "🌅 أذكار الصباح"

            ReminderScheduler.TYPE_EVENING ->
                "🌙 أذكار المساء"

            else ->
                "🔔 تذكير بالذكر"
        }

        setContent {
            ReminderScreen(
                title = dhikrTitle,
                text = dhikrText,
                onClose = {
                    finish()
                }
            )
        }
    }
}

@Composable
private fun ReminderScreen(
    title: String,
    text: String,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF06131A),
                        Color(0xFF081D24),
                        Color(0xFF031015)
                    )
                )
            )
            .padding(24.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "وٌ ذکْــر",
                color = Color(0xFF00E5FF),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = title,
                color = Color(0xFF80DEEA),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF00FF9D)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(2.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF081B21),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(
                            horizontal = 22.dp,
                            vertical = 30.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00CFA3),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "رددته ✓",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            Text(
                text = "وَاذْكُر رَّبَّكَ إِذَا نَسِيتَ",
                color = Color(0xFF80DEEA),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}