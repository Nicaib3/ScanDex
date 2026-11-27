package com.example.scandex

import org.junit.Assert.assertEquals
import org.junit.Test

class OthersApiHelperTest {

    private val sampleEntries = listOf(
        OtherEntrySummary(1, "leftovers", "Leftovers", OtherEntryType.ITEM, "https://pokeapi.co/api/v2/item/211/", "https://img/leftovers.png"),
        OtherEntrySummary(2, "master-ball", "Master Ball", OtherEntryType.ITEM, "https://pokeapi.co/api/v2/item/1/", "https://img/master-ball.png"),
        OtherEntrySummary(3, "intimidate", "Intimidate", OtherEntryType.ABILITY, "https://pokeapi.co/api/v2/ability/22/", null),
        OtherEntrySummary(4, "solar-power", "Solar Power", OtherEntryType.ABILITY, "https://pokeapi.co/api/v2/ability/94/", null)
    )

    @Test
    fun formatSlug_formatsHyphenatedNames() {
        assertEquals("Master Ball", OthersApiHelper.formatSlug("master-ball"))
        assertEquals("Solar Power", OthersApiHelper.formatSlug("solar-power"))
        assertEquals("Leftovers", OthersApiHelper.formatSlug("leftovers"))
    }

    @Test
    fun filterEntries_filtersByCategoryAndQuery() {
        assertEquals(4, OthersApiHelper.filterEntries(sampleEntries, null, "").size)
        assertEquals(listOf("Leftovers", "Master Ball"), OthersApiHelper.filterEntries(sampleEntries, OtherEntryType.ITEM, "").map { it.displayName })
        assertEquals(listOf("Intimidate", "Solar Power"), OthersApiHelper.filterEntries(sampleEntries, OtherEntryType.ABILITY, "").map { it.displayName })
        assertEquals(listOf("Master Ball"), OthersApiHelper.filterEntries(sampleEntries, null, "master-b").map { it.displayName })
        assertEquals(listOf("Solar Power"), OthersApiHelper.filterEntries(sampleEntries, OtherEntryType.ABILITY, "solar").map { it.displayName })
        assertEquals(emptyList<OtherEntrySummary>(), OthersApiHelper.filterEntries(sampleEntries, OtherEntryType.ITEM, "intimidate"))
    }
}
