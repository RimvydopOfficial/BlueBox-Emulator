package com.rimvydop.bluebox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File

private val Bg = Color(0xFF080F20)
private val Panel = Color(0xFF101D35)
private val CardBg = Color(0xFF1B2B46)
private val Blue = Color(0xFF1468EF)
private val Sky = Color(0xFF69A7FF)
private val Muted = Color(0xFFA1B2CE)

private data class Vm(
    val id: String,
    val name: String,
    val arch: String,
    val ram: Int,
    val isoUri: String = "",
    val isoName: String = "",
    val diskUri: String = "",
    val diskName: String = "",
    val diskFormat: String = "",
    val cores: Int = 2,
    val cpuModel: String = "qemu64",
    val machine: String = "pc",
    val network: Boolean = false,
    val nic: String = "rtl8139",
    val audio: String = "ac97",
    val video: String = "std",
    val diskBus: String = "ide"
)

private fun loadVms(context: Context): List<Vm> {
    return try {
        val raw = context.getSharedPreferences("bluebox_vms", Context.MODE_PRIVATE)
            .getString("machines", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Vm(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Windows VM"),
                arch = obj.optString("arch", "x64"),
                ram = obj.optInt("ram", 2048),
                isoUri = obj.optString("isoUri"),
                isoName = obj.optString("isoName"),
                diskUri = obj.optString("diskUri"),
                diskName = obj.optString("diskName"),
                diskFormat = obj.optString("diskFormat"),
                cores = obj.optInt("cores", 2),
                cpuModel = obj.optString("cpuModel", "qemu64"),
                machine = obj.optString("machine", "pc"),
                network = obj.optBoolean("network", false),
                nic = obj.optString("nic", "rtl8139"),
                audio = obj.optString("audio", "ac97"),
                video = obj.optString("video", "std"),
                diskBus = obj.optString("diskBus", "ide")
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveVms(context: Context, vms: List<Vm>) {
    val array = JSONArray()
    vms.forEach { vm ->
        array.put(JSONObject().apply {
            put("id", vm.id)
            put("name", vm.name)
            put("arch", vm.arch)
            put("ram", vm.ram)
            put("isoUri", vm.isoUri)
            put("isoName", vm.isoName)
            put("diskUri", vm.diskUri)
            put("diskName", vm.diskName)
            put("diskFormat", vm.diskFormat)
            put("cores", vm.cores)
            put("cpuModel", vm.cpuModel)
            put("machine", vm.machine)
            put("network", vm.network)
            put("nic", vm.nic)
            put("audio", vm.audio)
            put("video", vm.video)
            put("diskBus", vm.diskBus)
        })
    }
    context.getSharedPreferences("bluebox_vms", Context.MODE_PRIVATE)
        .edit().putString("machines", array.toString()).apply()
}

private fun displayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: (uri.lastPathSegment ?: "Windows.iso")
    } catch (_: Exception) { uri.lastPathSegment ?: "Windows.iso" }
}

private fun qemuEngineReport(context: Context): String {
    val folder = File(context.applicationInfo.nativeLibraryDir)
    val names = listOf("libqemu-system-x86_64.so", "libbluebox_qemu_bridge.so")
    return buildString {
        appendLine("Engine location: APK native library directory")
        appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        names.forEach { name ->
            val file = File(folder, name)
            if (!file.isFile) {
                appendLine("$name: NOT INSTALLED")
            } else {
                val elf = try {
                    file.inputStream().use { input ->
                        val header = ByteArray(4)
                        input.read(header) == 4 && header.contentEquals(
                            byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
                        )
                    }
                } catch (_: Exception) { false }
                appendLine("$name: ${if (elf) "ELF FOUND" else "INVALID FILE"}")
                appendLine("Size: ${file.length()} bytes")
                appendLine("Executable permission: ${file.canExecute()}")
            }
        }
        appendLine()
        appendLine("File check only. Use Test QEMU Engine to verify native loading.")
    }
}

private fun prepareVmStart(context: Context, vm: Vm): String {
    if (vm.isoUri.isBlank() && vm.diskUri.isBlank()) {
        return "Cannot start: attach a Windows ISO or virtual hard disk first."
    }
    if (vm.ram !in 256..16384) return "Invalid VM RAM setting."
    val report = mutableListOf("VM: ${vm.name}", "Architecture: ${vm.arch}", "RAM: ${vm.ram} MB")
    val sources = listOf("ISO" to vm.isoUri, "Disk" to vm.diskUri)
    for ((label, source) in sources) {
        if (source.isBlank()) continue
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(source), "r")?.use { descriptor ->
                if (!descriptor.fileDescriptor.valid()) {
                    return "$label file descriptor is invalid."
                }
                report.add("$label: accessible")
            } ?: return "$label could not be opened."
        } catch (e: Exception) {
            return "$label access failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    return report.joinToString("\n") +
        "\n\nPreflight passed. Native QEMU service startup is not connected yet."
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BlueBoxScreen(this) }
    }
}

