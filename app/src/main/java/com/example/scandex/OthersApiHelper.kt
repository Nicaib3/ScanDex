package com.example.scandex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class OtherEntryType(val label: String) {
    ITEM("OBJETO"),
    ABILITY("HABILIDAD")
}

data class OtherEntrySummary(
    val id: Int,
    val rawName: String,
    val displayName: String,
    val type: OtherEntryType,
    val url: String,
    val spriteUrl: String?
)

data class OtherEntryDetail(
    val displayName: String,
    val localizedName: String,
    val type: OtherEntryType,
    val subtitle: String,
    val description: String,
    val spriteUrl: String?
)

/**
 * Consultas y caché en memoria contra PokéAPI v2 para objetos y habilidades.
 * Separado de `PokeApiService.kt` para evitar conflictos de merge con `main`.
 */
internal object OthersApiHelper {

    private const val BASE_URL = "https://pokeapi.co/api/v2"
    private const val TIMEOUT_MS = 10_000
    private const val ABILITY_LIMIT = 500
    private const val ITEM_LIMIT = 2500
    private const val MAIN_SERIES_MAX_ID = 10_000

    private val detailCache = ConcurrentHashMap<String, OtherEntryDetail>()
    private val spriteCache = ConcurrentHashMap<String, Bitmap>()

    @Volatile
    private var catalogCache: List<OtherEntrySummary>? = null

    fun fetchAllEntries(): List<OtherEntrySummary> {
        catalogCache?.let { return it }

        val abilitiesJson = getJson("$BASE_URL/ability?limit=$ABILITY_LIMIT&offset=0")
        val itemsJson = getJson("$BASE_URL/item?limit=$ITEM_LIMIT&offset=0")

        val abilities = parseSummaryList(abilitiesJson, OtherEntryType.ABILITY)
        val items = parseSummaryList(itemsJson, OtherEntryType.ITEM)

        return (items + abilities)
            .sortedBy { it.displayName.lowercase(Locale.US) }
            .also { catalogCache = it }
    }

    internal fun parseSummaryList(json: JSONObject, type: OtherEntryType): List<OtherEntrySummary> {
        val results = json.optJSONArray("results") ?: return emptyList()
        val list = ArrayList<OtherEntrySummary>(results.length())
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            val rawName = obj.getString("name")
            val url = obj.getString("url")
            val id = extractIdFromUrl(url) ?: (i + 1)
            if (id >= MAIN_SERIES_MAX_ID) continue

            val spriteUrl = if (type == OtherEntryType.ITEM) {
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/$rawName.png"
            } else {
                null
            }
            list.add(
                OtherEntrySummary(
                    id = id,
                    rawName = rawName,
                    displayName = formatSlug(rawName),
                    type = type,
                    url = url,
                    spriteUrl = spriteUrl
                )
            )
        }
        return list
    }

    fun filterEntries(
        entries: List<OtherEntrySummary>,
        filterType: OtherEntryType?,
        query: String
    ): List<OtherEntrySummary> {
        val cleanQuery = query.trim()
        return entries.filter { entry ->
            val matchesType = filterType == null || entry.type == filterType
            if (!matchesType) return@filter false
            if (cleanQuery.isEmpty()) return@filter true

            val cachedName = detailCache[entry.url]?.localizedName
            entry.displayName.contains(cleanQuery, ignoreCase = true) ||
                entry.rawName.contains(cleanQuery, ignoreCase = true) ||
                (cachedName != null && cachedName.contains(cleanQuery, ignoreCase = true))
        }
    }

    fun fetchEntryDetail(entry: OtherEntrySummary): OtherEntryDetail {
        detailCache[entry.url]?.let { return it }

        val json = getJson(entry.url)
        val detail = parseDetailJson(json, entry)
        detailCache[entry.url] = detail
        return detail
    }

    internal fun parseDetailJson(json: JSONObject, entry: OtherEntrySummary): OtherEntryDetail {
        val localizedName = findLocalizedText(json.optJSONArray("names"), "name", "es")
            ?: findLocalizedText(json.optJSONArray("names"), "name", "en")
            ?: entry.displayName

        return if (entry.type == OtherEntryType.ABILITY) {
            val gen = json.optJSONObject("generation")?.optString("name")?.let(::formatSlug) ?: "—"
            val desc = findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "es")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "effect", "es")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "es")
                ?: findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "en")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "en")
                ?: "Sin descripción disponible."
            OtherEntryDetail(
                displayName = entry.displayName,
                localizedName = localizedName,
                type = OtherEntryType.ABILITY,
                subtitle = "Generación: $gen",
                description = desc,
                spriteUrl = null
            )
        } else {
            val category = json.optJSONObject("category")?.optString("name")?.let(::formatSlug) ?: "—"
            val cost = json.optInt("cost", 0)
            val costText = if (cost > 0) "$cost Pokédólares" else "Sin precio de tienda"
            val sprite = json.optJSONObject("sprites")
                ?.optString("default")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: entry.spriteUrl
            val desc = findLocalizedText(json.optJSONArray("flavor_text_entries"), "text", "es")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "es")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "effect", "es")
                ?: findLocalizedText(json.optJSONArray("flavor_text_entries"), "text", "en")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "en")
                ?: "Sin descripción disponible."
            OtherEntryDetail(
                displayName = entry.displayName,
                localizedName = localizedName,
                type = OtherEntryType.ITEM,
                subtitle = "Categoría: $category • Precio: $costText",
                description = desc,
                spriteUrl = sprite
            )
        }
    }

    fun getCachedDetail(url: String): OtherEntryDetail? = detailCache[url]

    fun downloadSprite(imageUrl: String): Bitmap? {
        spriteCache[imageUrl]?.let { return it }
        val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use(BitmapFactory::decodeStream)?.also { spriteCache[imageUrl] = it }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun findLocalizedText(array: JSONArray?, field: String, lang: String): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optJSONObject("language")?.optString("name") == lang) {
                val text = obj.optString(field)
                    .replace("\u000c", " ")
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun extractIdFromUrl(url: String): Int? =
        url.trimEnd('/').substringAfterLast('/').toIntOrNull()

    internal fun formatSlug(raw: String): String =
        raw.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }

    private fun getJson(endpoint: String): JSONObject {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return try {
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode} en $endpoint" }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
