package com.example.scandex

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PokemonEncounter(
    val pokemonId: Int,
    val firstEncounteredAt: Long,
    val lastEncounteredAt: Long,
    val encounterCount: Int
)

/**
 * Helper compartido para leer/escribir archivos privados cifrados con AES-256-GCM.
 */
internal class EncryptedAppFile(
    private val file: File,
    private val throwOnCorruption: Boolean = false,
    private val onCorruption: ((Exception) -> Unit)? = null,
    private val keyProvider: () -> SecretKey
) {
    fun <T> readOrNull(decode: (ByteArray) -> T): T? {
        if (!file.exists()) return null
        return try {
            decode(decrypt(file.readBytes()))
        } catch (e: Exception) {
            if (throwOnCorruption) throw e
            onCorruption?.invoke(e)
            delete()
            null
        }
    }

    fun writeAtomic(plaintext: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(encrypt(plaintext))

        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw IOException("No se pudo guardar ${file.name}")
            }
        }
    }

    fun delete() {
        File(file.parentFile, "${file.name}.tmp").delete()
        file.delete()
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        if (payload.size < 1 + MIN_IV_BYTES + GCM_TAG_BYTES) {
            throw GeneralSecurityException("Archivo cifrado incompleto")
        }
        val ivLen = payload[0].toInt() and 0xFF
        val dataStart = 1 + ivLen
        if (ivLen < MIN_IV_BYTES || dataStart + GCM_TAG_BYTES > payload.size) {
            throw GeneralSecurityException("IV inválido en archivo cifrado")
        }
        val iv = payload.copyOfRange(1, dataStart)
        val ciphertext = payload.copyOfRange(dataStart, payload.size)

        val cipher = Cipher.getInstance(CIPHER_MODE)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val CIPHER_MODE = "AES/GCM/NoPadding"
        private const val MIN_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private val keyLock = Any()

        fun getOrCreateAesKey(alias: String): SecretKey = synchronized(keyLock) {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }
    }
}

/**
 * Motor interno que guarda encuentros en un archivo privado cifrado con AES-256-GCM.
 */
internal class LocalEncounterStorage(
    file: File,
    throwOnCorruption: Boolean = false,
    onCorruption: ((Exception) -> Unit)? = null,
    keyProvider: () -> SecretKey
) {
    private val encryptedFile = EncryptedAppFile(file, throwOnCorruption, onCorruption, keyProvider)
    private val lock = Any()
    private var cache: MutableMap<Int, PokemonEncounter>? = null

    fun preload() {
        synchronized(lock) { loadLocked() }
    }

    fun registerEncounter(
        pokemonId: Int,
        timestampMillis: Long = System.currentTimeMillis()
    ): PokemonEncounter {
        require(pokemonId > 0) { "pokemonId debe ser mayor a 0" }

        synchronized(lock) {
            val updated = LinkedHashMap(loadLocked())
            val prev = updated[pokemonId]
            val next = if (prev == null) {
                PokemonEncounter(pokemonId, timestampMillis, timestampMillis, 1)
            } else {
                PokemonEncounter(
                    pokemonId = pokemonId,
                    firstEncounteredAt = minOf(prev.firstEncounteredAt, timestampMillis),
                    lastEncounteredAt = maxOf(prev.lastEncounteredAt, timestampMillis),
                    encounterCount = prev.encounterCount + 1
                )
            }
            updated[pokemonId] = next
            encryptedFile.writeAtomic(serialize(updated))
            cache = updated
            return next
        }
    }

    fun isEncountered(pokemonId: Int): Boolean =
        pokemonId > 0 && synchronized(lock) { loadLocked().containsKey(pokemonId) }

    fun getEncounter(pokemonId: Int): PokemonEncounter? =
        if (pokemonId <= 0) null else synchronized(lock) { loadLocked()[pokemonId] }

    fun getEncounteredIds(): Set<Int> =
        synchronized(lock) { loadLocked().keys.toSet() }

    fun getAllEncounters(): List<PokemonEncounter> =
        synchronized(lock) { loadLocked().values.sortedBy { it.pokemonId } }

    fun clearAll() {
        synchronized(lock) {
            encryptedFile.delete()
            cache = LinkedHashMap()
        }
    }

    private fun loadLocked(): MutableMap<Int, PokemonEncounter> {
        cache?.let { return it }
        val loaded = encryptedFile.readOrNull(::deserialize) ?: LinkedHashMap()
        cache = loaded
        return loaded
    }

    private fun serialize(encounters: Map<Int, PokemonEncounter>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeInt(SCHEMA_VERSION)
            dos.writeInt(encounters.size)
            for (e in encounters.values.sortedBy { it.pokemonId }) {
                dos.writeInt(e.pokemonId)
                dos.writeLong(e.firstEncounteredAt)
                dos.writeLong(e.lastEncounteredAt)
                dos.writeInt(e.encounterCount)
            }
        }
        return out.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): MutableMap<Int, PokemonEncounter> {
        val result = LinkedHashMap<Int, PokemonEncounter>()
        DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            val version = dis.readInt()
            require(version == SCHEMA_VERSION) { "Versión no soportada: $version" }

            val count = dis.readInt()
            require(count >= 0 && count <= (bytes.size - HEADER_BYTES) / RECORD_BYTES) {
                "Cantidad inválida de registros: $count"
            }

            repeat(count) {
                val id = dis.readInt()
                val first = dis.readLong()
                val last = dis.readLong()
                val hits = dis.readInt().coerceAtLeast(1)
                if (id > 0) {
                    result[id] = PokemonEncounter(id, first, last, hits)
                }
            }
            require(dis.read() == -1) { "Datos corruptos al final del archivo" }
        }
        return result
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val HEADER_BYTES = 8
        private const val RECORD_BYTES = 24
    }
}

/**
 * Almacena los encuentros Pokémon en un archivo interno privado y cifrado en `filesDir`.
 * Excluido de respaldos automáticos para que los datos se pierdan al desinstalar la app.
 */
object PokemonEncounterStore {
    private const val FILE_NAME = "scandex_encounters.bin"
    private const val KEY_ALIAS = "scandex_encounters_aes_key"

    @Volatile
    private var storage: LocalEncounterStorage? = null

    fun preload(context: Context) = getStorage(context).preload()

    fun registerEncounter(
        context: Context,
        pokemonId: Int,
        timestampMillis: Long = System.currentTimeMillis()
    ): PokemonEncounter = getStorage(context).registerEncounter(pokemonId, timestampMillis)

    fun isEncountered(context: Context, pokemonId: Int): Boolean =
        getStorage(context).isEncountered(pokemonId)

    fun getEncounter(context: Context, pokemonId: Int): PokemonEncounter? =
        getStorage(context).getEncounter(pokemonId)

    fun getEncounteredIds(context: Context): Set<Int> =
        getStorage(context).getEncounteredIds()

    fun getAllEncounters(context: Context): List<PokemonEncounter> =
        getStorage(context).getAllEncounters()

    fun clearAll(context: Context) = getStorage(context).clearAll()

    private fun getStorage(context: Context): LocalEncounterStorage {
        storage?.let { return it }
        synchronized(this) {
            storage?.let { return it }
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            return LocalEncounterStorage(
                file = file,
                onCorruption = { error ->
                    Log.w("PokemonEncounterStore", "Archivo inválido o alterado; se reinicia el registro", error)
                }
            ) { EncryptedAppFile.getOrCreateAesKey(KEY_ALIAS) }.also { storage = it }
        }
    }
}
