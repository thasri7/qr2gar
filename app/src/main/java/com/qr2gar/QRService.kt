package com.qr2gar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QRService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val captureInterval = 10000L // 10 seconds
    private val channelId = "qr2gar_channel"
    private lateinit var connectIQBridge: ConnectIQBridge

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("QR2Gar Running")
            .setContentText("Mirroring Darb QR to Garmin")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)
        connectIQBridge = ConnectIQBridge(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != -1 && data != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            startScreenCapture()
        }
        return START_STICKY
    }

    private fun startScreenCapture() {
        // TODO: Dynamically get screen size
        val width = 1080
        val height = 2400
        val dpi = 400
        imageReader = ImageReader.newInstance(width, height, 0x1, 2)
        mediaProjection?.createVirtualDisplay(
            "QR2GarDisplay",
            width, height, dpi,
            0,
            imageReader?.surface, null, handler
        )
        handler.post(captureRunnable)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            captureAndDecodeQR()
            handler.postDelayed(this, captureInterval)
        }
    }

    private fun captureAndDecodeQR() {
        val image = imageReader?.acquireLatestImage() ?: return
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        decodeQRCode(croppedBitmap)
    }

    private fun decodeQRCode(bitmap: Bitmap) {
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = MultiFormatReader().decode(binaryBitmap)
            Log.d("QRService", "QR Code: ${result.text}")
            connectIQBridge.sendQRToGarmin(result.text)
        } catch (e: Exception) {
            Log.d("QRService", "No QR code found: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "QR2Gar Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        imageReader?.close()
        mediaProjection?.stop()
        super.onDestroy()
    }
} 