@Composable
private fun BlueBoxScreen(context: Context) {
    val vms = remember { mutableStateListOf<Vm>().apply { addAll(loadVms(context)) } }
    var page by remember { mutableStateOf("Home") }
    var currentId by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var fileTarget by remember { mutableStateOf("") }
    var draftName by remember { mutableStateOf("Windows 10") }
    var draftArch by remember { mutableStateOf("x64") }
    var draftRam by remember { mutableStateOf("2048") }
    var draftIsoUri by remember { mutableStateOf("") }
    var draftIsoName by remember { mutableStateOf("") }
    var draftDiskUri by remember { mutableStateOf("") }
    var draftDiskName by remember { mutableStateOf("") }
    var draftDiskFormat by remember { mutableStateOf("") }
    var draftCores by remember { mutableStateOf("2") }
    var draftCpuModel by remember { mutableStateOf("qemu64") }
    var draftMachine by remember { mutableStateOf("pc") }
    var draftNetwork by remember { mutableStateOf(false) }
    var draftNic by remember { mutableStateOf("rtl8139") }
    var draftAudio by remember { mutableStateOf("ac97") }
    var draftVideo by remember { mutableStateOf("std") }
    var draftDiskBus by remember { mutableStateOf("ide") }
    val preferences = remember { context.getSharedPreferences("bluebox_settings", Context.MODE_PRIVATE) }
    var confirmBeforeDelete by remember { mutableStateOf(preferences.getBoolean("confirm_delete", true)) }
    var showHints by remember { mutableStateOf(preferences.getBoolean("show_hints", true)) }

    fun openEditor(vm: Vm?) {
        currentId = vm?.id ?: ""
        draftName = vm?.name ?: "Windows 10"
        draftArch = vm?.arch ?: "x64"
        draftRam = (vm?.ram ?: 2048).toString()
        draftIsoUri = vm?.isoUri ?: ""
        draftIsoName = vm?.isoName ?: ""
        draftDiskUri = vm?.diskUri ?: ""
        draftDiskName = vm?.diskName ?: ""
        draftDiskFormat = vm?.diskFormat ?: ""
        draftCores = (vm?.cores ?: 2).toString()
        draftCpuModel = vm?.cpuModel ?: "qemu64"
        draftMachine = vm?.machine ?: "pc"
        draftNetwork = vm?.network ?: false
        draftNic = vm?.nic ?: "rtl8139"
        draftAudio = vm?.audio ?: "ac97"
        draftVideo = vm?.video ?: "std"
        draftDiskBus = vm?.diskBus ?: "ide"
        page = if (vm == null) "Create" else "Edit"
    }

    val isoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            if (!name.endsWith(".iso", true)) notice = "Please select an ISO (.iso) file."
            else try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (fileTarget == "draft") {
                    draftIsoUri = uri.toString(); draftIsoName = name
                } else {
                    val index = vms.indexOfFirst { it.id == fileTarget }
                    if (index >= 0) {
                        vms[index] = vms[index].copy(isoUri = uri.toString(), isoName = name)
                        saveVms(context, vms)
                    }
                }
            } catch (e: Exception) { notice = "ISO access failed: ${e.message}" }
        }
    }
    val diskPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            val format = when {
                name.endsWith(".qcow2", true) -> "qcow2"
                name.endsWith(".vhd", true) -> "vpc"
                else -> ""
            }
            if (format.isBlank()) notice = "Choose a .vhd or .qcow2 disk."
            else try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (fileTarget == "draft") {
                    draftDiskUri = uri.toString(); draftDiskName = name; draftDiskFormat = format
                } else {
                    val index = vms.indexOfFirst { it.id == fileTarget }
                    if (index >= 0) {
                        vms[index] = vms[index].copy(diskUri = uri.toString(), diskName = name, diskFormat = format)
                        saveVms(context, vms)
                    }
                }
            } catch (e: Exception) { notice = "Disk access failed: ${e.message}" }
        }
    }
    fun pickIso(target: String) { fileTarget = target; isoPicker.launch(arrayOf("*/*")) }
    fun pickDisk(target: String) { fileTarget = target; diskPicker.launch(arrayOf("*/*")) }
    fun launchVm(vm: Vm) {
        val check = prepareVmStart(context, vm)
        if (!check.contains("Preflight passed")) { notice = check; return }
        try {
            context.startActivity(Intent(context, VmSessionActivity::class.java).apply {
                putExtra("vm_name", vm.name)
                putExtra("ram_mb", vm.ram)
                putExtra("arch", vm.arch)
                // Hardware preferences are persisted but QemuService must consume them.
                putExtra("cpu_cores", vm.cores)
                putExtra("cpu_model", vm.cpuModel)
                putExtra("machine_type", vm.machine)
                putExtra("network_enabled", vm.network)
                putExtra("network_adapter", vm.nic)
                putExtra("audio_device", vm.audio)
                putExtra("video_adapter", vm.video)
                putExtra("disk_bus", vm.diskBus) // Requires VM session/service support to take effect
                putExtra("disk_uri", vm.diskUri)
                putExtra("disk_image_name", vm.diskName)
                putExtra("iso_uri", vm.isoUri)
            })
        } catch (e: Exception) { notice = "Unable to start VM: ${e.message}" }
    }

    val selected = vms.firstOrNull { it.id == currentId }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Sky, onPrimary = Bg,
        primaryContainer = Blue, onPrimaryContainer = Color.White,
        secondary = Sky, onSecondary = Bg,
        background = Bg, onBackground = Color.White,
        surface = Panel, onSurface = Color.White,
        surfaceVariant = CardBg, onSurfaceVariant = Muted,
        outline = Sky,
        onError = Color.White, error = Color(0xFFFF9696),
        surfaceTint = Panel,
        inverseSurface = Color.White, inverseOnSurface = Bg
    )) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(Blue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("▣", fontSize = 27.sp, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("BlueBox", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("YOUR PC. YOUR POCKET.", fontSize = 10.sp, color = Muted)
                }
                Text("v0.1.0", fontSize = 12.sp, color = Sky)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    "Home" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(20.dp)) {
                                Text("WELCOME TO BLUEBOX", color = Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(7.dp))
                                Text("Your virtual PCs,\none tap away.", fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text("${vms.size} virtual machine${if (vms.size == 1) "" else "s"} saved", color = Muted)
                                Spacer(Modifier.height(15.dp))
                                Button(onClick = { openEditor(null) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                                    Text("＋  Create New VM", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        item { Text("MY VIRTUAL MACHINES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Muted) }
                        if (vms.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(18.dp)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("▣", color = Sky, fontSize = 42.sp)
                                Text("No machines yet", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Create one to get started.", color = Muted)
                            }
                        }
                        items(vms, key = { it.id }) { vm ->
                            Column(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(18.dp)).padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).background(Blue.copy(alpha = .25f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("▣", color = Sky, fontSize = 27.sp) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(vm.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                                        Text("${vm.arch.uppercase()}  •  ${vm.ram} MB RAM", fontSize = 12.sp, color = Muted)
                                    }
                                    TextButton(onClick = { openEditor(vm) }) { Text("Edit", color = Sky) }
                                }
                                Spacer(Modifier.height(9.dp))
                                Text("DISK  ${vm.diskName.ifBlank { "Not attached" }}", fontSize = 11.sp, color = Muted, maxLines = 1)
                                Text("ISO    ${vm.isoName.ifBlank { "Not attached" }}", fontSize = 11.sp, color = Muted, maxLines = 1)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { launchVm(vm) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("▶  Start VM", color = Color.White) }
                                    OutlinedButton(onClick = { currentId = vm.id; page = "Details" }, shape = RoundedCornerShape(12.dp)) { Text("Manage", color = Sky) }
                                }
                            }
                        }
                    }
                    "Create", "Edit" -> {
                        val editing = page == "Edit"
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            item { TextButton(onClick = { page = "Home" }) { Text("← Back to machines", color = Sky) } }
                            item { Text(if (editing) "Edit Virtual Machine" else "Create Virtual Machine", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                            item { Text("GENERAL", color = Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            item { OutlinedTextField(draftName, { draftName = it }, label = { Text("VM name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) }
                            item {
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                    Text("Architecture", fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("x86", "x64").forEach { choice -> FilterChip(selected = draftArch == choice, onClick = { draftArch = choice }, label = { Text(choice) }) }
                                    }
                                    Text("x86 for 32-bit Windows; x64 for 64-bit Windows.", color = Muted, fontSize = 12.sp)
                                }
                            }
                            item { OutlinedTextField(draftRam, { draftRam = it.filter(Char::isDigit) }, label = { Text("Memory (MB)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp), supportingText = { Text("256–16384 MB; choose an amount your phone can handle") }) }
                            item { Text("PROCESSOR (CPU) & VIRTUAL HARDWARE", color = Sky, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                            item { ChoiceCard("CPU model (processor)", listOf("qemu64" to "QEMU 64-bit CPU", "max" to "Maximum supported CPU", "qemu32" to "QEMU 32-bit CPU"), draftCpuModel) { draftCpuModel = it } }
                            item { OutlinedTextField(draftCores, { draftCores = it.filter(Char::isDigit) }, label = { Text("CPU cores (1–8)", color = Color.White) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                            item { ChoiceCard("Machine type", listOf("pc" to "i440FX (PC)", "q35" to "Q35 (PCIe)"), draftMachine) { draftMachine = it } }
                            item { ChoiceCard("Graphics adapter", listOf("std" to "Standard VGA", "cirrus" to "Cirrus VGA", "vmware" to "VMware SVGA"), draftVideo) { draftVideo = it } }
                            item { ChoiceCard("Audio device", listOf("ac97" to "AC97", "sb16" to "Sound Blaster 16", "hda" to "Intel HDA", "none" to "Disabled"), draftAudio) { draftAudio = it } }
                            item {
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Network (NAT)", fontWeight = FontWeight.Bold)
                                            Text("Enable virtual network adapter", fontSize = 12.sp, color = Muted)
                                        }
                                        Switch(checked = draftNetwork, onCheckedChange = { draftNetwork = it })
                                    }
                                    if (draftNetwork) {
                                        Spacer(Modifier.height(8.dp))
                                        listOf("rtl8139" to "Realtek RTL8139", "e1000" to "Intel E1000", "virtio-net-pci" to "VirtIO").forEach { (value, label) ->
                                            Row(Modifier.fillMaxWidth().clickable { draftNic = value }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = draftNic == value, onClick = { draftNic = value })
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                            }
                            item { Text("STORAGE & INSTALLATION", color = Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            item { ChoiceCard("Hard disk interface (bus)", listOf("ide" to "IDE — maximum Windows compatibility", "virtio" to "VirtIO — faster, requires guest drivers"), draftDiskBus) { draftDiskBus = it } }
                            item { Text("IDE is the default for existing VMs. VirtIO requires compatible Windows drivers and QEMU service support.", color = Muted, fontSize = 12.sp) }
                            item {
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                    Text("Virtual hard disk", fontWeight = FontWeight.SemiBold)
                                    Text(draftDiskName.ifBlank { "No disk selected" }, color = Muted, fontSize = 12.sp, maxLines = 2)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { pickDisk("draft") }, modifier = Modifier.fillMaxWidth()) { Text("Choose VHD / QCOW2") }
                                    if (draftDiskUri.isNotBlank()) TextButton(onClick = { draftDiskUri = ""; draftDiskName = ""; draftDiskFormat = "" }) { Text("Remove disk") }
                                }
                            }
                            item {
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                    Text("Installation ISO", fontWeight = FontWeight.SemiBold)
                                    Text(draftIsoName.ifBlank { "No ISO selected (optional with a bootable disk)" }, color = Muted, fontSize = 12.sp, maxLines = 2)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { pickIso("draft") }, modifier = Modifier.fillMaxWidth()) { Text("Choose ISO") }
                                    if (draftIsoUri.isNotBlank()) TextButton(onClick = { draftIsoUri = ""; draftIsoName = "" }) { Text("Remove ISO") }
                                }
                            }
                            item {
                                val valid = draftName.isNotBlank() && (draftRam.toIntOrNull() ?: 0) in 256..16384 && (draftCores.toIntOrNull() ?: 0) in 1..8
                                Button(enabled = valid, onClick = {
                                    val vm = Vm(editing.takeIf { it }?.let { currentId } ?: UUID.randomUUID().toString(), draftName.trim(), draftArch, draftRam.toInt(), draftIsoUri, draftIsoName, draftDiskUri, draftDiskName, draftDiskFormat, draftCores.toInt(), draftCpuModel, draftMachine, draftNetwork, draftNic, draftAudio, draftVideo, draftDiskBus)
                                    val index = vms.indexOfFirst { it.id == vm.id }
                                    if (index >= 0) vms[index] = vm else vms.add(vm)
                                    saveVms(context, vms)
                                    page = "Home"
                                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                                    Text(if (editing) "Save VM Changes" else "Create Virtual Machine", color = Bg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    "Details" -> {
                        if (selected == null) { page = "Home" }
                        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { TextButton(onClick = { page = "Home" }) { Text("← Back", color = Sky) } }
                            item { Text(selected.name, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                            item { SectionCard("Configuration", "${selected.arch}  •  ${selected.ram} MB RAM  •  ${selected.cores} cores\nCPU: ${selected.cpuModel}  •  Machine: ${selected.machine}  •  Graphics: ${selected.video}\nAudio: ${selected.audio}  •  Network: ${if (selected.network) selected.nic else "Off"}") }
                            item { SectionCard("Virtual hard disk", selected.diskName.ifBlank { "Not attached" } + "\nInterface: ${selected.diskBus.uppercase()}") }
                            item { SectionCard("Installation ISO", selected.isoName.ifBlank { "Not attached" }) }
                            item { Button(onClick = { launchVm(selected) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("▶  Start VM", color = Color.White) } }
                            item { OutlinedButton(onClick = { openEditor(selected) }, modifier = Modifier.fillMaxWidth()) { Text("Edit VM settings & storage") } }
                            item { OutlinedButton(onClick = { if (confirmBeforeDelete) confirmDelete = true else { vms.removeAll { it.id == selected.id }; saveVms(context, vms); page = "Home" } }, modifier = Modifier.fillMaxWidth()) { Text("Delete Virtual Machine", color = Color(0xFFFF9696)) } }
                        }
                    }
                    "Files" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Files & Storage", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Manage the ISO and disk images attached to your virtual machines. Files remain in their original locations.", color = Muted) }
                        item { SectionCard("Supported formats", "Windows ISO (.iso)\nVirtual hard disk (.vhd / .qcow2)") }
                        if (vms.isEmpty()) item { SectionCard("No virtual machines", "Create a VM first to attach files.") }
                        items(vms, key = { it.id }) { vm ->
                            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                Text(vm.name, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("ISO: ${vm.isoName.ifBlank { "None" }}", fontSize = 12.sp, color = Muted, maxLines = 2)
                                Text("Disk: ${vm.diskName.ifBlank { "None" }}", fontSize = 12.sp, color = Muted, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { pickIso(vm.id) }, modifier = Modifier.weight(1f)) { Text("Change ISO", fontSize = 12.sp) }
                                    OutlinedButton(onClick = { pickDisk(vm.id) }, modifier = Modifier.weight(1f)) { Text("Change disk", fontSize = 12.sp) }
                                }
                                TextButton(onClick = { openEditor(vm) }) { Text("Edit attachments / remove", color = Sky) }
                            }
                        }
                    }
                    "Settings" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                        item { Text("PERSONALIZATION", color = Sky, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        item {
                            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Confirm before deleting VM", fontWeight = FontWeight.Bold); Text("Avoid accidental VM removal", fontSize = 12.sp, color = Muted) }
                                    Switch(checked = confirmBeforeDelete, onCheckedChange = { confirmBeforeDelete = it; preferences.edit().putBoolean("confirm_delete", it).apply() })
                                }
                                HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Show helpful tips", fontWeight = FontWeight.Bold); Text("Show setup guidance", fontSize = 12.sp, color = Muted) }
                                    Switch(checked = showHints, onCheckedChange = { showHints = it; preferences.edit().putBoolean("show_hints", it).apply() })
                                }
                            }
                        }
                        item { Text("ABOUT", color = Sky, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        item { SectionCard("BlueBox", "Version 0.1.0\nPC emulator for Android\nPowered by the native QEMU engine") }
                        item { SectionCard("VM Library", "${vms.size} virtual machines saved locally") }
                        item { SectionCard("What's next?", "Mouse and keyboard improvements, fullscreen controls, and display scaling are planned for a later release.") }
                        item { Text("Hardware options are stored in VM profiles. The QEMU service must be updated separately to apply them at runtime.", color = Muted, fontSize = 12.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Panel).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Home", "Files", "Settings").forEach { tab ->
                    Column(Modifier.weight(1f).clickable { page = tab }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when (tab) { "Home" -> "⌂"; "Files" -> "▤"; else -> "⚙" }, color = if (page == tab || (tab == "Home" && page in listOf("Create", "Edit", "Details"))) Sky else Muted, fontSize = 22.sp)
                        Text(tab, color = if (page == tab || (tab == "Home" && page in listOf("Create", "Edit", "Details"))) Sky else Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (confirmDelete && selected != null) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.name}?") },
            text = { Text("This removes the saved VM configuration only. Your ISO and virtual disk files will not be deleted.") },
            confirmButton = { TextButton(onClick = { vms.removeAll { it.id == selected.id }; saveVms(context, vms); confirmDelete = false; page = "Home" }) { Text("Delete", color = Color(0xFFFF9696)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
        if (notice.isNotBlank()) AlertDialog(
            onDismissRequest = { notice = "" }, title = { Text("BlueBox") },
            text = { Text(notice) }, confirmButton = { TextButton(onClick = { notice = "" }) { Text("OK") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, description: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(description, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ChoiceCard(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        options.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) })
                Text(label, fontSize = 13.sp, color = Color.White)
            }
        }
    }
}
