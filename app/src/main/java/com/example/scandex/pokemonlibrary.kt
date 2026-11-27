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
                val pokemonList = PokeApiService.fetchGen1PokemonList()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    populatePokemonPanels(pokemonList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar la lista de Pokémon", e)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "No se pudo conectar con la PokéAPI",
                        Toast.LENGTH_LONG
                    ).show()
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
        val tvNum = tagView.findViewById<TextView>(R.id.tvPokenum)
        val tvName = tagView.findViewById<TextView>(R.id.tvPokename)
        val tvDesc = tagView.findViewById<TextView>(R.id.tvDescripcionPokemon)
        val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
        val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)
        val ivImage = tagView.findViewById<ImageView>(R.id.ivPokeimage)
        val btnFav = tagView.findViewById<ImageButton>(R.id.btnfav)
        val btnTeamToggle = tagView.findViewById<TextView>(R.id.btnTeamToggle)

        tvNum.text = String.format(Locale.US, "#%03d", pokemon.id)
        tvName.text = pokemon.name
        tvDesc.text = "Generación I"
        tvType1.text = "..."
        tvType2.visibility = View.GONE
        applyLockState(if (isTeamPickerMode) false else pokemon.isLocked, tvName, tvDesc, ivImage)

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
            btnFav.setOnClickListener {
                val isFav = if (favoriteIds.contains(pokemon.id)) {
                    favoriteIds.remove(pokemon.id)
                    false
                } else {
                    favoriteIds.add(pokemon.id)
                    true
                }
                btnFav.setImageResource(
                    if (isFav) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )
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

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val tvName = tagView.findViewById<TextView>(R.id.tvPokename)
                    val tvDesc = tagView.findViewById<TextView>(R.id.tvDescripcionPokemon)
                    val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
                    val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)
                    val ivImage = tagView.findViewById<ImageView>(R.id.ivPokeimage)

                    tvName.text = detail.name
                    tvDesc.text = String.format(
                        Locale.US,
                        "Altura: %.1f m • Peso: %.1f kg",
                        detail.heightMeters,
                        detail.weightKg
                    )

                    if (detail.types.isNotEmpty()) {
                        tvType1.visibility = View.VISIBLE
                        tvType1.text = detail.types[0]
                    } else {
                        tvType1.visibility = View.GONE
                    }

                    if (detail.types.size > 1) {
                        tvType2.visibility = View.VISIBLE
                        tvType2.text = detail.types[1]
                    } else {
                        tvType2.visibility = View.GONE
                    }

                    if (bitmap != null) {
                        ivImage.setImageBitmap(bitmap)
                    }
                    applyLockState(if (isTeamPickerMode) false else detail.isLocked, tvName, tvDesc, ivImage)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar detalle de #$pokemonId", e)
            }
        }
    }

    private fun applyLockState(
        isLocked: Boolean,
        tvName: TextView,
        tvDesc: TextView,
        ivImage: ImageView
    ) {
        if (isLocked) {
            ivImage.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            tvName.visibility = View.INVISIBLE
            tvDesc.visibility = View.INVISIBLE
        } else {
            ivImage.clearColorFilter()
            tvName.visibility = View.VISIBLE
            tvDesc.visibility = View.VISIBLE
        }
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