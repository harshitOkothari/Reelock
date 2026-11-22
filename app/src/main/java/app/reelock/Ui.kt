package app.reelock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- tokens: flat, one accent, no gradients ----------
val Ink = Color(0xFF0B0B0A)
val Panel = Color(0xFF151513)
val Paper = Color(0xFFF1EEE4)
val Muted = Color(0xFF86867C)
val Rule = Color(0xFF2B2B27)
val Danger = Color(0xFFFF4B2B)

class Palette(val name: String, val c: Color)
val PALETTES = listOf(
    Palette("Lime", Color(0xFFD4FF3A)), Palette("Tangerine", Color(0xFFFF7A3D)),
    Palette("Sky", Color(0xFF8AC7FF)), Palette("Blossom", Color(0xFFFFA6D5))
)
object Pal { var idx by mutableIntStateOf(0) }
val Accent: Color get() = PALETTES[Pal.idx].c

const val NAME = "Reelock"
const val TAGLINE = "Break the loop."
const val PRIVACY_URL = "https://harshitokothari.github.io/Reelock/privacy.html"

val Head = FontFamily(Font(R.font.head_extrabold, FontWeight.Normal), Font(R.font.head_extrabold, FontWeight.Bold))
val Body = FontFamily(
    Font(R.font.body_regular, FontWeight.Normal), Font(R.font.body_medium, FontWeight.Medium),
    Font(R.font.body_semibold, FontWeight.SemiBold), Font(R.font.body_semibold, FontWeight.Bold)
)
val Mono = FontFamily(Font(R.font.mono_regular, FontWeight.Normal), Font(R.font.mono_medium, FontWeight.Medium))

// ---------- mark ----------
@Composable
fun Logo(s: Dp, animate: Boolean = true, color: Color = Accent, mark: Boolean = true) {
    val sweep = remember { Animatable(if (animate) 0f else 290f) }
    LaunchedEffect(Unit) { if (animate) sweep.animateTo(290f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.size(s)) {
        val w = size.minDimension
        val st = w * 0.12f
        val rad = w / 2 - st / 2 - w * 0.05f
        drawArc(color, -10f, sweep.value, false, Offset(w / 2 - rad, w / 2 - rad), Size(rad * 2, rad * 2),
            style = Stroke(st, cap = StrokeCap.Round))
        if (mark) {
            val p = Path().apply { moveTo(w * 0.43f, w * 0.35f); lineTo(w * 0.67f, w * 0.5f); lineTo(w * 0.43f, w * 0.65f); close() }
            drawPath(p, Paper.copy(alpha = sweep.value / 290f))
        }
    }
}

// ---------- modifiers / motion ----------
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun Enter(i: Int = 0, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(i * 60L); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }) { content() }
}

// ---------- layout + type ----------
@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize().background(Ink), content = content)

