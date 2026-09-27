package cl.exy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cl.exy.app.WakeWordService.State
import cl.exy.app.databinding.ActivityMainBinding
import cl.exy.app.databinding.ItemAccionBinding
import cl.exy.app.databinding.ItemComandoBinding
import cl.exy.app.databinding.ItemPasoBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: ExySettings

    private val serviceListener: () -> Unit = { renderEstado() }

    /** Refresca la cuenta regresiva una vez por segundo mientras la pantalla está visible. */
    private val reloj = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() {
            renderEstado()
            if (WakeWordService.state == State.COOLDOWN) reloj.postDelayed(this, 1000)
        }
    }

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            openAppSettings()
        }
        renderTodo()
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { renderTodo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = ExySettings(this)

        Pantallas.bordes(binding.scroll)

        binding.txtVersion.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        setupAcciones()
        setupAjustes()
        setupHerramientas()
        setupAutomatico()
        setupActualizaciones()
        iniciarSiSePidio(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        iniciarSiSePidio(intent)
    }

    /** El botón de ajustes rápidos abre la app con este extra para empezar a escuchar. */
    private fun iniciarSiSePidio(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INICIAR, false) != true) return
        intent.removeExtra(EXTRA_INICIAR)
        if (hasMic() && !WakeWordService.isRunning) iniciar()
    }

    override fun onStart() {
        super.onStart()
        WakeWordService.addListener(serviceListener)
    }

    override fun onStop() {
        WakeWordService.removeListener(serviceListener)
        reloj.removeCallbacks(tic)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        renderTodo()
    }

    private fun renderTodo() {
        renderEstado()
        renderPasos()
        renderComandos()
        renderHerramientas()
        renderAutomatico()
    }

    // ---------------------------------------------------------------- estado

    private fun setupAcciones() {
        binding.btnPrimario.setOnClickListener {
            when {
                !hasMic() -> requestMic.launch(Manifest.permission.RECORD_AUDIO)
                WakeWordService.state == State.COOLDOWN || WakeWordService.state == State.PAUSED ||
                    WakeWordService.state == State.ERROR && WakeWordService.isRunning ->
                    WakeWordService.send(this, WakeWordService.ACTION_RESUME)
                else -> iniciar()
            }
        }
        binding.btnPausar.setOnClickListener { WakeWordService.send(this, WakeWordService.ACTION_PAUSE) }
        binding.btnDetener.setOnClickListener { WakeWordService.send(this, WakeWordService.ACTION_STOP) }
    }

    private fun iniciar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications()) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WakeWordService.start(this)
    }

    private fun renderEstado() {
        val estado = WakeWordService.state
        val mensaje = WakeWordService.lastMessage
        val destino = (WakeWordService.lastDestino ?: Destino.favorito(this)).nombre(this)

        // Título, detalle, X y anillo
        val (titulo, detalle) = when {
            !hasMic() && estado == State.STOPPED ->
                getString(R.string.estado_sin_mic) to getString(R.string.detalle_sin_mic)
            estado == State.STOPPED ->
                getString(R.string.estado_detenido) to (mensaje ?: getString(R.string.detalle_detenido))
            estado == State.STARTING ->
                getString(R.string.estado_iniciando) to getString(R.string.detalle_iniciando)
            estado == State.LISTENING ->
                getString(R.string.estado_escuchando) to getString(R.string.detalle_escuchando)
            estado == State.PAUSED ->
                getString(R.string.estado_pausa) to getString(R.string.detalle_pausa)
            estado == State.COOLDOWN ->
                getString(R.string.estado_cuenta, destino) to (mensaje ?: getString(R.string.detalle_cuenta, restante()))
            estado == State.RESTING -> getString(R.string.estado_descanso) to
                getString(R.string.detalle_descanso, ExySettings.hora(settings.descansoHasta))
            estado == State.NO_HEADPHONES ->
                getString(R.string.estado_auriculares) to getString(R.string.detalle_auriculares)
            else ->
                getString(R.string.estado_error) to mensaje.orEmpty()
        }
        binding.txtEstado.text = titulo
        binding.txtDetalle.text = detalle

        binding.imgX.setImageResource(if (estado == State.LISTENING) R.drawable.x_encendida else R.drawable.x_apagada)
        binding.orbe.modo = when (estado) {
            State.LISTENING -> OrbeView.Modo.ESCUCHANDO
            State.COOLDOWN -> OrbeView.Modo.CUENTA
            State.ERROR -> OrbeView.Modo.ERROR
            else -> OrbeView.Modo.APAGADO
        }
        if (estado == State.COOLDOWN) {
            val faltan = (WakeWordService.reanudarEn - System.currentTimeMillis()).coerceAtLeast(0)
            binding.orbe.progreso = faltan.toFloat() / WakeWordService.cuentaTotalMs.coerceAtLeast(1)
            reloj.removeCallbacks(tic)
            reloj.postDelayed(tic, 1000)
        }

        val oido = WakeWordService.lastHeard
        binding.txtOido.isVisible = oido != null && WakeWordService.isRunning
        binding.txtOido.text = oido?.let { getString(R.string.oido, it) }

        // Botones: una acción principal por estado
        val primario: Int? = when {
            !hasMic() -> R.string.btn_conceder_mic
            estado == State.STOPPED -> R.string.btn_start
            estado == State.STARTING -> R.string.btn_iniciando
            estado == State.PAUSED -> R.string.btn_reanudar
            estado == State.COOLDOWN -> R.string.btn_escuchar_ya
            estado == State.ERROR -> R.string.btn_reintentar
            else -> null
        }
        binding.btnPrimario.isVisible = primario != null
        primario?.let { binding.btnPrimario.setText(it) }
        binding.btnPrimario.isEnabled = estado != State.STARTING
        binding.btnPausar.isVisible = estado == State.LISTENING || estado == State.COOLDOWN
        // En la cuenta regresiva hay dos botones: "Escuchar ya" manda, "Pausar" acompaña.
        binding.btnDetener.isVisible = WakeWordService.isRunning && estado != State.COOLDOWN
    }

    /** "0:52" hasta volver a escuchar. */
    private fun restante(): String {
        val s = ceil((WakeWordService.reanudarEn - System.currentTimeMillis()).coerceAtLeast(0) / 1000.0).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    // --------------------------------------------------------- primeros pasos

    private data class Paso(
        val titulo: Int,
        val detalle: Int,
        val listo: Boolean,
        val pedir: () -> Unit,
    )

    private fun pasos(): List<Paso> = listOf(
        Paso(R.string.perm_mic, R.string.perm_mic_det, hasMic()) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        },
        Paso(R.string.perm_notifications, R.string.perm_notifications_det, hasNotifications()) {
            pedirNotificaciones()
        },
        Paso(R.string.perm_overlay, R.string.perm_overlay_det, Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        },
        Paso(R.string.perm_battery, R.string.perm_battery_det, sinLimiteBateria()) {
            pedirBateria()
        },
    )

    /** Mientras falte algo, pasos guiados; con todo listo, una sola línea. */
    private fun renderPasos() {
        val pasos = pasos()
        val listos = pasos.count { it.listo }
        val todo = listos == pasos.size
        binding.seccionPasos.isVisible = !todo
        binding.permisosListos.isVisible = todo
        if (todo) return

        binding.txtPasos.text = getString(R.string.pasos, listos, pasos.size)
        binding.progresoPasos.max = pasos.size
        binding.progresoPasos.setProgressCompat(listos, false)

        val lista = binding.listaPasos
        lista.removeAllViews()
        pasos.forEachIndexed { i, paso ->
            if (i > 0) lista.addView(separador())
            val fila = ItemPasoBinding.inflate(layoutInflater, lista, true)
            fila.txtTitulo.setText(paso.titulo)
            fila.txtDetalle.setText(paso.detalle)
            fila.imgEstado.setImageResource(if (paso.listo) R.drawable.ic_check else R.drawable.ic_pending)
            fila.btnConceder.isVisible = !paso.listo
            fila.btnConceder.contentDescription =
                getString(R.string.perm_request) + " " + getString(paso.titulo)
            fila.btnConceder.setOnClickListener { paso.pedir() }
        }
    }

    // ------------------------------------------------------- comandos de voz

    /** Favorito, los incluidos, los propios y una fila para administrarlos. */
    private fun renderComandos() {
        val favorito = Destino.favorito(this)
        val filas = buildList {
            add(getString(R.string.cmd_favorito) to favorito)
            add(getString(R.string.cmd_claude_code) to Destino.CLAUDE_CODE)
            add(getString(R.string.cmd_claude) to Destino.CLAUDE)
            add(getString(R.string.cmd_chatgpt) to Destino.CHATGPT)
            // «…Gemini» solo aporta si Gemini no es ya el favorito.
            if (favorito != Destino.GEMINI) add(getString(R.string.cmd_gemini) to Destino.GEMINI)
            ComandosUsuario(this@MainActivity).lista().forEach { c ->
                add("«…${c.frases.firstOrNull().orEmpty()}»" to Destino.deApp(c.paquete, c.nombre))
            }
        }

        val lista = binding.listaComandos
        lista.removeAllViews()
        filas.forEachIndexed { i, (frase, destino) ->
            if (i > 0) lista.addView(separador())
            val fila = ItemComandoBinding.inflate(layoutInflater, lista, true)
            val nombre = destino.nombre(this)
            val instalada = destino.instalada(this)
            val esFavorito = i == 0
            fila.txtFrase.text = frase
            fila.txtDestino.text = when {
                destino == Destino.CLAUDE_CODE -> getString(R.string.dest_claude_code_url)
                destino == Destino.GEMINI && !instalada -> getString(R.string.dest_sin_app, nombre)
                instalada || !destino.tieneWeb -> nombre
                else -> getString(R.string.dest_web, nombre)
            }
            when {
                esFavorito -> chip(fila, R.string.chip_favorito, R.drawable.bg_chip, R.color.exy_mint)
                destino.tieneWeb && destino != Destino.CLAUDE_CODE && !instalada ->
                    chip(fila, R.string.chip_web, R.drawable.bg_chip_muted, R.color.exy_text_2)
                else -> fila.txtChip.isVisible = false
            }
            fila.btnProbar.contentDescription = getString(R.string.probar, nombre)
            fila.btnProbar.setOnClickListener {
                if (!destino.abrir(this)) toast(getString(R.string.no_se_pudo_abrir, nombre))
            }
        }

        // Última fila: ir a administrar comandos y favorito.
        lista.addView(separador())
        val admin = ItemAccionBinding.inflate(layoutInflater, lista, true)
        admin.txtTitulo.setText(R.string.administrar_comandos)
        admin.txtDetalle.text = getString(R.string.administrar_det, favorito.nombre(this))
        admin.root.setOnClickListener { startActivity(Intent(this, ComandosActivity::class.java)) }
    }

    private fun chip(fila: ItemComandoBinding, texto: Int, fondo: Int, color: Int) {
        fila.txtChip.isVisible = true
        fila.txtChip.setText(texto)
        fila.txtChip.setBackgroundResource(fondo)
        fila.txtChip.setTextColor(ContextCompat.getColor(this, color))
    }

    // ------------------------------------------------------------ herramientas

    private fun setupHerramientas() {
        binding.filaPrueba.txtTitulo.setText(R.string.prueba_titulo)
        binding.filaPrueba.txtDetalle.setText(R.string.prueba_det)
        binding.filaPrueba.root.setOnClickListener {
            if (hasMic()) {
                startActivity(Intent(this, PruebaActivity::class.java))
            } else {
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        binding.filaHistorial.txtTitulo.setText(R.string.historial_titulo)
        binding.filaHistorial.root.setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }
    }

    private fun renderHerramientas() {
        val n = Historial(this).lista().size
        binding.filaHistorial.txtDetalle.text =
            if (n == 0) getString(R.string.historial_det) else getString(R.string.historial_det_n, n)
    }

    // -------------------------------------------------------------- automático

    private fun setupAutomatico() {
        binding.swDescanso.setOnCheckedChangeListener { _, on ->
            settings.descansoActivo = on
            renderAutomatico()
            WakeWordService.send(this, WakeWordService.ACTION_CHECK)
        }
        binding.swAuriculares.setOnCheckedChangeListener { _, on ->
            settings.soloAuriculares = on
            WakeWordService.send(this, WakeWordService.ACTION_CHECK)
        }
        binding.btnDesde.setOnClickListener {
            elegirHora(R.string.elegir_desde, settings.descansoDesde) { settings.descansoDesde = it }
        }
        binding.btnHasta.setOnClickListener {
            elegirHora(R.string.elegir_hasta, settings.descansoHasta) { settings.descansoHasta = it }
        }
    }

    private fun renderAutomatico() {
        binding.swDescanso.isChecked = settings.descansoActivo
        binding.swAuriculares.isChecked = settings.soloAuriculares
        binding.filaHoras.isVisible = settings.descansoActivo
        binding.btnDesde.text = getString(R.string.desde, ExySettings.hora(settings.descansoDesde))
        binding.btnHasta.text = getString(R.string.hasta, ExySettings.hora(settings.descansoHasta))
    }

    private fun elegirHora(titulo: Int, actual: Int, guardar: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(actual / 60)
            .setMinute(actual % 60)
            .setTitleText(titulo)
            .build()
        picker.addOnPositiveButtonClickListener {
            guardar(picker.hour * 60 + picker.minute)
            renderAutomatico()
            WakeWordService.send(this, WakeWordService.ACTION_CHECK)
        }
        picker.show(supportFragmentManager, "hora")
    }

    // ----------------------------------------------------------- actualización

    private fun setupActualizaciones() {
        binding.btnBuscarActualizacion.setOnClickListener {
            Actualizaciones.revisar(
                this,
                forzar = true,
                listo = { nueva ->
                    mostrarActualizacion(nueva)
                    if (nueva == null) toast(getString(R.string.sin_actualizacion))
                },
                error = { toast(getString(R.string.error_actualizacion)) },
            )
        }
        binding.btnDescargar.setOnClickListener {
            Actualizaciones.pendiente(this)?.let {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.descarga)))
            }
        }
        // Revisión automática, como mucho cada 6 horas.
        Actualizaciones.revisar(this, forzar = false, listo = ::mostrarActualizacion)
    }

    private fun mostrarActualizacion(nueva: Actualizaciones.Nueva?) {
        binding.bannerActualizacion.isVisible = nueva != null
        nueva?.let { binding.txtActualizacion.text = getString(R.string.hay_actualizacion, it.version) }
    }

    // ---------------------------------------------------------------- ajustes

    private fun setupAjustes() {
        val precision = ExySettings.Precision.desde(settings.sensitivity)
        binding.grupoPrecision.check(
            when (precision) {
                ExySettings.Precision.ESTRICTA -> R.id.precisionEstricta
                ExySettings.Precision.NORMAL -> R.id.precisionNormal
                ExySettings.Precision.SENSIBLE -> R.id.precisionSensible
            },
        )
        ayudaPrecision(precision)
        binding.grupoPrecision.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val elegida = when (id) {
                R.id.precisionEstricta -> ExySettings.Precision.ESTRICTA
                R.id.precisionSensible -> ExySettings.Precision.SENSIBLE
                else -> ExySettings.Precision.NORMAL
            }
            settings.sensitivity = elegida.sensibilidad
            ayudaPrecision(elegida)
            // La confianza mínima se fija al iniciar la escucha: hay que reiniciarla.
            WakeWordService.send(this, WakeWordService.ACTION_RELOAD)
        }

        val tiempos = mapOf(R.id.tiempo30 to 30, R.id.tiempo60 to 60, R.id.tiempo120 to 120, R.id.tiempo300 to 300)
        val actual = settings.resumeDelaySeconds
        val cercano = tiempos.minBy { (_, s) -> kotlin.math.abs(s - actual) }.key
        binding.grupoTiempo.check(cercano)
        binding.grupoTiempo.addOnButtonCheckedListener { _, id, checked ->
            if (checked) tiempos[id]?.let { settings.resumeDelaySeconds = it }
        }
    }

    private fun ayudaPrecision(p: ExySettings.Precision) {
        binding.txtPrecision.setText(
            when (p) {
                ExySettings.Precision.ESTRICTA -> R.string.help_precision_estricta
                ExySettings.Precision.NORMAL -> R.string.help_precision_normal
                ExySettings.Precision.SENSIBLE -> R.string.help_precision_sensible
            },
        )
    }

    // --------------------------------------------------------------- permisos

    private fun pedirNotificaciones() {
        val puedePreguntar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (puedePreguntar) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun pedirBateria() {
        // Diálogo directo del sistema; si el fabricante lo bloquea, la lista general.
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun sinLimiteBateria(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotifications(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun openAppSettings() {
        toast(getString(R.string.perm_denied_forever))
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // ------------------------------------------------------------- utilidades

    private fun separador(): View = View(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            resources.displayMetrics.density.toInt().coerceAtLeast(1),
        )
        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.exy_hair))
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        /** Extra para abrir la app y empezar a escuchar (botón de ajustes rápidos). */
        const val EXTRA_INICIAR = "cl.exy.app.INICIAR"
    }
}
