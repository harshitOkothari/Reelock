package app.reelock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

fun serviceOn(c: Context): Boolean {
    val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.contains("${c.packageName}/${GuardService::class.java.name}")
}

private fun ago(t: Long) = if (t == 0L) "not seen yet" else "${(System.currentTimeMillis() - t) / 1000}s ago"

private fun openA11y(c: Context) = c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 5 -> "Still up?"; h < 12 -> "Morning."; h < 17 -> "Afternoon."; h < 22 -> "Evening."; else -> "Late one." }
}
private fun dateLine(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

private val LEVELS = listOf("Wanderer" to 0, "Seeker" to 2, "Focused" to 10, "Sentinel" to 30, "Master" to 75, "Monk" to 150)
/** (index, progress to next, hours left) */
private fun levelOf(savedSec: Int): Triple<Int, Float, Float> {
    val h = savedSec / 3600f
    var idx = 0
    for (i in LEVELS.indices) if (h >= LEVELS[i].second) idx = i
    if (idx == LEVELS.lastIndex) return Triple(idx, 1f, 0f)
    val lo = LEVELS[idx].second; val hi = LEVELS[idx + 1].second
    return Triple(idx, ((h - lo) / (hi - lo)).coerceIn(0f, 1f), hi - h)
}

@Composable
private fun SettingRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Toggle(on, onChange)
    }

// =============================== TODAY ===============================
@Composable
fun TodayTab(ver: Int, on: Boolean, bump: () -> Unit) {
    val ctx = LocalContext.current
    val v = ver
    val now = System.currentTimeMillis()
    val saved = Store.savedSecToday()
    val used = Store.totalSecOn(Store.today())
    val score = Store.focusScore()
    val lvl = levelOf(Store.savedSecTotal())
    var passIdx by remember { mutableIntStateOf(0) }
    var focusIdx by remember { mutableIntStateOf(1) }
    val passes = listOf(5, 10, 15)
    val soft = Ink.copy(alpha = 0.6f)

    ScreenCol {
        Enter(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Label(dateLine()); Display(greeting(), 44.sp) }
                StatusTag(on) { openA11y(ctx) }
            }
        }
        Enter(1) {
            Tile(bg = Accent) {
                Row { Label("saved today", soft); Spacer(Modifier.weight(1f)); Label("focus $score", Ink) }
                CountText(saved, { fmtDur(it) }, 84.sp, Ink)
                DayTicks(score, Ink)
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(fmtDur(used), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Label("reels watched", soft)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.1f h".format(Store.hours), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Label("your usual day", soft)
                    }
                }
            }
        }
        Enter(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${Store.streak()}", "day streak", Modifier.weight(1f), Paper)
                Stat("${Store.blocksToday()}", "blocked today", Modifier.weight(1f))
                Stat("${Store.passesToday()}/3", "passes used", Modifier.weight(1f))
            }
        }
        Enter(3) {
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("level %02d".format(lvl.first + 1)); Spacer(Modifier.weight(1f))
                    Label(if (lvl.first == LEVELS.lastIndex) "max" else "next: ${LEVELS[lvl.first + 1].first.lowercase()}")
                }
                Display(LEVELS[lvl.first].first, 40.sp)
                Meter(lvl.second)
                if (lvl.first != LEVELS.lastIndex) Text("${"%.1f".format(lvl.third)} h of reclaimed time to go", color = Muted, fontSize = 12.sp)
            }
        }
        Enter(4) {
            Tile {
                Label("today's allowance")
                App.values().filter { Store.enabled(it) }.forEach { a ->
                    val u = Store.used(a); val l = Store.limitSec(a)
                    val live = Diag.countApp == a.key && System.currentTimeMillis() - Diag.countAt < 3000
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, color = Paper, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (live) { Label("counting", Accent); Spacer(Modifier.width(10.dp)) }
                        Text(if (l == 0) "blocked" else "${fmtSec(u)} / ${fmtSec(l)}", color = Muted, fontSize = 13.sp, fontFamily = Mono)
                    }
                    Meter(if (l == 0) 0f else u / l.toFloat(), danger = l > 0 && u >= l)
                }
            }
        }
        Enter(5) {
            Tile {
                Label("reel pass")
                if (Store.passActive()) {
                    CountText(((Store.passUntil - now) / 1000).toInt().coerceAtLeast(0), { fmtSec(it) }, 56.sp, Accent)
                    Text("Pass active. Reels lock again when it ends.", color = Muted, fontSize = 13.sp)
                } else {
                    Text("A timed, guilt-free session. Up to 3 a day.", color = Muted, fontSize = 13.sp)
                    Segmented(passes.map { "$it min" }, passIdx) { passIdx = it }
                    val locked = Store.focusing() || (Store.sleepOn && Store.sleeping()) || Store.passesToday() >= 3
                    if (locked) Text(if (Store.passesToday() >= 3) "All 3 passes used today." else "Unavailable during Focus Lock or Sleep Mode.", color = Danger, fontSize = 13.sp)
                    else HoldBar("Hold to start pass") {
                        Store.passUntil = System.currentTimeMillis() + passes[passIdx] * 60_000L; Store.addPass(); bump()
                    }
                }
            }
        }
        Enter(6) {
            Tile {
                Label("focus lock")
                if (Store.focusing()) {
                    Display("Until ${fmtClock(Store.focusUntil)}", 36.sp, Accent)
                    Text("Reels are fully blocked. Hold for 3 seconds to end early.", color = Muted, fontSize = 13.sp)
                    HoldBar("Hold to end lock", 3000) { Store.focusUntil = 0L; bump() }
                } else {
                    Text("Studying or working? Block every Reel for a set time.", color = Muted, fontSize = 13.sp)
                    Segmented(listOf("30 min", "1 hr", "2 hrs", "Morning"), focusIdx) { focusIdx = it }
                    HoldBar("Hold to lock") {
                        val ms = System.currentTimeMillis()
                        Store.focusUntil = when (focusIdx) { 0 -> ms + 30 * 60_000L; 1 -> ms + 60 * 60_000L; 2 -> ms + 120 * 60_000L; else -> Store.untilTomorrowMs() }
                        Store.passUntil = 0L; bump()
                    }
                }
            }
        }
    }
}

