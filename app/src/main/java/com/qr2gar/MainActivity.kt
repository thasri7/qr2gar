package com.qr2gar

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val REQUEST_MEDIA_PROJECTION = 1001
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val button = Button(this)
        button.text = "Start QR Mirroring"
        button.setOnClickListener {
            if (!isServiceRunning) {
                requestScreenCapturePermission()
            } else {
                stopQRService()
            }
        }
        setContentView(button)
    }

    private fun requestScreenCapturePermission() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startQRService(resultCode, data)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startQRService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, QRService::class.java)
        serviceIntent.putExtra("resultCode", resultCode)
        serviceIntent.putExtra("data", data)
        startForegroundService(serviceIntent)
        isServiceRunning = true
    }

    private fun stopQRService() {
        val serviceIntent = Intent(this, QRService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
    }
} 