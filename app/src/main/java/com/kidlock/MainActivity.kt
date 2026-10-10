package com.kidlock

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The child's home screen: shows only allowed apps, runs pinned (kiosk). */
class MainActivity : Activity() {

    private lateinit var grid: GridLayout
    private lateinit var banner: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "My apps"
            textSize = 28f
            setPadding(dp(24), dp(24), dp(24), dp(8))
            // Parent entry: long-press the title, then enter the PIN.
            setOnLongClickListener {
                PinUi.ask(this@MainActivity) {
                    startActivity(Intent(this@MainActivity, ParentActivity::class.java))
                }
                true
            }
        }
        banner = TextView(this).apply {
            textSize = 14f
            setPadding(dp(24), 0, dp(24), dp(8))
        }
        grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(grid) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(banner)
            addView(scroll)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.hasPin(this)) PinUi.setNew(this, cancelable = false)
        refresh()
    }

    private fun refresh() {
        if (Policy.isOwner(this)) {
            Policy.apply(this)
            if (Prefs.parentActive(this)) {
                val until = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                    .format(java.util.Date(Prefs.parentUntil(this)))
                banner.text = "PARENT MODE is on until $until. Tap here to lock again now."
                banner.setOnClickListener {
                    Policy.lockNow(this)
                    refresh()
                }
            } else {
                banner.text = ""
                banner.setOnClickListener(null)
                enterLockTask()
            }
        } else {
            banner.text = "Not locked down yet. Run the adb setup step from the README."
        }
        render()
    }

    private fun enterLockTask() {
        val am = getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask()
            } catch (_: Exception) {
            }
        }
    }

    private fun render() {
        grid.removeAllViews()
        val pm = packageManager
        val allowed = Prefs.allowed(this)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName in allowed }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        if (apps.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "No apps allowed yet."
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
            return
        }

        apps.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener {
                    pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                }
            }
            cell.addView(ImageView(this).apply {
                setImageDrawable(ri.loadIcon(pm))
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            })
            cell.addView(TextView(this).apply {
                text = ri.loadLabel(pm)
                gravity = Gravity.CENTER
                maxLines = 1
            })
            grid.addView(cell, GridLayout.LayoutParams().apply { width = dp(120) })
        }
    }
}
