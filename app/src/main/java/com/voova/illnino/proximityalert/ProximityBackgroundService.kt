package com.voova.illnino.proximityalert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log

class ProximityBackgroundService: Service() {
    companion object {
        const val NOTIFICATION_ID_PROXIMITY_SERVICE = 99
        lateinit var getInstance: ProximityBackgroundService
    }
    val TAG: String = "ProximityService"

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getInstance = this
        startBackgroundProximityService()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelf()
        Log.d(TAG, "onDestroy!!")
    }

    private fun startBackgroundProximityService() {
        val notification = NotificationCompat.Builder(MainActivity.getInstance, "proximity_notification_service_channel").apply {
            this.setContentTitle("ProximityService")
            this.setContentText("Proximity alert is running...")
            this.setTicker("Proximity service started")
        }.build()

        startForeground(NOTIFICATION_ID_PROXIMITY_SERVICE, notification)
    }
}