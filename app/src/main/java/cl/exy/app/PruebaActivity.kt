package cl.exy.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cl.exy.app.databinding.ActivityPruebaBinding
import cl.exy.app.databinding.ItemHistorialBinding
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * "Prueba tu voz": escucha con la misma gramática, precisión y favorito que el
 * servicio y muestra qué entendió y si se habría activado. Aquí no se abre
 * ninguna app.
 */
class PruebaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPruebaBinding
    private var escucha: EscuchaTemporal? = null
    private var cargando = false
    private var error: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPruebaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Pantallas.bordes(binding.scroll)
        Pantallas.barra(this, binding.barra, R.string.prueba_titulo)

        binding.lblResultados.isVisible = false
        binding.listaResultados.isVisible = false
        binding.btnPrueba.setOnClickListener {
            if (escucha?.escuchando == true) detener() else iniciar()
        }
        render()
    }

    override fun onStop() {
        detener()
        super.onStop()
    }

    private fun iniciar() {
        val comandos = Comandos.cargar(this)
        val minimo = 1f - ExySettings(this).sensitivity
        val favorito = Destino.favorito(this)
        cargando = true
        error = null
        escucha = EscuchaTemporal(
            context = this,
            gramatica = comandos.gramatica,
            alParcial = { json ->
                cargando = false
                val texto = Comandos.textoOido(json.replace("\"partial\"", "\"text\""))
                binding.txtParcial.isVisible = texto != null
                binding.txtParcial.text = texto
                render()
            },
            alResultado = { json -> mostrar(json, comandos, minimo, favorito) },
            alError = { msg ->
                cargando = false
                error = msg
                render()
            },
        ).also { it.iniciar() }
        render()
    }

    private fun detener() {
        escucha?.detener()
        escucha = null
        cargando = false
        binding.txtParcial.isVisible = false
        render()
    }

    private fun mostrar(json: String, comandos: Comandos, minimo: Float, favorito: Destino) {
        cargando = false
        val oido = Comandos.textoOido(json) ?: return
        val d = runCatching { comandos.detectar(json, minimo, favorito) }.getOrNull()
        val (texto, color) = when {
            d == null -> getString(R.string.prueba_sin_frase) to R.color.exy_text_3
            d.aceptada -> getString(R.string.prueba_abriria, d.destino.nombre(this), pct(d.confianza)) to R.color.exy_mint
            else -> getString(R.string.prueba_baja, pct(d.confianza), pct(minimo.toDouble())) to R.color.exy_danger
        }
        binding.lblResultados.isVisible = true
        binding.listaResultados.isVisible = true
        val lista = binding.listaResultados
        if (lista.childCount > 0) lista.addView(Pantallas.separador(this), 0)
        val fila = ItemHistorialBinding.inflate(layoutInflater, lista, false)
        fila.txtOido.text = getString(R.string.frases_de, oido)
        fila.txtResultado.text = texto
        fila.txtResultado.setTextColor(ContextCompat.getColor(this, color))
        fila.marca.setBackgroundColor(ContextCompat.getColor(this, color))
        fila.txtHora.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        lista.addView(fila.root, 0)
        binding.txtParcial.isVisible = false
        if (d?.aceptada == true) binding.orbe.modo = OrbeView.Modo.ESCUCHANDO
    }

    private fun render() {
        val activa = escucha?.escuchando == true
        binding.txtEstado.setText(
            when {
                cargando -> R.string.prueba_estado_cargando
                activa -> R.string.prueba_estado_escuchando
                else -> R.string.prueba_estado_listo
            },
        )
        binding.txtDetalle.text = error ?: getString(R.string.prueba_detalle)
        binding.btnPrueba.setText(if (activa) R.string.prueba_btn_detener else R.string.prueba_btn_probar)
        binding.orbe.modo = if (activa && !cargando) OrbeView.Modo.ESCUCHANDO else OrbeView.Modo.APAGADO
        binding.imgX.setImageResource(if (activa && !cargando) R.drawable.x_encendida else R.drawable.x_apagada)
    }

    private fun pct(x: Double): Int = (x * 100).roundToInt()
}
