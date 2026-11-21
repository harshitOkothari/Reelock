package app.reelock

/** Minimal view of a screen node, so the detection logic can be unit-tested without Android. */
interface Nd {
    val id: String?        // view id without the "pkg:id/" prefix
    val desc: String?      // content description
    val text: String?
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
    var home = false                // Instagram Home tab is selected
    var feedReelBottom = -1         // bottom edge of a Reel sitting inside the home feed
    var offFeedBottom = -1          // bottom edge of a post from someone you don't follow
    var hit = ""
}

object Detector {
    /**
     * Reels  = Reels tab selected, OR a full-screen "Reel by..." player, OR an Instagram "clips_*" view while Home is not selected,
     *          OR a "Reels" title at the top of the screen (Reels opened from Explore / a profile / a DM).
     * Story  = Instagram's "reel_viewer" views, but ONLY when none of the Reels signals above are present
     *          (Instagram reuses "reel_viewer" names inside the Reels player, so they cannot be trusted alone).
     */
    fun scan(root: Nd, a: App, screenH: Int): Scan {
        val out = Scan()
        val stack = ArrayList<Nd>()
        stack.add(root)
        var n = 0
        var strong: String? = null
        var strongBottom = -1
        var strongH = 0
        var tabSel = false
        var player = false
        var storyId = false
        var titleReels = false
        val ids = LinkedHashSet<String>()
        while (stack.isNotEmpty() && n < 900) {
            val nd = stack.removeAt(stack.size - 1); n++
            val id = nd.id
            val cd = nd.desc
            val tx = nd.text
            if (id != null) {
                if (ids.size < 70) ids.add(id)
                if (Diag.seen.size < 250 && Regex("clip|reel|short|pager|viewer|tab").containsMatchIn(id)) Diag.seen.add(id)
            }
            if (cd != null && cd.length <= 12 && Diag.labels.size < 40 && cd.none { it.isDigit() }) Diag.labels.add(cd)
            if (nd.visible) {
                val h = nd.bottom - nd.top
                if (a == App.IG) {
                    if (id != null && id.contains("reel_viewer", true)) storyId = true
                    if (nd.selected && (id == "feed_tab" || (cd != null && cd.startsWith("Home", true)))) out.home = true
                    if (strong == null && id != null && id != "clips_tab" && id.startsWith("clips_", true)) {
                        strong = id; strongBottom = nd.bottom; strongH = h
                    }
                    if (tx != null && tx.equals("Reels", true) && nd.top < screenH * 0.14f) titleReels = true
                } else if (strong == null && id != null && a.idHints.any { id.contains(it, true) }) {
                    strong = id
                }
                if (nd.selected && ((id != null && a.tabId != null && id == a.tabId) ||
                            (cd != null && cd.startsWith(a.tabDesc, true)))) tabSel = true
                if (cd != null && a.reelDesc != null && cd.contains(a.reelDesc, true)) {
                    if (h >= screenH * 0.6f) player = true
                    else if (h >= screenH * 0.2f && out.feedReelBottom < 0) out.feedReelBottom = nd.bottom
                }
                if (a == App.IG && out.offFeedBottom < 0) {
                    val label = tx ?: cd
                    if (label != null && (label.equals("Follow", true) || label.startsWith("Suggested for you", true) ||
                                label.startsWith("Suggested posts", true)) && nd.top > screenH * 0.12f && nd.top < screenH * 0.8f) {
                        out.offFeedBottom = nd.bottom
                    }
                }
            }
            stack.addAll(nd.children)
        }
        // a "clips_*" view while Home is selected is a Reel inside the feed, not the player
        val strongIsViewer = strong != null && !(a == App.IG && out.home)
        if (a == App.IG && out.home && strong != null && out.feedReelBottom < 0 && strongH >= screenH * 0.2f) out.feedReelBottom = strongBottom
        out.viewer = tabSel || player || strongIsViewer || (a == App.IG && titleReels && !out.home)
        out.story = a == App.IG && storyId && !out.viewer
        out.hit = when {
            tabSel -> "tab"; player -> "player"; strongIsViewer -> "id:$strong"
            a == App.IG && titleReels && !out.home -> "title"; else -> ""
        }
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
    private var lastBlock = Long.MIN_VALUE / 2
    private var lastSkip = Long.MIN_VALUE / 2

    fun reset() { wasViewer = false; acc = 0L }

    fun step(a: App, s: Scan, now: Long): List<Act> {
        val out = ArrayList<Act>()
        if (!Store.enabled(a)) { reset(); lastStep = now; return out }
        if (s.story) {                                   // stories are always allowed
            Diag.note = "story (allowed)"; Diag.storyAt = Store.now(); reset(); lastStep = now; return out
        }
        if (s.viewer) {
            Diag.note = "reels player"; Diag.reelAt = Store.now(); Diag.reelHit = s.hit
            if (wasViewer) {
                val before = Store.limitSec(a) - Store.used(a)
                acc += (now - lastStep).coerceIn(0L, 1500L)
                while (acc >= 1000) { Store.addUsed(a, 1); acc -= 1000 }
                val left = Store.limitSec(a) - Store.used(a)
                if (!Store.passActive()) {
                    if (before > 60 && left <= 60 && left > 0) out.add(Act.Toast("1 min of ${a.label} left"))
                    if (before > 10 && left <= 10 && left > 0) out.add(Act.Toast("10 seconds left"))
                }
            }
            wasViewer = true; lastStep = now
            Diag.countApp = a.key; Diag.countAt = Store.now()
            if (Store.isBlocked(a) && now - lastBlock >= 1500) {
                lastBlock = now; reset(); Store.addBlock(); out.add(Act.Block)
            }
            return out
        }
        reset(); lastStep = now
        // Home feed clean-up (Instagram only): skip Reels and not-followed posts between your posts
        if (a == App.IG && s.home && !Store.passActive()) {
            if (Store.skipFeedReels && s.feedReelBottom >= 0) {
                Diag.note = "skipping feed reel"
                if (now - lastSkip >= 1100) { lastSkip = now; Diag.skipAt = Store.now(); Store.addBlock(); out.add(Act.Skip(s.feedReelBottom)) }
            } else if (Store.followingOnly && s.offFeedBottom >= 0) {
                Diag.note = "skipping suggested post"
                if (now - lastSkip >= 1100) { lastSkip = now; Diag.skipAt = Store.now(); Store.addBlock(); out.add(Act.Skip(s.offFeedBottom)) }
            } else Diag.note = "home feed ok"
        }
        return out
    }
}
