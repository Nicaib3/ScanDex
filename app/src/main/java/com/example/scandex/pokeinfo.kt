package com.example.scandex

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class pokeinfo : AppCompatActivity() {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val moveDetailsCache = ConcurrentHashMap<String, PokemonMoveDetail>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pokeinfo)

        val mainScrollView: ScrollView = findViewById(R.id.typecolor)
        ViewCompat.setOnApplyWindowInsetsListener(mainScrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val pokemonId = intent.getIntExtra("pokemonId", 658)
        mostrarDatosIniciales(mainScrollView, pokemonId)
        configurarTabs()
        cargarPokemon(pokemonId, mainScrollView)
    }

    private fun mostrarDatosIniciales(mainScrollView: ScrollView, pokemonId: Int) {
        val nombreInicial = intent.getStringExtra("nombre")
        val tipo1Extra = intent.getStringExtra("tipo1")
        val tipo2Extra = intent.getStringExtra("tipo2")

        if (tipo1Extra != null) {
            // Venimos de la biblioteca: ya conocemos los tipos reales
            aplicarFondoPorTipos(mainScrollView, tipo1Extra, tipo2Extra)
            mostrarTipos(listOfNotNull(tipo1Extra, tipo2Extra))
        } else {
            // Venimos del escáner: los tipos llegan después con la PokeAPI
            mostrarTipos(emptyList())
        }
        findViewById<TextView>(R.id.pokenum2).text = String.format(Locale.US, "#%03d", pokemonId)
        if (!nombreInicial.isNullOrBlank()) {
            findViewById<TextView>(R.id.tvpokename2).text = nombreInicial
        }
    }

    private fun configurarTabs() {
        val btnTabInfo = findViewById<TextView>(R.id.btnTabInfo)
        val btnTabMovimientos = findViewById<TextView>(R.id.btnTabMovimientos)
        val layoutInfo = findViewById<LinearLayout>(R.id.layoutSeccionInfo)
        val layoutMovimientos = findViewById<LinearLayout>(R.id.layoutSeccionMovimientos)

        btnTabInfo.setOnClickListener {
            cambiarTab(btnTabInfo, btnTabMovimientos, layoutInfo, layoutMovimientos)
        }
        btnTabMovimientos.setOnClickListener {
            cambiarTab(btnTabMovimientos, btnTabInfo, layoutMovimientos, layoutInfo)
        }
    }

    private fun cambiarTab(
        tabActiva: TextView,
        tabInactiva: TextView,
        seccionVisible: View,
        seccionOculta: View
    ) {
        seccionVisible.visibility = View.VISIBLE
        seccionOculta.visibility = View.GONE
        tabActiva.setBackgroundResource(R.drawable.bg_pokeinfo_card)
        tabActiva.setTextColor(Color.parseColor("#1A1D24"))
        tabInactiva.background = null
        tabInactiva.setTextColor(Color.WHITE)
    }

    private fun cargarPokemon(pokemonId: Int, mainScrollView: ScrollView) {
        // Si la ficha ya se descargó (p. ej. desde el escáner), se pinta al instante
        val fichaEnCache = PokeApiService.fichaEnCache(pokemonId)
        if (fichaEnCache != null) {
            mostrarFicha(mainScrollView, fichaEnCache)
            return
        }

        // Si no (p. ej. desde la biblioteca), se descarga como antes
        executor.execute {
            try {
                val ficha = PokeApiService.obtenerFichaCompleta(pokemonId)
                enPantalla { mostrarFicha(mainScrollView, ficha) }
            } catch (e: Exception) {
                Log.e("PokeInfo", "Error al cargar Pokémon #$pokemonId", e)
                enPantalla {
                    mostrarMensaje("No se pudo cargar la información del Pokémon")
                }
            }
        }
    }

    /** Pinta todas las secciones de la ficha con los datos ya descargados. */
    private fun mostrarFicha(mainScrollView: ScrollView, ficha: PokemonFichaCompleta) {
        val info = ficha.info
        mostrarDatosGenerales(mainScrollView, info, ficha.sprite)
        mostrarTipos(info.types)
        mostrarHabilidades(info.abilities)
        mostrarEstadisticas(info)
        mostrarEvoluciones(info.evolutions, ficha.evoSprites)
        mostrarInteracciones(R.id.layoutWeaknesses, info.weaknesses)
        mostrarInteracciones(R.id.layoutStrengths, info.strengths)
        mostrarInteracciones(R.id.layoutResistances, info.resistances)
        mostrarMovimientos(info.moves)
    }

    private fun mostrarDatosGenerales(
        mainScrollView: ScrollView,
        info: PokemonFullInfo,
        mainBitmap: Bitmap?
    ) {
        val tipoPrincipal = info.types.firstOrNull() ?: "NORMAL"
        val tipoSecundario = info.types.getOrNull(1)
        aplicarFondoPorTipos(mainScrollView, tipoPrincipal, tipoSecundario)

        if (mainBitmap != null) {
            findViewById<ImageView>(R.id.ivPokeimage2).setImageBitmap(mainBitmap)
        }
        findViewById<TextView>(R.id.tvpokename2).text = info.name
        findViewById<TextView>(R.id.pokenum2).text = String.format(Locale.US, "#%03d", info.id)

        val tvGenus = findViewById<TextView>(R.id.pokedesc)
        if (info.genus.isNotBlank()) {
            tvGenus.visibility = View.VISIBLE
            tvGenus.text = info.genus
        } else {
            tvGenus.visibility = View.GONE
        }

        findViewById<TextView>(R.id.tvpokeinfo).text = info.description
        findViewById<TextView>(R.id.tvpokeweight).text =
            String.format(Locale.US, "%.1f kg", info.weightKg)
        findViewById<TextView>(R.id.pokeheight).text =
            String.format(Locale.US, "%.1f m", info.heightMeters)
    }

    private fun mostrarTipos(types: List<String>) {
        val etiquetas = listOf(
            findViewById<TextView>(R.id.tvpoketype1),
            findViewById<TextView>(R.id.tvpoketype2)
        )
        for (i in etiquetas.indices) {
            val tvTipo = etiquetas[i]
            val tipo = types.getOrNull(i)
            if (tipo != null) {
                tvTipo.visibility = View.VISIBLE
                pintarPastilla(tvTipo, tipo)
            } else {
                tvTipo.visibility = View.GONE
            }
        }
    }

    private fun mostrarHabilidades(abilities: List<PokemonAbilityInfo>) {
        val filas = listOf(
            Triple(R.id.rowHab1, R.id.pokehab, R.id.btnInfoHab1),
            Triple(R.id.rowHab2, R.id.pokehab2, R.id.btnInfoHab2),
            Triple(R.id.rowHab3, R.id.pokehab3, R.id.btnInfoHab3)
        )

        for (i in filas.indices) {
            val (rowId, textId, btnId) = filas[i]
            val row = findViewById<LinearLayout>(rowId)
            val ability = abilities.getOrNull(i)

            if (ability == null) {
                row.visibility = View.GONE
            } else {
                row.visibility = View.VISIBLE
                val etiqueta = if (ability.isHidden) "Oculta" else "Normal"
                findViewById<TextView>(textId).text = "${ability.name} ($etiqueta)"
                findViewById<TextView>(btnId).setOnClickListener {
                    mostrarAlerta(ability.name, ability.description)
                }
            }
        }
    }

    private fun mostrarEstadisticas(info: PokemonFullInfo) {
        findViewById<TextView>(R.id.tvStatHp).text = info.hp.toString()
        findViewById<TextView>(R.id.tvStatAtk).text = info.attack.toString()
        findViewById<TextView>(R.id.tvStatDef).text = info.defense.toString()
        findViewById<TextView>(R.id.tvStatSpAtk).text = info.spAttack.toString()
        findViewById<TextView>(R.id.tvStatSpDef).text = info.spDefense.toString()
        findViewById<TextView>(R.id.tvStatSpd).text = info.speed.toString()

        val total = info.hp + info.attack + info.defense + info.spAttack + info.spDefense + info.speed
        findViewById<TextView>(R.id.tvStatTotal).text = "TOTAL  $total"
    }

    private fun mostrarEvoluciones(
        evolutions: List<PokemonEvolutionStage>,
        bitmaps: List<Bitmap?>
    ) {
        val stageLayouts = listOf(R.id.evoStage1, R.id.evoStage2, R.id.evoStage3)
        val stageImages = listOf(R.id.ivpokeline1, R.id.ivpokeline2, R.id.ivpokeline3)
        val stageNames = listOf(R.id.tvEvo1, R.id.tvEvo2, R.id.tvEvo3)
        val arrows = listOf(R.id.tvEvoArrow1, R.id.tvEvoArrow2)

        for (i in stageLayouts.indices) {
            val layout = findViewById<LinearLayout>(stageLayouts[i])
            val stage = evolutions.getOrNull(i)
            if (stage == null) {
                layout.visibility = View.GONE
                if (i > 0) findViewById<TextView>(arrows[i - 1]).visibility = View.GONE
            } else {
                layout.visibility = View.VISIBLE
                if (i > 0) findViewById<TextView>(arrows[i - 1]).visibility = View.VISIBLE
                findViewById<TextView>(stageNames[i]).text = stage.name
                val bmp = bitmaps.getOrNull(i)
                if (bmp != null) {
                    findViewById<ImageView>(stageImages[i]).setImageBitmap(bmp)
                }
            }
        }
    }

    private fun mostrarInteracciones(containerId: Int, types: List<String>) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()

        if (types.isEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "Ninguno"
            emptyTv.setTextColor(Color.parseColor("#4A5262"))
            emptyTv.textSize = 12f
            container.addView(emptyTv)
            return
        }

        var index = 0
        while (index < types.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) rowParams.topMargin = dp(6)
            row.layoutParams = rowParams

            for (col in 0 until 3) {
                val pill = crearPastillaTipo(types.getOrNull(index + col), conMargen = col > 0)
                row.addView(pill)
            }

            container.addView(row)
            index += 3
        }
    }

    private fun crearPastillaTipo(tipo: String?, conMargen: Boolean): TextView {
        val pill = TextView(this)
        val params = LinearLayout.LayoutParams(0, dp(28), 1f)
        if (conMargen) params.marginStart = dp(6)
        pill.layoutParams = params

        if (tipo != null) {
            pill.setBackgroundResource(R.drawable.bg_pokeinfo_pill)
            pill.gravity = Gravity.CENTER
            pill.setTextColor(Color.WHITE)
            pill.textSize = 11f
            pill.setTypeface(null, Typeface.BOLD)
            pintarPastilla(pill, tipo)
        } else {
            pill.visibility = View.INVISIBLE
        }
        return pill
    }

    private fun mostrarMovimientos(moves: List<PokemonMoveSummary>) {
        val container = findViewById<LinearLayout>(R.id.contenedorListaMovimientos)
        container.removeAllViews()

        if (moves.isEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "No hay movimientos por nivel disponibles."
            emptyTv.setTextColor(Color.parseColor("#4A5262"))
            emptyTv.textSize = 13f
            container.addView(emptyTv)
            return
        }

        val inflater = LayoutInflater.from(this)
        for (move in moves) {
            val itemView = inflater.inflate(R.layout.item_move, container, false)
            val tvLevel = itemView.findViewById<TextView>(R.id.tvMoveLevel)
            val tvName = itemView.findViewById<TextView>(R.id.tvMoveName)
            val tvType = itemView.findViewById<TextView>(R.id.tvMoveType)

            tvLevel.text = if (move.level == 0) "Nv. —" else "Nv. ${move.level}"
            tvName.text = move.displayName
            tvType.text = "..."
            container.addView(itemView)

            cargarDetalleMovimiento(move.url) { detail ->
                tvName.text = detail.name
                pintarPastilla(tvType, detail.type)
            }

            itemView.setOnClickListener {
                if (!moveDetailsCache.containsKey(move.url)) {
                    mostrarMensaje("Cargando movimiento...")
                }
                cargarDetalleMovimiento(move.url, avisarError = true) { detail ->
                    mostrarDialogoMovimiento(detail)
                }
            }
        }
    }

    private fun cargarDetalleMovimiento(
        moveUrl: String,
        avisarError: Boolean = false,
        alTerminar: (PokemonMoveDetail) -> Unit
    ) {
        val cached = moveDetailsCache[moveUrl]
        if (cached != null) {
            alTerminar(cached)
            return
        }

        executor.execute {
            try {
                val detail = PokeApiService.fetchMoveDetail(moveUrl)
                moveDetailsCache[moveUrl] = detail
                enPantalla { alTerminar(detail) }
            } catch (_: Exception) {
                if (avisarError) {
                    enPantalla { mostrarMensaje("No se pudo cargar el movimiento") }
                }
            }
        }
    }

    private fun mostrarDialogoMovimiento(detail: PokemonMoveDetail) {
        val mensaje = """
            Tipo: ${detail.type}
            Categoría: ${detail.damageClass}
            Potencia: ${detail.power}
            Precisión: ${detail.accuracy}
            PP: ${detail.pp}

            ${detail.effect}
        """.trimIndent()
        mostrarAlerta(detail.name, mensaje)
    }

    private fun pintarPastilla(tv: TextView, tipo: String) {
        tv.text = tipo
        ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(obtenerColorDeTipo(tipo)))
    }

    private fun mostrarAlerta(titulo: String, mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun mostrarMensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    private fun enPantalla(accion: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                accion()
            }
        }
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

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

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
