package com.example.spark.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.RemoteInput
import com.example.spark.engine.NotificationStore
import com.example.spark.model.NotificationItem

class SparkNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        // Ignore our own package
        if (pkg == packageName) return

        val extras = sbn.notification.extras ?: Bundle()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast(".")
        }

        val item = NotificationItem(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            postTime = sbn.postTime
        )
        NotificationStore.addNotification(item)

        // Handle auto-reply if enabled
        if (NotificationStore.isAutoReplyEnabled.value && (pkg.contains("whatsapp") || pkg.contains("messaging") || pkg.contains("sms"))) {
            attemptAutoReply(sbn)
        }
    }

    private fun attemptAutoReply(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val actions = sbn.notification.actions ?: return
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (input in remoteInputs) {
                    if (input.allowFreeFormInput) {
                        try {
                            val intent = Intent()
                            val bundle = Bundle()
                            bundle.putCharSequence(input.resultKey, "Spark Assistant: Auto-reply active.")
                            RemoteInput.addResultsToIntent(
                                arrayOf(androidx.core.app.RemoteInput.Builder(input.resultKey).build()),
                                intent,
                                bundle
                            )
                            action.actionIntent.send(this, 0, intent)
                            return
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
}
