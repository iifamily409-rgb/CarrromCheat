package com.example.carromvision

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.carromvision.service.ScreenCaptureService
import com.example.carromvision.ui.theme.CarromVisionTheme

class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            showMessage("Screen capture permission was not granted")
            return@registerForActivityResult
        }
        if (!Settings.canDrawOverlays(this)) {
            showMessage("Enable 'Display over other apps' before starting capture")
            openOverlaySettings()
            return@registerForActivityResult
        }

        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to start MediaProjection service", error)
            showMessage("Unable to start screen capture on this device")
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(this)) {
            showMessage("Overlay permission is required for live aim guidance")
            return@registerForActivityResult
        }
        checkAndStartCapture()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // A denied notification permission must not block screen capture.
        requestOverlayAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarromVisionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartAimAssist = { requestPermissionsAndStart() }
                    )
                }
            }
        }
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestOverlayAndStart()
        }
    }

    private fun requestOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            showMessage("Allow CarromVision to display over other apps")
            openOverlaySettings()
        } else {
            checkAndStartCapture()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            overlayPermissionLauncher.launch(intent)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to open overlay permission settings", error)
            showMessage("Open Settings > Apps > Special access > Display over other apps")
        }
    }

    private fun checkAndStartCapture() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayAndStart()
            return
        }

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to request MediaProjection consent", error)
            showMessage("Screen capture is unavailable on this device")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, onStartAimAssist: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "CarromVision Aim Assist", modifier = Modifier.padding(16.dp))
        Button(onClick = onStartAimAssist) {
            Text(text = "Start Aim Assist")
        }
    }
}
