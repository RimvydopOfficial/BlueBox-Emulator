package com.rimvydop.bluebox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** First real boot test: transfers a live Surface and starts QEMU via Messenger. */
class VmSessionActivity : ComponentActivity() {
    private var service: Messenger? = null
    private var bound = false
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var launched = false
    private var stopRequested = false
    private lateinit var status: TextView
    private lateinit var startButton: Button

    private val responses = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val info = msg.data.getString(QemuIpc.KEY_STATUS, "")
            status.text = when (msg.what) {
                QemuIpc.MSG_VM_STARTING -> "Starting QEMU... $info"
                QemuIpc.MSG_VM_RUNNING -> "QEMU running. $info"
                QemuIpc.MSG_VM_STOPPING -> "Stopping... $info"
                QemuIpc.MSG_VM_STOPPED -> "QEMU exited: $info"
                QemuIpc.MSG_VM_ERROR -> "QEMU error: $info"
                else -> info
            }
            if (msg.what == QemuIpc.MSG_VM_STOPPED || msg.what == QemuIpc.MSG_VM_ERROR) {
                startButton.isEnabled = false // service process may terminate; reopen for a clean test
            }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            status.text = "QEMU service connected. Waiting for display..."
            maybeStart()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            status.text = "QEMU service disconnected. Check Logcat if boot failed."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("vm_name") ?: "Windows VM"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(8, 15, 32))
            setPadding(12, 30, 12, 12)
        }
        val title = TextView(this).apply {
            text = "BlueBox • $name"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
        }
        layout.addView(title)
        status = TextView(this).apply {
            text = "Connecting to QEMU service..."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
        layout.addView(status)
        val display = SurfaceView(this)
        layout.addView(display, LinearLayout.LayoutParams(-1, 0, 1f))
        display.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surface = holder.surface
                maybeStart()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                surface = holder.surface
                surfaceWidth = width
                surfaceHeight = height
                if (launched) sendSurface() else maybeStart()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                send(QemuIpc.MSG_CLEAR_SURFACE)
                surface = null
            }
        })
        startButton = Button(this).apply {
            text = "Starting..."
            isEnabled = false
        }
        layout.addView(startButton)
        layout.addView(Button(this).apply {
            text = "Stop VM and Close"
            setOnClickListener {
                stopRequested = true
                send(QemuIpc.MSG_STOP_VM)
                finish()
            }
        })
        setContentView(layout)
        bound = bindService(Intent(this, QemuService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) status.text = "Could not bind QEMU service. Check AndroidManifest.xml."
    }

    private fun send(what: Int, data: Bundle = Bundle()) {
        try {
            service?.send(Message.obtain(null, what).apply {
                this.data = data
                replyTo = responses
            })
        } catch (error: Exception) {
            status.text = "Service communication error: ${error.message}"
        }
    }

    private fun sendSurface() {
        val current = surface ?: return
        if (!current.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return
        send(QemuIpc.MSG_SET_SURFACE, Bundle().apply {
            putParcelable(QemuIpc.KEY_SURFACE, current)
            putInt(QemuIpc.KEY_SURFACE_WIDTH, surfaceWidth)
            putInt(QemuIpc.KEY_SURFACE_HEIGHT, surfaceHeight)
            putInt(QemuIpc.KEY_SURFACE_FORMAT, android.graphics.PixelFormat.RGBA_8888)
            putFloat(QemuIpc.KEY_SURFACE_REFRESH_RATE, 60f)
        })
    }

    private fun maybeStart() {
        if (launched || stopRequested || service == null || surface == null || surfaceWidth <= 0 || surfaceHeight <= 0) return
        launched = true
        sendSurface() // Register display before starting native QEMU.
        val ram = intent.getIntExtra("ram_mb", 2048)
        val disk = intent.getStringExtra("disk_uri") ?: ""
        val iso = intent.getStringExtra("iso_uri") ?: ""

        // These extras come from BlueBox v0.1.0 MainActivity's saved VM settings.
        val cpuCores = intent.getIntExtra("cpu_cores", 2).coerceIn(1, 8)
        val cpuModel = when (intent.getStringExtra("cpu_model")) {
            "qemu64", "max", "core2duo", "Nehalem", "SandyBridge", "Haswell" ->
                intent.getStringExtra("cpu_model")!!
            else -> "qemu64" // qemu32 is not supported by the current native selector.
        }
        val machine = when (intent.getStringExtra("machine_type")) {
            "q35" -> "q35"
            else -> "pc"
        }
        val diskInterface = when (intent.getStringExtra("disk_bus")?.lowercase()) {
            "virtio" -> "VirtIO Block"
            "ide" -> "IDE"
            else -> "AHCI"
        }
        val displayAdapter = when (intent.getStringExtra("video_adapter")?.lowercase()) {
            "cirrus" -> "Cirrus VGA"
            // "vmware" is not supported by this native executor; use Standard VGA.
            else -> "Standard VGA"
        }
        val soundCard = when (intent.getStringExtra("audio_device")?.lowercase()) {
            "ac97" -> "AC97"
            "sb16" -> "Sound Blaster 16"
            // Native executor currently substitutes Intel HDA for "none".
            else -> "Intel HDA"
        }
        val networkEnabled = intent.getBooleanExtra("network_enabled", false)
        val networkAdapter = when (intent.getStringExtra("network_adapter")?.lowercase()) {
            "rtl8139" -> "Realtek RTL8139"
            "e1000" -> "Intel E1000"
            // Native executor does not currently offer virtio-net-pci.
            else -> "Intel E1000"
        }

        send(QemuIpc.MSG_START_VM, Bundle().apply {
            putInt(QemuIpc.KEY_RAM_MB, ram)
            putInt(QemuIpc.KEY_CPU_CORES, cpuCores)
            putString(QemuIpc.KEY_CPU_MODEL, cpuModel)
            putString("disk_uri", disk)
            putString(QemuIpc.KEY_DISK_IMAGE_NAME, intent.getStringExtra("disk_image_name") ?: "")
            putString("iso_uri", iso)
            // Preserve existing installed Windows boot order when a hard disk is present.
            putString("boot_priority", if (disk.isBlank() && iso.isNotBlank()) "CD/DVD ISO First" else "Hard Disk First")
            putString("firmware_mode", "Legacy BIOS")
            putString(QemuIpc.KEY_MACHINE_TYPE, machine)
            putString(QemuIpc.KEY_DISK_INTERFACE, diskInterface)
            putString(QemuIpc.KEY_DISPLAY_ADAPTER, displayAdapter)
            putString(QemuIpc.KEY_SOUND_CARD, soundCard)
            putBoolean(QemuIpc.KEY_NETWORK_ENABLED, networkEnabled)
            putString(QemuIpc.KEY_NETWORK_ADAPTER, networkAdapter)
            putString(QemuIpc.KEY_NETWORK_MODE, "User (NAT)")
        })
        status.text = "Start request sent to QEMU..."
    }

    override fun onDestroy() {
        if (launched && !stopRequested) send(QemuIpc.MSG_STOP_VM)
        send(QemuIpc.MSG_CLEAR_SURFACE)
        if (bound) {
            try { unbindService(connection) } catch (_: Exception) { }
            bound = false
        }
        service = null
        super.onDestroy()
    }
}
