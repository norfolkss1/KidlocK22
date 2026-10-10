package com.kidlock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Parent screen: Parent mode, allowed apps, website list, PIN, remove controls. */
class ParentActivity : Activity() {

    private val checked = mutableSetOf<String>()
    private val sites = mutableListOf<Pair<String, Boolean>>()

    private lateinit var status: TextView
    private lateinit var appsBox: LinearLayout
    private lateinit var sitesBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f, top: Int = 16) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(top), 0, dp(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checked.addAll(Prefs.allowed(this))
        sites.addAll(Prefs.siteList(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(48))
        }

        // ---- Parent mode --------------------------------------------------------------
        root.addView(label("PARENT MODE", 20f, 0))
        status = TextView(this)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Turn on Parent mode (30 minutes)"
            setOnClickListener {
                Policy.openParentMode(this@ParentActivity, 30)
                try {
                    stopLockTask()
                } catch (_: Exception) {
                }
                updateStatus()
                Toast.makeText(
                    this@ParentActivity,
                    "Parent mode on. Open Play Store, sign in, install, then sign out.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Open Play Store"
            setOnClickListener {
                val i = packageManager.getLaunchIntentForPackage("com.android.vending")
                when {
                    !Prefs.parentActive(this@ParentActivity) ->
                        Toast.makeText(this@ParentActivity, "Turn on Parent mode first", Toast.LENGTH_SHORT).show()
                    i == null ->
                        Toast.makeText(this@ParentActivity, "Play Store not found", Toast.LENGTH_SHORT).show()
                    else -> startActivity(i)
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Lock again now"
            setOnClickListener {
                Policy.lockNow(this@ParentActivity)
                updateStatus()
                finish()
            }
        })
        root.addView(label("Tip: sign OUT of your Google account in Play Store when you are done, then lock again."))

        // ---- Allowed apps ---------------------------------------------------------------
        root.addView(label("ALLOWED APPS", 20f))
        root.addView(label("Tick the apps your child may use. Do NOT tick browsers other than Chrome, app stores, YouTube or anything with games.", 14f, 0))
        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appsBox)

        // ---- Websites -------------------------------------------------------------------
        root.addView(label("CHROME WEBSITES (tick = allowed)", 20f))
        root.addView(label("Only ticked websites open in Chrome. Untick to restrict a site, X to delete it. Chrome must be ticked above.", 14f, 0))
        sitesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(sitesBox)

        val newSite = EditText(this).apply {
            hint = "Add website, e.g. classroom.google.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        root.addView(newSite)
        root.addView(Button(this).apply {
            text = "Add website"
            setOnClickListener {
                val d = Prefs.cleanDomain(newSite.text.toString())
                when {
                    d == null ->
                        Toast.makeText(this@ParentActivity, "Type a website like kahoot.it", Toast.LENGTH_SHORT).show()
                    sites.any { it.first == d } ->
                        Toast.makeText(this@ParentActivity, "Already in the list", Toast.LENGTH_SHORT).show()
                    else -> {
                        sites.add(d to true)
                        newSite.setText("")
                        renderSites()
                    }
                }
            }
        })

        // ---- Save and other actions -----------------------------------------------------
        root.addView(Button(this).apply {
            text = "SAVE apps and websites"
            setOnClickListener {
                Prefs.setAllowed(this@ParentActivity, checked.toSet())
                Prefs.setSiteList(this@ParentActivity, sites.toList())
                Policy.apply(this@ParentActivity)
                Toast.makeText(this@ParentActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Change PIN"
            setOnClickListener { PinUi.setNew(this@ParentActivity, cancelable = true) }
        })
        root.addView(Button(this).apply {
            text = "Remove all controls and exit"
            setOnClickListener {
                AlertDialog.Builder(this@ParentActivity)
                    .setTitle("Remove all controls?")
                    .setMessage("The tablet goes back to normal. You can uninstall KidLock afterwards.")
                    .setPositiveButton("Remove") { _, _ ->
                        try {
                            stopLockTask()
                        } catch (_: Exception) {
                        }
                        Policy.release(this@ParentActivity)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        renderSites()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        renderApps() // picks up apps installed during Parent mode
    }

    private fun updateStatus() {
        status.text = if (Prefs.parentActive(this)) {
            val until = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(Prefs.parentUntil(this)))
            "ON until $until. Installing and accounts are allowed."
        } else {
            "OFF. The tablet is locked."
        }
    }

    private fun renderApps() {
        appsBox.removeAllViews()
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { ri ->
                val pkg = ri.activityInfo.packageName
                appsBox.addView(CheckBox(this).apply {
                    text = "${ri.loadLabel(pm)}  ($pkg)"
                    isChecked = pkg in checked
                    setOnCheckedChangeListener { _, on -> if (on) checked.add(pkg) else checked.remove(pkg) }
                })
            }
    }

    private fun renderSites() {
        sitesBox.removeAllViews()
        if (sites.isEmpty()) {
            sitesBox.addView(TextView(this).apply { text = "No websites. Chrome will block everything." })
        }
        sites.forEachIndexed { index, (domain, on) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(CheckBox(this).apply {
                text = domain
                isChecked = on
                setOnCheckedChangeListener { _, v -> sites[index] = domain to v }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "X"
                setOnClickListener {
                    sites.removeAt(index)
                    renderSites()
                }
            })
            sitesBox.addView(row)
        }
    }
}
