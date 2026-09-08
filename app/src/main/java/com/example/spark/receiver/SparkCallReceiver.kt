package com.example.spark.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.example.spark.engine.TelephonyEngine

class SparkCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                TelephonyEngine.currentIncomingNumber = incomingNumber

                if (!incomingNumber.isNullOrBlank()) {
                    val callerName = resolveContactName(context, incomingNumber)
                    TelephonyEngine.currentIncomingCaller = callerName ?: incomingNumber
                } else {
                    TelephonyEngine.currentIncomingCaller = "Private / Unknown Caller"
                }
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                TelephonyEngine.currentIncomingCaller = null
                TelephonyEngine.currentIncomingNumber = null
            }
        }
    }

    private fun resolveContactName(context: Context, number: String): String? {
        return try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(number)
                .build()

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
