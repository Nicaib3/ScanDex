package com.example.scandex

import android.content.Context
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Paleta y catálogo compartido de tipos Pokémon para Biblioteca, Favoritos y Detalle.
 */
object PokemonTypeStyle {

    val ALL_TYPES: List<String> = listOf(
        "NORMAL", "FIRE", "WATER", "ELECTRIC", "GRASS", "ICE",
        "FIGHTING", "POISON", "GROUND", "FLYING", "PSYCHIC", "BUG",
        "ROCK", "GHOST", "DRAGON", "DARK", "STEEL", "FAIRY"
    )

    private val TYPE_COLOR_RES: Map<String, Int> = mapOf(
        "NORMAL" to R.color.type_normal,
        "FIRE" to R.color.type_fire,
        "FUEGO" to R.color.type_fire,
        "WATER" to R.color.type_water,
        "AGUA" to R.color.type_water,
        "ELECTRIC" to R.color.type_electric,
        "ELECTRICO" to R.color.type_electric,
        "GRASS" to R.color.type_grass,
        "PLANTA" to R.color.type_grass,
        "ICE" to R.color.type_ice,
        "HIELO" to R.color.type_ice,
        "FIGHTING" to R.color.type_fighting,
        "LUCHA" to R.color.type_fighting,
        "POISON" to R.color.type_poison,
        "VENENO" to R.color.type_poison,
        "GROUND" to R.color.type_ground,
        "TIERRA" to R.color.type_ground,
        "FLYING" to R.color.type_flying,
        "VOLADOR" to R.color.type_flying,
        "PSYCHIC" to R.color.type_psychic,
        "PSIQUICO" to R.color.type_psychic,
        "BUG" to R.color.type_bug,
        "BICHO" to R.color.type_bug,
        "ROCK" to R.color.type_rock,
        "ROCA" to R.color.type_rock,
        "GHOST" to R.color.type_ghost,
        "FANTASMA" to R.color.type_ghost,
        "DRAGON" to R.color.type_dragon,
        "DARK" to R.color.type_dark,
        "SINIESTRO" to R.color.type_dark,
        "STEEL" to R.color.type_steel,
        "ACERO" to R.color.type_steel,
        "FAIRY" to R.color.type_fairy,
        "HADA" to R.color.type_fairy
    )

    fun colorRes(typeName: String): Int =
        TYPE_COLOR_RES[typeName.trim().uppercase(Locale.US)] ?: R.color.type_normal

    fun colorInt(context: Context, typeName: String): Int =
        ContextCompat.getColor(context, colorRes(typeName))

    fun displayLabel(typeName: String): String =
        typeName.trim().lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
}
