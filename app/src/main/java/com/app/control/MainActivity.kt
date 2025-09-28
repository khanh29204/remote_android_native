package com.app.control

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.os.PowerManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val qrImageView = findViewById<ImageView>(R.id.qrImageView)
        val commandEditText = findViewById<EditText>(R.id.commandEditText)
        val executeButton = findViewById<Button>(R.id.executeButton)
        val outputEditText = findViewById<TextInputEditText>(R.id.txt_output)
        var fcmToken = ""
        // Lấy FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            token?.let {
                val qrBitmap = generateQRCode(it)
                fcmToken = it;
                qrImageView.setImageBitmap(qrBitmap)
            }
        }
        requestDisableBatteryOptimization(this)
        val serviceIntent = Intent(this, MyForegroundService::class.java)
        startService(serviceIntent)


        // Thực thi command nhập vào
        executeButton.setOnClickListener {
            val command = commandEditText.text.toString()
            if (command.isNotBlank()) {
                val output = executeCommandAsRoot(command)
                Log.d(TAG, "Command: $command")
                Log.d(TAG, "Output: $output")
                outputEditText.setText(output)
            }
        }
        // Copy fcmToken vào clipboard
        qrImageView.setOnClickListener { view ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("FCM Token", fcmToken)
            clipboard.setPrimaryClip(clip)
            Toast
                .makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Tạo QR code từ chuỗi đầu vào.
     */
    private fun generateQRCode(text: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] = if (bits.get(
                        x,
                        y
                    )
                ) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        return bitmap
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
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.forEachLine { output.append(it).append("\n") }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            e.message ?: "Error executing command"
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestDisableBatteryOptimization(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = "package:$packageName".toUri()
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("BatteryOptimization", "Không thể mở cài đặt tối ưu pin", e)
            }
        }
    }
}