@Composable
fun ScreenCol(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
fun Label(t: String, color: Color = Muted) = Text(t.lowercase(), color = color, fontSize = 12.sp, fontFamily = Mono)

@Composable
fun Display(t: String, size: TextUnit, color: Color = Paper, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(t, modifier, color = color, fontSize = size, fontFamily = Head, fontWeight = FontWeight.Normal,
        lineHeight = size * 1.02f, letterSpacing = (-0.035f * size.value).sp, textAlign = align)

@Composable
fun Heading(t: String) = Display(t, 46.sp)

@Composable
fun Title(t: String, sub: String? = null) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Display(t, 42.sp)
    if (sub != null) Text(sub, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
fun CountText(target: Int, fmt: (Int) -> String, size: TextUnit, color: Color = Paper, ms: Int = 1000) {
    val v by animateIntAsState(target, tween(ms, easing = FastOutSlowInEasing), label = "count")
    Display(fmt(v), size, color)
}

@Composable
fun Tile(modifier: Modifier = Modifier, bg: Color = Panel, pad: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg)
            .then(if (bg == Panel) Modifier.border(1.dp, Rule, shape) else Modifier).padding(pad),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
fun Stat(n: String, label: String, modifier: Modifier = Modifier, bg: Color = Panel) {
    val fg = if (bg == Panel) Paper else Ink
    Tile(modifier, bg, pad = 16.dp) {
        Display(n, 34.sp, fg)
        Label(label, if (bg == Panel) Muted else Color(0xFF55554C))
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))

// ---------- controls ----------
@Composable
fun Btn(text: String, filled: Boolean = true, enabled: Boolean = true, color: Color = Accent, onClick: () -> Unit) {
    val hap = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().height(58.dp).bounceClick(enabled) { hap.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .clip(shape)
            .then(if (filled) Modifier.background(if (enabled) color else Rule) else Modifier.border(1.5.dp, if (enabled) Paper else Rule, shape)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) (if (enabled) Ink else Muted) else Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.7f), label = "tg")
    val bg by animateColorAsState(if (on) Accent else Rule, tween(200), label = "tgc")
    val hap = LocalHapticFeedback.current
    Box(
        Modifier.width(54.dp).height(32.dp).clip(RoundedCornerShape(11.dp)).background(bg)
            .tap { hap.performHapticFeedback(HapticFeedbackType.LongPress); onChange(!on) }.padding(4.dp)
    ) { Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) Ink else Muted)) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val hap = LocalHapticFeedback.current
    val shape = RoundedCornerShape(15.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(shape).background(Panel).border(1.dp, Rule, shape).padding(4.dp)) {
        val w = maxWidth / options.size
        val off by animateDpAsState(w * selected.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(Modifier.offset(x = off).width(w).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(Accent))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, t ->
                Box(Modifier.weight(1f).fillMaxHeight().tap { hap.performHapticFeedback(HapticFeedbackType.LongPress); onPick(i) },
                    contentAlignment = Alignment.Center) {
                    Text(t, color = if (i == selected) Ink else Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

val LIMITS = listOf(0, 5, 10, 15, 20)
@Composable
fun LimitSegmented(cur: Int, onPick: (Int) -> Unit) =
    Segmented(LIMITS.map { if (it == 0) "None" else "${it}m" }, LIMITS.indexOf(cur)) { onPick(LIMITS[it]) }

@Composable
fun ChoiceRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val bc by animateColorAsState(if (selected) Accent else Rule, tween(200), label = "cr")
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).clip(shape).background(Panel)
            .border(if (selected) 2.dp else 1.dp, bc, shape).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
        Box(Modifier.size(24.dp).border(2.dp, if (selected) Accent else Rule, CircleShape).padding(5.dp)) {
            if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(Accent))
        }
    }
}

@Composable
fun Meter(frac: Float, danger: Boolean = false) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "meter")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Rule)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(5.dp)).background(if (danger) Danger else Accent))
    }
}

@Composable
fun StepBar(step: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val a by animateFloatAsState(if (i < step) 1f else 0f, tween(400), label = "sb")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Rule)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(a).background(Accent))
            }
        }
    }
}

