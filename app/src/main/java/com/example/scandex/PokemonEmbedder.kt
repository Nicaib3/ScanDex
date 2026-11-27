package com.example.scandex

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.os.SystemClock
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

data class ResultadoDeteccion(
    val nombrePokemon: String,
    val pokemonId: Int,
    val certeza: Float,
    val similitudDirecta: Float,
    val segundoLugar: String,
    val detectado: Boolean
)

class PokemonEmbedder(private val context: Context) {

    private val interprete: Interpreter

    private val entrada: ByteBuffer = ByteBuffer.allocateDirect(LADO * LADO * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val pixeles = IntArray(LADO * LADO)
    private val salida = Array(1) { FloatArray(DIMENSION) }
    private var mediaGlobalForma: FloatArray? = null
    // Nombre de la clase -> Lista de sus K sub-estados de imagen (512 valores, incluye 000_fondo)
    private val baseDeDatosSubEstados = mutableMapOf<String, List<FloatArray>>()
    private val textoPorClase = mutableMapOf<String, FloatArray>()
    private val textoNegativas = mutableListOf<FloatArray>()
    private var escalaLogit = 64.3f
    private var pesoTexto = 0.5f
    private var tauImagen = 0.07f

    companion object {
        private const val TAG = "ScanDex"
        private const val MODELO = "mobileclip2_s0_imagen_int8.tflite"
        private const val LADO = 256
        private const val DIMENSION = 512
        private const val CLASE_FONDO = "000_fondo"
    }

    init {
        val opciones = Interpreter.Options().setNumThreads(4)
        interprete = Interpreter(cargarModelo(MODELO), opciones)
    }

    /** Mapea el .tflite de assets a memoria sin copiarlo (requiere noCompress += "tflite"). */
    private fun cargarModelo(nombre: String): MappedByteBuffer =
        context.assets.openFd(nombre).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { flujo ->
                flujo.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }

    /** Foto -> vector de 512 valores con norma 1. */
    fun extraerVector(bitmap: Bitmap): FloatArray {
        var imagen = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        // Reducir a la mitad mientras sobre (promedia píxeles, como hace PIL al reducir)
        while (minOf(imagen.width, imagen.height) >= LADO * 2) {
            imagen = Bitmap.createScaledBitmap(imagen, imagen.width / 2, imagen.height / 2, true)
        }
        val escalada = Bitmap.createScaledBitmap(imagen, LADO, LADO, true)
        escalada.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)

        // NHWC: fila por fila, píxel por píxel, R G B en 0..1
        entrada.rewind()
        for (p in pixeles) {
            entrada.putFloat(((p shr 16) and 0xFF) / 255f)
            entrada.putFloat(((p shr 8) and 0xFF) / 255f)
            entrada.putFloat((p and 0xFF) / 255f)
        }
        entrada.rewind()

        interprete.run(entrada, salida)
        return normalizarL2(salida[0])
    }

    private fun normalizarL2(arreglo: FloatArray): FloatArray {
        var sumaCuadrados = 0f
        for (v in arreglo) {
            sumaCuadrados += v * v
        }
        val norma = sqrt(sumaCuadrados)
        val resultado = FloatArray(arreglo.size)
        if (norma > 1e-8f) {
            for (i in arreglo.indices) {
                resultado[i] = arreglo[i] / norma
            }
        }
        return resultado
    }

    private fun centrarYNormalizarForma(secuenciaForma: FloatArray): FloatArray {
        val media = mediaGlobalForma ?: return secuenciaForma
        val n = minOf(secuenciaForma.size, media.size)
        val resultado = FloatArray(n)
        for (i in 0 until n) {
            resultado[i] = secuenciaForma[i] - media[i]
        }
        return normalizarL2(resultado)
    }

    fun cargarSecuenciasDesdeJson(archivoJson: String = "secuencias_pokedex.json"): Int {
        val jsonTexto = context.assets.open(archivoJson).bufferedReader().use { it.readText() }
        val root = JSONObject(jsonTexto)

        baseDeDatosSubEstados.clear()
        mediaGlobalForma = null
        textoPorClase.clear()
        textoNegativas.clear()

        if (root.optInt("version", 1) < 2 || !root.has("texto")) {
            Log.e(TAG, "$archivoJson tiene el formato viejo (MobileNet). Genera uno con generar_secuencias_clip.py")
            return 0
        }

        escalaLogit = root.getDouble("logit_scale").toFloat()
        pesoTexto = root.optDouble("peso_texto", 0.5).toFloat()
        tauImagen = root.optDouble("tau_imagen", 0.07).toFloat()

        val texto = root.getJSONObject("texto")
        for (clase in texto.keys()) {
            textoPorClase[clase] = aFloatArray(texto.getJSONArray(clase))
        }

        val negativas = root.getJSONArray("texto_negativas")
        for (i in 0 until negativas.length()) {
            textoNegativas.add(aFloatArray(negativas.getJSONArray(i)))
        }

        if (root.has("media_global") && root.has("pokemones")) {
            val arrMedia = root.getJSONArray("media_global")
            mediaGlobalForma = FloatArray(arrMedia.length()) { i ->
                arrMedia.getDouble(i).toFloat()
            }

            val objPokemones = root.getJSONObject("pokemones")
            val nombres = objPokemones.keys()
            while (nombres.hasNext()) {
                val nombre = nombres.next()
                val listaEstadosJson = objPokemones.getJSONArray(nombre)
                val subEstados = mutableListOf<FloatArray>()

                for (j in 0 until listaEstadosJson.length()) {
                    val arregloEstado = listaEstadosJson.getJSONArray(j)
                    val secuencia = FloatArray(arregloEstado.length()) { i ->
                        arregloEstado.getDouble(i).toFloat()
                    }
                    subEstados.add(secuencia)
                }
                baseDeDatosSubEstados[nombre] = subEstados
            }
        }

        return textoPorClase.size   // solo Pokémon (000_fondo no cuenta)
    }

