package com.adaptix.client.util

import java.text.SimpleDateFormat
import java.util.*

fun formatTimestamp(epoch: Long): String {
    if (epoch == 0L) return "N/A"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epoch * 1000))
}

fun formatRelativeTime(epoch: Long): String {
    if (epoch == 0L) return "Never"
    val now = System.currentTimeMillis() / 1000
    val diff = now - epoch
    return when {
        diff < 0 -> "just now"
        diff < 60 -> "${diff}s ago"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
