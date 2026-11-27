package com.example.scandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class PokemonEncounterStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun persistsEncountersAcrossRestarts() {
        val file = File(tempFolder.root, "scandex_encounters.bin")
        val key = newKey()

        val writer = LocalEncounterStorage(file) { key }
        writer.registerEncounter(25, timestampMillis = 1_000L)
        writer.registerEncounter(6, timestampMillis = 2_000L)
        val secondPikachu = writer.registerEncounter(25, timestampMillis = 5_000L)

        assertEquals(2, secondPikachu.encounterCount)
        assertTrue(file.exists())

        val reader = LocalEncounterStorage(file) { key }
        assertTrue(reader.isEncountered(25))
        assertTrue(reader.isEncountered(6))
        assertFalse(reader.isEncountered(1))

        val loaded = reader.getEncounter(25)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.encounterCount)
        assertEquals(1_000L, loaded.firstEncounteredAt)
        assertEquals(5_000L, loaded.lastEncounteredAt)
        assertEquals(listOf(6, 25), reader.getAllEncounters().map { it.pokemonId })
    }

    @Test
    fun encryptsWithRandomIvAndRejectsDifferentKey() {
        val file = File(tempFolder.root, "scandex_encounters.bin")
        val key1 = newKey()
        val key2 = newKey()

        val storage = LocalEncounterStorage(file) { key1 }
        storage.registerEncounter(150, timestampMillis = 123_456_789L)
        val bytes1 = file.readBytes()

        val rawTimestamp = ByteBuffer.allocate(8).putLong(123_456_789L).array()
        assertFalse(bytes1.toList().windowed(rawTimestamp.size).any { it == rawTimestamp.toList() })

        storage.registerEncounter(150, timestampMillis = 123_456_789L)
        assertFalse(bytes1.contentEquals(file.readBytes()))

        val wrongKeyReader = LocalEncounterStorage(file, throwOnCorruption = true) { key2 }
        var rejected = false
        try {
            wrongKeyReader.getAllEncounters()
        } catch (_: GeneralSecurityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun rejectsTamperedFileAndResetsSafely() {
        val file = File(tempFolder.root, "scandex_encounters.bin")
        val key = newKey()

        LocalEncounterStorage(file) { key }.registerEncounter(1, 10_000L)

        val tampered = file.readBytes()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0xFF).toByte()
        file.writeBytes(tampered)

        var threw = false
        try {
            LocalEncounterStorage(file, throwOnCorruption = true) { key }.isEncountered(1)
        } catch (_: GeneralSecurityException) {
            threw = true
        }
        assertTrue(threw)

        val safeReader = LocalEncounterStorage(file, throwOnCorruption = false) { key }
        assertFalse(safeReader.isEncountered(1))
        assertTrue(safeReader.getAllEncounters().isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun clearAllAndUninstallFileDeletionWipeData() {
        val file = File(tempFolder.root, "scandex_encounters.bin")
        val key = newKey()
        val storage = LocalEncounterStorage(file) { key }

        storage.registerEncounter(4, 500L)
        storage.clearAll()
        assertFalse(file.exists())
        assertNull(storage.getEncounter(4))

        storage.registerEncounter(7, 600L)
        assertTrue(file.delete())
        val afterUninstall = LocalEncounterStorage(file) { newKey() }
        assertTrue(afterUninstall.getAllEncounters().isEmpty())
    }

    @Test
    fun rejectsInvalidIdAndHandlesConcurrency() {
        val file = File(tempFolder.root, "scandex_encounters.bin")
        val key = newKey()
        val storage = LocalEncounterStorage(file) { key }

        assertFalse(storage.isEncountered(0))
        var threw = false
        try {
            storage.registerEncounter(0)
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
                    storage.registerEncounter(id, id * 100L)
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        val reloaded = LocalEncounterStorage(file) { key }
        assertEquals((1..total).toSet(), reloaded.getEncounteredIds())
    }
}
