package com.example.scandex

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

data class PokemonTeamMember(
    val pokemonId: Int,
    val name: String,
    val spriteUrl: String? = null,
    val types: List<String> = emptyList(),
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val spAttack: Int = 0,
    val spDefense: Int = 0,
    val speed: Int = 0
)

data class PokemonTeam(
    val id: String,
    val name: String,
    val members: List<PokemonTeamMember>,
    val createdAt: Long,
    val updatedAt: Long
)

/** Contrato para conectar después el almacenamiento cifrado de `localstorage`. */
interface TeamStorageBackend {
    fun loadTeams(context: Context): List<PokemonTeam>
    fun saveTeams(context: Context, teams: List<PokemonTeam>)
}

internal class TeamStoreManager(
    private val loadFn: () -> List<PokemonTeam>,
    private val saveFn: (List<PokemonTeam>) -> Unit
) {
    private val lock = Any()

    fun getAllTeams(): List<PokemonTeam> =
        synchronized(lock) { loadFn().sortedByDescending { it.updatedAt } }

    fun getTeamById(teamId: String): PokemonTeam? =
        synchronized(lock) { loadFn().firstOrNull { it.id == teamId } }

    fun searchTeams(query: String): List<PokemonTeam> {
        val q = query.trim()
        val all = getAllTeams()
        if (q.isEmpty()) return all
        return all.filter { team ->
            team.name.contains(q, ignoreCase = true) ||
                team.members.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    fun saveTeam(
        existingTeamId: String?,
        name: String,
        members: List<PokemonTeamMember>,
        nowMillis: Long = System.currentTimeMillis()
    ): PokemonTeam {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "El equipo necesita un nombre" }
        require(members.size in 1..PokemonTeamStore.MAX_TEAM_SIZE) {
            "El equipo debe tener entre 1 y ${PokemonTeamStore.MAX_TEAM_SIZE} Pokémon"
        }

        synchronized(lock) {
            val current = loadFn().toMutableList()
            val idx = existingTeamId?.let { id -> current.indexOfFirst { it.id == id } } ?: -1
            val bounded = members.take(PokemonTeamStore.MAX_TEAM_SIZE)
            val saved = if (idx >= 0) {
                current[idx].copy(name = cleanName, members = bounded, updatedAt = nowMillis)
                    .also { current[idx] = it }
            } else {
                PokemonTeam(existingTeamId ?: UUID.randomUUID().toString(), cleanName, bounded, nowMillis, nowMillis)
                    .also { current.add(it) }
            }
            saveFn(current)
            return saved
        }
    }

    fun deleteTeam(teamId: String): Boolean = synchronized(lock) {
        val current = loadFn().toMutableList()
        current.removeAll { it.id == teamId }.also { if (it) saveFn(current) }
    }
}

/**
 * Repositorio de equipos (en memoria por ahora; listo para conectar archivo cifrado `scandex_teams.bin`).
 */
object PokemonTeamStore {

    const val MAX_TEAM_SIZE = 6
    const val STORAGE_FILE_NAME = "scandex_teams.bin"
    private const val SCHEMA_VERSION = 1

    private var memoryTeams: List<PokemonTeam> = emptyList()

    @Volatile
    private var backend: TeamStorageBackend = object : TeamStorageBackend {
        override fun loadTeams(context: Context) = memoryTeams
        override fun saveTeams(context: Context, teams: List<PokemonTeam>) {
            memoryTeams = teams.toList()
        }
    }

    fun installBackend(customBackend: TeamStorageBackend) {
        backend = customBackend
    }

    private fun manager(context: Context) = TeamStoreManager(
        loadFn = { backend.loadTeams(context) },
        saveFn = { backend.saveTeams(context, it) }
    )

    fun getAllTeams(context: Context) = manager(context).getAllTeams()
    fun getTeamById(context: Context, teamId: String) = manager(context).getTeamById(teamId)
    fun searchTeams(context: Context, query: String) = manager(context).searchTeams(query)
    fun deleteTeam(context: Context, teamId: String) = manager(context).deleteTeam(teamId)

    fun saveTeam(
        context: Context,
        existingTeamId: String?,
        name: String,
        members: List<PokemonTeamMember>,
        nowMillis: Long = System.currentTimeMillis()
    ) = manager(context).saveTeam(existingTeamId, name, members, nowMillis)

    fun serialize(teams: List<PokemonTeam>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeInt(SCHEMA_VERSION)
            dos.writeInt(teams.size)
            for (team in teams) {
                dos.writeUTF(team.id)
                dos.writeUTF(team.name)
                dos.writeLong(team.createdAt)
                dos.writeLong(team.updatedAt)
                val bounded = team.members.take(MAX_TEAM_SIZE)
                dos.writeInt(bounded.size)
                for (m in bounded) {
                    dos.writeInt(m.pokemonId)
                    dos.writeUTF(m.name)
                    dos.writeUTF(m.spriteUrl.orEmpty())
                    dos.writeInt(m.types.size)
                    m.types.forEach { dos.writeUTF(it) }
                    dos.writeInt(m.hp); dos.writeInt(m.attack); dos.writeInt(m.defense)
                    dos.writeInt(m.spAttack); dos.writeInt(m.spDefense); dos.writeInt(m.speed)
                }
            }
        }
        return out.toByteArray()
    }

    fun deserialize(bytes: ByteArray): List<PokemonTeam> {
        DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            require(dis.readInt() == SCHEMA_VERSION) { "Versión no soportada" }
            val teamCount = dis.readInt()
            require(teamCount >= 0) { "Cantidad inválida de equipos" }

            return List(teamCount) {
                val id = dis.readUTF()
                val name = dis.readUTF()
                val createdAt = dis.readLong()
                val updatedAt = dis.readLong()
                val memberCount = dis.readInt()
                require(memberCount in 0..MAX_TEAM_SIZE) { "Integrantes inválidos: $memberCount" }

                val members = (0 until memberCount).mapNotNull {
                    val pokemonId = dis.readInt()
                    val mName = dis.readUTF()
                    val sprite = dis.readUTF().ifBlank { null }
                    val typeCount = dis.readInt()
                    require(typeCount in 0..4) { "Tipos inválidos: $typeCount" }
                    val types = List(typeCount) { dis.readUTF() }
                    val hp = dis.readInt(); val atk = dis.readInt(); val def = dis.readInt()
                    val spA = dis.readInt(); val spD = dis.readInt(); val spd = dis.readInt()
                    if (pokemonId > 0) {
                        PokemonTeamMember(pokemonId, mName, sprite, types, hp, atk, def, spA, spD, spd)
                    } else null
                }
                PokemonTeam(id, name, members, createdAt, updatedAt)
            }.also {
                require(dis.read() == -1) { "Datos corruptos al final" }
            }
        }
    }
}
