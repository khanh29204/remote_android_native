package com.app.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot completed - starting background service")
            // Nếu cần, khởi chạy một service nền để nhận command FCM khi app không được mở
            val serviceIntent = Intent(context, MyFirebaseMessagingService::class.java)
            context.startService(serviceIntent)
        }
    }
}
