package com.example.scandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonTeamStoreTest {

    private fun member(id: Int, name: String, types: List<String> = listOf("FIRE")) =
        PokemonTeamMember(id, name, "https://example.com/$id.png", types, 80, 90, 70, 100, 85, 95)

    private fun newManager(): TeamStoreManager {
        var data = emptyList<PokemonTeam>()
        return TeamStoreManager(loadFn = { data }, saveFn = { data = it })
    }

    @Test
    fun saveSearchEditAndDeleteTeams() {
        val manager = newManager()
        val four = listOf(
            member(6, "Charizard", listOf("FIRE", "FLYING")),
            member(9, "Blastoise", listOf("WATER")),
            member(25, "Pikachu", listOf("ELECTRIC")),
            member(94, "Gengar", listOf("GHOST", "POISON"))
        )
        val created = manager.saveTeam(null, "Liga Kanto", four, nowMillis = 1_000L)
        assertEquals(4, created.members.size)

        val updated = manager.saveTeam(created.id, "Liga Kanto Pro", four + member(143, "Snorlax", listOf("NORMAL")), 2_000L)
        assertEquals(created.id, updated.id)
        assertEquals(5, updated.members.size)
        assertEquals(1, manager.searchTeams("snorlax").size)
        assertEquals(0, manager.searchTeams("mewtwo").size)

        assertTrue(manager.deleteTeam(created.id))
        assertNull(manager.getTeamById(created.id))
        assertFalse(manager.deleteTeam(created.id))
    }

    @Test
    fun rejectsInvalidTeamSizes() {
        val manager = newManager()
        assertTrue(runCatching { manager.saveTeam(null, "Vacío", emptyList()) }.isFailure)
        assertTrue(runCatching { manager.saveTeam(null, "Grande", (1..7).map { member(it, "P$it") }) }.isFailure)
    }

    @Test
    fun binarySerializationRoundTrip() {
        val teams = listOf(PokemonTeam("t1", "Equipo 6", (1..6).map { member(it, "P$it", listOf("GRASS", "POISON")) }, 10L, 20L))
        assertEquals(teams, PokemonTeamStore.deserialize(PokemonTeamStore.serialize(teams)))

        var raw: ByteArray? = null
        val manager = TeamStoreManager(
            loadFn = { raw?.let(PokemonTeamStore::deserialize) ?: emptyList() },
            saveFn = { raw = PokemonTeamStore.serialize(it) }
        )
        val saved = manager.saveTeam(null, "Cifrable", listOf(member(150, "Mewtwo", listOf("PSYCHIC"))))
        assertNotNull(raw)
        assertEquals("Cifrable", manager.getTeamById(saved.id)?.name)
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
