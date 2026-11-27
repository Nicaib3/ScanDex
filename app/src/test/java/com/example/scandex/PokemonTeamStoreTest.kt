package com.example.scandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class PokemonTeamStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun member(id: Int, name: String, types: List<String> = listOf("FIRE")) =
        PokemonTeamMember(id, name, "https://example.com/$id.png", types, 80, 90, 70, 100, 85, 95)

    private fun newStorage(
        file: File = File(tempFolder.root, "scandex_teams.bin"),
        key: SecretKey = newKey(),
        throwOnCorruption: Boolean = false
    ) = LocalTeamStorage(file, throwOnCorruption) { key }

    @Test
    fun saveSearchEditAndDeleteTeams() {
        val storage = newStorage()
        val four = listOf(
            member(6, "Charizard", listOf("FIRE", "FLYING")),
            member(9, "Blastoise", listOf("WATER")),
            member(25, "Pikachu", listOf("ELECTRIC")),
            member(94, "Gengar", listOf("GHOST", "POISON"))
        )
        val created = storage.saveTeam(null, "Liga Kanto", four, nowMillis = 1_000L)
        assertEquals(4, created.members.size)

        val updated = storage.saveTeam(created.id, "Liga Kanto Pro", four + member(143, "Snorlax", listOf("NORMAL")), 2_000L)
        assertEquals(created.id, updated.id)
        assertEquals(5, updated.members.size)
        assertEquals(1, storage.searchTeams("snorlax").size)
        assertEquals(0, storage.searchTeams("mewtwo").size)

        assertTrue(storage.deleteTeam(created.id))
        assertNull(storage.getTeamById(created.id))
        assertFalse(storage.deleteTeam(created.id))
    }

    @Test
    fun rejectsInvalidTeamSizes() {
        val storage = newStorage()
        assertTrue(runCatching { storage.saveTeam(null, "Vacío", emptyList()) }.isFailure)
        assertTrue(runCatching { storage.saveTeam(null, "Grande", (1..7).map { member(it, "P$it") }) }.isFailure)
    }

    @Test
    fun persistsEncryptedTeamsAcrossRestartsAndRejectsTampering() {
        val file = File(tempFolder.root, "scandex_teams.bin")
        val key = newKey()

        val saved = newStorage(file, key).saveTeam(
            null,
            "Equipo Cifrado",
            listOf(member(150, "Mewtwo", listOf("PSYCHIC"))),
            500L
        )

        assertTrue(file.exists())
        val rawText = file.readBytes().toString(Charsets.UTF_8)
        assertFalse(rawText.contains("Equipo Cifrado"))
        assertFalse(rawText.contains("Mewtwo"))

        val loaded = newStorage(file, key).getTeamById(saved.id)
        assertEquals("Equipo Cifrado", loaded?.name)
        assertEquals(150, loaded?.members?.single()?.pokemonId)

        assertTrue(runCatching { newStorage(file, newKey(), throwOnCorruption = true).getAllTeams() }.exceptionOrNull() is GeneralSecurityException)

        val tampered = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0xFF).toByte() }
        file.writeBytes(tampered)
        assertTrue(newStorage(file, key, throwOnCorruption = false).getAllTeams().isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun calculatesStatsAndTypeBalance() {
        val members = listOf(
            member(6, "Charizard", listOf("FIRE", "FLYING")),
            member(9, "Blastoise", listOf("WATER")),
            member(18, "Pidgeot", listOf("NORMAL", "FLYING"))
        )
        val stats = TeamPokeApiHelper.calculateTeamStats(members)
        assertEquals(240, stats.hp)
        assertEquals(1560, stats.total)

        val rel = mapOf(
            "FIRE" to TypeDamageRelations(setOf("WATER", "GROUND", "ROCK"), setOf("FIRE", "GRASS"), emptySet(), setOf("GRASS", "ICE", "BUG", "STEEL")),
            "FLYING" to TypeDamageRelations(setOf("ELECTRIC", "ICE", "ROCK"), setOf("GRASS", "FIGHTING"), setOf("GROUND"), setOf("GRASS", "FIGHTING", "BUG")),
            "WATER" to TypeDamageRelations(setOf("ELECTRIC", "GRASS"), setOf("FIRE", "WATER"), emptySet(), setOf("FIRE", "GROUND", "ROCK")),
            "NORMAL" to TypeDamageRelations(setOf("FIGHTING"), emptySet(), setOf("GHOST"), emptySet())
        )
        val balance = TeamPokeApiHelper.computeTypeBalance(members, rel)
        assertEquals(TypeCountEntry("ELECTRIC", 3), balance.weaknesses[0])
        assertEquals(TypeCountEntry("ICE", 2), balance.weaknesses[1])
        assertEquals(TypeCountEntry("ROCK", 2), balance.weaknesses[2])
        assertEquals(TypeCountEntry("BUG", 2), balance.strengths[0])
    }
}
