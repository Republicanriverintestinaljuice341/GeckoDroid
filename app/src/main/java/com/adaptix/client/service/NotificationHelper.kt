package com.adaptix.client.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.adaptix.client.MainActivity
import com.adaptix.client.R

object NotificationHelper {

    private const val GROUP_AGENTS = "adaptix_agents"
    private const val GROUP_ALERTS = "adaptix_alerts"
    private const val SUMMARY_ID_AGENTS = 9000
    private const val SUMMARY_ID_ALERTS = 9001

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNewAgentNotification(context: Context, agentName: String, os: String, ip: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = launchIntent(context)

        val notification = NotificationCompat.Builder(context, AdaptixService.CHANNEL_ID_ALERTS)
            .setContentTitle("New Agent Connected")
            .setContentText("$agentName ($os) — $ip")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_AGENTS)
            .build()

        nm.notify(AdaptixService.nextNotificationId(), notification)

        // Summary notification for grouping
        val summary = NotificationCompat.Builder(context, AdaptixService.CHANNEL_ID_ALERTS)
            .setContentTitle("Adaptix Agents")
            .setContentText("New agent connections")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setGroup(GROUP_AGENTS)
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText("Agent notifications"))
            .build()

        nm.notify(SUMMARY_ID_AGENTS, summary)
    }

    fun showAlertNotification(context: Context, title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = launchIntent(context)

        val notification = NotificationCompat.Builder(context, AdaptixService.CHANNEL_ID_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_ALERTS)
            .build()

        nm.notify(AdaptixService.nextNotificationId(), notification)

        // Summary notification for grouping
        val summary = NotificationCompat.Builder(context, AdaptixService.CHANNEL_ID_ALERTS)
            .setContentTitle("Adaptix")
            .setContentText("Alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setGroup(GROUP_ALERTS)
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText("Alert notifications"))
            .build()

        nm.notify(SUMMARY_ID_ALERTS, summary)
    }

    fun showTaskCompleteNotification(context: Context, agentName: String, command: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = launchIntent(context)

        val notification = NotificationCompat.Builder(context, AdaptixService.CHANNEL_ID_ALERTS)
            .setContentTitle("Task Complete")
            .setContentText("$agentName: $command")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_ALERTS)
            .build()

        nm.notify(AdaptixService.nextNotificationId(), notification)
    }
}
