package cl.exy.app

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cl.exy.app.databinding.ActivityHistorialBinding
import cl.exy.app.databinding.ItemHistorialBinding
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Las últimas activaciones: qué oyó, qué abrió y cuáles ignoró. */
class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Pantallas.bordes(binding.scroll)
        Pantallas.barra(this, binding.barra, R.string.historial_titulo)
        binding.barra.btnAccion.setText(R.string.historial_borrar)
        binding.barra.btnAccion.setOnClickListener {
            Historial(this).borrar()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val entradas = Historial(this).lista()
        binding.txtVacio.isVisible = entradas.isEmpty()
        binding.lista.isVisible = entradas.isNotEmpty()
        binding.barra.btnAccion.isVisible = entradas.isNotEmpty()

        val lista = binding.lista
        lista.removeAllViews()
        val hora = DateFormat.getTimeInstance(DateFormat.SHORT)
        val fecha = DateFormat.getDateInstance(DateFormat.SHORT)
        entradas.forEachIndexed { i, e ->
            if (i > 0) lista.addView(Pantallas.separador(this))
            val fila = ItemHistorialBinding.inflate(layoutInflater, lista, true)
            val pct = (e.confianza * 100).roundToInt()
            val destino = e.destino ?: "?"
            val (texto, color) = when (e.resultado) {
                Historial.Resultado.ABIERTA -> getString(R.string.historial_abrio, destino, pct) to R.color.exy_mint
                Historial.Resultado.NO_ABRIO -> getString(R.string.historial_no_abrio, destino) to R.color.exy_danger
                Historial.Resultado.IGNORADA -> getString(R.string.historial_ignorada, destino, pct) to R.color.exy_text_3
            }
            fila.txtOido.text = getString(R.string.frases_de, e.oido)
            fila.txtResultado.text = texto
            fila.marca.setBackgroundColor(ContextCompat.getColor(this, color))
            val cuando = Date(e.cuando)
            fila.txtHora.text = if (DateUtils.isToday(e.cuando)) hora.format(cuando) else fecha.format(cuando)
        }
    }
}
