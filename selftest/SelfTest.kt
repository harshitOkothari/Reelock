package app.reelock

import android.content.SharedPreferences
import java.time.LocalDateTime
import java.time.ZoneId

// ---------- fakes ----------
class FakePrefs : SharedPreferences {
    val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(k: String?, d: String?) = (m[k] as String?) ?: d
    override fun getStringSet(k: String?, d: MutableSet<String>?) = d
    override fun getInt(k: String?, d: Int) = (m[k] as Int?) ?: d
    override fun getLong(k: String?, d: Long) = (m[k] as Long?) ?: d
    override fun getFloat(k: String?, d: Float) = (m[k] as Float?) ?: d
    override fun getBoolean(k: String?, d: Boolean) = (m[k] as Boolean?) ?: d
    override fun contains(k: String?) = m.containsKey(k)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(k: String?, v: String?) = apply { m[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { m[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { m[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { m[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { m[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { m[k!!] = v }
        override fun remove(k: String?) = apply { m.remove(k) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

const val H = 2400
class N(
    override val id: String? = null, override val desc: String? = null, override val text: String? = null, override val cls: String? = null,
    override val selected: Boolean = false, override val visible: Boolean = true,
    override val top: Int = 0, override val bottom: Int = 0, kids: List<N> = emptyList()
) : Nd { override val children: List<Nd> = kids }

// ---------- fake Instagram / YouTube screens ----------
fun igTabs(selected: String) = listOf(
    N("feed_tab", "Home", selected = selected == "home", top = 2250, bottom = 2400),
    N("clips_tab", "Reels", selected = selected == "reels", top = 2250, bottom = 2400),
    N("search_tab", "Search", selected = selected == "search", top = 2250, bottom = 2400),
    N("profile_tab", "Profile", selected = selected == "profile", top = 2250, bottom = 2400))
fun root(vararg k: N) = N(kids = k.toList())
fun igReelsTab() = root(*(igTabs("reels") + N("clips_viewer_view_pager", top = 0, bottom = 2250) + N(desc = "Reel by someone. Double tap to play or pause.", top = 0, bottom = 2250)).toTypedArray())
fun igReelsTabNoIds() = root(*igTabs("reels").toTypedArray())
fun igReelsTabWithReelViewerNames() = root(*(igTabs("reels") + N("reel_viewer_texture_view", top = 0, bottom = 2250) + N("reel_viewer_root", top = 0, bottom = 2250)).toTypedArray())
fun igStory() = root(N("reel_viewer_root", top = 0, bottom = 2400), N("reel_viewer_texture_view", top = 0, bottom = 2400), N("reel_viewer_message_composer", text = "Send message", top = 2200, bottom = 2400))
fun igHomeFeedClean() = root(*(igTabs("home") + N(desc = "Photo by friend", top = 300, bottom = 1500) + N("row_feed_photo_profile_name", text = "friend", top = 320, bottom = 400)).toTypedArray())
fun igHomeFeedWithInlineReel() = root(*(igTabs("home") + N(desc = "Reel by someone. Double tap to play or pause.", top = 400, bottom = 1500)).toTypedArray())
fun igHomeFeedWithClipsIds() = root(*(igTabs("home") + N("clips_viewer_media", top = 400, bottom = 1600)).toTypedArray())
fun igHomeFeedSuggested() = root(*(igTabs("home") + N(text = "Follow", top = 330, bottom = 420) + N(desc = "Photo by stranger", top = 300, bottom = 1500)).toTypedArray())
fun igHomeFeedSuggestedHeader() = root(*(igTabs("home") + N(text = "Suggested for you", top = 600, bottom = 680)).toTypedArray())
fun igExploreReelPlayer() = root(N(desc = "Reel by someone. Double tap to play or pause.", top = 0, bottom = 2400), N(text = "Reels", top = 90, bottom = 160))
fun igExploreReelTitleOnly() = root(N(text = "Reels", top = 90, bottom = 160), N("clips_viewer_view_pager", top = 0, bottom = 2400))
fun igHiddenClips() = root(*(igTabs("home") + N("clips_viewer_view_pager", visible = false, top = 0, bottom = 2250)).toTypedArray())
fun igDmWithFollowText() = root(*(igTabs("profile") + N(text = "Follow", top = 500, bottom = 560)).toTypedArray())
fun ytShorts() = root(N("reel_recycler", top = 0, bottom = 2300), N("reel_player_page_container", top = 0, bottom = 2300))
fun ytShortsTab() = root(N(desc = "Home", selected = false, top = 2250, bottom = 2400), N(desc = "Shorts", selected = true, top = 2250, bottom = 2400))
fun ytHome() = root(N(desc = "Home", selected = true, top = 2250, bottom = 2400), N(desc = "Shorts", selected = false, top = 2250, bottom = 2400), N("video_title", text = "Some video", top = 400, bottom = 600))

// ---------- tiny test framework ----------
var passed = 0; var failed = 0
var usedAtFirstBlock = -1
fun check(name: String, ok: Boolean, extra: String = "") {
    if (ok) passed++ else { failed++; println("  FAIL: $name $extra") }
}
fun section(s: String) = println("\n== $s")

var fakeNow = LocalDateTime.of(2026, 11, 19, 15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun setTime(y: Int, mo: Int, d: Int, h: Int, mi: Int) { fakeNow = LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

fun fresh(goalIg: Int = 5, goalYt: Int = 5) {
    Store.initForTest(FakePrefs())
    Store.clock = { fakeNow }
    setTime(2026, 11, 19, 15, 0)
    Store.onboarded = true; Store.consent = true
    Store.startedAt = fakeNow; Store.paceStart = fakeNow; Store.paceDays = 0
    Store.hours = 6f
    Store.setGoal(App.IG, goalIg); Store.setGoal(App.YT, goalYt)
    Store.setEnabled(App.IG, true); Store.setEnabled(App.YT, true)
    Store.sleepOn = true; Store.sleepStart = 23 * 60; Store.wakeMin = 6 * 60
    Store.focusUntil = 0L; Store.passUntil = 0L
    Store.skipFeedReels = true; Store.followingOnly = true
}

/** feed the engine one scan every 500 ms for [seconds]; returns all actions */
fun run(e: Engine, a: App, tree: N, seconds: Int, t0: Long = 0L, startAt: IntArray = intArrayOf(0)): List<Pair<Int, Act>> {
    val out = ArrayList<Pair<Int, Act>>()
    var t = startAt[0]
    val steps = seconds * 2
    for (i in 0 until steps) {
        val s = Detector.scan(tree, a, H)
        for (act in e.step(a, s, t.toLong() + t0)) { out.add(t to act); if (act is Act.Block && usedAtFirstBlock < 0) usedAtFirstBlock = Store.used(a) }
        t += 500
        fakeNow += 500
    }
    startAt[0] = t
    return out
}

fun main() {
    // ============ DETECTION ============
    section("Instagram detection")
    fresh()
    fun ig(t: N) = Detector.scan(t, App.IG, H)
    var s = ig(igReelsTab()); check("Reels tab + ids -> viewer", s.viewer && !s.story)
    s = ig(igReelsTabNoIds()); check("Reels tab selected, no ids -> viewer", s.viewer && !s.story)
    s = ig(igReelsTabWithReelViewerNames()); check("Reels tab with reel_viewer names is NOT a story", s.viewer && !s.story, "viewer=${s.viewer} story=${s.story}")
    s = ig(igStory()); check("Story viewer -> story, not viewer", s.story && !s.viewer)
    s = ig(igHomeFeedClean()); check("Home feed with normal post -> nothing", !s.viewer && !s.story && s.home && s.feedReelBottom < 0 && s.offFeedBottom < 0)
    s = ig(igHomeFeedWithInlineReel()); check("Home feed inline reel -> feedReel, not viewer", !s.viewer && s.feedReelBottom == 1500 && s.home)
    s = ig(igHomeFeedWithClipsIds()); check("Home feed clips_* id -> feedReel, not viewer", !s.viewer && s.feedReelBottom == 1600)
    s = ig(igHomeFeedSuggested()); check("Home feed 'Follow' button -> offFeed", !s.viewer && s.offFeedBottom == 420)
    s = ig(igHomeFeedSuggestedHeader()); check("Home feed 'Suggested for you' -> offFeed", s.offFeedBottom == 680)
    s = ig(igExploreReelPlayer()); check("Reel opened from Explore (player desc) -> viewer", s.viewer)
    s = ig(igExploreReelTitleOnly()); check("Reel opened from Explore (clips id, no tab) -> viewer", s.viewer)
    s = ig(root(N(text = "Reels", top = 90, bottom = 160))); check("'Reels' text alone is NOT enough (no false positives)", !s.viewer)
    s = ig(igHiddenClips()); check("Invisible clips view -> nothing", !s.viewer)
    s = ig(igDmWithFollowText()); check("'Follow' outside Home: Home flag is off", !s.home && !s.viewer)
    fresh(); check("'Follow' outside Home -> engine does nothing", run(Engine(), App.IG, igDmWithFollowText(), 4).isEmpty())

    section("YouTube detection")
    var y = Detector.scan(ytShorts(), App.YT, H); check("Shorts player ids -> viewer", y.viewer)
    y = Detector.scan(ytShortsTab(), App.YT, H); check("Shorts tab selected -> viewer", y.viewer)
    y = Detector.scan(ytHome(), App.YT, H); check("YouTube Home -> nothing", !y.viewer)
    y = Detector.scan(igStory(), App.YT, H); check("IG story tree is not Shorts", !y.viewer)

    // ============ ENGINE ============
    section("Limit None -> instant block, nothing counted")
    fresh(goalIg = 0)
    var e = Engine(); var clk = intArrayOf(0)
    var acts = run(e, App.IG, igReelsTab(), 3, startAt = clk)
    check("blocked within first 1s", acts.any { it.second is Act.Block && it.first <= 500 }, acts.toString())
    check("no usage counted", Store.used(App.IG) <= 1, "used=${Store.used(App.IG)}")

    section("5 minute allowance")
    fresh(goalIg = 5)
    usedAtFirstBlock = -1
    e = Engine(); clk = intArrayOf(0)
    acts = run(e, App.IG, igReelsTab(), 400, startAt = clk)
    val blk = acts.filter { it.second is Act.Block }
    check("blocked exactly around 300s", blk.isNotEmpty() && blk[0].first in 299_000..302_000, "first block at ${blk.firstOrNull()?.first}")
    check("used about 300s at the moment of first block", usedAtFirstBlock in 298..302, "used=$usedAtFirstBlock")
    val toasts = acts.filter { it.second is Act.Toast }.map { (it.second as Act.Toast).msg }
    check("1-minute warning shown once", toasts.count { it.contains("1 min") } == 1, toasts.toString())
    check("10-second warning shown once", toasts.count { it.contains("10 seconds") } == 1, toasts.toString())
    check("block repeats at most every 1.5s", blk.zipWithNext().all { (a, b) -> b.first - a.first >= 1500 })

    section("Counting accuracy: 2 min reels, 3 min story, 5 min feed, 1 min reels")
    fresh(goalIg = 20)
    e = Engine(); clk = intArrayOf(0)
    run(e, App.IG, igReelsTab(), 120, startAt = clk)
    run(e, App.IG, igStory(), 180, startAt = clk)
    run(e, App.IG, igHomeFeedClean(), 300, startAt = clk)
    run(e, App.IG, igReelsTab(), 60, startAt = clk)
    check("used ~ 180s (reels only)", Store.used(App.IG) in 175..185, "used=${Store.used(App.IG)}")

    check("self-check: reel timestamp recorded", Diag.reelAt != 0L)

    section("Stories never counted or blocked (even with limit None)")
    fresh(goalIg = 0)
    e = Engine(); clk = intArrayOf(0)
    acts = run(e, App.IG, igStory(), 600, startAt = clk)
    check("no actions for 10 min of stories", acts.isEmpty(), acts.toString())
    check("self-check: story timestamp recorded", Diag.storyAt != 0L)
    check("nothing counted", Store.used(App.IG) == 0)

    section("Reel Pass")
    fresh(goalIg = 5)
    Store.passUntil = fakeNow + 10 * 60_000L
    e = Engine(); clk = intArrayOf(0)
    acts = run(e, App.IG, igReelsTab(), 420, startAt = clk)   // 7 min, past the 5 min limit
    check("no block during pass", acts.none { it.second is Act.Block })
    check("time still counted during pass", Store.used(App.IG) in 410..430, "used=${Store.used(App.IG)}")
    Store.passUntil = 0L
    acts = run(e, App.IG, igReelsTab(), 5, startAt = clk)
    check("blocked once pass is over", acts.any { it.second is Act.Block })

    section("Focus Lock")
    fresh(goalIg = 20)
    Store.focusUntil = fakeNow + 3600_000L
    e = Engine(); clk = intArrayOf(0)
    acts = run(e, App.IG, igReelsTab(), 3, startAt = clk)
    check("blocked immediately during focus lock", acts.any { it.second is Act.Block })
    acts = run(e, App.IG, igStory(), 5, startAt = clk)
    check("stories still fine during focus lock", acts.isEmpty())

    section("Sleep Mode")
    fresh(goalIg = 20)
    setTime(2026, 11, 19, 23, 30)
    e = Engine(); clk = intArrayOf(0)
    check("23:30 is sleeping", Store.sleeping())
    acts = run(e, App.IG, igReelsTab(), 3, startAt = clk)
    check("blocked at 23:30", acts.any { it.second is Act.Block })
    setTime(2026, 11, 20, 3, 0); check("03:00 sleeping", Store.sleeping())
    setTime(2026, 11, 20, 6, 1); check("06:01 awake", !Store.sleeping())
    setTime(2026, 11, 19, 12, 0); check("12:00 awake", !Store.sleeping())
    Store.sleepStart = 30; setTime(2026, 11, 20, 3, 0)
    check("start 00:30: 03:00 sleeping", Store.sleeping())
    setTime(2026, 11, 20, 23, 50); check("start 00:30: 23:50 awake", !Store.sleeping())
    Store.sleepStart = 23 * 60; Store.sleepOn = false; setTime(2026, 11, 19, 23, 30)
    check("sleep mode off -> not blocked by sleep", !Store.isBlocked(App.IG) || Store.used(App.IG) >= Store.limitSec(App.IG))

    section("Per-app limits / disabled app")
    fresh(goalIg = 5, goalYt = 10)
    e = Engine(); clk = intArrayOf(0)
    run(e, App.YT, ytShorts(), 120, startAt = clk)
    check("YT counted separately", Store.used(App.YT) in 115..125 && Store.used(App.IG) == 0, "yt=${Store.used(App.YT)} ig=${Store.used(App.IG)}")
    Store.setEnabled(App.IG, false)
    acts = run(Engine(), App.IG, igReelsTab(), 5, startAt = intArrayOf(0))
    check("disabled app: no action, no count", acts.isEmpty() && Store.used(App.IG) == 0)

    section("Home feed clean-up")
    fresh()
    e = Engine(); clk = intArrayOf(0)
    acts = run(e, App.IG, igHomeFeedWithInlineReel(), 3, startAt = clk)
    var skips = acts.filter { it.second is Act.Skip }
    check("feed reel -> skip action", skips.isNotEmpty() && (skips[0].second as Act.Skip).bottom == 1500)
    check("self-check: skip timestamp recorded", Diag.skipAt != 0L)
    check("skips rate limited (>=1.8s apart)", skips.zipWithNext().all { (a, b) -> b.first - a.first >= 1800 })
    Store.skipFeedReels = false
    acts = run(Engine(), App.IG, igHomeFeedWithInlineReel(), 3, startAt = intArrayOf(0))
    check("skipFeedReels OFF -> no skip", acts.none { it.second is Act.Skip })
    Store.skipFeedReels = true
    acts = run(Engine(), App.IG, igHomeFeedSuggested(), 3, startAt = intArrayOf(0))
    check("suggested post -> skip", acts.any { it.second is Act.Skip })
    Store.followingOnly = false
    acts = run(Engine(), App.IG, igHomeFeedSuggested(), 3, startAt = intArrayOf(0))
    check("followingOnly OFF -> no skip", acts.none { it.second is Act.Skip })
    Store.followingOnly = true
    acts = run(Engine(), App.IG, igHomeFeedClean(), 5, startAt = intArrayOf(0))
    check("normal followed post -> untouched", acts.isEmpty())
    Store.passUntil = fakeNow + 60_000L
    acts = run(Engine(), App.IG, igHomeFeedWithInlineReel(), 3, startAt = intArrayOf(0))
    check("pass active -> feed untouched", acts.isEmpty())
    Store.passUntil = 0L
    acts = run(Engine(), App.IG, igStory(), 3, startAt = intArrayOf(0))
    check("story never skipped", acts.isEmpty())

    section("Suggested reels / suggested content in the feed")
    fresh()
    for (label in listOf("Suggested Reels", "Suggested for you", "suggested posts", "Reels for you", "Reels you might like", "More reels", "Suggested accounts")) {
        val sg = ig(root(*(igTabs("home") + N(text = label, top = 700, bottom = 780)).toTypedArray()))
        check("home: '$label' recognised", sg.offFeedBottom == 780 && !sg.viewer, "bottom=${sg.offFeedBottom}")
    }
    var sg = ig(root(*(igTabs("home") + N(desc = "Reel by someone, 12K views", top = 600, bottom = 940)).toTypedArray()))
    check("small suggested-reel thumbnail in feed (14% height) -> feedReel but not big", sg.feedReelBottom == 940 && !sg.feedReelBig)
    acts = run(Engine(), App.IG, root(*(igTabs("home") + N(desc = "Reel by someone, 12K views", top = 600, bottom = 940)).toTypedArray()), 4, startAt = intArrayOf(0))
    check("small thumbnail never triggers an auto-scroll", acts.none { it.second is Act.Skip })
    acts = run(Engine(), App.IG, root(*(igTabs("home") + N(text = "Suggested Reels", top = 700, bottom = 780)).toTypedArray()), 3, startAt = intArrayOf(0))
    check("'Suggested Reels' header -> skip action", acts.any { it.second is Act.Skip })
    acts = run(Engine(), App.IG, root(*(igTabs("profile") + N(text = "Suggested for you", top = 700, bottom = 780)).toTypedArray()), 3, startAt = intArrayOf(0))
    check("suggested label outside Home -> untouched", acts.isEmpty())

    section("Counting accuracy and instant stop")
    fresh(goalIg = 60)
    e = Engine(); clk = intArrayOf(0)
    val flick = root(N(text = "loading"))
    repeat(100) { run(e, App.IG, igReelsTab(), 3, startAt = clk); run(e, App.IG, flick, 1, startAt = clk) }   // 400 s wall time, swiping flicker every 4 s
    check("swiping flicker: counted ~100% of 400s", Store.used(App.IG) in 394..406, "used=${Store.used(App.IG)}")
    fresh(goalIg = 60); e = Engine(); clk = intArrayOf(0)
    run(e, App.IG, igReelsTab(), 10, startAt = clk); run(e, App.IG, igHomeFeedClean(), 10, startAt = clk); run(e, App.IG, igReelsTab(), 10, startAt = clk)
    check("left to Home for 10s: that gap is NOT credited (20s, not 30s)", Store.used(App.IG) in 19..22, "used=${Store.used(App.IG)}")
    fresh(goalIg = 60); e = Engine(); clk = intArrayOf(0)
    run(e, App.IG, igReelsTab(), 5, startAt = clk); run(e, App.IG, flick, 3, startAt = clk); run(e, App.IG, igReelsTab(), 5, startAt = clk)
    check("3s gap (>2s) is not credited (~10s)", Store.used(App.IG) in 9..12, "used=${Store.used(App.IG)}")
    fresh(goalIg = 60); e = Engine(); clk = intArrayOf(0)
    run(e, App.IG, igReelsTab(), 5, startAt = clk)
    check("'counting' active while a Reel is on screen", Diag.countAt != 0L && Diag.countApp == "ig")
    run(e, App.IG, igHomeFeedClean(), 1, startAt = clk)
    check("'counting' cleared immediately after leaving the Reel", Diag.countAt == 0L)
    fresh(goalIg = 60); e = Engine(); clk = intArrayOf(0)
    run(e, App.IG, igReelsTab(), 5, startAt = clk); e.reset()
    check("app left (hard reset) clears counting", Diag.countAt == 0L && Diag.countApp == "")
    val u1 = Store.used(App.IG)
    run(e, App.IG, igReelsTab(), 60, startAt = clk)
    check("60s of reels adds 60s (no lag)", Store.used(App.IG) - u1 in 59..61, "added=${Store.used(App.IG) - u1}")

    section("DM / photo picker / camera are never touched (the bug: could not send photos or videos)")
    fresh(goalIg = 0)
    val dmThread = root(*(igTabs("home") + listOf(
        N(cls = "android.widget.EditText", text = "Message...", top = 2200, bottom = 2340),
        N("row_thread_composer_edittext", top = 2200, bottom = 2340),
        N(text = "Suggested for you", top = 700, bottom = 780),
        N(desc = "Reel by someone, 12K views", top = 900, bottom = 1700),
        N("clips_viewer_stale", top = 0, bottom = 2250))).toTypedArray())
    s = ig(dmThread); check("DM thread: ignored, not viewer, not home", s.ignored && !s.viewer && !s.home && s.offFeedBottom < 0)
    acts = run(Engine(), App.IG, dmThread, 10, startAt = intArrayOf(0))
    check("DM thread: no block, no skip, nothing counted (limit None)", acts.isEmpty() && Store.used(App.IG) == 0, acts.toString())
    check("self-check: ignored timestamp recorded", Diag.ignoredAt != 0L)
    val gallery = root(N("gallery_grid", top = 300, bottom = 2100), N("clips_gallery_item", top = 300, bottom = 800),
        N("clips_camera_button", top = 100, bottom = 200), N(desc = "Reels", selected = true, top = 2150, bottom = 2250))
    s = ig(gallery); check("photo/video picker (clips_* ids, 'Reels' mode label selected) -> not viewer", !s.viewer && s.ignored)
    acts = run(Engine(), App.IG, gallery, 5, startAt = intArrayOf(0)); check("picker: no block at limit None", acts.isEmpty())
    val camera = root(N("shutter_button", top = 2000, bottom = 2200), N("clips_camera_preview", top = 0, bottom = 2400),
        N(desc = "Reels", selected = true, top = 2230, bottom = 2330))
    s = ig(camera); check("camera -> not viewer", !s.viewer && s.ignored)
    s = ig(root(N("clips_camera_button", top = 100, bottom = 200), N("clips_gallery_item", top = 300, bottom = 800)))
    check("clips_* camera/gallery ids alone (no shutter, no tab) -> still not viewer", !s.viewer)
    s = ig(root(*(igTabs("reels") + N("clips_camera_button", top = 100, bottom = 200) + N("clips_viewer_view_pager", top = 0, bottom = 2250)).toTypedArray()))
    check("Reels tab with its own camera button -> still a viewer", s.viewer && !s.ignored)
    s = ig(root(*(igTabs("reels") + N("clips_camera_button", top = 100, bottom = 200)).toTypedArray()))
    check("Reels tab selected, only a camera button -> still a viewer", s.viewer)
    val dmInbox = root(N("direct_tab", "Messages", selected = true, top = 2250, bottom = 2400), N("feed_tab", "Home", top = 2250, bottom = 2400),
        N(desc = "Reel by someone", top = 500, bottom = 1700))
    s = ig(dmInbox); check("DM inbox (Messages tab selected) -> ignored", s.ignored && !s.viewer)
    val bottomLabel = root(N(desc = "Reels", selected = true, top = 2250, bottom = 2400))   // no Home tab visible = not a tab bar
    s = ig(bottomLabel); check("a selected 'Reels' label without a tab bar is not the Reels tab", !s.viewer)

    section("Feed auto-scroll is gentle (the bug: feed scrolled by itself)")
    fresh(); Store.skipFeedReels = true; Store.followingOnly = true
    e = Engine(); clk = intArrayOf(0)
    // a feed where EVERY post has a Follow button (like yours): must not scroll forever
    val allFollow = root(*(igTabs("home") + N(text = "Follow", top = 330, bottom = 420) + N(desc = "Reel by x", top = 400, bottom = 1500)).toTypedArray())
    acts = run(e, App.IG, allFollow, 60, startAt = clk)
    var nSkips = acts.count { it.second is Act.Skip }
    check("60 s of an all-suggested feed: only a few skips (<=6)", nSkips in 3..6, "skips=$nSkips")
    val firstTen = acts.filter { it.second is Act.Skip && it.first < 10_000 }.size
    check("max 3 skips in a row at the start", firstTen in 1..3, "firstTen=$firstTen")
    val gapOk = acts.filter { it.second is Act.Skip }.map { it.first }.zipWithNext().any { (a, b) -> b - a >= 29_000 }
    check("then ~30 s of normal scrolling", gapOk)
    fresh(); Store.followingOnly = false; Store.skipFeedReels = false
    acts = run(Engine(), App.IG, allFollow, 20, startAt = intArrayOf(0))
    check("both feed options OFF -> feed is never touched", acts.none { it.second is Act.Skip })

    // ============ STORE MATH ============
    section("Pace easing")
    fresh(goalIg = 0)
    Store.paceDays = 7; Store.paceStart = fakeNow
    check("day 0 limit = 30 min", Store.limitSec(App.IG) == 1800, "${Store.limitSec(App.IG)}")
    setTime(2026, 11, 22, 15, 0)
    check("day 3 limit between 10 and 20 min", Store.limitSec(App.IG) in 600..1200, "${Store.limitSec(App.IG)}")
    setTime(2026, 11, 26, 15, 0)
    check("day 7 limit = 0", Store.limitSec(App.IG) == 0, "${Store.limitSec(App.IG)}")

    section("Time saved, streak, score")
    fresh(goalIg = 10)
    setTime(2026, 11, 19, 22, 0)   // frac of day = 1.0
    check("nothing watched -> saved = full usual day (6h)", Store.savedSecToday() == 6 * 3600, "${Store.savedSecToday()}")
    check("focus score 100 with no reels", Store.focusScore() == 100)
    Store.addUsed(App.IG, 600)
    check("10 min watched -> saved reduced", Store.savedSecToday() == 6 * 3600 - 600)
    check("score < 100", Store.focusScore() in 90..99, "${Store.focusScore()}")
    fresh(goalIg = 10)
    Store.startedAt = fakeNow - 3L * 86_400_000L          // started 3 days ago
    setTime(2026, 11, 19, 22, 0)
    Store.startedAt = LocalDateTime.of(2026, 11, 16, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    check("3 earlier days -> total saved ~ 3 full days + today", Store.savedSecTotal() >= 3 * 6 * 3600, "${Store.savedSecTotal()}")
    check("streak counts days within limit", Store.streak() >= 4, "${Store.streak()}")
    Store.addUsed(App.IG, 20 * 60)   // 20 min > 10 min goal today
    check("over limit today -> streak 0", Store.streak() == 0)
    check("best streak keeps history", Store.bestStreak() >= 3, "${Store.bestStreak()}")

    section("Hourly pattern, blocks, passes")
    fresh()
    setTime(2026, 11, 19, 23, 10); Store.addUsed(App.IG, 120)
    check("hourly bucket for 23h", Store.hourly()[23] == 120)
    Store.addBlock(); Store.addBlock()
    check("blocks counted", Store.blocksToday() == 2 && Store.blocksTotal() == 2)
    Store.addPass(); check("passes counted", Store.passesToday() == 1 && Store.passesTotal() == 1)
    check("untilTomorrow = 06:00 next day", run { val t = Store.untilTomorrowMs(); t > fakeNow && t - fakeNow < 20 * 3600_000L })

    section("Water log (7 days only)")
    fresh()
    setTime(2026, 11, 10, 9, 0); Store.addWater(500)
    setTime(2026, 11, 16, 9, 0); Store.addWater(750)
    setTime(2026, 11, 19, 8, 0); Store.addWater(250); Store.addWater(500)
    check("today adds up", Store.waterToday() == 750, "${Store.waterToday()}")
    check("week has 7 days, oldest first, today last", Store.waterWeek().size == 7 && Store.waterWeek().last().second == 750 && Store.waterWeek().first().first == Store.today().minusDays(6))
    check("3 days back is still kept", Store.waterOn(Store.today().minusDays(3)) == 750)
    check("9 days old entry is dropped", Store.waterOn(Store.today().minusDays(9)) == 0)
    Store.undoWater()
    check("undo removes the last add only", Store.waterToday() == 250, "${Store.waterToday()}")
    Store.undoWater()
    check("second undo does nothing", Store.waterToday() == 250)
    Store.addWater(-9999)
    check("never below zero", Store.waterToday() == 0)
    Store.waterGoal = 99999; check("goal is clamped", Store.waterGoal == 6000)
    setTime(2026, 11, 20, 0, 5); Store.addWater(100); setTime(2026, 11, 26, 12, 0); Store.addWater(100)
    check("old days fall off as time passes", Store.waterOn(java.time.LocalDate.of(2026, 11, 19)) == 0 && Store.waterOn(java.time.LocalDate.of(2026, 11, 20)) == 100)

    println("\n==== $passed passed, $failed failed ====")
    if (failed > 0) System.exit(1)
}
