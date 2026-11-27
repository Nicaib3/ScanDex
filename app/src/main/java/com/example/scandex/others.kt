package com.example.scandex

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class others : AppCompatActivity() {

    private lateinit var othersContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var btnFilter: TextView
    private lateinit var tvEmpty: TextView

    private val executor = Executors.newFixedThreadPool(4)
    private var allEntries: List<OtherEntrySummary> = emptyList()
    private var currentFilter: OtherEntryType? = null
    private var currentQuery: String = ""
    private var visibleLimit: Int = PAGE_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_others)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        othersContainer = findViewById(R.id.othersContainer)
        progressBar = findViewById(R.id.progressBarOthers)
        etSearch = findViewById(R.id.etSearchOthers)
        btnFilter = findViewById(R.id.btnFilterOthers)
        tvEmpty = findViewById(R.id.tvEmptyOthers)

        othersContainer.removeAllViews()

        configurarFiltro()
        configurarBusqueda()
        cargarCatalogo()
    }

    private fun configurarFiltro() {
        btnFilter.setOnClickListener { view ->
            PopupMenu(this, view).apply {
                menu.add(0, 0, 0, "Todos")
                menu.add(0, 1, 1, "Objetos")
                menu.add(0, 2, 2, "Habilidades")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            currentFilter = OtherEntryType.ITEM
                            btnFilter.text = "Objetos  ▾"
                        }
                        2 -> {
                            currentFilter = OtherEntryType.ABILITY
                            btnFilter.text = "Habilidades  ▾"
                        }
                        else -> {
                            currentFilter = null
                            btnFilter.text = "Todos  ▾"
                        }
                    }
                    visibleLimit = PAGE_SIZE
                    renderFilteredList()
                    true
                }
            }.show()
        }
    }

    private fun configurarBusqueda() {
        findViewById<ImageView>(R.id.ivOthersSearch).setOnClickListener {
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
                visibleLimit = PAGE_SIZE
                renderFilteredList()
            }
        })
    }

    private fun cargarCatalogo() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        executor.execute {
            try {
                val entries = OthersApiHelper.fetchAllEntries()
                enPantalla {
                    progressBar.visibility = View.GONE
                    allEntries = entries
                    renderFilteredList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar objetos y habilidades", e)
                enPantalla {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "No se pudo conectar con la PokéAPI."
                    Toast.makeText(this, "No se pudo conectar con la PokéAPI", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderFilteredList() {
        val filtered = OthersApiHelper.filterEntries(allEntries, currentFilter, currentQuery)
        othersContainer.removeAllViews()

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (allEntries.isEmpty()) {
                "No se pudo conectar con la PokéAPI."
            } else {
                "No se encontraron resultados."
            }
            return
        }

        tvEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        val page = filtered.take(visibleLimit)

        for (entry in page) {
            val card = inflater.inflate(R.layout.item_other, othersContainer, false)
            val ivIcon = card.findViewById<ImageView>(R.id.ivOtherIcon)
            val tvName = card.findViewById<TextView>(R.id.tvOtherName)
            val tvCategory = card.findViewById<TextView>(R.id.tvOtherCategory)

            val cached = OthersApiHelper.getCachedDetail(entry.url)
            tvName.text = cached?.let { formatTitle(it.localizedName, it.displayName) } ?: entry.displayName
            tvCategory.text = entry.type.label
            val badgeColor = if (entry.type == OtherEntryType.ITEM) COLOR_ITEM_BADGE else COLOR_ABILITY_BADGE
            ViewCompat.setBackgroundTintList(tvCategory, ColorStateList.valueOf(badgeColor))

            if (entry.type == OtherEntryType.ABILITY) {
                ivIcon.visibility = View.GONE
            } else {
                ivIcon.visibility = View.VISIBLE
                cargarIcono(ivIcon, cached?.spriteUrl ?: entry.spriteUrl)
            }

            card.setOnClickListener {
                abrirDetalle(entry, tvName, ivIcon)
            }
            othersContainer.addView(card)
        }

        if (filtered.size > visibleLimit) {
            val btnMore = Button(this).apply {
                text = "Cargar más (${filtered.size - visibleLimit} restantes)"
                setOnClickListener {
                    visibleLimit += PAGE_SIZE
                    renderFilteredList()
                }
            }
            othersContainer.addView(btnMore)
        }
    }

    private fun cargarIcono(imageView: ImageView, spriteUrl: String?) {
        if (spriteUrl.isNullOrBlank()) {
            imageView.setImageResource(R.mipmap.ic_launcher)
            return
        }
        imageView.tag = spriteUrl
        imageView.setImageResource(R.mipmap.ic_launcher)

        executor.execute {
            val bitmap = try {
                OthersApiHelper.downloadSprite(spriteUrl)
            } catch (_: Exception) {
                null
            } ?: return@execute

            enPantalla {
                if (imageView.tag == spriteUrl) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun abrirDetalle(entry: OtherEntrySummary, tvName: TextView, ivIcon: ImageView) {
        val cached = OthersApiHelper.getCachedDetail(entry.url)
        if (cached != null) {
            mostrarDialogoDetalle(cached)
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setMessage("Cargando información...")
            .setPositiveButton("Cerrar", null)
            .show()

        executor.execute {
            try {
                val detail = OthersApiHelper.fetchEntryDetail(entry)
                enPantalla {
                    tvName.text = formatTitle(detail.localizedName, detail.displayName)
                    if (detail.type == OtherEntryType.ITEM && !detail.spriteUrl.isNullOrBlank()) {
                        cargarIcono(ivIcon, detail.spriteUrl)
                    }
                    if (dialog.isShowing) {
                        dialog.setTitle(formatTitle(detail.localizedName, detail.displayName))
                        dialog.setMessage("${detail.subtitle}\n\n${detail.description}")
                    }
                }
            } catch (_: Exception) {
                enPantalla {
                    if (dialog.isShowing) {
                        dialog.setMessage("No se pudo cargar la información desde PokéAPI.")
                    }
                }
            }
        }
    }

    private fun mostrarDialogoDetalle(detail: OtherEntryDetail) {
        AlertDialog.Builder(this)
            .setTitle(formatTitle(detail.localizedName, detail.displayName))
            .setMessage("${detail.subtitle}\n\n${detail.description}")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun formatTitle(localized: String, english: String): String =
        if (localized.equals(english, ignoreCase = true)) localized else "$localized ($english)"

    private fun enPantalla(accion: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                accion()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "OthersActivity"
        private const val PAGE_SIZE = 60
        private val COLOR_ITEM_BADGE = Color.parseColor("#EE8130")
        private val COLOR_ABILITY_BADGE = Color.parseColor("#6390F0")
    }
}
