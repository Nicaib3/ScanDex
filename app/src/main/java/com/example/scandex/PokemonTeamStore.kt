package com.example.scandex

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

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

/**
 * Motor interno que guarda equipos en un archivo privado cifrado con AES-256-GCM.
 */
internal class LocalTeamStorage(
    file: File,
    throwOnCorruption: Boolean = false,
    onCorruption: ((Exception) -> Unit)? = null,
    keyProvider: () -> SecretKey
) {
    private val encryptedFile = EncryptedAppFile(file, throwOnCorruption, onCorruption, keyProvider)
    private val lock = Any()
    private var cache: List<PokemonTeam>? = null

    fun getAllTeams(): List<PokemonTeam> =
        synchronized(lock) { loadLocked().sortedByDescending { it.updatedAt } }

    fun getTeamById(teamId: String): PokemonTeam? =
        synchronized(lock) { loadLocked().firstOrNull { it.id == teamId } }

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
            val current = loadLocked().toMutableList()
            val idx = existingTeamId?.let { id -> current.indexOfFirst { it.id == id } } ?: -1
            val bounded = members.take(PokemonTeamStore.MAX_TEAM_SIZE)
            val saved = if (idx >= 0) {
                current[idx].copy(name = cleanName, members = bounded, updatedAt = nowMillis)
                    .also { current[idx] = it }
            } else {
                PokemonTeam(existingTeamId ?: UUID.randomUUID().toString(), cleanName, bounded, nowMillis, nowMillis)
                    .also { current.add(it) }
            }
            encryptedFile.writeAtomic(serialize(current))
            cache = current
            return saved
        }
    }

    fun deleteTeam(teamId: String): Boolean = synchronized(lock) {
        val current = loadLocked().toMutableList()
        val removed = current.removeAll { it.id == teamId }
        if (removed) {
            encryptedFile.writeAtomic(serialize(current))
            cache = current
        }
        removed
    }

    private fun loadLocked(): List<PokemonTeam> {
        cache?.let { return it }
        return (encryptedFile.readOrNull(::deserialize) ?: emptyList()).also { cache = it }
    }

    private fun serialize(teams: List<PokemonTeam>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeInt(SCHEMA_VERSION)
            dos.writeInt(teams.size)
            for (team in teams) {
                dos.writeUTF(team.id)
                dos.writeUTF(team.name)
                dos.writeLong(team.createdAt)
                dos.writeLong(team.updatedAt)
                val bounded = team.members.take(PokemonTeamStore.MAX_TEAM_SIZE)
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

    private fun deserialize(bytes: ByteArray): List<PokemonTeam> {
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
                require(memberCount in 0..PokemonTeamStore.MAX_TEAM_SIZE) { "Integrantes inválidos: $memberCount" }

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

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}

/**
 * Almacena los equipos Pokémon en `filesDir/scandex_teams.bin` cifrado con `AndroidKeyStore`.
 */
object PokemonTeamStore {

    const val MAX_TEAM_SIZE = 6
    private const val FILE_NAME = "scandex_teams.bin"
    private const val KEY_ALIAS = "scandex_teams_aes_key"

    @Volatile
    private var storage: LocalTeamStorage? = null

    fun getAllTeams(context: Context) = getStorage(context).getAllTeams()
    fun getTeamById(context: Context, teamId: String) = getStorage(context).getTeamById(teamId)
    fun searchTeams(context: Context, query: String) = getStorage(context).searchTeams(query)
    fun deleteTeam(context: Context, teamId: String) = getStorage(context).deleteTeam(teamId)

    fun saveTeam(
        context: Context,
        existingTeamId: String?,
        name: String,
        members: List<PokemonTeamMember>,
        nowMillis: Long = System.currentTimeMillis()
    ) = getStorage(context).saveTeam(existingTeamId, name, members, nowMillis)

    private fun getStorage(context: Context): LocalTeamStorage {
        storage?.let { return it }
        synchronized(this) {
            storage?.let { return it }
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            return LocalTeamStorage(
                file = file,
                onCorruption = { error ->
                    Log.w("PokemonTeamStore", "Archivo de equipos inválido; se reinicia", error)
                }
            ) { EncryptedAppFile.getOrCreateAesKey(KEY_ALIAS) }.also { storage = it }
        }
    }
}
