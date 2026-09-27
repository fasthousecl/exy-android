package cl.exy.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat

/**
 * Anillo alrededor de la X del estado principal.
 *
 * - [Modo.APAGADO]: anillo fino y tenue.
 * - [Modo.ESCUCHANDO]: anillo menta con un halo que "respira" lento (se queda
 *   quieto si el sistema tiene las animaciones desactivadas).
 * - [Modo.CUENTA]: arco menta que se vacía hasta volver a escuchar ([progreso]).
 * - [Modo.ERROR]: anillo rojo suave.
 */
class OrbeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Modo { APAGADO, ESCUCHANDO, CUENTA, ERROR }

    var modo: Modo = Modo.APAGADO
        set(value) {
            if (field == value) return
            field = value
            actualizarPulso()
            invalidate()
        }

    /** 1 = recién abierta la app, 0 = vuelve a escuchar. Solo en [Modo.CUENTA]. */
    var progreso: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val menta = ContextCompat.getColor(context, R.color.exy_mint)
    private val linea = ContextCompat.getColor(context, R.color.exy_hair)
    private val peligro = ContextCompat.getColor(context, R.color.exy_danger)

    private val anillo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3 * dp
        color = menta
    }
    private val caja = RectF()

    /** 0..1: fase del halo. */
    private var fase = 0.5f
    private var pulso: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        actualizarPulso()
    }

    override fun onDetachedFromWindow() {
        pulso?.cancel()
        pulso = null
        super.onDetachedFromWindow()
    }

    private fun actualizarPulso() {
        val animar = modo == Modo.ESCUCHANDO && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()
        if (!animar) {
            pulso?.cancel()
            pulso = null
            fase = 0.5f
            return
        }
        if (pulso != null) return
        pulso = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = PathInterpolator(0.45f, 0f, 0.55f, 1f)
            addUpdateListener {
                fase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 14 * dp

        when (modo) {
            Modo.APAGADO -> {
                anillo.strokeWidth = 2 * dp
                anillo.color = linea
                canvas.drawCircle(cx, cy, r, anillo)
            }
            Modo.ESCUCHANDO -> {
                // Halo exterior que respira: se expande y se desvanece un poco.
                halo.strokeWidth = 10 * dp
                halo.color = conAlfa(menta, 0.05f + 0.07f * fase)
                canvas.drawCircle(cx, cy, r + 6 * dp + 4 * dp * fase, halo)
                halo.strokeWidth = 1 * dp
                halo.color = conAlfa(menta, 0.14f + 0.12f * (1 - fase))
                canvas.drawCircle(cx, cy, r + 12 * dp, halo)
                anillo.strokeWidth = 2 * dp
                anillo.color = conAlfa(menta, 0.6f)
                canvas.drawCircle(cx, cy, r, anillo)
            }
            Modo.CUENTA -> {
                anillo.strokeWidth = 2 * dp
                anillo.color = linea
                canvas.drawCircle(cx, cy, r, anillo)
                caja.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(caja, -90f, 360f * progreso, false, arco)
            }
            Modo.ERROR -> {
                anillo.strokeWidth = 2 * dp
                anillo.color = conAlfa(peligro, 0.7f)
                canvas.drawCircle(cx, cy, r, anillo)
            }
        }
    }

    private fun conAlfa(color: Int, alfa: Float): Int =
        (color and 0x00FFFFFF) or ((alfa.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
