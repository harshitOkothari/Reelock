package app.reelock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

private fun ml(n: Int) = "%,d".format(Locale.getDefault(), n)

fun openWater(c: Context) = c.startActivity(Intent(c, WaterActivity::class.java))

/**
 * Small floating "how much water?" window.
 * It is a see-through activity, so whatever app you were in stays visible behind it, and finishing it drops you
 * straight back there. No overlay permission and no background service needed.
 */
class WaterActivity : ComponentActivity() {
    private var session by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        Store.pruneWater()
        Pal.idx = Store.theme
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTextStyle(TextStyle(fontFamily = Body)) {
                    key(session) { WaterPopup { finish() } }
                }
            }
        }
    }

    /** triple-tap again while the popup is already open: start it fresh */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session++
    }
}

// =============================== POPUP ===============================
@Composable
fun WaterPopup(onClose: () -> Unit) {
    val hap = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val a = remember { Animatable(0f) }
    var total by remember { mutableIntStateOf(Store.waterToday()) }
    var goal by remember { mutableIntStateOf(Store.waterGoal) }
    var last by remember { mutableIntStateOf(Store.waterLast()) }
    var pick by remember { mutableFloatStateOf((Store.waterPick.coerceIn(50, 1000) / 50 * 50).toFloat()) }
    var burst by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch { a.animateTo(0f, tween(220)); onClose() }
    }
    fun add(n: Int) {
        if (n <= 0 || closing) return
        val before = total
        Store.addWater(n)
        Store.waterPick = if (n % 50 == 0 && n <= 1000) n else Store.waterPick
        total = Store.waterToday(); last = Store.waterLast()
        val hit = before < goal && total >= goal
        if (hit) burst++
        job?.cancel()
        job = scope.launch { delay(if (hit) 1900L else 1300L); close() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f * a.value)).tap { close() },
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 420.dp).fillMaxWidth()
                .graphicsLayer {
                    alpha = a.value
                    translationY = (1f - a.value) * 70f
                    scaleX = 0.94f + 0.06f * a.value; scaleY = 0.94f + 0.06f * a.value
                }
                .clip(shape).background(Panel).border(1.dp, Rule, shape)
                .tap { }                                   // taps on the card must not close the popup
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("water  /  today"); Spacer(Modifier.weight(1f))
                Box(Modifier.tap { close() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) { Label("close") }
            }

            // ---- glass + numbers ----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                WaterGlass(total / goal.coerceAtLeast(1).toFloat(), Modifier.width(98.dp).height(168.dp), wave = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        CountText(total, { ml(it) }, 52.sp, Paper)
                        Text(" ml", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text("of ${ml(goal)} ml target", color = Paper, fontSize = 14.sp)
                    if (total >= goal) Label("target hit", Accent) else Label("${ml(goal - total)} ml to go")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("target"); Spacer(Modifier.weight(1f))
                        StepKey("-") { Store.waterGoal = goal - 250; goal = Store.waterGoal; job?.cancel() }
                        StepKey("+") { Store.waterGoal = goal + 250; goal = Store.waterGoal; job?.cancel() }
                    }
                }
            }

            // ---- one-tap amounts ----
            Label("quick add")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(100, 250, 500).forEach { n ->
                    val sh = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.weight(1f).bounceClick { hap.performHapticFeedback(HapticFeedbackType.LongPress); add(n) }
                            .clip(sh).border(1.5.dp, Paper.copy(alpha = 0.5f), sh).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Display("$n", 28.sp)
                        Label("ml")
                    }
                }
            }

            // ---- exact amount ----
            Label("or set an exact amount")
            Row(verticalAlignment = Alignment.Bottom) {
                Display("${pick.roundToInt()}", 48.sp, Accent)
                Text(" ml", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            RulerPicker(pick, 0f, 1000f, 50f, { pick = it; job?.cancel() })
            Btn("Add ${pick.roundToInt()} ml", enabled = pick.roundToInt() >= 50) { add(pick.roundToInt()) }

            if (last > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("+${ml(last)} ml logged", Accent); Spacer(Modifier.weight(1f))
                    Box(Modifier.tap { Store.undoWater(); total = Store.waterToday(); last = 0; job?.cancel() }.padding(vertical = 4.dp, horizontal = 4.dp)) {
                        Label("undo", Paper)
                    }
                }
            }
        }
        if (burst > 0) key(burst) { Burst() }
    }
}

