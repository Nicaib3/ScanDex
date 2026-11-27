package com.example.scandex

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class pickteam : AppCompatActivity() {

    private data class SlotViews(val box: LinearLayout, val plus: TextView, val img: ImageView, val name: TextView)

    private lateinit var etNombre: EditText
    private lateinit var tvHeader: TextView
    private lateinit var btnEliminar: TextView
    private lateinit var slots: List<SlotViews>

    private val executor = Executors.newFixedThreadPool(4)
    private val spriteCache = ConcurrentHashMap<Int, Bitmap>()
    private val requestSeq = AtomicInteger(0)
    private var editingTeamId: String? = null
    private val selectedMembers = mutableListOf<PokemonTeamMember>()

    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val ids = res.data?.getIntArrayExtra(pokemonlibrary.EXTRA_SELECTED_POKEMON_IDS) ?: intArrayOf()
                aplicarSeleccion(ids.toList())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pickteam)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        etNombre = findViewById(R.id.etNombreEquipo)
        tvHeader = findViewById(R.id.tvTeamMembersHeader)
        btnEliminar = findViewById(R.id.btnEliminarEquipo)

        val slotIds = listOf(
            intArrayOf(R.id.slotPoke1, R.id.tvPlus1, R.id.ivSlotPoke1, R.id.tvSlotName1),
            intArrayOf(R.id.slotPoke2, R.id.tvPlus2, R.id.ivSlotPoke2, R.id.tvSlotName2),
            intArrayOf(R.id.slotPoke3, R.id.tvPlus3, R.id.ivSlotPoke3, R.id.tvSlotName3),
            intArrayOf(R.id.slotPoke4, R.id.tvPlus4, R.id.ivSlotPoke4, R.id.tvSlotName4),
            intArrayOf(R.id.slotPoke5, R.id.tvPlus5, R.id.ivSlotPoke5, R.id.tvSlotName5),
            intArrayOf(R.id.slotPoke6, R.id.tvPlus6, R.id.ivSlotPoke6, R.id.tvSlotName6)
        )
        slots = slotIds.map { ids ->
            SlotViews(findViewById(ids[0]), findViewById(ids[1]), findViewById(ids[2]), findViewById(ids[3]))
        }

        findViewById<TextView>(R.id.btnSeleccionarPokemon).setOnClickListener { abrirSelector() }
        slots.forEach { it.box.setOnClickListener { abrirSelector() } }
        findViewById<TextView>(R.id.btnGuardarEquipo).setOnClickListener { guardarEquipo() }
        btnEliminar.setOnClickListener { eliminarEquipo() }

        cargarInicial()
    }

    private fun cargarInicial() {
        val existing = intent.getStringExtra(EXTRA_TEAM_ID)?.let { PokemonTeamStore.getTeamById(this, it) }
        if (existing != null) {
            editingTeamId = existing.id
            etNombre.setText(existing.name)
            btnEliminar.visibility = View.VISIBLE
            selectedMembers.addAll(existing.members.take(PokemonTeamStore.MAX_TEAM_SIZE))
            renderSlots()
            renderStats(TeamPokeApiHelper.calculateTeamStats(selectedMembers))
            cargarDetallesAsync()
        } else {
            btnEliminar.visibility = View.GONE
            renderSlots()
            renderStats(TeamStatsSummary())
            renderTypeBalance(TeamTypeBalance())
        }
    }

    private fun abrirSelector() {
        pickerLauncher.launch(
            Intent(this, pokemonlibrary::class.java)
                .putExtra(pokemonlibrary.EXTRA_TEAM_PICKER_MODE, true)
                .putExtra(pokemonlibrary.EXTRA_SELECTED_POKEMON_IDS, selectedMembers.map { it.pokemonId }.toIntArray())
        )
    }

    private fun aplicarSeleccion(ids: List<Int>) {
        val prevById = selectedMembers.associateBy { it.pokemonId }
        selectedMembers.clear()
        ids.filter { it > 0 }.distinct().take(PokemonTeamStore.MAX_TEAM_SIZE).forEach { id ->
            selectedMembers.add(prevById[id] ?: PokemonTeamMember(id, String.format(Locale.US, "#%03d", id)))
        }

        renderSlots()
        if (selectedMembers.isEmpty()) {
            requestSeq.incrementAndGet()
            renderStats(TeamStatsSummary())
            renderTypeBalance(TeamTypeBalance())
            return
        }
        cargarDetallesAsync()
    }

    private fun cargarDetallesAsync() {
        val gen = requestSeq.incrementAndGet()
        val fallback = selectedMembers.associateBy { it.pokemonId }
        val ids = selectedMembers.map { it.pokemonId }

        executor.execute {
            val resolved = ids.map { id ->
                val member = runCatching { TeamPokeApiHelper.fetchTeamMember(id) }
                    .getOrElse { fallback[id] ?: PokemonTeamMember(id, String.format(Locale.US, "#%03d", id)) }
                val url = member.spriteUrl
                if (url != null && !spriteCache.containsKey(id)) {
                    runCatching { PokeApiService.downloadBitmap(url) }.getOrNull()?.let { spriteCache[id] = it }
                }
                member
            }
            val stats = TeamPokeApiHelper.calculateTeamStats(resolved)
            val balance = runCatching { TeamPokeApiHelper.fetchTeamTypeBalance(resolved) }.getOrDefault(TeamTypeBalance())

            runOnUiThread {
                if (isFinishing || isDestroyed || requestSeq.get() != gen) return@runOnUiThread
                selectedMembers.clear()
                selectedMembers.addAll(resolved)
                renderSlots()
                renderStats(stats)
                renderTypeBalance(balance)
            }
        }
    }

    private fun renderSlots() {
        tvHeader.text = "Integrantes del equipo (${selectedMembers.size} / ${PokemonTeamStore.MAX_TEAM_SIZE})"
        slots.forEachIndexed { i, slot ->
            val member = selectedMembers.getOrNull(i)
            if (member == null) {
                slot.plus.visibility = View.VISIBLE
                slot.img.visibility = View.GONE
                slot.img.setImageDrawable(null)
                slot.name.text = "Agregar"
            } else {
                slot.plus.visibility = View.GONE
                slot.img.visibility = View.VISIBLE
                spriteCache[member.pokemonId]?.let { slot.img.setImageBitmap(it) }
                    ?: slot.img.setImageResource(R.mipmap.ic_launcher)
                slot.name.text = member.name
            }
        }
    }

    private fun renderStats(s: TeamStatsSummary) {
        listOf(
            R.id.tvTeamStatHp to s.hp, R.id.tvTeamStatAtk to s.attack, R.id.tvTeamStatDef to s.defense,
            R.id.tvTeamStatSpAtk to s.spAttack, R.id.tvTeamStatSpDef to s.spDefense, R.id.tvTeamStatSpd to s.speed
        ).forEach { (id, value) -> findViewById<TextView>(id).text = value.toString() }
        findViewById<TextView>(R.id.tvTeamStatTotal).text = "PODER BASE TOTAL DEL EQUIPO  ${s.total}"
    }

    private fun renderTypeBalance(b: TeamTypeBalance) {
        bindTypeRow(listOf(R.id.tvTeamWeak1, R.id.tvTeamWeak2, R.id.tvTeamWeak3), b.weaknesses)
        bindTypeRow(listOf(R.id.tvTeamStrong1, R.id.tvTeamStrong2, R.id.tvTeamStrong3), b.strengths)
    }

    private fun bindTypeRow(viewIds: List<Int>, entries: List<TypeCountEntry>) {
        viewIds.forEachIndexed { i, id ->
            val tv = findViewById<TextView>(id)
            val entry = entries.getOrNull(i)
            when {
                entries.isEmpty() && i == 0 -> {
                    tv.visibility = View.VISIBLE
                    tv.text = "Sin datos"
                    ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(0xFF64748B.toInt()))
                }
                entry == null -> tv.visibility = View.GONE
                else -> {
                    tv.visibility = View.VISIBLE
                    tv.text = "${entry.type} (x${entry.count})"
                    ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(TeamPokeApiHelper.colorIntForType(entry.type)))
                }
            }
        }
    }

    private fun guardarEquipo() {
        val nombre = etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            Toast.makeText(this, "Escribe un nombre para el equipo", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos 1 Pokémon", Toast.LENGTH_SHORT).show()
            return
        }
        PokemonTeamStore.saveTeam(this, editingTeamId, nombre, selectedMembers)
        Toast.makeText(this, "Equipo «$nombre» listo", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun eliminarEquipo() {
        editingTeamId?.let { PokemonTeamStore.deleteTeam(this, it) }
        Toast.makeText(this, "Equipo eliminado", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        const val EXTRA_TEAM_ID = "teamId"
    }
}
