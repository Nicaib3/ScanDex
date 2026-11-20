package com.example.scandex

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
    private val executor: ExecutorService = Executors.newFixedThreadPool(6)
    private val favoriteIds = mutableSetOf<Int>()

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

        loadGen1Pokemon()
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

        tvNum.text = String.format(Locale.US, "#%03d", pokemon.id)
        tvName.text = pokemon.name
        tvDesc.text = "Generación I"
        tvType1.text = "..."
        tvType2.visibility = View.GONE
        applyLockState(pokemon.isLocked, tvName, tvDesc, ivImage)

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
                    applyLockState(detail.isLocked, tvName, tvDesc, ivImage)
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
    }
}