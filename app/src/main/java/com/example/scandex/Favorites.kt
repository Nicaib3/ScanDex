package com.example.scandex

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pantalla de Pokémon favoritos: aplica la misma presentación y bloqueo que la Biblioteca,
 * pero iterando sobre los IDs guardados en almacenamiento local cifrado en lugar de los 151 completos.
 */
class Favorites : AppCompatActivity() {

    private lateinit var favoritesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnFilterTypes: TextView
    private lateinit var etSearch: EditText

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val detailsById = ConcurrentHashMap<Int, PokemonDetail>()
    private val spritesById = ConcurrentHashMap<Int, Bitmap>()
    private val viewsById = mutableMapOf<Int, View>()

    private var favoriteIds: List<Int> = emptyList()
    private var unlockedIds: Set<Int> = emptySet()
    private var selectedType: String? = null
    private var currentQuery: String = ""
    private var skipNextResume = true
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_favorites)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        favoritesContainer = findViewById(R.id.favoritesContainer)
        progressBar = findViewById(R.id.progressBarFavorites)
        tvEmpty = findViewById(R.id.tvEmptyFavorites)
        btnFilterTypes = findViewById(R.id.favTypes)
        etSearch = findViewById(R.id.etFavSearch)

        configurarFiltroTipos()
        configurarBusqueda()
        loadFavoritesFromLocalStorage()
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResume) {
            skipNextResume = false
            return
        }
        executor.execute {
            PokemonEncounterStore.preload(this)
            PokemonFavoriteStore.preload(this)
            val currentFavs = PokemonFavoriteStore.getFavoriteIds(this).toList()
            val currentUnlocked = PokemonEncounterStore.getEncounteredIds(this)
            runOnUiThreadIfAlive {
                val idsChanged = currentFavs != favoriteIds
                unlockedIds = currentUnlocked
                if (idsChanged) {
                    favoriteIds = currentFavs
                    renderFilteredCards()
                    fetchMissingDetails(currentFavs, loadGeneration)
                } else {
                    renderFilteredCards()
                }
            }
        }
    }

    private fun configurarFiltroTipos() {
        btnFilterTypes.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(0, 0, 0, "Todos")
                PokemonTypeStyle.ALL_TYPES.forEachIndexed { index, typeName ->
                    menu.add(0, index + 1, index + 1, PokemonTypeStyle.displayLabel(typeName))
                }
                setOnMenuItemClickListener { item ->
                    if (item.itemId == 0) {
                        selectedType = null
                        btnFilterTypes.text = "Tipo  ▾"
                    } else {
                        val type = PokemonTypeStyle.ALL_TYPES[item.itemId - 1]
                        selectedType = type
                        btnFilterTypes.text = "${PokemonTypeStyle.displayLabel(type)}  ▾"
                    }
                    renderFilteredCards()
                    true
                }
            }.show()
        }
    }

    private fun configurarBusqueda() {
        findViewById<ImageView>(R.id.ivFavSearch).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            if (etSearch.visibility == View.VISIBLE) {
                etSearch.setText("")
                etSearch.visibility = View.GONE
                imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
            } else {
                etSearch.visibility = View.VISIBLE
                etSearch.requestFocus()
                imm?.showSoftInput(etSearch, 0)
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                renderFilteredCards()
            }
        })
    }

    private fun loadFavoritesFromLocalStorage() {
        val generation = ++loadGeneration
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        executor.execute {
            PokemonEncounterStore.preload(this)
            PokemonFavoriteStore.preload(this)
            val savedFavorites = PokemonFavoriteStore.getFavoriteIds(this).toList()
            val savedUnlocked = PokemonEncounterStore.getEncounteredIds(this)

            runOnUiThreadIfAlive {
                if (generation != loadGeneration) return@runOnUiThreadIfAlive
                progressBar.visibility = View.GONE
                favoriteIds = savedFavorites
                unlockedIds = savedUnlocked
                renderFilteredCards()
                fetchMissingDetails(savedFavorites, generation)
            }
        }
    }

    private fun fetchMissingDetails(ids: List<Int>, generation: Int) {
        val pendingIds = ids.filter { !detailsById.containsKey(it) }
        if (pendingIds.isEmpty()) return

        val remaining = AtomicInteger(pendingIds.size)
        val failures = AtomicInteger(0)

        for (pokemonId in pendingIds) {
            executor.execute {
                try {
                    val detail = PokeApiService.fetchPokemonDetail(pokemonId)
                    detailsById[pokemonId] = detail
                    val sprite = detail.spriteUrl?.let { url ->
                        runCatching { PokeApiService.downloadBitmap(url) }.getOrNull()
                    }
                    if (sprite != null) {
                        spritesById[pokemonId] = sprite
                    }
                    runOnUiThreadIfAlive {
                        if (generation != loadGeneration || pokemonId !in favoriteIds) return@runOnUiThreadIfAlive
                        if (selectedType != null || currentQuery.isNotBlank()) {
                            renderFilteredCards()
                        } else {
                            viewsById[pokemonId]?.let { bindCard(it, pokemonId) }
                        }
                    }
                } catch (e: Exception) {
                    failures.incrementAndGet()
                    Log.w(TAG, "Error al cargar favorito #$pokemonId", e)
                } finally {
                    if (remaining.decrementAndGet() == 0 && failures.get() == pendingIds.size) {
                        runOnUiThreadIfAlive {
                            if (generation == loadGeneration && favoriteIds.isNotEmpty()) {
                                Toast.makeText(
                                    this,
                                    "No se pudo conectar con la PokéAPI para actualizar los detalles",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderFilteredCards() {
        favoritesContainer.removeAllViews()
        viewsById.clear()

        if (favoriteIds.isEmpty()) {
            tvEmpty.text = "Aún no tienes Pokémon favoritos.\nMárcalos desde la Biblioteca para verlos aquí."
            tvEmpty.visibility = View.VISIBLE
            return
        }

        val visibleIds = filterFavoritePokemonIds(
            favoriteIds = favoriteIds,
            unlockedIds = unlockedIds,
            detailsById = detailsById,
            selectedType = selectedType,
            query = currentQuery
        )

        if (visibleIds.isEmpty()) {
            tvEmpty.text = "No hay Pokémon favoritos que coincidan con el filtro."
            tvEmpty.visibility = View.VISIBLE
            return
        }

        tvEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        for (pokemonId in visibleIds) {
            val card = inflater.inflate(R.layout.pokemon_tag, favoritesContainer, false)
            viewsById[pokemonId] = card
            bindCard(card, pokemonId)
            favoritesContainer.addView(card)
        }
    }

    private fun bindCard(tagView: View, pokemonId: Int) {
        val detail = detailsById[pokemonId]
        val isLocked = pokemonId !in unlockedIds

        val tvNum = tagView.findViewById<TextView>(R.id.tvPokenum)
        val tvName = tagView.findViewById<TextView>(R.id.tvPokename)
        val tvDesc = tagView.findViewById<TextView>(R.id.tvDescripcionPokemon)
        val ivImage = tagView.findViewById<ImageView>(R.id.ivPokeimage)
        val btnFav = tagView.findViewById<ImageButton>(R.id.btnfav)
        val tvType1 = tagView.findViewById<TextView>(R.id.tvtype1)
        val tvType2 = tagView.findViewById<TextView>(R.id.tvtype2)

        tagView.tag = pokemonId
        tvNum.text = String.format(Locale.US, "#%03d", pokemonId)

        btnFav.visibility = View.VISIBLE
        btnFav.setImageResource(android.R.drawable.btn_star_big_on)
        btnFav.setOnClickListener {
            PokemonFavoriteStore.setFavorite(this, pokemonId, false)
            favoriteIds = favoriteIds.filterNot { it == pokemonId }
            renderFilteredCards()
        }

        val bitmap = spritesById[pokemonId]
        if (bitmap != null) {
            ivImage.setImageBitmap(bitmap)
        } else {
            ivImage.setImageResource(R.mipmap.ic_launcher)
        }

        if (isLocked) {
            tvName.text = "???"
            tvDesc.text = "Escanea este Pokémon para desbloquear"
            ivImage.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            bindLockedTypes(tvType1, tvType2)
            tagView.setOnClickListener {
                Toast.makeText(this, "Este Pokémon está bloqueado", Toast.LENGTH_LONG).show()
            }
        } else {
            ivImage.clearColorFilter()
            tvName.text = detail?.name ?: "Pokémon #$pokemonId"
            tvDesc.text = if (detail != null) {
                String.format(Locale.US, "Altura: %.1f m • Peso: %.1f kg", detail.heightMeters, detail.weightKg)
            } else {
                "Cargando..."
            }
            bindUnlockedTypes(tvType1, tvType2, detail?.types.orEmpty())
            tagView.setOnClickListener {
                openPokeInfo(
                    id = pokemonId,
                    name = detail?.name ?: "Pokémon #$pokemonId",
                    tipo1 = detail?.types?.getOrNull(0),
                    tipo2 = detail?.types?.getOrNull(1)
                )
            }
        }
    }

    private fun bindLockedTypes(tvType1: TextView, tvType2: TextView) {
        tvType1.visibility = View.VISIBLE
        tvType1.text = "???"
        ViewCompat.setBackgroundTintList(tvType1, ColorStateList.valueOf(Color.parseColor("#8C8C8C")))
        tvType2.visibility = View.GONE
    }

    private fun bindUnlockedTypes(tvType1: TextView, tvType2: TextView, types: List<String>) {
        listOf(tvType1 to types.getOrNull(0), tvType2 to types.getOrNull(1)).forEach { (badge, type) ->
            if (type != null) {
                badge.visibility = View.VISIBLE
                badge.text = type.uppercase(Locale.US)
                ViewCompat.setBackgroundTintList(
                    badge,
                    ColorStateList.valueOf(PokemonTypeStyle.colorInt(this, type))
                )
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    private fun openPokeInfo(id: Int, name: String, tipo1: String?, tipo2: String?) {
        startActivity(
            Intent(this, pokeinfo::class.java).apply {
                putExtra("pokemonId", id)
                putExtra("nombre", name)
                putExtra("tipo1", tipo1)
                putExtra("tipo2", tipo2)
            }
        )
    }

    private inline fun runOnUiThreadIfAlive(crossinline block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                block()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "Favorites"
    }
}
