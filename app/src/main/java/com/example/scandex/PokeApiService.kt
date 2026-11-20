package com.example.scandex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PokemonSummary(
    val id: Int,
    val name: String,
    val isLocked: Boolean = true
)

data class PokemonDetail(
    val id: Int,
    val name: String,
    val types: List<String>,
    val heightMeters: Double,
    val weightKg: Double,
    val spriteUrl: String?,
    val isLocked: Boolean = true
)

object PokeApiService {

    const val GEN_1_COUNT = 151
    private const val BASE_URL = "https://pokeapi.co/api/v2"
    private const val TIMEOUT_MS = 10_000

    fun fetchGen1PokemonList(): List<PokemonSummary> {
        val json = getJson("$BASE_URL/pokemon?limit=$GEN_1_COUNT&offset=0")
        val results = json.getJSONArray("results")
        val list = ArrayList<PokemonSummary>(results.length())

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val rawName = item.getString("name")
            val url = item.getString("url").trimEnd('/')
            val id = url.substringAfterLast('/').toIntOrNull() ?: (i + 1)
            list.add(
                PokemonSummary(
                    id = id,
                    name = formatName(rawName),
                    isLocked = id % 2 == 0
                )
            )
        }
        return list
    }

    fun fetchPokemonDetail(id: Int): PokemonDetail {
        val json = getJson("$BASE_URL/pokemon/$id")
        val name = formatName(json.getString("name"))
        val heightMeters = json.optInt("height", 0) / 10.0
        val weightKg = json.optInt("weight", 0) / 10.0

        val typesArray = json.getJSONArray("types")
        val types = ArrayList<String>(typesArray.length())
        for (i in 0 until typesArray.length()) {
            val typeName = typesArray.getJSONObject(i)
                .getJSONObject("type")
                .getString("name")
                .uppercase(Locale.US)
            types.add(typeName)
        }

        val sprites = json.optJSONObject("sprites")
        val spriteUrl = sprites?.optString("front_default")?.takeIf {
            it.isNotBlank() && it != "null"
        }

        return PokemonDetail(
            id = id,
            name = name,
            types = types,
            heightMeters = heightMeters,
            weightKg = weightKg,
            spriteUrl = spriteUrl,
            isLocked = id % 2 == 0
        )
    }

    fun downloadBitmap(imageUrl: String): Bitmap? {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $code al consultar $endpoint")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun formatName(raw: String): String =
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
