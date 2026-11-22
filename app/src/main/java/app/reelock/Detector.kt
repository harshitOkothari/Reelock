package app.reelock

/** Minimal view of a screen node, so the detection logic can be unit-tested without Android. */
interface Nd {
    val id: String?        // view id without the "pkg:id/" prefix
    val desc: String?      // content description
    val text: String?      // text (or hint for an empty input field)
    val cls: String?       // view class name, e.g. android.widget.EditText
    val selected: Boolean
    val visible: Boolean
    val top: Int
    val bottom: Int
    val children: List<Nd>
}

/** What one pass over the screen found. */
class Scan {
    var viewer = false              // Reels / Shorts player (or the Reels tab) is showing
    var story = false               // Instagram story viewer (always allowed)
    var home = false                // Instagram Home feed screen (Home tab selected, tab bar showing)
    var ignored = false             // DM thread / photo-video picker / camera: never touched
    var feedReelBottom = -1         // bottom edge of a Reel sitting inside the home feed
    var feedReelBig = false         // ... and it fills a good part of the screen (not a small thumbnail)
    var offFeedBottom = -1          // bottom edge of a post from someone you don't follow
    var hit = ""
}

object Detector {
    /** Labels that mark content from accounts you don't follow: the "Follow" button and Instagram's "Suggested ..." shelves. */
    fun isOffFeedLabel(label: String): Boolean {
        val l = label.trim()
        if (l.equals("Follow", true)) return true
        val prefixes = listOf("Suggested for you", "Suggested posts", "Suggested reels", "Suggested Reels", "Reels for you",
            "Reels you might like", "More reels", "Suggested accounts", "Suggested videos")
        return prefixes.any { l.startsWith(it, true) }
    }

    /** View ids of screens where Reelock must never interfere: DM thread, photo/video picker, camera. */
    private val IGNORE_IDS = listOf("gallery", "media_picker", "photo_picker", "shutter", "quick_capture",
        "thread_composer", "direct_thread", "row_thread", "direct_text_message")

    /**
     * Reels  = the Reels / Shorts tab is selected on a visible tab bar, OR a full-screen "Reel by..." player,
     *          OR a Reels-player view id ("clips_viewer...", "root_clips_layout" / YouTube "reel_recycler"...).
     *          Deliberately NOT "any clips_* id": the camera and photo picker use those too.
     * Story  = Instagram's "reel_viewer" views, but ONLY when none of the Reels signals are present.
     * Ignored = DM thread (message box), photo/video picker, camera. Nothing is blocked or scrolled there.
     */
    fun scan(root: Nd, a: App, screenH: Int): Scan {
        val out = Scan()
        val stack = ArrayList<Nd>()
        stack.add(root)
        var n = 0
        var strong: String? = null
        var strongBottom = -1
        var strongH = 0
        var tabSelId = false          // the tab is recognised by its view id (clips_tab)
        var tabSelDesc = false        // ... or by its label ("Reels" / "Shorts")
        var tabBarVisible = false     // a Home tab is showing at the bottom of the screen
        var homeSelected = false
        var player = false
        var storyId = false
        var suppress = false
        val ids = LinkedHashSet<String>()
        while (stack.isNotEmpty() && n < 900) {
            val nd = stack.removeAt(stack.size - 1); n++
            val id = nd.id
            val cd = nd.desc
            val tx = nd.text
            if (id != null) {
                if (ids.size < 70) ids.add(id)
                if (Diag.seen.size < 250 && Regex("clip|reel|short|pager|viewer|tab|gallery|camera|thread").containsMatchIn(id)) Diag.seen.add(id)
            }
            if (cd != null && cd.length <= 12 && Diag.labels.size < 40 && cd.none { it.isDigit() }) Diag.labels.add(cd)
            if (nd.visible) {
                val h = nd.bottom - nd.top
                val atBottom = nd.top > screenH * 0.88f
                if (a == App.IG) {
                    if (id != null && id.contains("reel_viewer", true)) storyId = true
                    if (id != null && IGNORE_IDS.any { id.contains(it, true) }) suppress = true
                    if (nd.cls?.contains("EditText", true) == true && (tx ?: cd)?.startsWith("Message", true) == true) suppress = true
                    // the Messages tab itself is selected (DM inbox): hands off
                    if (nd.selected && atBottom && (id == "direct_tab" || (cd != null && (cd.startsWith("Messages", true) || cd.startsWith("Direct", true))))) suppress = true
                }
                if (atBottom && (id == "feed_tab" || (cd != null && cd.startsWith("Home", true)))) {
                    tabBarVisible = true
                    if (nd.selected) homeSelected = true
                }
                if (strong == null && id != null && a.idHints.any { id.contains(it, true) }) {
                    strong = id; strongBottom = nd.bottom; strongH = h
                }
                if (nd.selected) {
                    if (id != null && a.tabId != null && id == a.tabId) tabSelId = true
                    else if (cd != null && atBottom && cd.startsWith(a.tabDesc, true)) tabSelDesc = true
                }
                if (cd != null && a.reelDesc != null && cd.contains(a.reelDesc, true)) {
                    if (h >= screenH * 0.6f) player = true
                    else if (h >= screenH * 0.12f && out.feedReelBottom < 0) { out.feedReelBottom = nd.bottom; out.feedReelBig = h >= screenH * 0.30f }
                }
                if (a == App.IG && out.offFeedBottom < 0) {
                    val label = tx ?: cd
                    if (label != null && isOffFeedLabel(label) && nd.top > screenH * 0.10f && nd.top < screenH * 0.85f) {
                        out.offFeedBottom = nd.bottom
                    }
                }
            }
            stack.addAll(nd.children)
        }
        val tabSel = tabSelId || (tabSelDesc && tabBarVisible)
        out.home = a == App.IG && homeSelected && !suppress
        // a Reels-player id while the Home feed is showing is a Reel inside the feed, not the player
        val strongIsViewer = strong != null && !(a == App.IG && out.home)
        if (a == App.IG && out.home && strong != null && out.feedReelBottom < 0 && strongH >= screenH * 0.12f) { out.feedReelBottom = strongBottom; out.feedReelBig = strongH >= screenH * 0.30f }
        var viewer = tabSel || player || strongIsViewer
        if (suppress && !tabSel) { viewer = false; out.ignored = true; out.feedReelBottom = -1; out.feedReelBig = false; out.offFeedBottom = -1 }
        out.viewer = viewer
        out.story = a == App.IG && storyId && !viewer && !suppress
        out.hit = when { tabSel -> "tab"; player -> "player"; strongIsViewer -> "id:$strong"; else -> "" }
        if (out.ignored) out.hit = ""
        Diag.scans++; Diag.hit = out.hit
        Diag.ids = ids.joinToString(", ")
        return out
    }
}

