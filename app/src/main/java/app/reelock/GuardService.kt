package app.reelock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/** Adapts a real AccessibilityNodeInfo to the testable [Nd] interface. */
private class NodeAdapter(private val n: AccessibilityNodeInfo) : Nd {
    private val r: Rect by lazy { Rect().also { n.getBoundsInScreen(it) } }
    override val id: String? get() = n.viewIdResourceName?.substringAfter(":id/")
    override val desc: String? get() = n.contentDescription?.toString()
    override val text: String? get() = n.text?.toString()
    override val selected: Boolean get() = n.isSelected
    override val visible: Boolean get() = n.isVisibleToUser
    override val top: Int get() = r.top
    override val bottom: Int get() = r.bottom
    override val children: List<Nd>
        get() {
            val l = ArrayList<Nd>()
            for (i in 0 until n.childCount) n.getChild(i)?.let { l.add(NodeAdapter(it)) }
            return l
        }
}

class GuardService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private val engine = Engine()
    private var watch: App? = null          // app we are watching while it is in front
    private var loopRunning = false
    private var missingSince = 0L
    private var lastStep = 0L

    /**
     * ONE loop does everything: every 500 ms while Instagram / YouTube is in front it scans the screen and
     * hands the result to the [Engine]. Wrapped in try/catch so a bad node can never stop it.
     */
    private val loop = object : Runnable {
        override fun run() {
            val a = watch
            if (a == null) { loopRunning = false; return }
            try { step(a) } catch (t: Throwable) { Diag.err = t.javaClass.simpleName + ": " + (t.message ?: "") }
            if (watch != null) h.postDelayed(this, 500) else loopRunning = false
        }
    }

    override fun onServiceConnected() { Store.init(this); Diag.connected = true }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        Store.init(this)
        if (!Store.consent) return
        val pkg = e.packageName?.toString() ?: return
        Diag.connected = true; Diag.lastPkg = pkg; Diag.lastAt = System.currentTimeMillis()
        val app = App.values().firstOrNull { it.pkg == pkg } ?: return
        if (!Store.enabled(app)) return
        if (watch != app) { watch = app; engine.reset(); missingSince = 0L }
        if (!loopRunning) { loopRunning = true; lastStep = SystemClock.uptimeMillis(); h.post(loop) }
    }

    private fun stopWatch() {
        watch = null; engine.reset()
        h.removeCallbacks(loop); loopRunning = false
    }

    /** The window of [a], even when it is not the "active" one. */
    private fun findRoot(a: App): AccessibilityNodeInfo? {
        try {
            for (w in windows) { val r = w.root; if (r != null && r.packageName?.toString() == a.pkg) return r }
        } catch (_: Throwable) { }
        val r = rootInActiveWindow
        return if (r != null && r.packageName?.toString() == a.pkg) r else null
    }

    private fun step(a: App) {
        val now = SystemClock.uptimeMillis()
        val root = findRoot(a)
        if (root == null) {
            if (missingSince == 0L) missingSince = now
            if (now - missingSince > 4000) stopWatch()   // left the app
            engine.reset()
            return
        }
        missingSince = 0L
        val s = Detector.scan(NodeAdapter(root), a, resources.displayMetrics.heightPixels)
        for (act in engine.step(a, s, now)) when (act) {
            is Act.Block -> block(a)
            is Act.Skip -> skip(act.bottom)
            is Act.Toast -> Toast.makeText(this, act.msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** Swipe the feed up so whatever ends at [bottomPx] scrolls off screen. */
    private fun skip(bottomPx: Int) {
        val dm = resources.displayMetrics
        val startY = dm.heightPixels * 0.80f
        val dist = (bottomPx + 40f).coerceIn(dm.heightPixels * 0.25f, dm.heightPixels * 0.66f)
        val path = Path().apply { moveTo(dm.widthPixels * 0.5f, startY); lineTo(dm.widthPixels * 0.5f, startY - dist) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 450)).build()
        dispatchGesture(g, null, null)
    }

    private fun block(a: App) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        startActivity(Intent(this, BlockActivity::class.java).putExtra("m", Store.reason(a))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        // If back didn't leave the reel, send the user home.
        h.postDelayed({
            try {
                val r = findRoot(a)
                if (r != null && Detector.scan(NodeAdapter(r), a, resources.displayMetrics.heightPixels).viewer) performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (_: Throwable) { }
        }, 900)
    }

    override fun onInterrupt() { stopWatch() }
    override fun onUnbind(intent: Intent?): Boolean { stopWatch(); Diag.connected = false; return super.onUnbind(intent) }
}
