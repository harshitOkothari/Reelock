package app.reelock

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Shared with the consent gate. Wording follows Google Play's Accessibility API "prominent disclosure" rules. */
@Composable
fun DisclosureBody() {
    val items = listOf(
        "what it does" to "Reelock uses Android's Accessibility Service to detect when Instagram Reels or YouTube Shorts are open on your screen, so it can block them or apply the daily limit you choose.",
        "what it reads" to "Only on-screen element identifiers and labels inside the Instagram and YouTube apps, to tell Reels, Stories and normal posts apart. It does not read any other app.",
        "what it does on screen" to "It can press Back or Home to leave a blocked Reel, and, if you turn these options on, scroll your Instagram home feed past Reels and posts from accounts you don't follow. Stories are never touched.",
        "what it never does" to "It never reads your messages, passwords or personal content, never records your screen, and never collects, stores or shares personal data. Everything stays on your phone and Reelock has no internet access.",
        "you're in control" to "Switch it off any time in Android Settings > Accessibility. Reelock is not affiliated with Instagram, Meta or YouTube."
    )
    Column {
        items.forEachIndexed { i, (l, t) ->
            if (i > 0) { Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp)) }
            Label(l)
            Spacer(Modifier.height(6.dp))
            Text(t, color = Paper, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

/** Shown to existing installs that haven't accepted the disclosure yet. */
@Composable
fun ConsentGate(onAgree: () -> Unit) {
    val ctx = LocalContext.current
    Backdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Title("Before you continue", "Reelock needs the Accessibility permission. Please read how it is used.")
                Spacer(Modifier.height(24.dp))
                DisclosureBody()
            }
            Spacer(Modifier.height(14.dp))
            Btn("Agree and continue") { Store.consent = true; onAgree() }
            Spacer(Modifier.height(10.dp))
            Btn("No thanks", filled = false) { (ctx as? Activity)?.finish() }
        }
    }
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

