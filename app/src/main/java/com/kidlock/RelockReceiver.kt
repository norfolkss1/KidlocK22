package com.kidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when Parent mode runs out: re-applies the lockdown and goes back to KidLock. */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.parentActive(context)) {
            Policy.scheduleRelock(context)
            return
        }
        Policy.apply(context)
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
