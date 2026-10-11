package com.xayah.core.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo

object NotificationUtil {
    const val PROCESSING_ID = 1
    const val APP_REFRESH_ID = 2
    const val APP_SIZE_ID = 3
    const val RESULT_ID = 7

    enum class Channel(val id: String, val nameRes: Int, val descriptionRes: Int, val importance: Int) {
        BackupRestore("ForegroundServiceChannel", R.string.notification_channel_tasks, R.string.notification_channel_tasks_desc, NotificationManager.IMPORTANCE_LOW),
        AppUpdates("app_updates", R.string.notification_channel_updates, R.string.notification_channel_updates_desc, NotificationManager.IMPORTANCE_LOW),
        Results("task_results", R.string.notification_channel_results, R.string.notification_channel_results_desc, NotificationManager.IMPORTANCE_DEFAULT),
    }

    fun checkPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun requestPermissions(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(context.getActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            runCatching {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.grant_ntfy_perm_manually), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localizedContext = ContextCompat.getContextForLanguage(context)
            val channels = Channel.entries.map { channel ->
                NotificationChannel(channel.id, localizedContext.getString(channel.nameRes), channel.importance).apply {
                    description = localizedContext.getString(channel.descriptionRes)
                }
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
        }
    }

    fun getForegroundNotification(context: Context) = getProgressNotificationBuilder(context, Channel.BackupRestore)
        .setContentTitle(ContextCompat.getString(context, R.string.app_name))
        .setContentText(ContextCompat.getString(context, R.string.necessary_preparations))
        .setProgress(0, 0, true)
        .build()

    fun getProgressNotificationBuilder(context: Context, channel: Channel = Channel.AppUpdates): NotificationCompat.Builder {
        createChannels(context)
        val intent = Intent(context, ActivityUtil.classMainActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setForegroundServiceBehavior(
                if (channel == Channel.BackupRestore) NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE else NotificationCompat.FOREGROUND_SERVICE_DEFAULT
            )
    }

    fun notify(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        max: Int = 0,
        progress: Int = 0,
        indeterminate: Boolean = max <= 0,
    ) {
        if (checkPermission(context)) {
            val notification = updateProgress(builder, title, content, max, progress, indeterminate)
                .setSubText(if (max > 0) ContextCompat.getContextForLanguage(context).getString(R.string.notification_progress_count, progress.coerceIn(0, max), max) else null)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(PROCESSING_ID, notification)
        }
    }

    fun notifyResult(context: Context, title: String, content: String) {
        if (checkPermission(context)) {
            val notification = getProgressNotificationBuilder(context, Channel.Results)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(RESULT_ID, notification)
        }
    }

    fun cancel(context: Context, notificationId: Int = PROCESSING_ID) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    fun createForegroundInfo(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        max: Int = 0,
        progress: Int = 0,
        indeterminate: Boolean = max <= 0,
        notificationId: Int = APP_REFRESH_ID,
    ): ForegroundInfo {
        val notification = updateProgress(builder, title, content, max, progress, indeterminate)
            .setSubText(if (max > 0) ContextCompat.getContextForLanguage(context).getString(R.string.notification_progress_percent, (progress.toLong() * 100 / max).coerceIn(0, 100)) else null)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateProgress(
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        max: Int,
        progress: Int,
        indeterminate: Boolean,
    ): NotificationCompat.Builder = builder
        .setContentTitle(title)
        .setContentText(content)
        .setProgress(max.coerceAtLeast(0), progress.coerceIn(0, max.coerceAtLeast(0)), indeterminate)
}