@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val total = 12
    var step by remember { mutableIntStateOf(0) }
    var age by remember { mutableFloatStateOf(19f) }
    var hours by remember { mutableFloatStateOf(6f) }
    var apps by remember { mutableStateOf(setOf(App.IG, App.YT)) }
    val goals = remember { mutableStateMapOf(App.IG to 0, App.YT to 0) }
    var skipReels by remember { mutableStateOf(true) }
    var followOnly by remember { mutableStateOf(false) }
    var sleep by remember { mutableFloatStateOf(1380f) }
    var pace by remember { mutableIntStateOf(0) }
    var checks by remember { mutableIntStateOf(0) }
    var on by remember { mutableStateOf(serviceOn(ctx)) }
    var declined by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == 8) { checks = 0; for (i in 1..3) { delay(900); checks = i }; delay(400); step = 9 }
        if (step == 11) while (true) { on = serviceOn(ctx); delay(700) }
    }
    LaunchedEffect(celebrate) { if (celebrate) { delay(1400); onDone() } }

    val pct = (hours / 16f * 100).roundToInt()
    val years = (80 - age) * hours / 24f
    val maxGoal = apps.maxOfOrNull { goals[it] ?: 0 } ?: 0
    val cut = (1f - maxGoal / (hours * 60f)).coerceIn(0f, 1f)
    val daysBack = (hours * 365f / 24f * cut).roundToInt()

    Backdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            if (step in 1..total) StepBar(step, total)
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = step, modifier = Modifier.weight(1f).fillMaxWidth(), label = "step",
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 8 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 8 })
                }
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (s in 1..total) Label("%02d / %02d".format(s, total))
                    when (s) {
                        0 -> Box(Modifier.fillMaxWidth().height(560.dp).clipToBounds()) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = 170.dp, y = (-30).dp)) { Logo(430.dp, mark = false) }
                            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Display(NAME.lowercase() + ".", 76.sp)
                                Label(TAGLINE)
                                Spacer(Modifier.height(6.dp))
                                Text("Block Reels and Shorts, or give yourself a daily allowance. Stories and posts from people you follow stay as they are.",
                                    color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                        1 -> {
                            Title("How old are you?", "Only used to estimate your numbers. It never leaves this phone.")
                            Spacer(Modifier.height(16.dp))
                            Display("${age.roundToInt()}", 132.sp, Accent)
                            RulerPicker(age, 13f, 80f, 1f, { age = it })
                        }
                        2 -> {
                            Title("How long do you scroll each day?", "Reels, Shorts, the endless feed. Be honest, this is your baseline.")
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Display("%.1f".format(hours), 132.sp, Accent)
                                Text(" h/day", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 22.dp))
                            }
                            RulerPicker(hours, 1f, 12f, 0.5f, { hours = it })
                        }
                        3 -> {
                            Title("What it costs you", "At this pace, over the rest of your life.")
                            Tile {
                                Label("of every waking hour")
                                CountText(pct, { "$it%" }, 84.sp, Danger)
                                Meter(pct / 100f, danger = true)
                            }
                            Tile {
                                Label("years spent scrolling")
                                CountText((years * 10).toInt(), { "%.1f".format(it / 10f) }, 72.sp)
                                Text("That is %,d minutes you will not get back.".format((years * 525600).toInt()), color = Muted, fontSize = 14.sp)
                            }
                        }
                        4 -> {
                            Title("What should we guard?", "Pick one or both.")
                            App.values().forEach { a ->
                                ChoiceRow(a.label, if (a == App.IG) "Reels tab, Reels from Explore and DMs" else "The Shorts tab and shelf", a in apps) {
                                    apps = if (a in apps) apps - a else apps + a
                                }
                            }
                        }
                        5 -> {
                            Title("Set your daily limit", "None blocks completely. Or allow 5 to 20 minutes a day.")
                            apps.forEach { a ->
                                Tile {
                                    Text(a.label, color = Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    LimitSegmented(goals[a] ?: 0) { goals[a] = it }
                                }
                            }
                            if (App.IG in apps) Tile {
                                Label("instagram home feed")
                                SettingRow("Skip big Reels in my feed", "Scrolls past a Reel that fills the screen. Stories always work.", skipReels) { skipReels = it }
                                Divider()
                                SettingRow("Skip posts from accounts I don't follow", "Experimental. May scroll your feed a lot.", followOnly) { followOnly = it }
                            }
                        }
                        6 -> {
                            Title("When does Sleep Mode start?", "Reels stay locked from then until 6:00 AM.")
                            Spacer(Modifier.height(16.dp))
                            Display(fmtTime(sleep.roundToInt()), 84.sp, Accent)
                            RulerPicker(sleep, 1080f, 1620f, 15f, { sleep = it })
                        }
                        7 -> {
                            Title("How fast do you want to get there?")
                            listOf("Start today" to "Your full limit from day one.", "Over 7 days" to "A little less each day.", "Over 14 days" to "More room to ease in.")
                                .forEachIndexed { i, (t, sub) -> ChoiceRow(t, sub, pace == i) { pace = i } }
                        }
                        8 -> {
                            Title("Building your plan")
                            Tile {
                                listOf("Reading your scroll time", "Measuring the gap to your goal", "Working out what you get back").forEachIndexed { i, t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).background(if (checks > i) Accent else Rule))
                                        Spacer(Modifier.width(14.dp))
                                        Text(t, color = if (checks > i) Paper else Muted, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        9 -> {
                            Title("Your plan")
                            Tile(bg = Accent) {
                                Label("you get back", Ink.copy(alpha = 0.6f))
                                CountText(daysBack, { "$it days" }, 72.sp, Ink)
                                Text("every year, from the time you now spend scrolling.", color = Ink, fontSize = 14.sp)
                            }
                            Tile {
                                apps.forEach { a ->
                                    Row { Text(a.label, color = Muted, modifier = Modifier.weight(1f))
                                        Text(if ((goals[a] ?: 0) == 0) "Blocked" else "${goals[a]} min / day", color = Paper, fontWeight = FontWeight.SemiBold) }
                                }
                                Row { Text("Sleep Mode", color = Muted, modifier = Modifier.weight(1f))
                                    Text("${fmtTime(sleep.roundToInt())} to 6:00 AM", color = Paper, fontWeight = FontWeight.SemiBold) }
                                Row { Text("Pace", color = Muted, modifier = Modifier.weight(1f))
                                    Text(listOf("Today", "7 days", "14 days")[pace], color = Paper, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        10 -> {
                            Title("Before we turn it on", "Reelock needs the Accessibility permission. Here is exactly how it is used.")
                            Spacer(Modifier.height(8.dp))
                            DisclosureBody()
                            if (declined) Text("Reelock can't block anything without this permission.", color = Danger, fontSize = 14.sp)
                        }
                        11 -> {
                            Title("Turn on Reelock", "Three taps in Android settings.")
                            Tile {
                                listOf("Open Accessibility settings below", "Choose Reelock under Installed apps", "Switch it on and allow").forEachIndexed { i, t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}", color = Accent, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                                        Text(t, color = Paper, fontSize = 15.sp)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(if (on) Accent else Danger))
                                Spacer(Modifier.width(10.dp))
                                Text(if (on) "Protection is on" else "Protection is off", color = Paper, fontWeight = FontWeight.SemiBold)
                            }
                            Text("If the switch is greyed out: App info > menu (three dots) > Allow restricted settings.", color = Muted, fontSize = 13.sp)
                        }
                        12 -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Logo(110.dp, animate = false)
                            Display("Lock it in.", 64.sp)
                            Text("$daysBack days back each year.\nPress and hold to commit.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            when (step) {
                8 -> Spacer(Modifier.height(58.dp))
                10 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Agree and continue") { Store.consent = true; step++ }
                    Btn("No thanks", filled = false) { declined = true }
                }
                11 -> if (!on) Btn("Open Accessibility settings") {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else Btn("Continue") { step++ }
                12 -> HoldBar("Hold to commit", 1500) {
                    Store.age = age.roundToInt(); Store.hours = hours
                    App.values().forEach { a -> Store.setEnabled(a, a in apps); Store.setGoal(a, goals[a] ?: 0) }
                    Store.skipFeedReels = skipReels; Store.followingOnly = followOnly
                    Store.sleepStart = sleep.roundToInt() % 1440; Store.paceDays = listOf(0, 7, 14)[pace]
                    Store.paceStart = System.currentTimeMillis(); Store.startedAt = System.currentTimeMillis()
                    Store.onboarded = true
                    celebrate = true
                }
                else -> Btn(
                    when (step) { 0 -> "Get started"; 2 -> "See what it costs"; else -> "Continue" },
                    enabled = step != 4 || apps.isNotEmpty()
                ) { step++ }
            }
        }
        if (celebrate) Burst()
    }
}
