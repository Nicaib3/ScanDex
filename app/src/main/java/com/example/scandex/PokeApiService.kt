package com.example.scandex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PokemonSummary(
    val id: Int,
    val name: String
)

data class PokemonDetail(
    val id: Int,
    val name: String,
    val types: List<String>,
    val heightMeters: Double,
    val weightKg: Double,
    val spriteUrl: String?
)

data class PokemonAbilityInfo(
    val name: String,
    val isHidden: Boolean,
    val description: String
)

data class PokemonEvolutionStage(
    val id: Int,
    val name: String,
    val spriteUrl: String?
)

data class PokemonMoveSummary(
    val level: Int,
    val displayName: String,
    val url: String
)

data class PokemonMoveDetail(
    val name: String,
    val type: String,
    val power: String,
    val accuracy: String,
    val pp: String,
    val damageClass: String,
    val effect: String
)

data class PokemonFullInfo(
    val id: Int,
    val name: String,
    val genus: String,
    val description: String,
    val types: List<String>,
    val heightMeters: Double,
    val weightKg: Double,
    val spriteUrl: String?,
    val abilities: List<PokemonAbilityInfo>,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val spAttack: Int,
    val spDefense: Int,
    val speed: Int,
    val evolutions: List<PokemonEvolutionStage>,
    val weaknesses: List<String>,
    val strengths: List<String>,
    val resistances: List<String>,
    val moves: List<PokemonMoveSummary>
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
            val url = item.getString("url")
            val id = extractIdFromUrl(url) ?: (i + 1)
            list.add(
                PokemonSummary(
                    id = id,
                    name = formatName(rawName)
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
            spriteUrl = spriteUrl
        )
    }

    fun fetchPokemonFullInfo(id: Int): PokemonFullInfo {
        val pokemonJson = getJson("$BASE_URL/pokemon/$id")
        val name = formatName(pokemonJson.getString("name"))
        val heightMeters = pokemonJson.optInt("height", 0) / 10.0
        val weightKg = pokemonJson.optInt("weight", 0) / 10.0

        val typesArray = pokemonJson.getJSONArray("types")
        val types = ArrayList<String>(typesArray.length())
        for (i in 0 until typesArray.length()) {
            val typeName = typesArray.getJSONObject(i)
                .getJSONObject("type")
                .getString("name")
                .uppercase(Locale.US)
            types.add(typeName)
        }

        val sprites = pokemonJson.optJSONObject("sprites")
        val officialArtwork = sprites
            ?.optJSONObject("other")
            ?.optJSONObject("official-artwork")
            ?.optString("front_default")
            ?.takeIf { it.isNotBlank() && it != "null" }
        val defaultSprite = sprites
            ?.optString("front_default")
            ?.takeIf { it.isNotBlank() && it != "null" }
        val spriteUrl = officialArtwork ?: defaultSprite

        var hp = 0
        var atk = 0
        var def = 0
        var spAtk = 0
        var spDef = 0
        var spd = 0
        val statsArray = pokemonJson.optJSONArray("stats")
        if (statsArray != null) {
            for (i in 0 until statsArray.length()) {
                val statItem = statsArray.getJSONObject(i)
                val baseStat = statItem.optInt("base_stat", 0)
                when (statItem.getJSONObject("stat").getString("name")) {
                    "hp" -> hp = baseStat
                    "attack" -> atk = baseStat
                    "defense" -> def = baseStat
                    "special-attack" -> spAtk = baseStat
                    "special-defense" -> spDef = baseStat
                    "speed" -> spd = baseStat
                }
            }
        }

        val abilities = ArrayList<PokemonAbilityInfo>()
        val abilitiesArray = pokemonJson.optJSONArray("abilities")
        if (abilitiesArray != null) {
            val limit = minOf(abilitiesArray.length(), 3)
            for (i in 0 until limit) {
                val abItem = abilitiesArray.getJSONObject(i)
                val isHidden = abItem.optBoolean("is_hidden", false)
                val abObj = abItem.getJSONObject("ability")
                val rawAbilityName = abObj.getString("name")
                val abilityUrl = abObj.getString("url")
                abilities.add(fetchAbilityInfo(abilityUrl, rawAbilityName, isHidden))
            }
        }

        val moves = extractLevelUpMoves(pokemonJson.optJSONArray("moves"))

        var genus = ""
        var description = "Sin descripción de Pokédex disponible."
        var evolutions = emptyList<PokemonEvolutionStage>()
        try {
            val speciesJson = getJson("$BASE_URL/pokemon-species/$id")
            genus = findLocalizedText(speciesJson.optJSONArray("genera"), "genus", "es")
                ?: findLocalizedText(speciesJson.optJSONArray("genera"), "genus", "en")
                ?: ""
            description = findLocalizedText(speciesJson.optJSONArray("flavor_text_entries"), "flavor_text", "es")
                ?: findLocalizedText(speciesJson.optJSONArray("flavor_text_entries"), "flavor_text", "en")
                ?: description
            val evoUrl = speciesJson.optJSONObject("evolution_chain")?.optString("url").orEmpty()
            evolutions = fetchEvolutionChain(evoUrl, id)
        } catch (_: Exception) {
        }

        val (weaknesses, strengths, resistances) = try {
            fetchTypeInteractions(types)
        } catch (_: Exception) {
            Triple(emptyList(), emptyList(), emptyList())
        }

        return PokemonFullInfo(
            id = id,
            name = name,
            genus = genus,
            description = description,
            types = types,
            heightMeters = heightMeters,
            weightKg = weightKg,
            spriteUrl = spriteUrl,
            abilities = abilities,
            hp = hp,
            attack = atk,
            defense = def,
            spAttack = spAtk,
            spDefense = spDef,
            speed = spd,
            evolutions = evolutions,
            weaknesses = weaknesses,
            strengths = strengths,
            resistances = resistances,
            moves = moves
        )
    }

    fun fetchMoveDetail(moveUrl: String): PokemonMoveDetail {
        val json = getJson(moveUrl)
        val rawName = json.getString("name")
        val name = findLocalizedText(json.optJSONArray("names"), "name", "es")
            ?: formatName(rawName.replace("-", " "))
        val type = json.getJSONObject("type").getString("name").uppercase(Locale.US)
        val power = if (json.isNull("power")) "—" else json.getInt("power").toString()
        val accuracy = if (json.isNull("accuracy")) "—" else "${json.getInt("accuracy")}%"
        val pp = if (json.isNull("pp")) "—" else json.getInt("pp").toString()
        val damageClass = when (json.optJSONObject("damage_class")?.optString("name")) {
            "physical" -> "Físico"
            "special" -> "Especial"
            "status" -> "Estado"
            else -> "—"
        }
        var effect = findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "es")
            ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "es")
            ?: findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "en")
            ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "en")
            ?: "Sin descripción disponible."
        if (!json.isNull("effect_chance")) {
            effect = effect.replace("\$effect_chance", json.getInt("effect_chance").toString())
        }

        return PokemonMoveDetail(
            name = name,
            type = type,
            power = power,
            accuracy = accuracy,
            pp = pp,
            damageClass = damageClass,
            effect = effect
        )
    }

    private fun fetchAbilityInfo(
        abilityUrl: String,
        rawName: String,
        isHidden: Boolean
    ): PokemonAbilityInfo {
        val fallbackName = formatName(rawName.replace("-", " "))
        return try {
            val json = getJson(abilityUrl)
            val name = findLocalizedText(json.optJSONArray("names"), "name", "es") ?: fallbackName
            val desc = findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "es")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "es")
                ?: findLocalizedText(json.optJSONArray("flavor_text_entries"), "flavor_text", "en")
                ?: findLocalizedText(json.optJSONArray("effect_entries"), "short_effect", "en")
                ?: "Sin descripción disponible."
            PokemonAbilityInfo(name, isHidden, desc)
        } catch (_: Exception) {
            PokemonAbilityInfo(fallbackName, isHidden, "No se pudo cargar la descripción.")
        }
    }

    private fun extractLevelUpMoves(movesArray: JSONArray?): List<PokemonMoveSummary> {
        if (movesArray == null) return emptyList()
        val list = ArrayList<PokemonMoveSummary>()
        val seen = HashSet<String>()

        for (i in 0 until movesArray.length()) {
            val moveItem = movesArray.getJSONObject(i)
            val moveObj = moveItem.getJSONObject("move")
            val rawName = moveObj.getString("name")
            if (seen.contains(rawName)) continue

            val details = moveItem.optJSONArray("version_group_details") ?: continue
            var levelFound = -1
            for (j in 0 until details.length()) {
                val detail = details.getJSONObject(j)
                val method = detail.optJSONObject("move_learn_method")?.optString("name")
                if (method == "level-up") {
                    levelFound = detail.optInt("level_learned_at", 0)
                }
            }

            if (levelFound >= 0) {
                seen.add(rawName)
                list.add(
                    PokemonMoveSummary(
                        level = levelFound,
                        displayName = formatName(rawName.replace("-", " ")),
                        url = moveObj.getString("url")
                    )
                )
            }
        }
        list.sortBy { it.level }
        return list
    }

    private fun fetchEvolutionChain(evoUrl: String, targetId: Int): List<PokemonEvolutionStage> {
        if (evoUrl.isBlank()) return emptyList()
        val root = getJson(evoUrl).getJSONObject("chain")
        val stages = ArrayList<PokemonEvolutionStage>()
        var current: JSONObject? = root

        while (current != null && stages.size < 3) {
            val species = current.getJSONObject("species")
            val speciesId = extractIdFromUrl(species.getString("url")) ?: 0
            val spriteUrl = if (speciesId > 0) {
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$speciesId.png"
            } else {
                null
            }
            stages.add(
                PokemonEvolutionStage(
                    id = speciesId,
                    name = formatName(species.getString("name")),
                    spriteUrl = spriteUrl
                )
            )

            val evolvesTo = current.optJSONArray("evolves_to")
            if (evolvesTo == null || evolvesTo.length() == 0) {
                current = null
            } else {
                var nextNode = evolvesTo.getJSONObject(0)
                for (i in 0 until evolvesTo.length()) {
                    val candidate = evolvesTo.getJSONObject(i)
                    if (chainContainsPokemon(candidate, targetId)) {
                        nextNode = candidate
                        break
                    }
                }
                current = nextNode
            }
        }
        return stages
    }

    private fun chainContainsPokemon(node: JSONObject, targetId: Int): Boolean {
        val speciesId = extractIdFromUrl(node.getJSONObject("species").optString("url"))
        if (speciesId == targetId) return true
        val evolvesTo = node.optJSONArray("evolves_to") ?: return false
        for (i in 0 until evolvesTo.length()) {
            if (chainContainsPokemon(evolvesTo.getJSONObject(i), targetId)) return true
        }
        return false
    }

    private fun fetchTypeInteractions(types: List<String>): Triple<List<String>, List<String>, List<String>> {
        val defenseMap = mutableMapOf<String, Double>()
        val strengthSet = mutableSetOf<String>()

        for (type in types) {
            val typeJson = getJson("$BASE_URL/type/${type.lowercase(Locale.US)}")
            val relations = typeJson.getJSONObject("damage_relations")

            applyMultiplier(relations.optJSONArray("double_damage_from"), defenseMap, 2.0)
            applyMultiplier(relations.optJSONArray("half_damage_from"), defenseMap, 0.5)
            applyMultiplier(relations.optJSONArray("no_damage_from"), defenseMap, 0.0)

            val doubleTo = relations.optJSONArray("double_damage_to")
            if (doubleTo != null) {
                for (i in 0 until doubleTo.length()) {
                    strengthSet.add(doubleTo.getJSONObject(i).getString("name").uppercase(Locale.US))
                }
            }
        }

        val weaknesses = defenseMap.filter { it.value > 1.0 }
            .entries.sortedByDescending { it.value }
            .map { it.key }
        val resistances = defenseMap.filter { it.value < 1.0 }
            .entries.sortedBy { it.value }
            .map { it.key }
        return Triple(weaknesses, strengthSet.toList(), resistances)
    }

    private fun applyMultiplier(array: JSONArray?, map: MutableMap<String, Double>, factor: Double) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val typeName = array.getJSONObject(i).getString("name").uppercase(Locale.US)
            val current = map[typeName] ?: 1.0
            map[typeName] = current * factor
        }
    }

    private fun findLocalizedText(array: JSONArray?, field: String, lang: String): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val languageName = obj.optJSONObject("language")?.optString("name")
            if (languageName == lang) {
                val text = obj.optString(field)
                    .replace("\u000c", " ")
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("  ", " ")
                    .trim()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun extractIdFromUrl(url: String): Int? =
        url.trimEnd('/').substringAfterLast('/').toIntOrNull()

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
