package com.example.scandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class PokemonFavoriteStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun persistsFavoritesAcrossRestarts() {
        val file = File(tempFolder.root, "scandex_favorites.bin")
        val key = newKey()

        val writer = LocalFavoriteStorage(file) { key }
        assertTrue(writer.setFavorite(25, true))
        assertTrue(writer.setFavorite(6, true))
        assertTrue(writer.toggleFavorite(1))
        assertTrue(file.exists())

        val reader = LocalFavoriteStorage(file) { key }
        assertTrue(reader.isFavorite(25))
        assertTrue(reader.isFavorite(6))
        assertTrue(reader.isFavorite(1))
        assertFalse(reader.isFavorite(150))
        assertEquals(listOf(1, 6, 25), reader.getFavoriteIds().toList())
    }

    @Test
    fun toggleUnfavoriteAndClearAllWorkCorrectly() {
        val file = File(tempFolder.root, "scandex_favorites.bin")
        val key = newKey()
        val storage = LocalFavoriteStorage(file) { key }

        assertTrue(storage.toggleFavorite(25))
        assertTrue(storage.setFavorite(25, true))
        assertFalse(storage.toggleFavorite(25))
        assertFalse(storage.isFavorite(25))

        val reloaded = LocalFavoriteStorage(file) { key }
        assertTrue(reloaded.getFavoriteIds().isEmpty())

        reloaded.setFavorite(7, true)
        reloaded.clearAll()
        assertFalse(file.exists())
        assertTrue(LocalFavoriteStorage(file) { key }.getFavoriteIds().isEmpty())
    }

    @Test
    fun encryptsWithRandomIvAndRejectsDifferentKey() {
        val file = File(tempFolder.root, "scandex_favorites.bin")
        val key1 = newKey()
        val key2 = newKey()

        val storage = LocalFavoriteStorage(file) { key1 }
        storage.setFavorite(150, true)
        val bytes1 = file.readBytes()

        val rawId = ByteBuffer.allocate(4).putInt(150).array()
        assertFalse(bytes1.toList().windowed(rawId.size).any { it == rawId.toList() })

        storage.setFavorite(150, false)
        storage.setFavorite(150, true)
        assertFalse(bytes1.contentEquals(file.readBytes()))

        val wrongKeyReader = LocalFavoriteStorage(file, throwOnCorruption = true) { key2 }
        var rejected = false
        try {
            wrongKeyReader.getFavoriteIds()
        } catch (_: GeneralSecurityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun rejectsTamperedFileAndResetsSafely() {
        val file = File(tempFolder.root, "scandex_favorites.bin")
        val key = newKey()

        LocalFavoriteStorage(file) { key }.setFavorite(4, true)

        val tampered = file.readBytes()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0xFF).toByte()
        file.writeBytes(tampered)

        var threw = false
        try {
            LocalFavoriteStorage(file, throwOnCorruption = true) { key }.isFavorite(4)
        } catch (_: GeneralSecurityException) {
            threw = true
        }
        assertTrue(threw)

        val safeReader = LocalFavoriteStorage(file, throwOnCorruption = false) { key }
        assertFalse(safeReader.isFavorite(4))
        assertTrue(safeReader.getFavoriteIds().isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun rejectsInvalidIdsAndHandlesConcurrency() {
        val file = File(tempFolder.root, "scandex_favorites.bin")
        val key = newKey()
        val storage = LocalFavoriteStorage(file) { key }

        assertFalse(storage.isFavorite(0))
        assertFalse(storage.isFavorite(-5))

        var threw = false
        try {
            storage.setFavorite(0, true)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)

        val total = 25
        val pool = Executors.newFixedThreadPool(5)
        val latch = CountDownLatch(total)
        for (id in 1..total) {
            pool.execute {
                try {
                    storage.toggleFavorite(id)
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        val reloaded = LocalFavoriteStorage(file) { key }
        assertEquals((1..total).toSet(), reloaded.getFavoriteIds())
    }

    @Test
    fun filtersFavoritePokemonByTypeQueryAndLockState() {
        val favoriteIds = listOf(1, 6, 25)
        val unlockedIds = setOf(1, 6)
        val details = mapOf(
            1 to PokemonDetail(1, "Bulbasaur", listOf("GRASS", "POISON"), 0.7, 6.9, null),
            6 to PokemonDetail(6, "Charizard", listOf("FIRE", "FLYING"), 1.7, 90.5, null),
            25 to PokemonDetail(25, "Pikachu", listOf("ELECTRIC"), 0.4, 6.0, null)
        )

        assertEquals(
            listOf(1, 6, 25),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = null, query = "")
        )
        assertEquals(
            listOf(6),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = "FIRE", query = "")
        )
        // Bloqueado no coincide con filtro de tipo ni revela nombre secreto en búsqueda
        assertEquals(
            emptyList<Int>(),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = "ELECTRIC", query = "")
        )
        assertEquals(
            emptyList<Int>(),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = null, query = "Pikachu")
        )
        // Pero sí coincide por número visible (#025)
        assertEquals(
            listOf(25),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = null, query = "#025")
        )
        assertEquals(
            listOf(6),
            filterFavoritePokemonIds(favoriteIds, unlockedIds, details, selectedType = null, query = "char")
        )
    }
}
