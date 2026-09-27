package cl.exy.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import cl.exy.app.databinding.ActivityComandosBinding
import cl.exy.app.databinding.DialogoEnsenarBinding
import cl.exy.app.databinding.ItemAccionBinding
import cl.exy.app.databinding.ItemComandoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Favorito, comandos propios y los incluidos.
 *
 * Un comando propio se crea eligiendo una app y "enseñando" su nombre: Exy
 * escucha sin gramática y guarda lo que entendió. Así la frase usa palabras que
 * el modelo conoce y que coinciden con cómo pronuncias el nombre.
 */
class ComandosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComandosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityComandosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Pantallas.bordes(binding.scroll)
        Pantallas.barra(this, binding.barra, R.string.comandos_titulo)

        binding.filaFavorito.root.setOnClickListener { elegirFavorito() }
        binding.btnAgregar.setOnClickListener { elegirApp() }
        render()
    }

    private fun render() {
        val favorito = Destino.favorito(this)
        binding.filaFavorito.txtTitulo.text = getString(R.string.favorito_fila, favorito.nombre(this))
        binding.filaFavorito.txtDetalle.setText(R.string.favorito_cambiar)

        // Tus comandos
        val propios = ComandosUsuario(this).lista()
        binding.listaPropios.isVisible = propios.isNotEmpty()
        binding.txtSinPropios.isVisible = propios.isEmpty()
        val lista = binding.listaPropios
        lista.removeAllViews()
        propios.forEachIndexed { i, c ->
            if (i > 0) lista.addView(Pantallas.separador(this))
            val fila = ItemComandoBinding.inflate(layoutInflater, lista, true)
            fila.txtFrase.text = c.nombre
            fila.txtDestino.text = c.frases.joinToString(" · ") { getString(R.string.frases_de, it) }
            fila.txtChip.isVisible = false
            fila.btnProbar.setIconResource(R.drawable.ic_delete)
            fila.btnProbar.contentDescription = getString(R.string.borrar_comando, c.nombre)
            fila.btnProbar.setOnClickListener {
                ComandosUsuario(this).borrar(c.paquete)
                if (ExySettings(this).favoritoId == Destino.idDeApp(c.paquete)) {
                    ExySettings(this).favoritoId = Destino.GEMINI.id
                }
                cambiaronComandos()
            }
        }

        // Incluidos (solo lectura)
        val integrados = binding.listaIntegrados
        integrados.removeAllViews()
        val frases = listOf(
            R.string.cmd_claude_code to Destino.CLAUDE_CODE,
            R.string.cmd_claude to Destino.CLAUDE,
            R.string.cmd_chatgpt to Destino.CHATGPT,
            R.string.cmd_gemini to Destino.GEMINI,
        )
        frases.forEachIndexed { i, (frase, destino) ->
            if (i > 0) integrados.addView(Pantallas.separador(this))
            val fila = ItemAccionBinding.inflate(layoutInflater, integrados, true)
            fila.txtTitulo.setText(frase)
            fila.txtDetalle.text = destino.nombre(this)
            fila.imgFlecha.isVisible = false
        }
    }

    private fun elegirFavorito() {
        val destinos = Destino.todos(this)
        val actual = destinos.indexOfFirst { it.id == ExySettings(this).favoritoId }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.elegir_favorito)
            .setSingleChoiceItems(destinos.map { it.nombre(this) }.toTypedArray(), actual) { d, i ->
                ExySettings(this).favoritoId = destinos[i].id
                d.dismiss()
                render()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /** Apps con ícono en el cajón, ordenadas por nombre (sin Exy). */
    private fun elegirApp() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.elegir_app)
            .setItems(apps.map { it.second }.toTypedArray()) { _, i ->
                val (paquete, nombre) = apps[i]
                ensenar(paquete, nombre)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /** Escucha el nombre de la app (hasta 3 formas) y lo guarda como comando. */
    private fun ensenar(paquete: String, nombre: String) {
        val vista = DialogoEnsenarBinding.inflate(layoutInflater)
        val frases = mutableListOf<String>()
        var escucha: EscuchaTemporal? = null

        fun mostrarFrases() {
            vista.txtFrases.isVisible = frases.isNotEmpty()
            vista.txtFrases.text = getString(
                R.string.ensenar_frases,
                frases.joinToString("\n") { getString(R.string.frases_de, it) },
            )
        }

        lateinit var dialogo: AlertDialog

        fun escuchar() {
            escucha?.detener()
            vista.txtInstruccion.setText(R.string.ensenar_escuchando)
            vista.txtParcial.text = ""
            escucha = EscuchaTemporal(
                context = this,
                gramatica = null,
                alParcial = { json ->
                    vista.txtParcial.text = Comandos.textoOido(json.replace("\"partial\"", "\"text\"")).orEmpty()
                },
                alResultado = resultado@{ json ->
                    val texto = Comandos.textoOido(json)
                    if (texto == null) {
                        vista.txtInstruccion.setText(R.string.ensenar_nada)
                        return@resultado
                    }
                    escucha?.detener()
                    if (texto !in frases) frases.add(texto)
                    vista.txtParcial.text = ""
                    vista.txtInstruccion.setText(R.string.ensenar_otra)
                    mostrarFrases()
                    dialogo.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    dialogo.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = frases.size < 3
                },
                alError = { msg -> vista.txtInstruccion.text = msg },
            ).also { it.iniciar() }
        }

        vista.txtInstruccion.setText(R.string.ensenar_instruccion)
        dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ensenar_titulo, nombre))
            .setView(vista.root)
            .setPositiveButton(R.string.ensenar_guardar) { _, _ ->
                ComandosUsuario(this).guardar(ComandosUsuario.Comando(paquete, nombre, frases.toList()))
                Toast.makeText(this, getString(R.string.ensenar_guardado, frases.first(), nombre), Toast.LENGTH_LONG).show()
                cambiaronComandos()
            }
            .setNeutralButton(R.string.ensenar_repetir, null)
            .setNegativeButton(R.string.cancelar, null)
            .setOnDismissListener { escucha?.detener() }
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
            // "Otra forma" no cierra el diálogo: vuelve a escuchar.
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                it.isEnabled = false
                escuchar()
            }
            escuchar()
        }
        dialogo.show()
    }

    /** Rehace la gramática y, si Exy está escuchando, la reinicia con los cambios. */
    private fun cambiaronComandos() {
        Comandos.invalidar()
        WakeWordService.send(this, WakeWordService.ACTION_RELOAD)
        render()
    }
}
