package com.example.scandex

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
class pokeinfo : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokeinfo)

        val mainScrollView: ScrollView = findViewById(R.id.main)

        // Placeholder actual para Greninja (Tipo 1: WATER, Tipo 2: DARK)
        // A futuro, estos valores vendrán del Intent de la biblioteca
        val tipo1 = intent.getStringExtra("tipo1") ?: "WATER"
        val tipo2 = intent.getStringExtra("tipo2") ?: "DARK"

        aplicarFondoPorTipos(mainScrollView, tipo1, tipo2)
    }
    private fun aplicarFondoPorTipos(contenedor: ScrollView, tipo1: String, tipo2: String?) {
        val colorInicio = obtenerColorDeTipo(tipo1)
        // Si el Pokémon solo tiene 1 tipo (tipo2 vacío o nulo), repite el mismo color
        val colorFin = if (!tipo2.isNullOrBlank()) {
            obtenerColorDeTipo(tipo2)
        } else {
            colorInicio
        }
        val degradado = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colorInicio, colorFin)
        )
        contenedor.background = degradado
    }
    private fun obtenerColorDeTipo(tipo: String): Int {
        val colorResId = when (tipo.trim().uppercase()) {
            "NORMAL" -> R.color.type_normal
            "FIRE", "FUEGO" -> R.color.type_fire
            "WATER", "AGUA" -> R.color.type_water
            "ELECTRIC", "ELECTRICO" -> R.color.type_electric
            "GRASS", "PLANTA" -> R.color.type_grass
            "ICE", "HIELO" -> R.color.type_ice
            "FIGHTING", "LUCHA" -> R.color.type_fighting
            "POISON", "VENENO" -> R.color.type_poison
            "GROUND", "TIERRA" -> R.color.type_ground
            "FLYING", "VOLADOR" -> R.color.type_flying
            "PSYCHIC", "PSIQUICO" -> R.color.type_psychic
            "BUG", "BICHO" -> R.color.type_bug
            "ROCK", "ROCA" -> R.color.type_rock
            "GHOST", "FANTASMA" -> R.color.type_ghost
            "DRAGON" -> R.color.type_dragon
            "DARK", "SINIESTRO" -> R.color.type_dark
            "STEEL", "ACERO" -> R.color.type_steel
            "FAIRY", "HADA" -> R.color.type_fairy
            else -> R.color.type_normal
        }
        return ContextCompat.getColor(this, colorResId)
    }
}