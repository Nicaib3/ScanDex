package com.example.scandex

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import javax.crypto.SecretKey

/**
 * Filtra los IDs favoritos cargados desde almacenamiento local según tipo y búsqueda.
 */
internal fun filterFavoritePokemonIds(
    favoriteIds: List<Int>,
    unlockedIds: Set<Int>,
    detailsById: Map<Int, PokemonDetail>,
    selectedType: String?,
    query: String
): List<Int> {
    val cleanQuery = query.trim().lowercase(Locale.US)
    val cleanType = selectedType?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }

    return favoriteIds.filter { id ->
        val isUnlocked = id in unlockedIds
        val detail = detailsById[id]

        val matchesType = if (cleanType == null) {
            true
        } else {
            isUnlocked && detail?.types?.any { it.trim().uppercase(Locale.US) == cleanType } == true
        }

        val matchesQuery = if (cleanQuery.isEmpty()) {
            true
        } else {
            val formattedNum = String.format(Locale.US, "#%03d", id).lowercase(Locale.US)
            val idMatch = formattedNum.contains(cleanQuery) || id.toString().contains(cleanQuery)
            val nameMatch = isUnlocked && detail?.name?.lowercase(Locale.US)?.contains(cleanQuery) == true
            idMatch || nameMatch
        }

        matchesType && matchesQuery
    }
}

/**
 * Motor interno que persiste los IDs de Pokémon favoritos en un archivo cifrado con AES-256-GCM.
 */
internal class LocalFavoriteStorage(
    file: File,
    throwOnCorruption: Boolean = false,
    onCorruption: ((Exception) -> Unit)? = null,
    keyProvider: () -> SecretKey
) {
    private val encryptedFile = EncryptedAppFile(file, throwOnCorruption, onCorruption, keyProvider)
    private val lock = Any()
    private var cache: MutableSet<Int>? = null

    fun preload() {
        synchronized(lock) { loadLocked() }
    }

    fun isFavorite(pokemonId: Int): Boolean =
        pokemonId > 0 && synchronized(lock) { loadLocked().contains(pokemonId) }

    fun setFavorite(pokemonId: Int, favorite: Boolean): Boolean {
        require(pokemonId > 0) { "pokemonId debe ser mayor a 0" }
        synchronized(lock) {
            val current = loadLocked()
            val alreadyFavorite = current.contains(pokemonId)
            if (alreadyFavorite == favorite) return favorite

            val updated = LinkedHashSet(current)
            if (favorite) {
                updated.add(pokemonId)
            } else {
                updated.remove(pokemonId)
            }
            encryptedFile.writeAtomic(serialize(updated))
            cache = updated
            return favorite
        }
    }

    fun toggleFavorite(pokemonId: Int): Boolean {
        require(pokemonId > 0) { "pokemonId debe ser mayor a 0" }
        synchronized(lock) {
            val updated = LinkedHashSet(loadLocked())
            val next = if (updated.remove(pokemonId)) {
                false
            } else {
                updated.add(pokemonId)
                true
            }
            encryptedFile.writeAtomic(serialize(updated))
            cache = updated
            return next
        }
    }

    fun getFavoriteIds(): Set<Int> = synchronized(lock) {
        loadLocked().sorted().toCollection(LinkedHashSet())
    }

    fun clearAll() {
        synchronized(lock) {
            encryptedFile.delete()
            cache = LinkedHashSet()
        }
    }

    private fun loadLocked(): MutableSet<Int> {
        cache?.let { return it }
        val loaded = encryptedFile.readOrNull(::deserialize) ?: LinkedHashSet()
        cache = loaded
        return loaded
    }

    private fun serialize(favoriteIds: Set<Int>): ByteArray {
        val sorted = favoriteIds.filter { it > 0 }.sorted()
        val out = ByteArrayOutputStream(HEADER_BYTES + sorted.size * RECORD_BYTES)
        DataOutputStream(out).use { dos ->
            dos.writeInt(SCHEMA_VERSION)
            dos.writeInt(sorted.size)
            for (id in sorted) {
                dos.writeInt(id)
            }
        }
        return out.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): MutableSet<Int> {
        val result = LinkedHashSet<Int>()
        DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            val version = dis.readInt()
            require(version == SCHEMA_VERSION) { "Versión no soportada: $version" }

            val count = dis.readInt()
            require(count >= 0 && count <= (bytes.size - HEADER_BYTES) / RECORD_BYTES) {
                "Cantidad inválida de favoritos: $count"
            }

            repeat(count) {
                val id = dis.readInt()
                if (id > 0) {
                    result.add(id)
                }
            }
            require(dis.read() == -1) { "Datos corruptos al final del archivo" }
        }
        return result
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val HEADER_BYTES = 8
        private const val RECORD_BYTES = 4
    }
}

/**
 * Almacena los Pokémon favoritos en un archivo interno privado y cifrado en `filesDir`.
 * Excluido de respaldos automáticos para que los datos se pierdan al desinstalar la app.
 */
object PokemonFavoriteStore {
    private const val FILE_NAME = "scandex_favorites.bin"
    private const val KEY_ALIAS = "scandex_favorites_aes_key"

    @Volatile
    private var storage: LocalFavoriteStorage? = null

    fun preload(context: Context) = getStorage(context).preload()

    fun isFavorite(context: Context, pokemonId: Int): Boolean =
        getStorage(context).isFavorite(pokemonId)

    fun setFavorite(context: Context, pokemonId: Int, favorite: Boolean): Boolean =
        getStorage(context).setFavorite(pokemonId, favorite)

    fun toggleFavorite(context: Context, pokemonId: Int): Boolean =
        getStorage(context).toggleFavorite(pokemonId)

    fun getFavoriteIds(context: Context): Set<Int> =
        getStorage(context).getFavoriteIds()

    fun clearAll(context: Context) = getStorage(context).clearAll()

    private fun getStorage(context: Context): LocalFavoriteStorage {
        storage?.let { return it }
        synchronized(this) {
            storage?.let { return it }
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            return LocalFavoriteStorage(
                file = file,
                onCorruption = { error ->
                    Log.w("PokemonFavoriteStore", "Archivo inválido o alterado; se reinician favoritos", error)
                }
            ) { EncryptedAppFile.getOrCreateAesKey(KEY_ALIAS) }.also { storage = it }
        }
    }
}
