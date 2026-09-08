package com.example.spark.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.spark.model.ContactItem
import java.net.URLEncoder

class TelephonyEngine(private val context: Context) {
    companion object {
        @Volatile
        var currentIncomingCaller: String? = null
        @Volatile
        var currentIncomingNumber: String? = null
    }

    fun searchContacts(query: String, limit: Int = 5): List<ContactItem> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return emptyList()
        }

        val results = mutableListOf<ContactItem>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext() && results.size < limit) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx) else ""
                    results.add(ContactItem(id, name, number))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun makeCall(recipient: String): Result<String> {
        var phoneNumber = recipient.trim()

        // If recipient contains alphabets, try searching contacts
        if (phoneNumber.any { it.isLetter() }) {
            val contacts = searchContacts(phoneNumber, limit = 1)
            if (contacts.isNotEmpty()) {
                phoneNumber = contacts.first().phoneNumber
            }
        }

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Could not resolve phone number for '$recipient'"))
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(intentAction).apply {
            data = Uri.parse("tel:$cleanNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            Result.success("Calling $cleanNumber")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendSms(recipient: String, message: String): Result<String> {
        var phoneNumber = recipient.trim()

        if (phoneNumber.any { it.isLetter() }) {
            val contacts = searchContacts(phoneNumber, limit = 1)
            if (contacts.isNotEmpty()) {
                phoneNumber = contacts.first().phoneNumber
            }
        }

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasSmsPermission && cleanNumber.isNotBlank()) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                Result.success("SMS sent to $cleanNumber: \"$message\"")
            } catch (e: Exception) {
                // Fallback to composer intent
                launchSmsIntent(cleanNumber, message)
            }
        } else {
            launchSmsIntent(cleanNumber, message)
        }
    }

    private fun launchSmsIntent(number: String, message: String): Result<String> {
        return try {
            val uri = if (number.isNotBlank()) Uri.parse("smsto:$number") else Uri.parse("sms:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Opened SMS composer for $number")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendWhatsApp(contactName: String, message: String, directNumber: String?): Result<String> {
        var targetNumber = directNumber?.replace(Regex("[^0-9]"), "") ?: ""

        if (targetNumber.isBlank() && contactName.isNotBlank()) {
            val contacts = searchContacts(contactName, limit = 1)
            if (contacts.isNotEmpty()) {
                targetNumber = contacts.first().phoneNumber.replace(Regex("[^0-9]"), "")
            }
        }

        return try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uri = if (targetNumber.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$targetNumber&text=$encodedMessage")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                context.startActivity(intent)
                Result.success("Dispatched WhatsApp message to $contactName ($targetNumber)")
            } catch (noWhatsApp: Exception) {
                // Fallback to open browser or standard send
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                Result.success("Opened WhatsApp link for $contactName")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun controlCall(action: String): Result<String> {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        return when (action.lowercase().trim()) {
            "announce", "who", "who_is_calling", "check" -> {
                val caller = currentIncomingCaller ?: currentIncomingNumber ?: "No active incoming call"
                Result.success("Current caller: $caller")
            }
            "answer", "accept", "pick_up" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        try {
                            telecomManager.acceptRingingCall()
                            Result.success("Answered incoming call")
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    } else {
                        Result.failure(IllegalStateException("ANSWER_PHONE_CALLS permission not granted"))
                    }
                } else {
                    Result.failure(UnsupportedOperationException("Answering calls requires Android 8.0+"))
                }
            }
            "reject", "decline", "end", "hang_up" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager != null) {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        try {
                            telecomManager.endCall()
                            Result.success("Rejected incoming call")
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    } else {
                        Result.failure(IllegalStateException("Call management permission not granted"))
                    }
                } else {
                    Result.failure(UnsupportedOperationException("Rejecting calls requires Android 9.0+"))
                }
            }
            else -> Result.failure(IllegalArgumentException("Unknown call action '$action'"))
        }
    }
}