sealed class Act {
    object Block : Act()
    class Skip(val bottom: Int) : Act()
    class Toast(val msg: String) : Act()
}

/** The decision engine: counts reel time, warns, blocks, and decides when to scroll the home feed. Pure Kotlin + Store. */
class Engine {
    private var wasViewer = false
    private var lastStep = 0L
    private var acc = 0L
    private var gapStart = -1L          // when a Reel was last confirmed, if the screen briefly stopped looking like a Reel
    private var curApp: App? = null
    private var lastBlock = Long.MIN_VALUE / 2
    private var lastSkip = Long.MIN_VALUE / 2
    private var burst = 0
    private var pausedUntil = 0L

    /** Hard reset: the user left the Reels / app. Nothing is credited for the time in between. */
    fun reset() {
        val a = curApp
        if (a != null && acc >= 500) Store.addUsed(a, 1)     // don't lose the last half-second of a session: round it
        curApp = null
        wasViewer = false; acc = 0L; gapStart = -1L
        Diag.countApp = ""; Diag.countAt = 0L
    }

    fun step(a: App, s: Scan, now: Long): List<Act> {
        val out = ArrayList<Act>()
        if (!Store.enabled(a)) { reset(); lastStep = now; return out }
        if (s.ignored) {                                 // DM thread / photo picker / camera: hands off
            Diag.note = "dm / picker / camera (ignored)"; Diag.ignoredAt = Store.now(); reset(); lastStep = now; return out
        }
        if (s.story) {                                   // stories are always allowed
            Diag.note = "story (allowed)"; Diag.storyAt = Store.now(); reset(); lastStep = now; return out
        }
        if (s.viewer) {
            Diag.note = "reels player"; Diag.reelAt = Store.now(); Diag.reelHit = s.hit
            // How much real time to credit since the last step:
            //  - still on a Reel: the elapsed time
            //  - a Reel came back within 2 s (screen flickered while swiping to the next one): the whole gap
            //  - first time we see it: 250 ms (we notice it up to one poll late)
            val credit = when {
                wasViewer -> (now - lastStep).coerceIn(0L, 1500L)
                gapStart >= 0 && now - gapStart <= 2000L -> now - gapStart
                else -> 250L
            }
            gapStart = -1L
            val before = Store.limitSec(a) - Store.used(a)
            curApp = a
            acc += credit
            while (acc >= 1000) { Store.addUsed(a, 1); acc -= 1000 }
            val left = Store.limitSec(a) - Store.used(a)
            if (!Store.passActive()) {
                if (before > 60 && left <= 60 && left > 0) out.add(Act.Toast("1 min of ${a.label} left"))
                if (before > 10 && left <= 10 && left > 0) out.add(Act.Toast("10 seconds left"))
            }
            wasViewer = true; lastStep = now
            Diag.countApp = a.key; Diag.countAt = Store.now()
            if (Store.isBlocked(a) && now - lastBlock >= 1500) {
                lastBlock = now; reset(); Store.addBlock(); out.add(Act.Block)
            }
            return out
        }
        // not a Reel on screen
        if (s.home) { reset() }                          // explicitly on the Home tab: really left the Reels
        else if (wasViewer) { gapStart = lastStep }      // could be a flicker between two Reels; wait and see
        if (gapStart >= 0 && now - gapStart > 2000L) reset()
        wasViewer = false; lastStep = now
        Diag.countAt = 0L                                // never show "counting" while no Reel is on screen
        // Home feed clean-up (Instagram only). Gentle on purpose: only big Reels / suggested posts, at most
        // 3 skips in a row, then 30 s of normal scrolling so the feed never "runs away" from you.
        if (a == App.IG && s.home && !Store.passActive()) {
            val wantReel = Store.skipFeedReels && s.feedReelBottom >= 0 && s.feedReelBig
            val wantOff = Store.followingOnly && s.offFeedBottom >= 0
            when {
                now < pausedUntil -> Diag.note = "feed skipping paused (you scroll)"
                wantReel || wantOff -> {
                    Diag.note = if (wantReel) "skipping feed reel" else "skipping suggested post"
                    if (now - lastSkip >= 1800) {
                        if (burst >= 3) { pausedUntil = now + 30_000L; burst = 0 }
                        else {
                            burst++; lastSkip = now; Diag.skipAt = Store.now(); Store.addBlock()
                            out.add(Act.Skip(if (wantReel) s.feedReelBottom else s.offFeedBottom))
                        }
                    }
                }
                else -> { Diag.note = "home feed ok"; if (now - lastSkip > 10_000L) burst = 0 }
            }
        }
        return out
    }
}
