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
import androidx.compose.material3.MaterialTheme
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val dhikrText =
            intent.getStringExtra("dhikr_text")
                ?: "سبحان الله وبحمده"

        val dhikrTitle =
            intent.getStringExtra("dhikr_title")
                ?: "تذكير بالذكر"

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

    val background = Color(0xFF050B10)
    val neonCyan = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00FF9D)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "وٌ ذکْــر",
                color = neonCyan,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "تذكير بالذكر",
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                neonCyan,
                                neonGreen,
                                neonCyan
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(2.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF0B151C),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(
                            horizontal = 22.dp,
                            vertical = 30.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = title,
                        color = neonGreen,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(
                        modifier = Modifier.height(22.dp)
                    )

                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 23.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(35.dp)
            )

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00C98B),
                    contentColor = Color.Black
                )
            ) {

                Text(
                    text = "رددته ✓",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "واذكر ربك إذا نسيت",
                color = Color(0xFF8FA3AD),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}