@Composable
private fun StepKey(t: String, onClick: () -> Unit) {
    val hap = LocalHapticFeedback.current
    val sh = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(40.dp).bounceClick { hap.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .clip(sh).border(1.dp, Rule, sh),
        contentAlignment = Alignment.Center
    ) { Text(t, color = Paper, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
}

// =============================== GLASS ===============================
/** A glass that fills with the accent colour as the day's target is reached. Flat, with a gentle surface wave. */
@Composable
fun WaterGlass(frac: Float, modifier: Modifier = Modifier, wave: Boolean = true) {
    val lvl = remember { Animatable(0f) }
    LaunchedEffect(frac) { lvl.animateTo(frac.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    val tr = rememberInfiniteTransition(label = "wave")
    val ph by tr.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    val accent = Accent
    Canvas(modifier) {
        val gw = size.width - 14.dp.toPx()          // the right-hand strip holds the 25/50/75% ticks
        val h = size.height
        val r = 22.dp.toPx()
        val glass = Path().apply { addRoundRect(RoundRect(0f, 0f, gw, h, CornerRadius(r, r))) }
        drawPath(glass, Ink)
        clipPath(glass) {
            val l = lvl.value
            if (l > 0.002f) {
                val top = h * (1f - l)
                val amp = if (wave && l < 0.995f) 3.dp.toPx() else 0f
                val phase = if (wave) ph else 0f
                val p = Path()
                p.moveTo(0f, h)
                p.lineTo(0f, top + sin(phase) * amp)
                var x = 0f
                while (x < gw) { p.lineTo(x, top + sin(x / gw * 6.2832f + phase) * amp); x += 4f }
                p.lineTo(gw, top + sin(6.2832f + phase) * amp)
                p.lineTo(gw, h)
                p.close()
                drawPath(p, accent)
            }
        }
        drawPath(glass, Paper.copy(alpha = 0.3f), style = Stroke(2.dp.toPx()))
        for (i in 1..3) {
            val y = h * (1f - i / 4f)
            val len = if (i == 2) 11.dp.toPx() else 7.dp.toPx()
            drawLine(Paper.copy(alpha = 0.35f), Offset(gw + 3.dp.toPx(), y), Offset(gw + 3.dp.toPx() + len, y), 2f)
        }
    }
}

// =============================== WEEK ===============================
/** 7 bars, oldest to today. Bars that reached the target are accent coloured; the dashed line is the target. */
@Composable
fun WaterWeek(week: List<Pair<java.time.LocalDate, Int>>, goal: Int) {
    val a = remember { Animatable(0f) }
    val key = week.sumOf { it.second } + goal
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val accent = Accent
    Column {
        Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val mx = maxOf(goal, week.maxOfOrNull { it.second } ?: 0, 1) * 1.1f
            val slot = size.width / week.size
            val bw = slot * 0.5f
            val y = size.height * (1 - goal / mx)
            drawLine(Paper.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)))
            week.forEachIndexed { i, (_, v) ->
                val bh = maxOf(size.height * (v / mx) * a.value, 6f)
                val isToday = i == week.size - 1
                val col = if (v >= goal) accent else Paper
                val alpha = if (v >= goal) (if (isToday) 1f else 0.6f) else (if (isToday) 0.5f else 0.25f)
                drawRoundRect(col.copy(alpha = alpha), Offset(i * slot + (slot - bw) / 2, size.height - bh), Size(bw, bh), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            week.forEach { (d, _) ->
                Text(
                    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).lowercase().take(3),
                    Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontFamily = Mono, textAlign = TextAlign.Center
                )
            }
        }
    }
}

// =============================== TODAY TAB TILE ===============================
@Composable
fun WaterTile() {
    val ctx = LocalContext.current
    val total = Store.waterToday()
    val goal = Store.waterGoal
    val week = Store.waterWeek()
    val avg = week.sumOf { it.second } / week.size
    Tile(Modifier.bounceClick { openWater(ctx) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("water today"); Spacer(Modifier.weight(1f)); Label("tap to log", Accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WaterGlass(total / goal.coerceAtLeast(1).toFloat(), Modifier.width(64.dp).height(108.dp), wave = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    CountText(total, { ml(it) }, 40.sp, Paper)
                    Text(" ml", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("of ${ml(goal)} ml target", color = Paper, fontSize = 13.sp)
                if (total >= goal) Label("target hit", Accent) else Label("${ml(goal - total)} ml to go")
            }
        }
        Row { Label("last 7 days"); Spacer(Modifier.weight(1f)); Label("avg ${ml(avg)} ml") }
        WaterWeek(week, goal)
    }
}
