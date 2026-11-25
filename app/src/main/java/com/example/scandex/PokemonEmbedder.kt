package com.example.scandex

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
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

    private val embedder: ImageEmbedder
    private var mediaGlobalForma: FloatArray? = null
    // Nombre del Pokémon -> Lista de sus K sub-estados híbridos normalizados (forma + color)
    private val baseDeDatosSubEstados = mutableMapOf<String, List<FloatArray>>()

    companion object {
        private const val PESO_FORMA = 0.80f
        private const val PESO_COLOR = 0.60f
    }

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("mobilenet_v3_large.tflite")
            .build()

        val options = ImageEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .build()

        embedder = ImageEmbedder.createFromOptions(context, options)
    }

    /**
     * Extrae la secuencia híbrida centrada de 1042 valores:
     * 1. Extrae los 1024 valores de forma (MobileNetV3), les resta la Media Global y los normaliza con L2.
     * 2. Extrae los 18 valores de espectro cromático HSV del centro y los normaliza con L2.
     * 3. Une ambos con sus pesos (0.80 forma / 0.60 color) y normaliza con L2.
     */
    fun extraerSecuenciaHibridaCentrada(bitmap: Bitmap): FloatArray {
        val bitmapCompatible = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val mpImage = BitmapImageBuilder(bitmapCompatible).build()
        val resultado = embedder.embed(mpImage)
        val secFormaCruda = normalizarL2(resultado.embeddingResult().embeddings().first().floatEmbedding())
        val secFormaCentrada = centrarYNormalizarForma(secFormaCruda)
        val secColor = extraerHistogramaHsv18(bitmapCompatible)

        val hibrida = FloatArray(secFormaCentrada.size + secColor.size)
        for (i in secFormaCentrada.indices) {
            hibrida[i] = secFormaCentrada[i] * PESO_FORMA
        }
        for (i in secColor.indices) {
            hibrida[secFormaCentrada.size + i] = secColor[i] * PESO_COLOR
        }

        return normalizarL2(hibrida)
    }

    private fun extraerHistogramaHsv18(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val x1 = (w * 0.20f).toInt()
        val y1 = (h * 0.20f).toInt()
        val cropW = (w * 0.60f).toInt().coerceAtLeast(1)
        val cropH = (h * 0.60f).toInt().coerceAtLeast(1)

        val recorte = Bitmap.createBitmap(bitmap, x1, y1, cropW, cropH)
        val muestreado = Bitmap.createScaledBitmap(recorte, 64, 64, true)

        val pixeles = IntArray(64 * 64)
        muestreado.getPixels(pixeles, 0, 64, 0, 0, 64, 64)

        val hist = FloatArray(18)

        for (pixel in pixeles) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val delta = maxC - minC

            val s = if (maxC > 1e-5f) delta / maxC else 0f
            val v = maxC

            // Ignoramos fondos blancos (baja saturación) y bordes negros (bajo brillo)
            if (s > 0.18f && v > 0.15f && delta > 1e-5f) {
                val hueDeg = when (maxC) {
                    r -> (60f * ((g - b) / delta) + 360f) % 360f
                    g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }
                val bin = ((hueDeg / 20f).toInt() % 18 + 18) % 18
                hist[bin] += s
            }
        }

        return normalizarL2(hist)
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

        return baseDeDatosSubEstados.size
    }

    fun identificarPokemon(foto: Bitmap, umbralMinimo: Float = 0.22f): ResultadoDeteccion {
        if (baseDeDatosSubEstados.isEmpty()) {
            return ResultadoDeteccion("JSON de secuencias vacío", 0, 0f, 0f, "", false)
        }

        val secuenciaFoto = extraerSecuenciaHibridaCentrada(foto)

        // Calculamos la mejor similitud de cada Pokémon (su sub-estado más cercano a la foto)
        val puntajesPorPokemon = mutableListOf<Pair<String, Float>>()

        for ((nombre, listaSubEstados) in baseDeDatosSubEstados) {
            var mejorSimSubEstado = -1f
            for (subEstado in listaSubEstados) {
                val sim = calcularSimilitudCoseno(secuenciaFoto, subEstado)
                if (sim > mejorSimSubEstado) {
                    mejorSimSubEstado = sim
                }
            }
            puntajesPorPokemon.add(nombre to mejorSimSubEstado)
        }

        puntajesPorPokemon.sortByDescending { it.second }

        val (mejorPokemon, mejorSimilitud) = puntajesPorPokemon[0]
        val segundoPar = puntajesPorPokemon.getOrNull(1)

        // Calculamos probabilidad contrastiva (Softmax con temperatura tau = 0.07)
        val tau = 0.07f
        var sumaExp = 0.0
        for ((_, sim) in puntajesPorPokemon) {
            sumaExp += exp(((sim - mejorSimilitud) / tau).toDouble())
        }
        val certezaContrastiva = (1.0 / sumaExp).toFloat()

        val textoSegundo = if (segundoPar != null) {
            "2° lugar: ${segundoPar.first} (${(segundoPar.second * 100).toInt()}%)"
        } else {
            ""
        }

        val esDetectado = mejorSimilitud >= umbralMinimo
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

    private fun calcularSimilitudCoseno(secuenciaA: FloatArray, secuenciaB: FloatArray): Float {
        val n = minOf(secuenciaA.size, secuenciaB.size)
        var suma = 0f
        for (i in 0 until n) {
            suma += secuenciaA[i] * secuenciaB[i]
        }
        return suma
    }
}