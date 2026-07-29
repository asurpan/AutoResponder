package com.sagon.legendario

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sagon.legendario.ui.theme.LegendarioTheme

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build

import android.os.PowerManager
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

class MainActivity : ComponentActivity() {
    private val CHANNEL_ID = "test_channel_v3"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.READ_CONTACTS)
        
        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            LegendarioTheme {
                val context = LocalContext.current
                var hasNotificationPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
                var hasContactsPermission by remember { 
                    mutableStateOf(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
                }
                var isIgnoringBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasNotificationPermission = isNotificationServiceEnabled(context)
                            hasContactsPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val prefs = remember { context.getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE) }
                var history by remember { mutableStateOf(prefs.getString("leads_history", "") ?: "") }
                var logs by remember { mutableStateOf(prefs.getString("activity_logs", "") ?: "") }
                var usageCount by remember { mutableIntStateOf(prefs.getInt("usage_count", 0)) }
                var isProActivated by remember { mutableStateOf(prefs.getBoolean("is_pro_activated", false)) }

                LaunchedEffect(Unit) {
                    while(true) {
                        history = prefs.getString("leads_history", "") ?: ""
                        logs = prefs.getString("activity_logs", "") ?: ""
                        usageCount = prefs.getInt("usage_count", 0)
                        isProActivated = prefs.getBoolean("is_pro_activated", false)
                        delay(2000)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(text = "PuliSevilla Automation", style = MaterialTheme.typography.headlineMedium)
                        }

                        item {
                            LicenseStatusPanel(usageCount, isProActivated)
                        }
                        
                        item {
                            val scope = rememberCoroutineScope()
                            AutomationControls(
                                hasPermission = hasNotificationPermission,
                                hasContactsPermission = hasContactsPermission,
                                isIgnoringBattery = isIgnoringBatteryOptimizations,
                                isPro = isProActivated || usageCount < 30,
                                isActuallyPro = isProActivated,
                                onSendTest = { 
                                    scope.launch {
                                        delay(5000)
                                        sendTestNotification(context)
                                    }
                                }
                            )
                        }

                        item { HorizontalDivider() }
                        
                        item {
                            Text(text = "Historial de Contactos (Leads)", style = MaterialTheme.typography.titleMedium)
                        }
                        
                        val historyList = if (history.isEmpty()) emptyList() else history.split("|")
                        if (historyList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                    Text("Aún no hay contactos registrados")
                                }
                            }
                        } else {
                            items(historyList.size) { index ->
                                val entry = historyList[index]
                                val parts = entry.split("#")
                                val date = parts.getOrNull(0) ?: ""
                                val name = parts.getOrNull(1) ?: "Desconocido"
                                val number = parts.getOrNull(2) ?: ""
                                val status = parts.getOrNull(3)?.toIntOrNull() ?: 0
                                
                                LeadCard(
                                    name = name,
                                    number = number,
                                    date = date,
                                    status = status, 
                                    onStatusChange = { newStatus ->
                                        val newList = historyList.toMutableList()
                                        newList[index] = "$date#$name#$number#$newStatus"
                                        val newHistory = newList.joinToString("|")
                                        prefs.edit().putString("leads_history", newHistory).apply()
                                        history = newHistory
                                    },
                                    onDelete = {
                                        val newList = historyList.toMutableList()
                                        newList.removeAt(index)
                                        val newHistory = newList.joinToString("|")
                                        prefs.edit().putString("leads_history", newHistory).apply()
                                        history = newHistory
                                    }
                                )
                            }
                        }

                        item { HorizontalDivider() }

                        item {
                            Text(text = "📟 Consola de Actividad (Logs)", style = MaterialTheme.typography.titleMedium)
                        }
                        
                        item {
                            ActivityConsoleView(logs)
                        }
                        
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = android.content.ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }

    private fun createNotificationChannel() {
        val name = "Canal de Prueba"
        val descriptionText = "Para probar el robot de respuestas"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendTestNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prueba del Sistema")
            .setContentText("Hola, necesito presupuesto para pulir")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        val remoteInput = androidx.core.app.RemoteInput.Builder("result_key").setLabel("Responder").build()
        val testIntent = Intent(context, MainActivity::class.java)
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Responder",
            android.app.PendingIntent.getActivity(context, 0, testIntent, android.app.PendingIntent.FLAG_MUTABLE)
        ).addRemoteInput(remoteInput).build()
        
        builder.addAction(action)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1234, builder.build())
    }
}