    @Synchronized   // el intérprete no se puede usar desde dos hilos a la vez
    fun identificarPokemon(foto: Bitmap, umbralMinimo: Float = 0.0f): ResultadoDeteccion {
        if (baseDeDatosSubEstados.isEmpty()) {
            return ResultadoDeteccion("JSON de secuencias vacío", 0, 0f, 0f, "", false)
        }

        val inicio = SystemClock.elapsedRealtime()
        val vector = extraerVector(foto)
        val pTexto = probabilidadesTexto(vector)
        val pImagen = probabilidadesImagen(vector)

        val clases = textoPorClase.keys + baseDeDatosSubEstados.keys + CLASE_FONDO
        val puntajesPorPokemon = clases.map { clase ->
            clase to pesoTexto * (pTexto[clase] ?: 0f) + (1f - pesoTexto) * (pImagen[clase] ?: 0f)
        }.sortedByDescending { it.second }

        val (mejorPokemon, mejorSimilitud) = puntajesPorPokemon[0]
        val segundoPar = puntajesPorPokemon.getOrNull(1)
        val certezaContrastiva = mejorSimilitud - (segundoPar?.second ?: 0f)   // margen 1° - 2°

        Log.d(
            TAG, "mejor=$mejorPokemon p=%.3f texto=%.3f imagen=%.3f | 2do=%s %.3f | %d ms".format(
                mejorSimilitud, pTexto[mejorPokemon] ?: 0f, pImagen[mejorPokemon] ?: 0f,
                segundoPar?.first ?: "-", segundoPar?.second ?: 0f,
                SystemClock.elapsedRealtime() - inicio
            )
        )

        val textoSegundo = if (segundoPar != null) {
            "2° lugar: ${segundoPar.first} (${(segundoPar.second * 100).toInt()}%)"
        } else {
            ""
        }

        val esDetectado = mejorSimilitud >= umbralMinimo && !mejorPokemon.startsWith("000_")
        return ResultadoDeteccion(
            nombrePokemon = if (esDetectado) mejorPokemon.substringAfter("_").uppercase()
            else "No identificado",
            pokemonId = if (esDetectado) mejorPokemon.substringBefore("_").toIntOrNull() ?: 0 else 0,
            certeza = certezaContrastiva,
            similitudDirecta = mejorSimilitud,
            segundoLugar = textoSegundo,
            detectado = esDetectado
        )
    }

    private fun probabilidadesTexto(vector: FloatArray): Map<String, Float> {
        val nombres = textoPorClase.keys.toList()
        val logits = DoubleArray(nombres.size + textoNegativas.size)
        for (i in nombres.indices) {
            logits[i] = (escalaLogit * calcularSimilitudCoseno(vector, textoPorClase.getValue(nombres[i]))).toDouble()
        }
        for (j in textoNegativas.indices) {
            logits[nombres.size + j] = (escalaLogit * calcularSimilitudCoseno(vector, textoNegativas[j])).toDouble()
        }
        val p = softmax(logits)

        val resultado = mutableMapOf<String, Float>()
        for (i in nombres.indices) resultado[nombres[i]] = p[i].toFloat()
        var fondo = 0.0
        for (j in nombres.size until p.size) fondo += p[j]
        resultado[CLASE_FONDO] = fondo.toFloat()
        return resultado
    }

    private fun probabilidadesImagen(vector: FloatArray): Map<String, Float> {
        val centrado = centrarYNormalizarForma(vector)
        val nombres = baseDeDatosSubEstados.keys.toList()
        val logits = DoubleArray(nombres.size) { i ->
            val mejor = baseDeDatosSubEstados.getValue(nombres[i]).maxOf { calcularSimilitudCoseno(centrado, it) }
            (mejor / tauImagen).toDouble()
        }
        val p = softmax(logits)
        return nombres.indices.associate { i -> nombres[i] to p[i].toFloat() }
    }

    private fun softmax(logits: DoubleArray): DoubleArray {
        val maximo = logits.maxOrNull() ?: 0.0
        val e = DoubleArray(logits.size) { i -> exp(logits[i] - maximo) }
        val suma = e.sum()
        return DoubleArray(e.size) { i -> e[i] / suma }
    }

    private fun aFloatArray(arr: JSONArray): FloatArray =
        FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }

    private fun calcularSimilitudCoseno(secuenciaA: FloatArray, secuenciaB: FloatArray): Float {
        val n = minOf(secuenciaA.size, secuenciaB.size)
        var suma = 0f
        for (i in 0 until n) {
            suma += secuenciaA[i] * secuenciaB[i]
        }
        return suma
    }
}