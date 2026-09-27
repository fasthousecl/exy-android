package cl.exy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cl.exy.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var secureStore: SecureStore
    private lateinit var settings: ExySettings

    private val serviceListener: () -> Unit = { renderStatus() }

    /** Qué archivo se está importando en este momento. */
    private var pendingImport: ModelFile? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = pendingImport ?: return@registerForActivityResult
        pendingImport = null
        if (uri != null) importFile(uri, type)
    }

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            openAppSettings()
        }
        renderPermissions()
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { renderPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        secureStore = SecureStore(this)
        settings = ExySettings(this)

        setupService()
        setupAccessKey()
        setupFiles()
        setupSliders()
        setupPermissions()
        binding.txtVersion.text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    override fun onStart() {
        super.onStart()
        WakeWordService.addListener(serviceListener)
    }

    override fun onStop() {
        WakeWordService.removeListener(serviceListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        renderAll()
    }

    private fun renderAll() {
        renderStatus()
        renderAccessKey()
        renderFiles()
        renderPermissions()
    }

    // ----------------------------------------------------------- servicio

    private fun setupService() {
        binding.btnStartStop.setOnClickListener {
            if (WakeWordService.isRunning) {
                WakeWordService.send(this, WakeWordService.ACTION_STOP)
                return@setOnClickListener
            }
            if (!hasMic()) {
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
                return@setOnClickListener
            }
            val missing = WakeWordService.missingConfig(this)
            if (missing.isNotEmpty()) {
                toast(getString(R.string.missing_config, missing.joinToString(", ")))
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications()) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            WakeWordService.start(this)
        }

        binding.btnTestAssistant.setOnClickListener {
            if (!AssistantLauncher.launch(this)) toast(getString(R.string.no_assistant))
        }
    }

    private fun renderStatus() {
        val message = WakeWordService.lastMessage
        val text = when (WakeWordService.state) {
            WakeWordService.State.STOPPED -> getString(R.string.status_stopped)
            WakeWordService.State.STARTING -> getString(R.string.status_starting)
            WakeWordService.State.LISTENING -> getString(R.string.status_listening)
            WakeWordService.State.PAUSED -> getString(R.string.status_paused)
            WakeWordService.State.COOLDOWN -> getString(R.string.status_cooldown)
            WakeWordService.State.ERROR -> getString(R.string.status_error, message.orEmpty())
        }
        val extra = if (WakeWordService.state != WakeWordService.State.ERROR && message != null) {
            "\n$message"
        } else {
            ""
        }
        binding.txtStatus.text = text + extra
        binding.btnStartStop.setText(
            if (WakeWordService.isRunning) R.string.btn_stop else R.string.btn_start,
        )
    }

    // ---------------------------------------------------------- AccessKey

    private fun setupAccessKey() {
        binding.btnSaveKey.setOnClickListener {
            val value = binding.editAccessKey.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) {
                toast(getString(R.string.key_empty))
                return@setOnClickListener
            }
            try {
                secureStore.saveAccessKey(value)
                binding.editAccessKey.text = null
                toast(getString(R.string.key_saved))
                WakeWordService.send(this, WakeWordService.ACTION_RELOAD)
            } catch (e: Exception) {
                toast(getString(R.string.key_error, e.message ?: e.javaClass.simpleName))
            }
            renderAccessKey()
        }

        binding.btnDeleteKey.setOnClickListener {
            secureStore.clearAccessKey()
            renderAccessKey()
        }
    }

    private fun renderAccessKey() {
        val saved = secureStore.hasAccessKey()
        binding.txtKeyStatus.setText(if (saved) R.string.key_saved else R.string.key_missing)
        binding.txtKeyStatus.setTextColor(color(if (saved) R.color.exy_ok else R.color.exy_warn))
        binding.btnDeleteKey.isVisible = saved
    }

    // ------------------------------------------------------------ archivos

    private fun setupFiles() {
        binding.btnImportPpn.setOnClickListener { pick(ModelFile.KEYWORD) }
        binding.btnImportPv.setOnClickListener { pick(ModelFile.MODEL) }
    }

    private fun pick(type: ModelFile) {
        pendingImport = type
        // Los .ppn y .pv no tienen tipo MIME conocido, así que se aceptan todos
        // y se valida la extensión al importar.
        pickFile.launch(arrayOf("*/*"))
    }

    private fun importFile(uri: Uri, type: ModelFile) {
        try {
            ModelFiles.import(this, uri, type)
            toast(getString(R.string.import_ok))
            WakeWordService.send(this, WakeWordService.ACTION_RELOAD)
        } catch (e: IllegalArgumentException) {
            toast(e.message.orEmpty())
        } catch (e: Exception) {
            toast(getString(R.string.import_error, e.message ?: e.javaClass.simpleName))
        }
        renderFiles()
    }

    private fun renderFiles() {
        renderFile(ModelFile.KEYWORD, binding.txtPpn)
        renderFile(ModelFile.MODEL, binding.txtPv)
    }

    private fun renderFile(type: ModelFile, view: android.widget.TextView) {
        if (type.exists(this)) {
            val name = ModelFiles.originalName(this, type) ?: type.fileName
            val size = Formatter.formatShortFileSize(this, type.file(this).length())
            view.text = getString(R.string.file_ok, name, size)
            view.setTextColor(color(R.color.exy_ok))
        } else {
            view.setText(R.string.file_missing)
            view.setTextColor(color(R.color.exy_warn))
        }
    }

    // -------------------------------------------------------------- ajustes

    private fun setupSliders() {
        val sensitivity = (settings.sensitivity * 20).roundToInt() / 20f
        binding.sliderSensitivity.value = sensitivity.coerceIn(0f, 1f)
        binding.txtSensitivity.text = getString(R.string.label_sensitivity, sensitivity)
        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            binding.txtSensitivity.text = getString(R.string.label_sensitivity, value)
        }
        binding.sliderSensitivity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                settings.sensitivity = slider.value
                // La sensibilidad se fija al crear Porcupine: hay que reiniciarlo.
                WakeWordService.send(this@MainActivity, WakeWordService.ACTION_RELOAD)
            }
        })

        val delay = ((settings.resumeDelaySeconds / 5f).roundToInt() * 5).coerceIn(10, 300)
        binding.sliderResumeDelay.value = delay.toFloat()
        binding.txtResumeDelay.text = getString(R.string.label_resume_delay, delay)
        binding.sliderResumeDelay.addOnChangeListener { _, value, _ ->
            binding.txtResumeDelay.text = getString(R.string.label_resume_delay, value.roundToInt())
            settings.resumeDelaySeconds = value.roundToInt()
        }
    }

    // ------------------------------------------------------------- permisos

    private fun setupPermissions() {
        binding.btnPermMic.setOnClickListener { requestMic.launch(Manifest.permission.RECORD_AUDIO) }

        binding.btnPermNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shouldAskNotificationsAtRuntime()
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
        }

        binding.btnPermOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }

        binding.btnPermBattery.setOnClickListener {
            // Diálogo directo del sistema; si el fabricante lo bloquea, se abre la lista general.
            val direct = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
            try {
                startActivity(direct)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /** En Android 13+ se puede pedir en diálogo mientras el usuario no lo haya negado dos veces. */
    private fun shouldAskNotificationsAtRuntime(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun renderPermissions() {
        renderPermission(binding.btnPermMic, hasMic())
        renderPermission(binding.btnPermNotif, hasNotifications())
        renderPermission(binding.btnPermOverlay, Settings.canDrawOverlays(this))
        renderPermission(
            binding.btnPermBattery,
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
        )
    }

    private fun renderPermission(button: MaterialButton, granted: Boolean) {
        button.setText(if (granted) R.string.perm_granted else R.string.perm_request)
        button.isEnabled = !granted
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotifications(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun openAppSettings() {
        toast(getString(R.string.perm_denied_forever))
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    // -------------------------------------------------------------- utilidades

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
