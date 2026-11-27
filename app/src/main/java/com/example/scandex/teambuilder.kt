package com.example.scandex

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class teambuilder : AppCompatActivity() {

    private lateinit var contenedor: LinearLayout
    private lateinit var tvVacio: TextView
    private val executor = Executors.newFixedThreadPool(4)
    private val spriteCache = ConcurrentHashMap<String, Bitmap>()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_teambuilder)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        contenedor = findViewById(R.id.contenedorEquiposGuardados)
        tvVacio = findViewById(R.id.tvSinEquipos)

        findViewById<LinearLayout>(R.id.btnCrearEquipo).setOnClickListener {
            startActivity(Intent(this, pickteam::class.java))
        }
        findViewById<SearchView>(R.id.searchTeams).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = actualizarFiltro(q)
            override fun onQueryTextChange(q: String?) = actualizarFiltro(q)
        })

        renderTeams()
    }

    override fun onResume() {
        super.onResume()
        renderTeams()
    }

    private fun actualizarFiltro(q: String?): Boolean {
        query = q.orEmpty()
        renderTeams()
        return true
    }

    private fun renderTeams() {
        val teams = PokemonTeamStore.searchTeams(this, query)
        contenedor.removeAllViews()

        if (teams.isEmpty()) {
            tvVacio.visibility = View.VISIBLE
            tvVacio.text = if (query.isBlank()) {
                "Aún no tienes equipos guardados.\nToca «Crear nuevo equipo» para comenzar."
            } else {
                "No se encontraron equipos para «${query.trim()}»."
            }
            return
        }

        tvVacio.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        val slotIds = listOf(
            R.id.ivTeamPoke1, R.id.ivTeamPoke2, R.id.ivTeamPoke3,
            R.id.ivTeamPoke4, R.id.ivTeamPoke5, R.id.ivTeamPoke6
        )

        for (team in teams) {
            val card = inflater.inflate(R.layout.item_team, contenedor, false)
            card.findViewById<TextView>(R.id.tvTeamName).text = team.name
            card.findViewById<TextView>(R.id.tvTeamCount).text = "${team.members.size} / ${PokemonTeamStore.MAX_TEAM_SIZE}"

            slotIds.forEachIndexed { i, id ->
                val iv = card.findViewById<ImageView>(id)
                val member = team.members.getOrNull(i)
                if (member == null) {
                    iv.setImageDrawable(null)
                    iv.visibility = View.INVISIBLE
                } else {
                    iv.visibility = View.VISIBLE
                    bindSprite(iv, member.spriteUrl)
                }
            }

            card.setOnClickListener {
                startActivity(Intent(this, pickteam::class.java).putExtra(pickteam.EXTRA_TEAM_ID, team.id))
            }
            contenedor.addView(card)
        }
    }

    private fun bindSprite(iv: ImageView, url: String?) {
        if (url == null) return
        iv.tag = url
        spriteCache[url]?.let { iv.setImageBitmap(it); return }

        iv.setImageResource(R.mipmap.ic_launcher)
        executor.execute {
            val bmp = runCatching { PokeApiService.downloadBitmap(url) }.getOrNull() ?: return@execute
            spriteCache[url] = bmp
            runOnUiThread {
                if (!isFinishing && !isDestroyed && iv.tag == url) iv.setImageBitmap(bmp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