@Composable
fun StatusTag(on: Boolean, onClick: () -> Unit) {
    val c = if (on) Accent else Danger
    Row(
        Modifier.clip(CircleShape).border(1.dp, Rule, CircleShape).then(if (!on) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(if (on) "protected" else "off, tap to fix", color = Paper, fontSize = 12.sp, fontFamily = Mono)
    }
}

@Composable
fun Chip(text: String, on: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.clip(shape).then(if (on) Modifier.background(Accent) else Modifier.border(1.dp, Rule, shape)).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(text, color = if (on) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ThemeSwatches(selected: Int, onPick: (Int) -> Unit) {
    val hap = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PALETTES.forEachIndexed { i, p ->
            Column(
                Modifier.tap { hap.performHapticFeedback(HapticFeedbackType.LongPress); onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(p.c)
                    .border(if (i == selected) 3.dp else 0.dp, Paper, RoundedCornerShape(16.dp)))
                Label(p.name, if (i == selected) Paper else Muted)
            }
        }
    }
}

// ---------- ruler picker ----------
@Composable
fun RulerPicker(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = Accent) {
    val n = ((max - min) / step).roundToInt()
    val spacing = with(LocalDensity.current) { 14.dp.toPx() }
    val pos = remember { Animatable((value - min) / step) }
    val scope = rememberCoroutineScope()
    val hap = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var last by remember { mutableIntStateOf(pos.value.roundToInt()) }
    val cb by rememberUpdatedState(onChange)
    val accent = color
    LaunchedEffect(Unit) { pos.updateBounds(0f, n.toFloat()) }
    LaunchedEffect(value) {
        if (!dragging) { val t = (value - min) / step; if (abs(pos.value - t) > 0.01f) pos.animateTo(t) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pos.value.roundToInt() }.collect { i ->
            if (i != last) { last = i; hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); cb(min + i * step) }
        }
    }
    val drag = rememberDraggableState { dx -> scope.launch { pos.snapTo((pos.value - dx / spacing).coerceIn(0f, n.toFloat())) } }
    Canvas(
        modifier.fillMaxWidth().height(84.dp).draggable(
            drag, Orientation.Horizontal,
            onDragStarted = { dragging = true; pos.stop() },
            onDragStopped = { v ->
                pos.animateDecay(-v / spacing, exponentialDecay(frictionMultiplier = 2.2f))
                pos.animateTo(pos.value.roundToInt().toFloat(), spring(stiffness = 300f))
                dragging = false
            }
        )
    ) {
        val cx = size.width / 2
        val h = size.height
        val from = (floor(pos.value - cx / spacing).toInt() - 1).coerceAtLeast(0)
        val to = (ceil(pos.value + cx / spacing).toInt() + 1).coerceAtMost(n)
        for (i in from..to) {
            val x = cx + (i - pos.value) * spacing
            val d = (abs(x - cx) / cx).coerceIn(0f, 1f)
            val major = i % 5 == 0
            val th = if (major) 36.dp.toPx() else 20.dp.toPx()
            val col = Paper.copy(alpha = (1f - d * d).coerceIn(0.08f, if (major) 0.95f else 0.45f))
            drawLine(col, Offset(x, h - 4.dp.toPx() - th), Offset(x, h - 4.dp.toPx()), if (major) 2.5.dp.toPx() else 1.5.dp.toPx())
        }
        drawLine(accent, Offset(cx, 0f), Offset(cx, h * 0.8f), 4.dp.toPx(), StrokeCap.Round)
    }
}

// ---------- hold to confirm ----------
@Composable
fun HoldBar(label: String, ms: Int = 1500, onDone: () -> Unit) {
    val prog = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hap = LocalHapticFeedback.current
    val done by rememberUpdatedState(onDone)
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(shape).border(1.5.dp, Paper.copy(alpha = 0.5f), shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    val job = scope.launch {
                        prog.animateTo(1f, tween(ms, easing = LinearEasing))
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        done(); prog.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (prog.value < 1f) { job.cancel(); scope.launch { prog.animateTo(0f, tween(250)) } }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(prog.value).background(Accent)) }
        Text(label, color = if (prog.value > 0.5f) Ink else Paper, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

// ---------- charts ----------
@Composable
fun BarChart(vals: List<Int>, labels: List<String>, base: Int, limit: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(vals) { a.snapTo(0f); a.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val accent = Accent
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val mx = maxOf(base, vals.maxOrNull() ?: 0, 1) * 1.1f
            val slot = size.width / vals.size
            val bw = slot * 0.5f
            val y = size.height * (1 - base / mx)
            drawLine(Paper.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)))
            vals.forEachIndexed { i, v ->
                val h = maxOf(size.height * (v / mx) * a.value, 6f)
                val dim = if (i == vals.size - 1) 1f else 0.55f
                val col = (if (v <= limit) accent else Danger).copy(alpha = dim)
                drawRoundRect(col, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            labels.forEach { Text(it.lowercase(), Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontFamily = Mono, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun HourStrip(vals: IntArray) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(vals.sum()) { a.snapTo(0f); a.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val mx = (vals.maxOrNull() ?: 0).coerceAtLeast(1)
    val peak = vals.indices.maxByOrNull { vals[it] } ?: 0
    val accent = Accent
    Column {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val slot = size.width / 24
            val bw = slot * 0.6f
            for (i in 0 until 24) {
                val h = maxOf(size.height * (vals[i] / mx.toFloat()) * a.value, 4f)
                val c = if (i == peak && vals[i] > 0) accent else Paper.copy(alpha = 0.22f)
                drawRoundRect(c, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(4f, 4f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "11p").forEach { Text(it, color = Muted, fontSize = 10.sp, fontFamily = Mono) }
        }
    }
}

/** 30 thin bars, the first (score%) are solid. Drawn on the accent tile. */
@Composable
fun DayTicks(score: Int, color: Color) {
    val f by animateFloatAsState(score / 100f, tween(1000, easing = FastOutSlowInEasing), label = "ticks")
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val n = 30
        val slot = size.width / n
        val lit = (f * n).roundToInt()
        for (i in 0 until n) {
            drawRoundRect(color.copy(alpha = if (i < lit) 1f else 0.18f), Offset(i * slot + slot * 0.2f, 0f), Size(slot * 0.4f, size.height), CornerRadius(3f, 3f))
        }
    }
}

@Composable
fun Burst(color: Color = Accent) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    val accent = color
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val maxR = size.minDimension * 0.6f
        val p = t.value
        drawCircle(accent.copy(alpha = (1f - p) * 0.9f), maxR * p, c, style = Stroke(4.dp.toPx() * (1f - p) + 1f))
        for (i in 0 until 40) {
            val a = Math.toRadians(i * 360.0 / 40 + (i % 3) * 5)
            val r = maxR * (0.5f + (i % 5) * 0.14f) * p * 1.1f
            val col = if (i % 2 == 0) accent else Paper
            drawRoundRect(col.copy(alpha = (1f - p).coerceIn(0f, 1f)), Offset(c.x + r * cos(a).toFloat() - 6f, c.y + r * sin(a).toFloat() - 6f),
                Size(12f * (1f - p * 0.5f), 12f * (1f - p * 0.5f)), CornerRadius(3f, 3f))
        }
    }
}
