package com.example.spark.engine

import com.example.spark.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

object NotificationStore {
    private const val MAX_STORED = 50
    private val notifications = ConcurrentLinkedDeque<NotificationItem>()

    private val _notificationFlow = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notificationFlow: StateFlow<List<NotificationItem>> = _notificationFlow.asStateFlow()

    private val _isAutoReplyEnabled = MutableStateFlow(false)
    val isAutoReplyEnabled: StateFlow<Boolean> = _isAutoReplyEnabled.asStateFlow()

    fun addNotification(item: NotificationItem) {
        notifications.addFirst(item)
        while (notifications.size > MAX_STORED) {
            notifications.removeLast()
        }
        _notificationFlow.value = notifications.toList()
    }

    fun getRecent(appNameFilter: String? = null, limit: Int = 10): List<NotificationItem> {
        val list = notifications.toList()
        if (appNameFilter.isNullOrBlank() || appNameFilter.equals("all", ignoreCase = true)) {
            return list.take(limit)
        }
        val lower = appNameFilter.lowercase()
        return list.filter {
            it.appName.lowercase().contains(lower) ||
            it.packageName.lowercase().contains(lower)
        }.take(limit)
    }

    fun setAutoReply(enabled: Boolean) {
        _isAutoReplyEnabled.value = enabled
    }

    fun clear() {
        notifications.clear()
        _notificationFlow.value = emptyList()
    }
}
