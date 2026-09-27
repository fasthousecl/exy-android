package cl.exy.app

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import cl.exy.app.databinding.BarraBinding

/** Piezas repetidas entre pantallas. */
object Pantallas {

    /** Pantalla de borde a borde: el contenido respeta barra de estado y de gestos. */
    fun bordes(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    /** Barra superior de las pantallas secundarias. */
    fun barra(activity: Activity, barra: BarraBinding, titulo: Int) {
        barra.txtTitulo.setText(titulo)
        barra.btnVolver.setOnClickListener { activity.finish() }
    }

    /** Línea fina entre filas de una tarjeta. */
    fun separador(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.displayMetrics.density.toInt().coerceAtLeast(1),
        )
        setBackgroundColor(ContextCompat.getColor(context, R.color.exy_hair))
    }
}