// =============================== INSIGHTS ===============================
@Composable
fun InsightsTab(ver: Int) {
    val v = ver
    val ctx = LocalContext.current
    val today = Store.today()
    val savedTotal = Store.savedSecTotal()
    val last7 = (6 downTo 0).map { Store.totalSecOn(today.minusDays(it.toLong())) / 60 }
    val prev7 = (13 downTo 7).map { Store.totalSecOn(today.minusDays(it.toLong())) / 60 }
    val labels = (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    val t = last7.sum(); val p = prev7.sum()
    val vs = if (p == 0) null else ((t - p) * 100f / p).roundToInt()
    val goalsMin = App.values().filter { Store.enabled(it) }.sumOf { Store.goal(it) }
    val best = Store.bestStreak()
    val totalBlocks = Store.blocksTotal()
    val savedMin = savedTotal / 60
    val hourly = Store.hourly()
    val peak = hourly.indices.maxByOrNull { hourly[it] } ?: 0
    val weekSaved = Store.savedSecWeek()
    val days = minOf(7, Store.daysActive())
    val weekPct = (weekSaved * 100f / maxOf(1f, Store.hours * 3600f * days)).roundToInt()
    val medals = listOf(
        "First block" to (totalBlocks >= 1), "3-day streak" to (best >= 3), "7-day streak" to (best >= 7),
        "10 hours back" to (savedMin >= 600), "50 hours back" to (savedMin >= 3000), "100 blocks" to (totalBlocks >= 100)
    )

    ScreenCol {
        Enter(0) { Heading("Insights") }
        Enter(1) {
            Tile {
                Label("total time reclaimed")
                CountText(savedTotal, { fmtDur(it) }, 68.sp, Accent)
                Text("About ${"%.1f".format(savedTotal / 86400f)} days of your life, ${"%.1f".format(savedMin / 120f)} films or ${"%.1f".format(savedMin / 360f)} books.",
                    color = Paper, fontSize = 14.sp, lineHeight = 20.sp)
                Text("Estimate: your usual ${"%.1f".format(Store.hours)} h a day minus actual Reels time.", color = Muted, fontSize = 12.sp)
                Btn("Share my progress", filled = false) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "I've reclaimed ${fmtDur(savedTotal)} from endless scrolling with Reelock. Break the loop.")
                    ctx.startActivity(Intent.createChooser(send, "Share"))
                }
            }
        }
        Enter(2) {
            Tile(bg = Paper) {
                Label("weekly review", Ink.copy(alpha = 0.6f))
                Display("This week you got ${fmtDur(weekSaved)} back.", 32.sp, Ink)
                Text("That is about $weekPct% of the scrolling you would usually do." +
                    (if (vs != null) (if (vs <= 0) " Reels time is down ${-vs}% on last week." else " Reels time is up $vs% on last week.") else ""),
                    color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Enter(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(fmtDur(Store.savedSecToday()), "today", Modifier.weight(1f))
                Stat(fmtDur(weekSaved), "this week", Modifier.weight(1f))
                Stat("${(Store.hours * 365f / 24f).roundToInt()}d", "back per year", Modifier.weight(1f))
            }
        }
        Enter(4) {
            Tile {
                Label("last 7 days, reels watched (min)")
                BarChart(last7, labels, (Store.hours * 60).roundToInt(), goalsMin)
                Text("Solid is within your limit, red is over. The dashed line is your usual day.", color = Muted, fontSize = 12.sp)
            }
        }
        Enter(5) {
            Tile {
                Label("when you scroll")
                HourStrip(hourly)
                Text(if (hourly.all { it == 0 }) "Your pattern appears after a few sessions." else "Your peak is around ${fmtTime(peak * 60)}.",
                    color = Muted, fontSize = 13.sp)
            }
        }
        Enter(6) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${Store.streak()}", "current streak", Modifier.weight(1f))
                    Stat("$best", "best streak", Modifier.weight(1f))
                    Stat("$totalBlocks", "total blocks", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${t / 7}m", "avg per day", Modifier.weight(1f))
                    Stat("${Store.passesTotal()}", "passes used", Modifier.weight(1f))
                    Stat("${Store.daysActive()}", "days in", Modifier.weight(1f))
                }
            }
        }
        Enter(7) {
            Tile {
                Label("today by app")
                App.values().forEach { a ->
                    val u = Store.used(a)
                    Row { Text(a.label, color = Paper, modifier = Modifier.weight(1f)); Text(fmtSec(u), color = Muted, fontFamily = Mono) }
                    Meter(u / maxOf(1f, Store.totalSecOn(today).toFloat()))
                }
            }
        }
        Enter(8) {
            Tile {
                Label("milestones")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    medals.forEach { (name, ok) -> Chip(name, ok) }
                }
            }
        }
    }
}

