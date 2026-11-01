package app.derminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class TerminalService : Service() {
    private val engine get()=(application as DerminalApp).engine
    private var wake: PowerManager.WakeLock?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("sessions","终端会话",NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        startForeground(11,notification())
        engine.onSessionCountChanged={ update() }
        update();engine.prepare()
    }
    private fun notification(): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"sessions").setContentTitle("derminal").setContentText(if(engine.boot.value.ready) "${engine.tabs.count { !it.exited }} 个会话运行中" else "正在准备 Debian 13")
            .setSmallIcon(R.drawable.ic_launcher).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE).build()
    }
    private fun update() {
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) getSystemService(NotificationManager::class.java).notify(11,notification())
        if(engine.settings.value.wakeLock && engine.tabs.any { !it.exited }) {
            if(wake?.isHeld!=true) {
                wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"derminal:sessions").apply { setReferenceCounted(false);acquire() }
            }
        } else { if(wake?.isHeld==true) wake?.release();wake=null }
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int { engine.prepare();return START_NOT_STICKY }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onDestroy() { engine.onSessionCountChanged=null;engine.closeAll();if(wake?.isHeld==true)wake?.release();super.onDestroy() }
}
