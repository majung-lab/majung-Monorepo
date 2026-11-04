package com.majung.vehicle.core.notice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.majung.vehicle.MainActivity
import com.majung.vehicle.R

/**
 * 내비가 앞에 있어도 마중으로 바로 돌아올 수 있게 하는 알림.
 * - 상시 알림: 방문이 진행되는 동안 떠 있고, 누르면 마중 화면으로 돌아온다.
 *   포그라운드 서비스로 띄워서 앱이 뒤에 있어도 위치 공유와 서버 동기화가 끊기지 않는다.
 * - 이벤트 알림: 가족이 장소를 바꾸자고 하거나 나오는 중일 때 한 번 알린다.
 */
interface VisitNotifier {
    fun show(title: String, text: String)
    fun alert(key: String, title: String, text: String)
    /** 마중 화면을 앞으로 띄운다. 시스템이 막으면 누르면 열리는 알림으로 대신한다 */
    fun bringToFront(title: String, text: String)
    fun stop()
}

class AndroidVisitNotifier(context: Context) : VisitNotifier {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val alerted = this.context.getSharedPreferences("notice", 0)
    private var running = false

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "진행 중인 방문", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_EVENT, "가족 응답", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun show(title: String, text: String) {
        OverlayBanner.update(context, title, text)
        if (!running) {
            try {
                context.startForegroundService(Intent(context, VisitNoticeService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text))
                running = true
                return
            } catch (_: Exception) {
                // 뒤에 있을 때는 서비스를 새로 시작할 수 없다 → 알림만 띄운다
            }
        }
        manager.notify(ID_ONGOING, ongoing(context, title, text))
    }

    override fun alert(key: String, title: String, text: String) {
        if (alerted.getString("last", null) == key) return
        alerted.edit().putString("last", key).apply()
        if (VisitNoticeService.appVisible) return // 화면에 이미 보이는 내용은 알리지 않는다
        // 내비 위에 앱 디자인 카드를 띄울 수 있으면 시스템 알림 팝업은 띄우지 않는다
        if (OverlayBanner.canShow(context)) return OverlayBanner.alert(context, title, text)
        manager.notify(ID_EVENT, Notification.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(openApp(context))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build())
    }

    override fun bringToFront(title: String, text: String) {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(open) }
        if (OverlayBanner.canShow(context)) return OverlayBanner.alert(context, title, text)
        manager.notify(ID_EVENT, Notification.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(openApp(context), true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build())
    }

    override fun stop() {
        OverlayBanner.stop(context)
        if (running) context.stopService(Intent(context, VisitNoticeService::class.java))
        running = false
        manager.cancel(ID_ONGOING)
        manager.cancel(ID_EVENT)
    }

    companion object {
        const val CHANNEL_ONGOING = "visit"
        const val CHANNEL_EVENT = "visit_event"
        const val ID_ONGOING = 1
        const val ID_EVENT = 2
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun ongoing(context: Context, title: String, text: String): Notification = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()
    }
}

class VisitNoticeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notice = AndroidVisitNotifier.ongoing(
            this,
            intent?.getStringExtra(AndroidVisitNotifier.EXTRA_TITLE) ?: "마중",
            intent?.getStringExtra(AndroidVisitNotifier.EXTRA_TEXT) ?: "눌러서 마중 열기",
        )
        try {
            startForeground(AndroidVisitNotifier.ID_ONGOING, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: Exception) {
            // 위치 권한이 없으면 위치 유형으로 시작할 수 없다 → 서비스 없이 알림만 둔다
            getSystemService(NotificationManager::class.java).notify(AndroidVisitNotifier.ID_ONGOING, notice)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        /** MainActivity가 화면에 보이는지. 보이면 이벤트 알림을 띄우지 않는다 */
        @Volatile var appVisible = false

        /** 마중이 앞으로 나오면 남아 있는 이벤트 알림은 치운다 */
        fun onAppShown(context: Context) {
            appVisible = true
            context.getSystemService(NotificationManager::class.java).cancel(AndroidVisitNotifier.ID_EVENT)
        }
    }
}
