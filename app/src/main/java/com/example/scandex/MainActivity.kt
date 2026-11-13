package com.example.scandex

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private var imageCapture: ImageCapture? = null

    // Permisos necesarios: cámara siempre; almacenamiento solo en Android 9 o menor
    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                startCamera()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = findViewById(R.id.cameraPreview)
        btnCapture = findViewById(R.id.btnCapture)
        btnCapture.setOnClickListener { takePhoto() }

        if (allPermissionsGranted()) startCamera() else permissionLauncher.launch(requiredPermissions)
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Inicia la vista previa y prepara la captura usando CameraX. */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar la cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Toma una foto y la guarda en la galería (Pictures/ScanDex). */
    private fun takePhoto() {
        val capture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScanDex")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        btnCapture.isEnabled = false
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    btnCapture.isEnabled = true
                    Toast.makeText(this@MainActivity, "Foto guardada en galería", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Foto guardada: ${output.savedUri}")
                }

                override fun onError(exc: ImageCaptureException) {
                    btnCapture.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error: ${exc.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error al tomar foto", exc)
                }
            }
        )
    }

    companion object {
        private const val TAG = "ScanDex"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
