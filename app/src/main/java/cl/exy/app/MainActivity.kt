package cl.exy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.exy.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: ExySettings

    private val serviceListener: () -> Unit = { renderStatus() }

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
        settings = ExySettings(this)

        setupService()
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
        binding.txtLastHeard.text = WakeWordService.lastHeard
            ?.let { getString(R.string.last_heard, it) }
            ?: getString(R.string.last_heard_none)
        binding.btnStartStop.setText(
            if (WakeWordService.isRunning) R.string.btn_stop else R.string.btn_start,
        )
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
                // La confianza mínima se fija al iniciar la escucha: hay que reiniciarla.
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

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
