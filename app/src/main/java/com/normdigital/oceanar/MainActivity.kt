package com.normdigital.oceanar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.normdigital.oceanar.webrtc.WebRTCManager
import org.webrtc.SurfaceViewRenderer

class MainActivity : ComponentActivity() {

    private lateinit var webRTCManager: WebRTCManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var localSurfaceView: SurfaceViewRenderer
    private lateinit var remoteSurfaceView: SurfaceViewRenderer

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            startWebRTC()
        } else {
            Toast.makeText(this, "Kamera ve mikrofon izinleri gerekli.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase ve WebRTCManager'ı başlatma
        firestore = FirebaseFirestore.getInstance()
        webRTCManager = WebRTCManager(this, firestore)
        webRTCManager.initialize()

        // SurfaceView bileşenlerini başlatma
        localSurfaceView = findViewById(R.id.localSurfaceView)
        remoteSurfaceView = findViewById(R.id.remoteSurfaceView)
        localSurfaceView.init(webRTCManager.eglBaseContext, null)
        remoteSurfaceView.init(webRTCManager.eglBaseContext, null)

        // Buton olaylarını tanımlama
        findViewById<Button>(R.id.startWebcamButton).setOnClickListener {
            checkAndRequestPermissions()
        }

        findViewById<Button>(R.id.callButton).setOnClickListener {
            try {
                webRTCManager.createOffer()
                Toast.makeText(this, "Çağrı başlatıldı.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Çağrı başlatılırken hata oluştu: ${e.message}")
                Toast.makeText(this, "Çağrı başlatılamadı.", Toast.LENGTH_SHORT).show()
            }
        }

        val roomId = findViewById<EditText>(R.id.roomIdInput).text.toString()
        if (roomId.isNotEmpty()) {
            webRTCManager.answerCall(roomId)  // Doğru odadaki verileri dinle
        } else {
            Toast.makeText(this, "Room ID boş olamaz.", Toast.LENGTH_SHORT).show()
        }


        findViewById<Button>(R.id.answerButton).setOnClickListener {
            val roomId = findViewById<EditText>(R.id.roomIdInput).text.toString()
            if (roomId.isNotEmpty()) {
                webRTCManager.answerCall(roomId)
            } else {
                Toast.makeText(this, "Room ID boş olamaz.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.hangupButton).setOnClickListener {
            webRTCManager.hangup()
        }
    }

    private fun checkAndRequestPermissions() {
        // Gerekli izinleri kontrol et
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startWebRTC()
        } else {
            permissionsLauncher.launch(requiredPermissions)
        }
    }

    private fun startWebRTC() {
        try {
            webRTCManager.startWebcam(localSurfaceView, remoteSurfaceView)
            Toast.makeText(this, "Kamera ve mikrofon başlatıldı.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "WebRTC başlatılırken hata oluştu: ${e.message}")
            Toast.makeText(this, "WebRTC başlatılırken hata oluştu.", Toast.LENGTH_SHORT).show()
        }
    }
}