// =============================== CONTROLS ===============================
@Composable
fun ControlsTab(ver: Int, bump: () -> Unit, onRedo: () -> Unit) {
    val v = ver
    val ctx = LocalContext.current
    var showDisc by remember { mutableStateOf(false) }
    ScreenCol {
        Enter(0) { Heading("Controls") }
        App.values().forEachIndexed { i, a ->
            Enter(i + 1) {
                Tile {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Toggle(Store.enabled(a)) { Store.setEnabled(a, it); bump() }
                    }
                    Label("daily limit, none blocks completely")
                    LimitSegmented(Store.goal(a)) { Store.setGoal(a, it); bump() }
                    if (Store.paceDays > 0) Text("Easing in: today's limit is ${Store.limitSec(a) / 60} min", color = Accent, fontSize = 12.sp)
                }
            }
        }
        Enter(3) {
            Tile {
                Label("instagram home feed")
                SettingRow("Skip Reels in my feed", "Scrolls past Reels that show up between posts. Stories always work.", Store.skipFeedReels) { Store.skipFeedReels = it; bump() }
                Divider()
                SettingRow("Only people I follow", "Scrolls past suggested posts and accounts you don't follow.", Store.followingOnly) { Store.followingOnly = it; bump() }
            }
        }
        Enter(4) {
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sleep Mode", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${fmtTime(Store.sleepStart)} to ${fmtTime(Store.wakeMin)}", color = Muted, fontSize = 13.sp)
                    }
                    Toggle(Store.sleepOn) { Store.sleepOn = it; bump() }
                }
                Label("starts")
                Display(fmtTime(Store.sleepStart), 44.sp)
                RulerPicker(Store.sleepStart.let { if (it < 1080) it + 1440 else it }.toFloat(), 1080f, 1620f, 15f,
                    { Store.sleepStart = it.roundToInt() % 1440; bump() })
                Label("wakes")
                Display(fmtTime(Store.wakeMin), 44.sp)
                RulerPicker(Store.wakeMin.toFloat(), 240f, 600f, 15f, { Store.wakeMin = it.roundToInt(); bump() })
            }
        }
        Enter(5) {
            Tile {
                Text("Pace", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Ease down from 30 minutes to your limit instead of stopping at once.", color = Muted, fontSize = 13.sp)
                val opts = listOf(0, 7, 14)
                Segmented(listOf("Today", "7 days", "14 days"), opts.indexOf(Store.paceDays).coerceAtLeast(0)) {
                    Store.paceDays = opts[it]; Store.paceStart = System.currentTimeMillis(); bump()
                }
            }
        }
        Enter(6) {
            Tile {
                Text("Your usual scrolling", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Used to estimate the time you reclaim.", color = Muted, fontSize = 13.sp)
                Display("%.1f h / day".format(Store.hours), 44.sp)
                RulerPicker(Store.hours, 1f, 12f, 0.5f, { Store.hours = it; bump() })
            }
        }
        Enter(7) {
            Tile {
                Text("Accent colour", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                ThemeSwatches(Pal.idx) { Pal.idx = it; Store.theme = it; bump() }
            }
        }
        Enter(8) {
            Tile {
                Label("privacy and permissions")
                Text("Reelock has no internet access and stores nothing outside your phone.", color = Muted, fontSize = 13.sp)
                Btn("Privacy policy", filled = false) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
                Btn(if (showDisc) "Hide accessibility disclosure" else "Accessibility disclosure", filled = false) { showDisc = !showDisc }
                if (showDisc) DisclosureBody()
                Btn("Open Accessibility settings", filled = false) { openA11y(ctx) }
                Text("If counting stops after a while, set Reelock to Unrestricted battery so Android doesn't put it to sleep.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Btn("Battery settings", filled = false) { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }
        Enter(9) {
            val ago = if (Diag.lastAt == 0L) "never" else "${(System.currentTimeMillis() - Diag.lastAt) / 1000}s ago"
            val report = buildString {
                append("Reelock diagnostics\nService connected: ${Diag.connected}\nLast app seen: ${Diag.lastPkg.ifEmpty { "none" }} ($ago)\n")
                append("Reel detected: ${Diag.hit.ifEmpty { "no" }}\nNote: ${Diag.note}\nError: ${Diag.err.ifEmpty { "none" }}\nScans: ${Diag.scans}\n")
                App.values().forEach { a -> append("${a.key}: enabled=${Store.enabled(a)} goal=${Store.goal(a)}m limit=${Store.limitSec(a)}s used=${Store.used(a)}s blocked=${Store.isBlocked(a)}\n") }
                append("sleeping=${Store.sleeping()} focus=${Store.focusing()} pass=${Store.passActive()} skipFeedReels=${Store.skipFeedReels} followingOnly=${Store.followingOnly}\n")
                append("SELF-CHECK reels: ${ago(Diag.reelAt)} (${Diag.reelHit}) | stories: ${ago(Diag.storyAt)} | feed skip: ${ago(Diag.skipAt)}\n")
                append("IDS: ${Diag.ids}\nSEEN: ${Diag.seen.joinToString(", ")}\nLABELS: ${Diag.labels.joinToString(", ")}")
            }
            Tile {
                Label("self-check")
                Text("reels    ${ago(Diag.reelAt)}${if (Diag.reelHit.isNotEmpty()) " via " + Diag.reelHit else ""}\nstories  ${ago(Diag.storyAt)}\nfeed skip ${ago(Diag.skipAt)}",
                    color = Paper, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = Mono)
                Text("Open a Reel, then a Story, then scroll your feed. Each line should change.", color = Muted, fontSize = 12.sp)
                Label("diagnostics")
                Text("Service connected: ${Diag.connected}\nLast app seen: ${Diag.lastPkg.ifEmpty { "none" }} ($ago)\nDetected: ${Diag.hit.ifEmpty { "no" }}\nNote: ${Diag.note.ifEmpty { "none" }}\nError: ${Diag.err.ifEmpty { "none" }}",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = Mono)
                Btn("Copy report", filled = false) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("report", report))
                }
            }
        }
        Enter(10) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Redo setup", filled = false) { onRedo() }
                Text("$NAME v2.4. $TAGLINE\nNot affiliated with Instagram, Meta or YouTube.", color = Muted, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 18.sp)
            }
        }
    }
}
