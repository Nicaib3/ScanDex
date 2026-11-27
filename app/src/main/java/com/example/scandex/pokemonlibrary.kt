package com.example.scandex

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class pokemonlibrary : AppCompatActivity() {

    private lateinit var pokemonContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutTeamPickerBar: LinearLayout
    private lateinit var tvTeamPickerCount: TextView
    private val executor: ExecutorService = Executors.newFixedThreadPool(6)
    private val favoriteIds = mutableSetOf<Int>()
    private var isTeamPickerMode = false
    private val selectedTeamIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pokemonlibrary)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        pokemonContainer = findViewById(R.id.pokemonContainer)
        progressBar = findViewById(R.id.progressBar)
        layoutTeamPickerBar = findViewById(R.id.layoutTeamPickerBar)
        tvTeamPickerCount = findViewById(R.id.tvTeamPickerCount)

        isTeamPickerMode = intent.getBooleanExtra(EXTRA_TEAM_PICKER_MODE, false)
        intent.getIntArrayExtra(EXTRA_SELECTED_POKEMON_IDS)?.forEach { id ->
            if (id > 0 && id !in selectedTeamIds && selectedTeamIds.size < PokemonTeamStore.MAX_TEAM_SIZE) {
                selectedTeamIds.add(id)
            }
        }
        configurarModoSeleccionEquipo()

        loadGen1Pokemon()
    }

    override fun onResume() {
        super.onResume()
        if (!isTeamPickerMode) {
            refreshEncounterStates()
        }
    }

    private fun refreshEncounterStates() {
        for (i in 0 until pokemonContainer.childCount) {
            val tagView = pokemonContainer.getChildAt(i)
            val pokemonId = tagView.tag as? Int ?: continue
            applyLockState(tagView, estaBloqueado(pokemonId))
        }
    }

    private fun configurarModoSeleccionEquipo() {
        layoutTeamPickerBar.visibility = if (isTeamPickerMode) View.VISIBLE else View.GONE
        if (!isTeamPickerMode) return
        actualizarContadorEquipo()
        findViewById<Button>(R.id.btnTeamPickerDone).setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_POKEMON_IDS, selectedTeamIds.toIntArray()))
            finish()
        }
    }

    private fun actualizarContadorEquipo() {
        tvTeamPickerCount.text = "Seleccionados: ${selectedTeamIds.size} / ${PokemonTeamStore.MAX_TEAM_SIZE}"
    }
    private fun loadGen1Pokemon() {
        progressBar.visibility = View.VISIBLE
        executor.execute {
            try {
                PokemonEncounterStore.preload(this)
                val pokemonList = PokeApiService.fetchGen1PokemonList()
                enPantalla {
                    progressBar.visibility = View.GONE
                    populatePokemonPanels(pokemonList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar la lista de Pokémon", e)
                enPantalla {
                    progressBar.visibility = View.GONE
                    mostrarMensaje("No se pudo conectar con la PokéAPI")
                }
            }
        }
    }

    private fun populatePokemonPanels(pokemonList: List<PokemonSummary>) {
        val inflater = LayoutInflater.from(this)
        pokemonContainer.removeAllViews()

        for (pokemon in pokemonList) {
            val tagView = inflater.inflate(R.layout.pokemon_tag, pokemonContainer, false)
            bindInitialPanel(tagView, pokemon)
            pokemonContainer.addView(tagView)
            loadPokemonDetails(tagView, pokemon.id)
        }
    }

    private fun bindInitialPanel(tagView: View, pokemon: PokemonSummary) {
        tagView.tag = pokemon.id
        tagView.findViewById<TextView>(R.id.tvPokenum).text =
            String.format(Locale.US, "#%03d", pokemon.id)
        tagView.findViewById<TextView>(R.id.tvPokename).text = pokemon.name
        tagView.findViewById<TextView>(R.id.tvDescripcionPokemon).text = "Generación I"

        val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
        val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)
        val btnFav = tagView.findViewById<ImageButton>(R.id.btnfav)
        val btnTeamToggle = tagView.findViewById<TextView>(R.id.btnTeamToggle)

        tvType1.text = "..."
        tvType2.text = ""
        tvType2.visibility = View.GONE
        applyLockState(tagView, estaBloqueado(pokemon.id))

        if (isTeamPickerMode) {
            btnFav.visibility = View.GONE
            btnTeamToggle.visibility = View.VISIBLE
            pintarBotonEquipo(btnTeamToggle, pokemon.id in selectedTeamIds)
            val toggleAction = View.OnClickListener {
                alternarPokemonEnEquipo(pokemon.id, btnTeamToggle)
            }
            btnTeamToggle.setOnClickListener(toggleAction)
            tagView.setOnClickListener(toggleAction)
        } else {
            btnFav.visibility = View.VISIBLE
            btnTeamToggle.visibility = View.GONE
            configurarFavorito(tagView, pokemon.id)
            tagView.setOnClickListener {
                openPokeInfo(pokemon.id, pokemon.name, null, null)
            }
        }
    }

    private fun alternarPokemonEnEquipo(pokemonId: Int, btnTeamToggle: TextView) {
        if (!selectedTeamIds.remove(pokemonId)) {
            if (selectedTeamIds.size >= PokemonTeamStore.MAX_TEAM_SIZE) {
                Toast.makeText(this, "Máximo ${PokemonTeamStore.MAX_TEAM_SIZE} Pokémon por equipo", Toast.LENGTH_SHORT).show()
                return
            }
            selectedTeamIds.add(pokemonId)
        }
        pintarBotonEquipo(btnTeamToggle, pokemonId in selectedTeamIds)
        actualizarContadorEquipo()
    }

    private fun pintarBotonEquipo(btnTeamToggle: TextView, seleccionado: Boolean) {
        btnTeamToggle.text = if (seleccionado) "-" else "+"
        btnTeamToggle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (seleccionado) "#E53935" else "#43A047"))
        }
    }

    private fun loadPokemonDetails(tagView: View, pokemonId: Int) {
        executor.execute {
            try {
                val detail = PokeApiService.fetchPokemonDetail(pokemonId)
                val bitmap = detail.spriteUrl?.let { PokeApiService.downloadBitmap(it) }

                enPantalla {
                    tagView.findViewById<TextView>(R.id.tvPokename).text = detail.name
                    tagView.findViewById<TextView>(R.id.tvDescripcionPokemon).text = String.format(
                        Locale.US,
                        "Altura: %.1f m • Peso: %.1f kg",
                        detail.heightMeters,
                        detail.weightKg
                    )
                    if (bitmap != null) {
                        tagView.findViewById<ImageView>(R.id.ivPokeimage).setImageBitmap(bitmap)
                    }

                    mostrarTipos(tagView, detail.types)
                    applyLockState(tagView, estaBloqueado(detail.id))

                    if (!isTeamPickerMode) {
                        tagView.setOnClickListener {
                            openPokeInfo(
                                detail.id,
                                detail.name,
                                detail.types.getOrNull(0),
                                detail.types.getOrNull(1)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar detalle de #$pokemonId", e)
            }
        }
    }

    private fun mostrarTipos(tagView: View, types: List<String>) {
        val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
        val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)

        val tipo1 = types.getOrNull(0)
        if (tipo1 != null) {
            tvType1.text = tipo1
            tvType1.visibility = View.VISIBLE
        } else {
            tvType1.visibility = View.GONE
        }

        val tipo2 = types.getOrNull(1)
        if (tipo2 != null) {
            tvType2.text = tipo2
            tvType2.visibility = View.VISIBLE
        } else {
            tvType2.text = ""
            tvType2.visibility = View.GONE
        }
    }

    // En modo selector todos los 151 están desbloqueados; en modo normal depende de PokemonEncounterStore
    private fun estaBloqueado(pokemonId: Int): Boolean =
        if (isTeamPickerMode) false else !PokemonEncounterStore.isEncountered(this, pokemonId)

    private fun applyLockState(tagView: View, isLocked: Boolean) {
        val tvName = tagView.findViewById<TextView>(R.id.tvPokename)
        val tvDesc = tagView.findViewById<TextView>(R.id.tvDescripcionPokemon)
        val ivImage = tagView.findViewById<ImageView>(R.id.ivPokeimage)
        val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
        val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)

        if (isLocked) {
            ivImage.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            tvName.visibility = View.INVISIBLE
            tvDesc.visibility = View.INVISIBLE
            tvType1.visibility = View.INVISIBLE
            tvType2.visibility = View.INVISIBLE
        } else {
            ivImage.clearColorFilter()
            tvName.visibility = View.VISIBLE
            tvDesc.visibility = View.VISIBLE
            tvType1.visibility = View.VISIBLE
            tvType2.visibility = if (tvType2.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun configurarFavorito(tagView: View, pokemonId: Int) {
        val btnFav = tagView.findViewById<ImageButton>(R.id.btnfav)
        btnFav.setOnClickListener {
            val esFavorito = if (favoriteIds.contains(pokemonId)) {
                favoriteIds.remove(pokemonId)
                false
            } else {
                favoriteIds.add(pokemonId)
                true
            }
            val icono = if (esFavorito) {
                android.R.drawable.btn_star_big_on
            } else {
                android.R.drawable.btn_star_big_off
            }
            btnFav.setImageResource(icono)
        }
    }

    private fun openPokeInfo(
        id: Int,
        name: String,
        tipo1: String?,
        tipo2: String?
    ) {
        if (estaBloqueado(id)) {
            mostrarMensaje("Este Pokémon está bloqueado")
            return
        }
        val intent = Intent(this, pokeinfo::class.java)
        intent.putExtra("pokemonId", id)
        intent.putExtra("nombre", name)
        if (!tipo1.isNullOrBlank()) intent.putExtra("tipo1", tipo1)
        if (!tipo2.isNullOrBlank()) intent.putExtra("tipo2", tipo2)
        startActivity(intent)
    }

    private fun enPantalla(accion: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                accion()
            }
        }
    }

    private fun mostrarMensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "PokemonLibrary"
        const val EXTRA_TEAM_PICKER_MODE = "teamPickerMode"
        const val EXTRA_SELECTED_POKEMON_IDS = "selectedPokemonIds"
    }
}
