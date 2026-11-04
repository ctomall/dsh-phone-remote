package com.ila3.dshnative.protocol

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ila3.dshnative.MainActivity
import com.ila3.dshnative.R

/** Notification channels and taps for background agent activity. */
class NativeNotifications(private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTION, "需要处理", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "需要你回答或批准的 Agent 请求"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVITY, "任务动态", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "任务完成、执行失败与进度提醒"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_KEEPALIVE, "后台连接", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持与 DSH 的长连接（前台服务）"
            },
        )
    }

    fun question(sessionId: String, sessionTitle: String, text: String) =
        post(
            CHANNEL_ACTION,
            "需要你的回答",
            sessionTitle.ifBlank { "DSH" },
            text,
            sessionId,
            1000 + sessionId.hashCode(),
            inboxAction = true,
        )

    fun turnDone(sessionId: String, sessionTitle: String, text: String) =
        post(CHANNEL_ACTIVITY, "任务已完成", sessionTitle.ifBlank { "DSH" }, text, sessionId, 2000 + sessionId.hashCode())

    fun error(sessionId: String, sessionTitle: String, text: String) =
        post(CHANNEL_ACTIVITY, "执行出错", sessionTitle.ifBlank { "DSH" }, text, sessionId, 3000 + sessionId.hashCode())

    private fun post(
        channel: String,
        title: String,
        subject: String,
        text: String,
        sessionId: String,
        id: Int,
        inboxAction: Boolean = false,
    ) {
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SESSION, sessionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(subject)
            .setSubText(title)
            .setContentText(text.lineSequence().firstOrNull()?.take(120) ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(600)))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (inboxAction) {
            builder.addAction(0, "打开处理", inboxPendingIntent(id + 5000))
        }
        val notification = builder.build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun inboxPendingIntent(id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TAB, "inbox")
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun allowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    companion object {
        const val CHANNEL_ACTION = "dsh_action"
        const val CHANNEL_ACTIVITY = "dsh_activity"
        const val CHANNEL_KEEPALIVE = "dsh_keepalive"
        const val EXTRA_SESSION = "sessionId"
        const val EXTRA_TAB = "tab"
    }
}
