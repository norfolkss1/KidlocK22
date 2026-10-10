package com.kidlock

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

object PinUi {

    private fun field(a: Activity, hint: String) = EditText(a).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        this.hint = hint
    }

    fun ask(a: Activity, onOk: () -> Unit) {
        if (System.currentTimeMillis() < Prefs.lockedUntil(a)) {
            Toast.makeText(a, "Too many wrong tries. Try again later.", Toast.LENGTH_LONG).show()
            return
        }
        val et = field(a, "Parent PIN")
        AlertDialog.Builder(a)
            .setTitle("Parent PIN")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                if (Prefs.tryPin(a, et.text.toString())) onOk()
                else Toast.makeText(a, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun setNew(a: Activity, cancelable: Boolean, onDone: () -> Unit = {}) {
        val et = field(a, "At least 6 digits")
        val b = AlertDialog.Builder(a)
            .setTitle("Set parent PIN")
            .setView(et)
            .setCancelable(cancelable)
            .setPositiveButton("Save") { _, _ ->
                val pin = et.text.toString()
                if (pin.length >= 6) {
                    Prefs.setPin(a, pin)
                    onDone()
                } else {
                    Toast.makeText(a, "PIN must be at least 6 digits", Toast.LENGTH_SHORT).show()
                    setNew(a, cancelable, onDone)
                }
            }
        if (cancelable) b.setNegativeButton("Cancel", null)
        b.show()
    }
}
