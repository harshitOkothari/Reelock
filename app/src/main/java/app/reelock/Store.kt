package app.reelock

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import kotlin.math.roundToInt

/** Apps we guard. If Instagram/YouTube update their UI and blocking stops, edit the view ids here. */
enum class App(
    val pkg: String, val label: String, val key: String,
    val idHints: List<String>,      // view-id fragments that only exist on the Reels/Shorts player
    val tabId: String?,             // bottom-tab view id (checked when selected)
    val tabDesc: String,            // bottom-tab label (checked when selected)
    val reelDesc: String?           // full-screen player content-description fragment
) {
    IG("com.instagram.android", "Instagram Reels", "ig",
        listOf("clips_viewer", "root_clips_layout", "clips_video_container", "clips_swipe_refresh"), "clips_tab", "Reels", "Reel by"),
    YT("com.google.android.youtube", "YouTube Shorts", "yt",
        listOf("reel_recycler", "reel_player", "shorts_player", "reel_watch", "shorts_fragment"), null, "Shorts", null)
}

/** Live debug info shown in Controls > Diagnostics */
object Diag {
    var connected = false
    var lastPkg = ""
    var lastAt = 0L
    var hit = ""
    var note = ""
    var err = ""
    var countApp = ""
    var countAt = 0L
    var reelAt = 0L
    var reelHit = ""
    var storyAt = 0L
    var skipAt = 0L
    var ids = ""
    var seen = LinkedHashSet<String>()
    var labels = LinkedHashSet<String>()
    var scans = 0
}