@Composable
fun LicenseStatusPanel(usageCount: Int, isPro: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isPro) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⭐ VERSIÓN PRO ACTIVA", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "Uso Ilimitado", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text(text = "Versión de Prueba Gratuita", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { usageCount / 30f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Mensajes enviados: $usageCount / 30",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (usageCount >= 30) {
                    Text(
                        text = "⚠️ Límite alcanzado. Robot detenido.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityConsoleView(logs: String) {
    val logList = if (logs.isEmpty()) emptyList() else logs.split("|")

    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (logList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Esperando actividad...", color = androidx.compose.ui.graphics.Color.Green)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logList.size) { index ->
                    val log = logList[index]
                    Text(
                        text = log,
                        color = when {
                            log.contains("❌") || log.contains("⚠️") -> androidx.compose.ui.graphics.Color.Red 
                            log.contains("🚀") -> androidx.compose.ui.graphics.Color.Cyan
                            log.contains("✉️") -> androidx.compose.ui.graphics.Color.Yellow 
                            else -> androidx.compose.ui.graphics.Color.Green
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    if (index < logList.size - 1) {
                        HorizontalDivider(color = androidx.compose.ui.graphics.Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeadCard(name: String, number: String, date: String, status: Int, onStatusChange: (Int) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar contacto?") },
            text = { Text("¿Estás seguro de que quieres eliminar este contacto?") },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Borrar")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (number.isNotEmpty() && number != "Número no encontrado") {
                        val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    }
                },
                onLongClick = { showDeleteDialog = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when(status) {
                1 -> MaterialTheme.colorScheme.primaryContainer
                2 -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (number.isNotEmpty()) {
                Text(text = number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(text = date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    text = when(status) { 1 -> "✅ COBRADO"; 2 -> "❌ NO HECHO"; else -> "🟡 PENDIENTE" },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onStatusChange(1) }) { Text("Cobrado") }
                TextButton(onClick = { onStatusChange(2) }) { Text("No hecho") }
            }
        }
    }
}

@Composable
fun AutomationControls(
    hasPermission: Boolean, 
    hasContactsPermission: Boolean, 
    isIgnoringBattery: Boolean, 
    isPro: Boolean,
    isActuallyPro: Boolean,
    onSendTest: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE) }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("service_enabled", false)) }
    var techNumber by remember { mutableStateOf(prefs.getString("tech_number", "34677319393") ?: "34677319393") }
    
    val defaultKeywords = "pulir, suelo, brillo, pulidor, terrazo, marmol, escalera, presupuesto, cuanto, precio, cera, repasar, salon"
    var keywords by remember { mutableStateOf(prefs.getString("keywords", defaultKeywords) ?: defaultKeywords) }
    
    val defaultDayMsg = "¡Buenas! Soy Jose el pulidor. Ahora mismo estoy liado con la máquina y no puedo atenderte bien por aquí. Escríbeme porfa a mi número personal {numero} y así te paso un presupuesto bueno y económico rápido. Dime por allí los metros, la zona donde estás y mándame alguna foto del suelo si puedes. ¡En cuanto pare un segundo te escribo!"
    val defaultNightMsg = "¡Buenas! Soy Jose el pulidor. Ya he terminado por hoy y estoy descansando. Para dejarte el presupuesto listo para mañana a primera hora, escríbeme porfa a mi número personal {numero}. Déjame allí los metros, la zona y mándame alguna foto del suelo. ¡Mañana a primera hora te paso el precio sin falta! ¡Gracias!"
    
    var dayMsg by remember { mutableStateOf(prefs.getString("msg_day", defaultDayMsg) ?: defaultDayMsg) }
    var nightMsg by remember { mutableStateOf(prefs.getString("msg_night", defaultNightMsg) ?: defaultNightMsg) }

    var startHour by remember { mutableStateOf(prefs.getInt("start_hour", 8)) }
    var endHour by remember { mutableStateOf(prefs.getInt("end_hour", 22)) }

    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showDayMsgDialog by remember { mutableStateOf(false) }
    var showNightMsgDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showActivationDialog by remember { mutableStateOf(false) }

    val isActuallyRunning = isEnabled && hasPermission && isPro
    val needsAttention = isEnabled && (!hasPermission || !isIgnoringBattery || !hasContactsPermission || !isPro)

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.errorContainer,
        targetValue = MaterialTheme.colorScheme.error,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkColor"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isActuallyPro) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "🚀 ¡ACTIVA LA VERSIÓN PRO!", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Uso ilimitado y soporte directo por solo 3.99€.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showActivationDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar con mi código")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActuallyRunning) MaterialTheme.colorScheme.primaryContainer 
                                else if (needsAttention) blinkColor
                                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActuallyRunning) "SERVICIO ACTIVO ✅" 
                          else if (isEnabled && !isPro) "PAGO REQUERIDO 💰"
                          else if (isEnabled) "¡REVISA PERMISOS! ⚠️"
                          else "SERVICIO APAGADO ⚪",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (needsAttention) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { 
                        isEnabled = it
                        prefs.edit().putBoolean("service_enabled", it).apply()
                    }
                )
            }
        }

        OutlinedTextField(
            value = techNumber,
            onValueChange = { 
                techNumber = it
                prefs.edit().putString("tech_number", it).apply()
            },
            label = { Text("Número del Pulidor") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (hasPermission) ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                     else if (isEnabled) ButtonDefaults.buttonColors(containerColor = blinkColor)
                     else ButtonDefaults.buttonColors()
        ) {
            Text(
                text = if (hasPermission) "✅ Permisos Notificación" else "Dar Permiso de Notificaciones",
                color = if (!hasPermission && isEnabled) MaterialTheme.colorScheme.onError else androidx.compose.ui.graphics.Color.Unspecified
            )
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (hasContactsPermission) ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                     else if (isEnabled) ButtonDefaults.buttonColors(containerColor = blinkColor)
                     else ButtonDefaults.buttonColors()
        ) {
            Text(
                text = if (hasContactsPermission) "✅ Permiso de Agenda" else "Dar Permiso de Agenda",
                color = if (!hasContactsPermission && isEnabled) MaterialTheme.colorScheme.onError else androidx.compose.ui.graphics.Color.Unspecified
            )
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (isIgnoringBattery) ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                     else if (isEnabled) ButtonDefaults.buttonColors(containerColor = blinkColor)
                     else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(
                text = if (isIgnoringBattery) "✅ Batería: Sin Restricciones" else "⚙️ Ajustes de Batería",
                color = if (!isIgnoringBattery && isEnabled) MaterialTheme.colorScheme.onError else androidx.compose.ui.graphics.Color.Unspecified
            )
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "⚠️ ¿No funciona en tu Xiaomi?", style = MaterialTheme.typography.titleSmall)
                Text(text = "1. En 'Ajustes de Batería' activa 'Sin restricciones'.\n2. En 'Info de aplicación', activa 'Inicio automático'.\n3. Si los permisos salen bloqueados: ve a Info de aplicación -> 3 puntos -> 'Permitir ajustes restringidos'.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showDebugDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("🔍 Probar si lee notificaciones") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSendTest() }, modifier = Modifier.fillMaxWidth(), enabled = isPro) { Text("🚀 Lanzar Prueba de Robot") }
            }
        }

        Text(text = "Configuración Personalizada:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

        Card(onClick = { showScheduleDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Horario Laboral:", style = MaterialTheme.typography.labelMedium)
                Text("$startHour:00 a $endHour:00", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(onClick = { showKeywordsDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Palabras Clave:", style = MaterialTheme.typography.labelMedium)
                Text(text = keywords, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Card(onClick = { showDayMsgDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Respuesta Laboral:", style = MaterialTheme.typography.labelMedium)
                Text(text = dayMsg, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Card(onClick = { showNightMsgDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Respuesta Descanso:", style = MaterialTheme.typography.labelMedium)
                Text(text = nightMsg, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }

    if (showActivationDialog) {
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = { Text("Activar Versión PRO") },
            text = {
                Column {
                    Text("Para obtener tu código de activación:", style = MaterialTheme.typography.bodySmall)
                    Text("1. Envía un Bizum de 3.99€ a appsaiber@gmail.com", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("2. Envía captura del pago al mismo email.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Introduce tu código") },
                        isError = error.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotEmpty()) {
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (code == "121212") {
                        prefs.edit().putBoolean("is_pro_activated", true).apply()
                        showActivationDialog = false
                    } else {
                        error = "Código incorrecto."
                    }
                }) { Text("Activar") }
            },
            dismissButton = { TextButton(onClick = { showActivationDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showScheduleDialog) {
        var tStart by remember { mutableIntStateOf(startHour) }
        var tEnd by remember { mutableIntStateOf(endHour) }
        AlertDialog(onDismissRequest = { showScheduleDialog = false }, title = { Text("Editar Horario") }, text = {
            Column {
                Text("Inicio: $tStart:00")
                Slider(value = tStart.toFloat(), onValueChange = { tStart = it.toInt() }, valueRange = 0f..23f)
                Text("Fin: $tEnd:00")
                Slider(value = tEnd.toFloat(), onValueChange = { tEnd = it.toInt() }, valueRange = 0f..23f)
            }
        }, confirmButton = { Button(onClick = { startHour = tStart; endHour = tEnd; prefs.edit().putInt("start_hour", tStart).putInt("end_hour", tEnd).apply(); showScheduleDialog = false }) { Text("Guardar") } })
    }
    
    if (showKeywordsDialog) {
        var temp by remember { mutableStateOf(keywords) }
        AlertDialog(onDismissRequest = { showKeywordsDialog = false }, title = { Text("Palabras Clave") }, text = { OutlinedTextField(value = temp, onValueChange = { temp = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { keywords = temp; prefs.edit().putString("keywords", temp).apply(); showKeywordsDialog = false }) { Text("Guardar") } })
    }
    
    if (showDayMsgDialog) {
        var temp by remember { mutableStateOf(dayMsg) }
        AlertDialog(onDismissRequest = { showDayMsgDialog = false }, title = { Text("Mensaje Laboral") }, text = { OutlinedTextField(value = temp, onValueChange = { temp = it }, modifier = Modifier.fillMaxWidth().height(150.dp)) }, confirmButton = { Button(onClick = { dayMsg = temp; prefs.edit().putString("msg_day", temp).apply(); showDayMsgDialog = false }) { Text("Guardar") } })
    }
    
    if (showNightMsgDialog) {
        var temp by remember { mutableStateOf(nightMsg) }
        AlertDialog(onDismissRequest = { showNightMsgDialog = false }, title = { Text("Mensaje Descanso") }, text = { OutlinedTextField(value = temp, onValueChange = { temp = it }, modifier = Modifier.fillMaxWidth().height(150.dp)) }, confirmButton = { Button(onClick = { nightMsg = temp; prefs.edit().putString("msg_night", temp).apply(); showNightMsgDialog = false }) { Text("Guardar") } })
    }
    
    if (showDebugDialog) {
        val lastNotif = prefs.getString("debug_last_notif", "Ninguna aún.") ?: ""
        AlertDialog(onDismissRequest = { showDebugDialog = false }, title = { Text("Diagnóstico") }, text = { Text(lastNotif) }, confirmButton = { Button(onClick = { showDebugDialog = false }) { Text("Cerrar") } })
    }
}
