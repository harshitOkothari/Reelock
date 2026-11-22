package app.reelock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Water is always blue, whatever accent theme the app uses. */
val Aqua = Color(0xFF4DA6FF)

private fun ml(n: Int) = "%,d".format(Locale.getDefault(), n)

fun openWater(c: Context, help: Boolean = false) =
    c.startActivity(Intent(c, WaterActivity::class.java).putExtra("help", help))

private fun openGoodLock(c: Context) {
    try {
        val li = c.packageManager.getLaunchIntentForPackage("com.samsung.android.goodlock")
        if (li != null) { c.startActivity(li); return }
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/com.samsung.android.goodlock")))
    } catch (e: Exception) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/com.samsung.android.goodlock")))
        } catch (e2: Exception) {
            Toast.makeText(c, "Install Good Lock from Galaxy Store", Toast.LENGTH_LONG).show()
        }
    }
}

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
        Pal.idx = Store.theme
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTextStyle(TextStyle(fontFamily = Body)) {
                    key(session) { WaterPopup(intent?.getBooleanExtra("help", false) == true) { finish() } }
                }
            }
        }
    }

    /** back-tap again while the popup is already open: start it fresh */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session++
    }
}

// =============================== POPUP ===============================
@Composable
fun WaterPopup(startHelp: Boolean = false, onOpenApp: (() -> Unit)? = null, onClose: () -> Unit) {
    val hap = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val a = remember { Animatable(0f) }
    val bar = remember { Animatable(1f) }
    var total by remember { mutableIntStateOf(Store.waterToday()) }
    var goal by remember { mutableIntStateOf(Store.waterGoal) }
    var last by remember { mutableIntStateOf(Store.waterLast()) }
    var pick by remember { mutableFloatStateOf((Store.waterPick.coerceIn(50, 1000) / 50 * 50).toFloat()) }
    var burst by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var help by remember { mutableStateOf(startHelp) }
    var flash by remember { mutableIntStateOf(0) }
    var flashMl by remember { mutableIntStateOf(0) }
    var lit by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { a.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    LaunchedEffect(flash) { if (flash > 0) { delay(260); lit = 0 } }

    fun cancelClose() { job?.cancel(); pending = false }
    fun close() {
        if (closing) return
        closing = true; cancelClose()
        scope.launch { a.animateTo(0f, tween(150, easing = FastOutSlowInEasing)); onClose() }
    }
    fun add(n: Int) {
        if (n <= 0 || closing) return
        val before = total
        Store.addWater(n)
        if (n % 50 == 0 && n <= 1000) Store.waterPick = n
        total = Store.waterToday(); last = Store.waterLast()
        val hit = before < goal && total >= goal
        if (hit) { burst++; hap.performHapticFeedback(HapticFeedbackType.LongPress) }
        flashMl = n; lit = n; flash++
        cancelClose()
        val ms = if (hit) 1800 else 1100
        job = scope.launch {
            pending = true; bar.snapTo(1f)
            bar.animateTo(0f, tween(ms, easing = LinearEasing))
            pending = false; close()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f * a.value.coerceIn(0f, 1f))).tap { close() },
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 420.dp).fillMaxWidth()
                .graphicsLayer {
                    alpha = a.value.coerceIn(0f, 1f)
                    translationY = (1f - a.value) * 50f
                    scaleX = 0.96f + 0.04f * a.value; scaleY = 0.96f + 0.04f * a.value
                }
                .clip(shape).background(Panel).border(1.dp, Rule, shape)
                .tap { cancelClose() }                       // a tap on the card keeps it open; it never closes it
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            AnimatedContent(
                targetState = help, label = "page",
                transitionSpec = { fadeIn(tween(160, delayMillis = 60)) togetherWith fadeOut(tween(90)) }
            ) { h ->
                if (h) HelpPage { help = false } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("water  /  today"); Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(30.dp).tap { cancelClose(); help = true }.clip(CircleShape).border(1.dp, Rule, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("?", color = Aqua, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Box(Modifier.tap { close() }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp)) { Label("close") }
                    }

                    // ---- glass + numbers ----
                    run {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Box(Modifier.width(98.dp).height(168.dp), contentAlignment = Alignment.Center) {
                                WaterGlass(total / goal.coerceAtLeast(1).toFloat(), Modifier.fillMaxSize(), wave = true)
                                if (flash > 0) key(flash) { FloatUp("+${ml(flashMl)}") }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    CountText(total, { ml(it) }, 52.sp, Paper, 450)
                                    Text(" ml", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                                }
                                Text("of ${ml(goal)} ml target", color = Paper, fontSize = 14.sp)
                                if (total >= goal) Label("target hit", Aqua) else Label("${ml(goal - total)} ml to go")
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Label("target"); Spacer(Modifier.weight(1f))
                                    StepKey("-") { Store.waterGoal = goal - 250; goal = Store.waterGoal; cancelClose() }
                                    StepKey("+") { Store.waterGoal = goal + 250; goal = Store.waterGoal; cancelClose() }
                                }
                            }
                        }
                    }

                    // ---- one-tap amounts ----
                    run {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Label("quick add")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(100, 250, 500).forEach { n ->
                                    QuickAmt(n, lit == n, Modifier.weight(1f)) {
                                        hap.performHapticFeedback(HapticFeedbackType.LongPress); add(n)
                                    }
                                }
                            }
                        }
                    }

                    // ---- exact amount ----
                    run {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Label("or set an exact amount")
                            Row(verticalAlignment = Alignment.Bottom) {
                                Display("${pick.roundToInt()}", 48.sp, Aqua)
                                Text(" ml", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            RulerPicker(pick, 0f, 1000f, 50f, { pick = it; cancelClose() }, color = Aqua)
                            Btn("Add ${pick.roundToInt()} ml", enabled = pick.roundToInt() >= 50, color = Aqua) { add(pick.roundToInt()) }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(visible = last > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Label("+${ml(last)} ml logged", Aqua)
                                Box(Modifier.tap { Store.undoWater(); total = Store.waterToday(); last = 0; cancelClose() }.padding(vertical = 4.dp)) {
                                    Label("undo", Paper)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.tap {
                            if (onOpenApp != null) { cancelClose(); onOpenApp() }
                            else { ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra("open", true)); close() }
                        }.padding(vertical = 4.dp)) { Label("open reelock") }
                    }

                    // shows how long until the popup closes itself; any tap or drag keeps it open
                    AnimatedVisibility(visible = pending) {
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Rule)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(bar.value).background(Aqua))
                        }
                    }
                }
            }
        }
        if (burst > 0) key(burst) { Burst(Aqua) }
    }
}

