package com.example.scandex

import android.Manifest
import android.content.ContentValues
import android.content.Intent
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
import android.graphics.Bitmap
import android.widget.TextView

class   MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private var imageCapture: ImageCapture? = null

    private var pokemonEmbedder: PokemonEmbedder? = null

    private lateinit var tvTitle: TextView

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
        tvTitle = findViewById(R.id.tvTitle)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        // Cargar el motor en segundo plano para no congelar la pantalla
        Thread {
            val motor = PokemonEmbedder(this)
            val total = motor.cargarSecuenciasDesdeJson("secuencias_pokedex.json")
            pokemonEmbedder = motor
            runOnUiThread { tvTitle.text = "ScanDex lista ($total Pokémon)" }
        }.start()
        btnCapture.setOnClickListener { scanAndCapturePokemon() }

        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, pokemonlibrary::class.java))
        }
        findViewById<Button>(R.id.button).setOnClickListener {
            startActivity(Intent(this, teambuilder::class.java))
        }

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
    /** Identifica el Pokémon del visor, lo desbloquea, abre su ficha */
    private fun scanAndCapturePokemon() {
        val motor = pokemonEmbedder
        val bitmap = previewView.bitmap
        if (motor != null && bitmap != null) {
            val lado = (minOf(bitmap.width, bitmap.height) * 0.65f).toInt()
            val recorte = Bitmap.createBitmap(
                bitmap, (bitmap.width - lado) / 2, (bitmap.height - lado) / 2, lado, lado
            )
            btnCapture.isEnabled = false
            Thread {
                val res = motor.identificarPokemon(recorte)
                Log.d(TAG, "scan → ${res.nombrePokemon} sim=${"%.3f".format(res.similitudDirecta)} certeza=${"%.3f".format(res.certeza)} | ${res.segundoLugar} | detectado=${res.detectado}")
                if (res.detectado && res.pokemonId > 0) {
                    PokemonEncounterStore.registerEncounter(this, res.pokemonId)
                }
                runOnUiThread {
                    btnCapture.isEnabled = true
                    if (res.detectado && res.pokemonId > 0) {
                        val pct = (res.similitudDirecta * 100).toInt()
                        tvTitle.text = "¡${res.nombrePokemon} ($pct%)!"
                        startActivity(
                            Intent(this, pokeinfo::class.java)
                                .putExtra("pokemonId", res.pokemonId)
                        )
                    } else {
                        tvTitle.text = res.nombrePokemon   // "No identificado": no abre nada
                    }
                }
            }.start()
        }
    }
    companion object {
        private const val TAG = "ScanDex"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