fun fmtSec(s: Int) = "%d:%02d".format(s / 60, s % 60)
fun fmtTime(min: Int): String {
    val m = min % 1440; val h = m / 60
    return "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m % 60, if (h < 12) "AM" else "PM")
}
fun fmtDur(sec: Int): String { val m = sec / 60; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
fun fmtClock(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return fmtTime(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
}

object Store {
    private lateinit var sp: SharedPreferences
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("reelock", Context.MODE_PRIVATE)
    }
    private fun zone() = ZoneId.systemDefault()
    private fun k(d: LocalDate) = d.format(fmt)
    /** Clock used everywhere (replaced in self-tests). */
    var clock: () -> Long = { System.currentTimeMillis() }
    fun now(): Long = clock()
    private fun cal(): Calendar = Calendar.getInstance().apply { timeInMillis = clock() }
    fun initForTest(prefs: SharedPreferences) { sp = prefs }
    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()
    private fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    private fun putI(key: String, v: Int) { sp.edit().putInt(key, v).apply() }
    private fun putL(key: String, v: Long) { sp.edit().putLong(key, v).apply() }
    private fun putB(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }

    /** user agreed to the in-app Accessibility disclosure (Play policy) */
    var consent: Boolean
        get() = sp.getBoolean("consent", false)
        set(v) = putB("consent", v)
    /** Instagram home feed: auto-scroll past Reels that appear between posts */
    var skipFeedReels: Boolean
        get() = sp.getBoolean("skipFeedReels", true)
        set(v) = putB("skipFeedReels", v)
    /** Instagram home feed: auto-scroll past posts from accounts you do not follow */
    var followingOnly: Boolean
        get() = sp.getBoolean("followingOnly", true)
        set(v) = putB("followingOnly", v)
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = putI("theme", v)
    var onboarded: Boolean
        get() = sp.getBoolean("ob", false)
        set(v) = putB("ob", v)
    var age: Int
        get() = sp.getInt("age", 19)
        set(v) = putI("age", v)
    /** usual reels/doomscroll hours per day; baseline for "time saved" */
    var hours: Float
        get() = sp.getFloat("hrs", 6f)
        set(v) { sp.edit().putFloat("hrs", v).apply() }
    var paceDays: Int
        get() = sp.getInt("pace", 0)
        set(v) = putI("pace", v)
    var paceStart: Long
        get() = sp.getLong("pstart", System.currentTimeMillis())
        set(v) = putL("pstart", v)
    var startedAt: Long
        get() = sp.getLong("start", System.currentTimeMillis())
        set(v) = putL("start", v)
    var sleepOn: Boolean
        get() = sp.getBoolean("sleepOn", true)
        set(v) = putB("sleepOn", v)
    var sleepStart: Int
        get() = sp.getInt("sleepStart", 23 * 60)
        set(v) = putI("sleepStart", v)
    var wakeMin: Int
        get() = sp.getInt("wake", 6 * 60)
        set(v) = putI("wake", v)
    var focusUntil: Long
        get() = sp.getLong("focus", 0L)
        set(v) = putL("focus", v)
    var passUntil: Long
        get() = sp.getLong("pass", 0L)
        set(v) = putL("pass", v)

    fun startDate(): LocalDate = dateOf(startedAt)
    fun enabled(a: App) = sp.getBoolean("en_${a.key}", true)
    fun setEnabled(a: App, v: Boolean) = putB("en_${a.key}", v)
    /** daily goal in minutes; 0 = no reels at all */
    fun goal(a: App) = sp.getInt("goal_${a.key}", 0)
    fun setGoal(a: App, m: Int) = putI("goal_${a.key}", m)

    fun usedOn(d: LocalDate, a: App) = sp.getInt("use_${k(d)}_${a.key}", 0)
    fun used(a: App) = usedOn(today(), a)
    fun addUsed(a: App, s: Int) {
        val h = cal().get(Calendar.HOUR_OF_DAY)
        sp.edit().putInt("use_${k(today())}_${a.key}", used(a) + s).putInt("hr_$h", sp.getInt("hr_$h", 0) + s).apply()
    }
    /** all-time Reels seconds per hour of day */
    fun hourly() = IntArray(24) { sp.getInt("hr_$it", 0) }
    fun focusScore(): Int {
        val sv = savedSecToday(); val u = totalSecOn(today())
        return if (sv + u == 0) 100 else (100f * sv / (sv + u)).roundToInt()
    }
    fun totalSecOn(d: LocalDate) = App.values().sumOf { usedOn(d, it) }

    fun blocksOn(d: LocalDate) = sp.getInt("blk_${k(d)}", 0)
    fun blocksToday() = blocksOn(today())
    fun blocksTotal() = sp.getInt("blk_total", 0)
    fun addBlock() { sp.edit().putInt("blk_${k(today())}", blocksToday() + 1).putInt("blk_total", blocksTotal() + 1).apply() }

    fun passesToday() = sp.getInt("pass_${k(today())}", 0)
    fun passesTotal() = sp.getInt("pass_total", 0)
    fun addPass() { sp.edit().putInt("pass_${k(today())}", passesToday() + 1).putInt("pass_total", passesTotal() + 1).apply() }

    fun untilTomorrowMs(): Long =
        today().plusDays(1).atTime(wakeMin / 60, wakeMin % 60).atZone(zone()).toInstant().toEpochMilli()

    /** today's allowed seconds (eases down from 30 min to goal if pace > 0) */
    fun limitSec(a: App): Int {
        val goal = goal(a) * 60
        if (paceDays <= 0) return goal
        val d = ChronoUnit.DAYS.between(dateOf(paceStart), today()).toInt()
        val f = minOf(1f, d / paceDays.toFloat())
        return (1800 + (goal - 1800) * f).toInt()
    }

    fun sleeping(): Boolean {
        val c = cal()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val s = sleepStart % 1440; val w = wakeMin % 1440
        return if (s > w) (m >= s || m < w) else (m in s until w)
    }
    fun focusing() = focusUntil > clock()
    fun passActive() = passUntil > clock()

    fun isBlocked(a: App): Boolean {
        if (focusing()) return true
        if (sleepOn && sleeping()) return true
        if (passActive()) return false
        return used(a) >= limitSec(a)
    }

    fun reason(a: App): String = when {
        focusing() -> "Focus Lock is on.\nReels are back at ${fmtClock(focusUntil)}."
        sleepOn && sleeping() -> "Sleep Mode is on.\nReels are blocked till ${fmtTime(wakeMin)}."
        limitSec(a) == 0 -> "${a.label} are blocked\nby Reelock."
        else -> "Today's ${limitSec(a) / 60} min of\n${a.label} is over."
    }

    // ---- time saved (estimate: your usual daily scrolling minus actual Reels time) ----
    private fun baseSec() = (hours * 3600).toInt()
    fun savedSecToday(): Int {
        val c = cal()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val frac = ((m - 360) / 960f).coerceIn(0f, 1f)
        return maxOf(0, (baseSec() * frac).toInt() - totalSecOn(today()))
    }
    fun savedSecTotal(): Int {
        var t = savedSecToday()
        val n = minOf(365L, ChronoUnit.DAYS.between(startDate(), today())).toInt()
        for (i in 1..n) t += maxOf(0, baseSec() - totalSecOn(today().minusDays(i.toLong())))
        return t
    }
    fun savedSecWeek(): Int {
        var t = savedSecToday()
        val n = minOf(6L, ChronoUnit.DAYS.between(startDate(), today())).toInt()
        for (i in 1..n) t += maxOf(0, baseSec() - totalSecOn(today().minusDays(i.toLong())))
        return t
    }
    fun daysActive() = ChronoUnit.DAYS.between(startDate(), today()).toInt() + 1

    private fun okDay(d: LocalDate) =
        App.values().filter { enabled(it) }.all { usedOn(d, it) <= goal(it) * 60 + 15 }
    fun streak(): Int {
        var d = today()
        if (!okDay(d)) return 0
        var s = 0
        while (!d.isBefore(startDate()) && s < 400 && okDay(d)) { s++; d = d.minusDays(1) }
        return s
    }
    fun bestStreak(): Int {
        var best = 0; var run = 0; var d = startDate(); val t = today()
        while (!d.isAfter(t)) { if (okDay(d)) { run++; best = maxOf(best, run) } else run = 0; d = d.plusDays(1) }
        return best
    }
}