@Composable
private fun QuickAmt(n: Int, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (lit) Aqua else Color.Transparent, tween(100), label = "qa")
    val bd by animateColorAsState(if (lit) Aqua else Paper.copy(alpha = 0.5f), tween(100), label = "qb")
    Column(
        modifier.bounceClick(onClick = onClick).clip(sh).background(bg).border(1.5.dp, bd, sh).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Display("$n", 28.sp, if (lit) Ink else Paper)
        Label("ml", if (lit) Ink else Muted)
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

/** "+250" that drifts up out of the glass and fades */
@Composable
private fun FloatUp(text: String) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Display(text, 26.sp, Paper, Modifier.graphicsLayer {
        translationY = -110f * p.value
        alpha = (1f - p.value * p.value).coerceIn(0f, 1f)
    })
}

// =============================== HELP ===============================
@Composable
private fun HelpPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("help  /  back-tap")
        Display("Tap the back. Log water.", 34.sp)
        Text(
            "Set this up once on your Samsung phone and a double or triple tap on the back opens this window over any app.",
            color = Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
        HelpStep(1, "Get Good Lock", "Install Good Lock from Galaxy Store, then add its RegiStar module.")
        HelpStep(2, "Open RegiStar", "Inside Good Lock open RegiStar, tap Back-tap action and switch it on.")
        HelpStep(3, "Pick your tap", "Choose Double tap or Triple tap, then Open app, then select Reelock.")
        HelpStep(4, "Try it", "Tap the back of your phone. This window opens and you pick how much you drank.")
        Text(
            "If the full app opens instead, check Controls > water: \"Back-tap opens the water popup\" must be on.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
        )
        Btn("Open Good Lock", color = Aqua) { openGoodLock(ctx) }
        Btn("Back to water", filled = false) { onBack() }
    }
}

