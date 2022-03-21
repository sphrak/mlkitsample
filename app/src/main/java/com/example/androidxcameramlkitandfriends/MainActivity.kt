package com.example.androidxcameramlkitandfriends

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlin.math.max

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val reticle: ImageView get() = findViewById(R.id.imageView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isCameraPermissionGranted()) {
            // startCamera
            val camera = findViewById<CameraPreview>(R.id.camera).apply {
                init()
                barcodeFlow
                    .filter { it.isNotEmpty() }
                    .map {

                        val item: CameraPreview.QrCode = it.first()

                        val scaleX: Float = width / item.width
                        val scaleY: Float = height / item.height

                        reticle.scaleX = item.barcode.boundingBox!!.width() / 196f
                        reticle.scaleY = item.barcode.boundingBox!!.width() / 196f

                        reticle.pivotX = 0F
                        reticle.pivotY = 0F

                        val scale = max(scaleX, scaleY)

                        val xOffset = (item.width * scale - width) / 2
                        val yOffset = (item.height * scale - height) / 2

                        reticle.x = scale * item.barcode.boundingBox!!.left.toFloat() - xOffset
                        reticle.y = (scale * item.barcode.boundingBox!!.top.toFloat()) - yOffset

                    }.launchIn(lifecycleScope)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_CAMERA_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                // start camera
            } else {
                println("permission: camera not granted fail")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSION_CAMERA_REQUEST = 1
    }
}