package com.example.scandex

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TeamStatsSummary(
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val spAttack: Int = 0,
    val spDefense: Int = 0,
    val speed: Int = 0
) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed
}

data class TypeCountEntry(val type: String, val count: Int)

data class TeamTypeBalance(
    val weaknesses: List<TypeCountEntry> = emptyList(),
    val strengths: List<TypeCountEntry> = emptyList()
)

internal data class TypeDamageRelations(
    val doubleFrom: Set<String>,
    val halfFrom: Set<String>,
    val noFrom: Set<String>,
    val doubleTo: Set<String>
)

/** Consultas y cálculos de estadísticas/tipos para equipos (separado de PokeApiService.kt). */
internal object TeamPokeApiHelper {

    private const val BASE_URL = "https://pokeapi.co/api/v2"
    private const val TIMEOUT_MS = 10_000

    private val memberCache = ConcurrentHashMap<Int, PokemonTeamMember>()
    private val typeCache = ConcurrentHashMap<String, TypeDamageRelations>()

    private val typeColors = mapOf(
        "NORMAL" to 0xFFA8A77A.toInt(), "FIRE" to 0xFFEE8130.toInt(), "WATER" to 0xFF6390F0.toInt(),
        "ELECTRIC" to 0xFFF7D02C.toInt(), "GRASS" to 0xFF7AC74C.toInt(), "ICE" to 0xFF96D9D6.toInt(),
        "FIGHTING" to 0xFFC22E28.toInt(), "POISON" to 0xFFA33EA1.toInt(), "GROUND" to 0xFFE2BF65.toInt(),
        "FLYING" to 0xFFA98FF3.toInt(), "PSYCHIC" to 0xFFF95587.toInt(), "BUG" to 0xFFA6B91A.toInt(),
        "ROCK" to 0xFFB6A136.toInt(), "GHOST" to 0xFF735797.toInt(), "DRAGON" to 0xFF6F35FC.toInt(),
        "DARK" to 0xFF2C2733.toInt(), "STEEL" to 0xFFB7B7CE.toInt(), "FAIRY" to 0xFFD685AD.toInt()
    )

    fun colorIntForType(type: String): Int =
        typeColors[type.trim().uppercase(Locale.US)] ?: 0xFFA8A77A.toInt()

    fun fetchTeamMember(pokemonId: Int): PokemonTeamMember {
        memberCache[pokemonId]?.let { return it }

        val json = getJson("$BASE_URL/pokemon/$pokemonId")
        val name = json.getString("name").replaceFirstChar { it.titlecase(Locale.US) }

        val typesArr = json.getJSONArray("types")
        val types = List(typesArr.length()) { i ->
            typesArr.getJSONObject(i).getJSONObject("type").getString("name").uppercase(Locale.US)
        }

        val sprites = json.optJSONObject("sprites")
        val spriteUrl = sprites?.optJSONObject("other")?.optJSONObject("official-artwork")
            ?.optString("front_default")?.takeIf { it.isNotBlank() && it != "null" }
            ?: sprites?.optString("front_default")?.takeIf { it.isNotBlank() && it != "null" }

        val stats = mutableMapOf<String, Int>()
        val statsArr = json.optJSONArray("stats")
        if (statsArr != null) {
            for (i in 0 until statsArr.length()) {
                val item = statsArr.getJSONObject(i)
                stats[item.getJSONObject("stat").getString("name")] = item.optInt("base_stat", 0)
            }
        }

        return PokemonTeamMember(
            pokemonId = pokemonId,
            name = name,
            spriteUrl = spriteUrl,
            types = types,
            hp = stats["hp"] ?: 0,
            attack = stats["attack"] ?: 0,
            defense = stats["defense"] ?: 0,
            spAttack = stats["special-attack"] ?: 0,
            spDefense = stats["special-defense"] ?: 0,
            speed = stats["speed"] ?: 0
        ).also { memberCache[pokemonId] = it }
    }

    fun calculateTeamStats(members: List<PokemonTeamMember>) = TeamStatsSummary(
        hp = members.sumOf { it.hp },
        attack = members.sumOf { it.attack },
        defense = members.sumOf { it.defense },
        spAttack = members.sumOf { it.spAttack },
        spDefense = members.sumOf { it.spDefense },
        speed = members.sumOf { it.speed }
    )

    fun fetchTeamTypeBalance(members: List<PokemonTeamMember>): TeamTypeBalance {
        if (members.isEmpty()) return TeamTypeBalance()
        val relations = members.flatMap { it.types }.distinct().associateWith { fetchTypeRelations(it) }
        return computeTypeBalance(members, relations)
    }

    internal fun computeTypeBalance(
        members: List<PokemonTeamMember>,
        relationsByType: Map<String, TypeDamageRelations>
    ): TeamTypeBalance {
        val weaknessCounts = mutableMapOf<String, Int>()
        val strengthCounts = mutableMapOf<String, Int>()

        for (member in members) {
            val defenseMult = mutableMapOf<String, Double>()
            val offensive = mutableSetOf<String>()

            for (type in member.types) {
                val rel = relationsByType[type] ?: continue
                rel.doubleFrom.forEach { defenseMult[it] = (defenseMult[it] ?: 1.0) * 2.0 }
                rel.halfFrom.forEach { defenseMult[it] = (defenseMult[it] ?: 1.0) * 0.5 }
                rel.noFrom.forEach { defenseMult[it] = 0.0 }
                offensive.addAll(rel.doubleTo)
            }
            defenseMult.filterValues { it > 1.0 }.keys.forEach {
                weaknessCounts[it] = (weaknessCounts[it] ?: 0) + 1
            }
            offensive.forEach { strengthCounts[it] = (strengthCounts[it] ?: 0) + 1 }
        }

        val comparator = compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        return TeamTypeBalance(
            weaknesses = weaknessCounts.entries.sortedWith(comparator).take(3).map { TypeCountEntry(it.key, it.value) },
            strengths = strengthCounts.entries.sortedWith(comparator).take(3).map { TypeCountEntry(it.key, it.value) }
        )
    }

    private fun fetchTypeRelations(type: String): TypeDamageRelations {
        val key = type.uppercase(Locale.US)
        typeCache[key]?.let { return it }

        val rel = getJson("$BASE_URL/type/${type.lowercase(Locale.US)}").getJSONObject("damage_relations")
        return TypeDamageRelations(
            doubleFrom = toTypeSet(rel.optJSONArray("double_damage_from")),
            halfFrom = toTypeSet(rel.optJSONArray("half_damage_from")),
            noFrom = toTypeSet(rel.optJSONArray("no_damage_from")),
            doubleTo = toTypeSet(rel.optJSONArray("double_damage_to"))
        ).also { typeCache[key] = it }
    }

    private fun toTypeSet(arr: JSONArray?): Set<String> =
        if (arr == null) emptySet()
        else (0 until arr.length()).map { arr.getJSONObject(it).getString("name").uppercase(Locale.US) }.toSet()

    private fun getJson(endpoint: String): JSONObject {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return try {
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