@Composable
private fun HelpStep(n: Int, title: String, sub: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Aqua), contentAlignment = Alignment.Center) {
            Text("$n", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// =============================== GLASS ===============================
private fun wavePath(w: Float, h: Float, top: Float, amp: Float, phase: Float): Path {
    val p = Path()
    p.moveTo(0f, h)
    p.lineTo(0f, top + sin(phase) * amp)
    var x = 0f
    while (x < w) { p.lineTo(x, top + sin(x / w * 6.2832f + phase) * amp); x += 4f }
    p.lineTo(w, top + sin(6.2832f + phase) * amp)
    p.lineTo(w, h)
    p.close()
    return p
}

/** A glass that fills with blue water as the day's target is reached. It sloshes a little while filling. */
@Composable
fun WaterGlass(frac: Float, modifier: Modifier = Modifier, wave: Boolean = true) {
    val lvl = remember { Animatable(frac.coerceIn(0f, 1f)) }
    LaunchedEffect(frac) { lvl.animateTo(frac.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = 230f)) }
    val tr = rememberInfiniteTransition(label = "wave")
    val ph by tr.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Canvas(modifier) {
        val gw = size.width - 14.dp.toPx()          // the right-hand strip holds the 25/50/75% ticks
        val h = size.height
        val r = 22.dp.toPx()
        val glass = Path().apply { addRoundRect(RoundRect(0f, 0f, gw, h, CornerRadius(r, r))) }
        drawPath(glass, Ink)
        clipPath(glass) {
            val l = lvl.value.coerceIn(0f, 1f)
            if (l > 0.002f) {
                val top = h * (1f - l)
                val amp = if (wave && l < 0.995f) (2.5f + (abs(lvl.velocity) * 5f).coerceAtMost(4f)).dp.toPx() else 0f
                val phase = if (wave) ph else 0f
                drawPath(wavePath(gw, h, top, amp, phase * -1f + 1.7f), Aqua.copy(alpha = 0.38f))   // back layer
                drawPath(wavePath(gw, h, top + 2.dp.toPx(), amp, phase), Aqua)                        // front layer
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
/** 7 bars, oldest to today. Bars that reached the target are blue; the dashed line is the target. */
@Composable
fun WaterWeek(week: List<Pair<java.time.LocalDate, Int>>, goal: Int) {
    val a = remember { Animatable(0f) }
    val key = week.sumOf { it.second } + goal
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
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
                val col = if (v >= goal) Aqua else Paper
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
            Label("water today"); Spacer(Modifier.weight(1f))
            Box(Modifier.tap { openWater(ctx, help = true) }.padding(horizontal = 10.dp, vertical = 4.dp)) { Label("help", Muted) }
            Label("tap to log", Aqua)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WaterGlass(total / goal.coerceAtLeast(1).toFloat(), Modifier.width(64.dp).height(108.dp), wave = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    CountText(total, { ml(it) }, 40.sp, Paper, 450)
                    Text(" ml", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("of ${ml(goal)} ml target", color = Paper, fontSize = 13.sp)
                if (total >= goal) Label("target hit", Aqua) else Label("${ml(goal - total)} ml to go")
            }
        }
        Row { Label("last 7 days"); Spacer(Modifier.weight(1f)); Label("avg ${ml(avg)} ml") }
        WaterWeek(week, goal)
    }
}
