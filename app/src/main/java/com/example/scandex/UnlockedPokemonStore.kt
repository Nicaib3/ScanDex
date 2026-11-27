package com.example.scandex

import android.content.Context

object UnlockedPokemonStore {
    private const val PREFS = "scandex_unlocked_prefs"
    private const val KEY_IDS = "unlocked_ids"

    fun unlock(context: Context, pokemonId: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val actual = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        actual.add(pokemonId.toString())
        prefs.edit().putStringSet(KEY_IDS, actual).apply()
    }

    fun isUnlocked(context: Context, pokemonId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_IDS, emptySet())?.contains(pokemonId.toString()) == true
    }
}