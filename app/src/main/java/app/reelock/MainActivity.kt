package app.reelock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        Pal.idx = Store.theme
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTextStyle(TextStyle(fontFamily = Body)) {
                    var splash by remember { mutableStateOf(true) }
                    var ob by remember { mutableStateOf(Store.onboarded) }
                    var consent by remember { mutableStateOf(Store.consent) }
                    LaunchedEffect(Unit) { delay(1600); splash = false }
                    Crossfade(targetState = splash, animationSpec = tween(400), label = "splash") { sp ->
                        when {
                            sp -> Splash()
                            !ob -> Onboarding { ob = true; consent = Store.consent }
                            !consent -> ConsentGate { consent = true }
                            else -> Shell { Store.onboarded = false; ob = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Splash() = Backdrop {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(500); a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Bottom) {
        Logo(96.dp)
        Spacer(Modifier.height(24.dp))
        Display(NAME.lowercase() + ".", 64.sp, modifier = Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 24f })
        Spacer(Modifier.height(4.dp))
        Box(Modifier.graphicsLayer { alpha = a.value }) { Label(TAGLINE) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Shell(onRedo: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var ver by remember { mutableIntStateOf(0) }
    var on by remember { mutableStateOf(serviceOn(ctx)) }
    LaunchedEffect(Unit) { while (true) { on = serviceOn(ctx); ver++; delay(1000) } }
    Backdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(), label = "tab",
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 12 * dir }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 12 * dir })
                }
            ) { t ->
                when (t) {
                    0 -> TodayTab(ver, on) { ver++ }
                    1 -> InsightsTab(ver)
                    else -> ControlsTab(ver, { ver++ }, onRedo)
                }
            }
            Column(Modifier.fillMaxWidth().background(Ink)) {
                Divider()
                BoxWithConstraints(Modifier.navigationBarsPadding().fillMaxWidth()) {
                    val w = maxWidth / 3
                    val off by animateDpAsState(w * tab, spring(dampingRatio = 0.8f, stiffness = 450f), label = "nav")
                    Box(Modifier.offset(x = off).width(w).height(3.dp).background(Accent))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("today", "insights", "controls").forEachIndexed { i, l ->
                            Box(Modifier.weight(1f).tap { tab = i }.padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                Text(l, color = if (tab == i) Paper else Muted, fontSize = 13.sp, fontFamily = Mono)
                            }
                        }
                    }
                }
            }
        }
    }
}
