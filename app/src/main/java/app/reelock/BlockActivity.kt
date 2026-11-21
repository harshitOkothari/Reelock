package app.reelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Full-screen accent "not now" card shown when a Reel is blocked. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        Pal.idx = Store.theme
        val msg = intent.getStringExtra("m") ?: "Reels are blocked\nby Reelock."
        val saved = fmtDur(Store.savedSecToday())
        val quotes = listOf("You won't remember this scroll.", "Your future self is watching.", "Boredom is where ideas start.",
            "One less reel. One more real moment.", "Go do something you'll tell people about.")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTextStyle(TextStyle(fontFamily = Body)) {
                    val a = remember { Animatable(0f) }
                    val p = remember { Animatable(1f) }
                    val quote = remember { quotes.random() }
                    LaunchedEffect(Unit) {
                        launch { a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
                        p.animateTo(0f, tween(6000, easing = LinearEasing))
                        finish()
                    }
                    Box(Modifier.fillMaxSize().background(Accent)) {
                        Column(
                            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp)
                                .graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }
                        ) {
                            Label(NAME, Ink.copy(alpha = 0.6f))
                            Spacer(Modifier.weight(1f))
                            Display(msg, 52.sp, Ink)
                            Spacer(Modifier.height(18.dp))
                            Text("You've got $saved back today.", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(quote, color = Ink.copy(alpha = 0.7f), fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(Ink).tap { finish() },
                                contentAlignment = Alignment.Center
                            ) { Text("Back to my feed", color = Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.copy(alpha = 0.18f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(Ink))
                            }
                        }
                    }
                }
            }
        }
    }
}
