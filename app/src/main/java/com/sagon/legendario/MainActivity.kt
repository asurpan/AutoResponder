package com.sagon.legendario

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import com.sagon.legendario.ui.theme.LegendarioTheme

// --- COLORES PREMIUM "DEEP OCEAN" ---
val OceanDeep = Color(0xFF000B18)
val OceanSurface = Color(0xFF001F33)
val TurquoiseNeon = Color(0xFF00F5D4)
val MintElectric = Color(0xFF20FF8C)
val GoldPro = Color(0xFFFFD700)
val GlassWhite = Color(0x1AFFFFFF)

@Composable
fun DeepOceanTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = TurquoiseNeon,
        onPrimary = OceanDeep,
        primaryContainer = Color(0xFF004D40),
        onPrimaryContainer = TurquoiseNeon,
        secondary = MintElectric,
        background = OceanDeep,
        surface = OceanSurface,
        error = Color(0xFFFF4B4B)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

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
            DeepOceanTheme {
                var showSplash by remember { mutableStateOf(true) }
                
                if (showSplash) {
                    WaterFillingSplash(onFinish = { showSplash = false })
                } else {
                    MainScreen()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Canal de Prueba"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = "Para probar el robot de respuestas"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun sendTestNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prueba del Sistema")
            .setContentText("Hola, necesito presupuesto para pulir")
            .setPriority(NotificationCompat.PRIORITY_MAX)
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
fun WaterFillingSplash(onFinish: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
        label = "fill"
    )

    LaunchedEffect(Unit) {
        progress = 1f
        delay(3500)
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize().background(OceanDeep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).border(2.dp, TurquoiseNeon, CircleShape)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawWater(animatedProgress)
                }
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).align(Alignment.Center),
                    tint = if (animatedProgress > 0.5f) OceanDeep else TurquoiseNeon
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "AUTORESPONDER PRO",
                color = TurquoiseNeon,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                text = "Cargando tu asistente...",
                color = TurquoiseNeon.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun DrawScope.drawWater(progress: Float) {
    val wavePath = Path()
    val width = size.width
    val height = size.height
    val waterLevel = height * (1f - progress)
    
    wavePath.moveTo(0f, height)
    wavePath.lineTo(0f, waterLevel)
    
    // Crear efecto de onda sutil
    for (x in 0..width.toInt() step 10) {
        val y = waterLevel + (sin(x.toFloat() * 0.05f) * 10f)
        wavePath.lineTo(x.toFloat(), y)
    }
    
    wavePath.lineTo(width, height)
    wavePath.close()
    
    drawPath(path = wavePath, color = TurquoiseNeon)
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val prefs = remember { context.getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE) }
    
    var hasNotifPerm by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var hasContactsPerm by remember { mutableStateOf(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var isIgnoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    
    var usageCount by remember { mutableIntStateOf(prefs.getInt("usage_count", 0)) }
    var isPro by remember { mutableStateOf(prefs.getBoolean("is_pro_activated", false)) }
    var history by remember { mutableStateOf(prefs.getString("leads_history", "") ?: "") }
    var logs by remember { mutableStateOf(prefs.getString("activity_logs", "") ?: "") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = isNotificationServiceEnabled(context)
                hasContactsPerm = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                isIgnoringBattery = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while(true) {
            usageCount = prefs.getInt("usage_count", 0)
            isPro = prefs.getBoolean("is_pro_activated", false)
            history = prefs.getString("leads_history", "") ?: ""
            logs = prefs.getString("activity_logs", "") ?: ""
            delay(2000)
        }
    }

    Scaffold(
        containerColor = OceanDeep,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                LicenseWaterCard(usageCount, isPro)
            }
            
            item {
                AutomationCard(
                    isEnabled = prefs.getBoolean("service_enabled", false),
                    hasPerms = hasNotifPerm && isIgnoringBattery,
                    isPro = isPro || usageCount < 30,
                    onToggle = { newValue -> prefs.edit().putBoolean("service_enabled", newValue).apply() }
                )
            }

            item {
                SectionTitle(title = "CONFIGURACIÓN PRO", icon = Icons.Default.Settings)
                SettingsGrid(
                    hasNotif = hasNotifPerm,
                    hasContacts = hasContactsPerm,
                    hasBattery = isIgnoringBattery,
                    onOpenTest = { activity.sendTestNotification(context) }
                )
            }

            item {
                SectionTitle(title = "ÚLTIMOS LEADS CAPTADOS", icon = Icons.Default.TrendingUp)
            }

            val historyList = if (history.isEmpty()) emptyList() else history.split("|")
            if (historyList.isEmpty()) {
                item { EmptyLeadsPlaceholder() }
            } else {
                items(historyList.size) { index ->
                    LeadPremiumCard(historyList[index], onDelete = {
                        val newList = historyList.toMutableList()
                        newList.removeAt(index)
                        prefs.edit().putString("leads_history", newList.joinToString("|")).apply()
                    })
                }
            }

            item {
                SectionTitle(title = "CONSOLA DE ACTIVIDAD", icon = Icons.Default.Terminal)
                ActivityTerminal(logs)
            }
        }
    }
}

@Composable
fun LicenseWaterCard(count: Int, isPro: Boolean) {
    val targetFill = if (isPro) 1f else (count / 30f).coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(targetFill, tween(2000), label = "license")

    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        colors = CardDefaults.cardColors(containerColor = OceanSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) {
                drawWater(animatedFill)
            }
            
            Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                if (isPro) {
                    Text("VERSIÓN PREMIUM ACTIVA", color = TurquoiseNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("¡Uso ilimitado desbloqueado!", color = MintElectric, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PRUEBA GRATUITA", color = TurquoiseNeon, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("$count / 30", color = TurquoiseNeon, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedFill },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = TurquoiseNeon,
                        trackColor = GlassWhite
                    )
                    Text("Paga 3.99€ para uso de por vida", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeadPremiumCard(entry: String, onDelete: () -> Unit) {
    val parts = entry.split("#")
    val name = parts.getOrNull(1) ?: "Desconocido"
    val number = parts.getOrNull(2) ?: ""
    val date = parts.getOrNull(0) ?: ""
    val status = parts.getOrNull(3)?.toIntOrNull() ?: 0

    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onLongClick = { onDelete() },
            onClick = { if (number.isNotEmpty() && number != "Número no encontrado") context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))) }
        ),
        colors = CardDefaults.cardColors(containerColor = GlassWhite),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if(status == 1) MintElectric.copy(alpha = 0.5f) else GlassWhite)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(TurquoiseNeon.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = TurquoiseNeon)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White)
                Text(number, color = TurquoiseNeon, fontSize = 14.sp)
                Text(date, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
            if (status == 1) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintElectric)
            }
        }
    }
}

@Composable
fun AutomationCard(isEnabled: Boolean, hasPerms: Boolean, isPro: Boolean, onToggle: (Boolean) -> Unit) {
    val active = isEnabled && hasPerms && isPro
    Card(
        modifier = Modifier.fillMaxWidth().shadow(if(active) 10.dp else 0.dp, RoundedCornerShape(24.dp), ambientColor = TurquoiseNeon, spotColor = TurquoiseNeon),
        colors = CardDefaults.cardColors(containerColor = if(active) TurquoiseNeon.copy(alpha = 0.15f) else OceanSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, if(active) TurquoiseNeon else Color.Transparent)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if(active) "ROBOT OPERATIVO" else "ROBOT DETENIDO", fontWeight = FontWeight.ExtraBold, color = if(active) TurquoiseNeon else Color.White)
                Text(if(active) "Respondiendo presupuestos..." else "Activa los permisos para empezar", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Switch(
                checked = isEnabled, 
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = TurquoiseNeon, checkedTrackColor = TurquoiseNeon.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
fun SettingsGrid(hasNotif: Boolean, hasContacts: Boolean, hasBattery: Boolean, onOpenTest: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsItem("Permisos de Notificación", hasNotif) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        SettingsItem("Permisos de Agenda", hasContacts) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.fromParts("package", context.packageName, null) }) }
        SettingsItem("Optimización de Batería", hasBattery) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.fromParts("package", context.packageName, null) }) }
        
        Button(
            onClick = onOpenTest,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("LANZAR PRUEBA DE DOPAMINA", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsItem(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Icon(
            imageVector = if(ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if(ok) MintElectric else Color(0xFFFF4B4B)
        )
    }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = TurquoiseNeon.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = TurquoiseNeon.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun ActivityTerminal(logs: String) {
    val logList = if (logs.isEmpty()) emptyList() else logs.split("|")
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TurquoiseNeon.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                logList.forEach { log ->
                    Text(
                        text = "> $log",
                        color = if(log.contains("🚀")) TurquoiseNeon else MintElectric.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLeadsPlaceholder() {
    Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(GlassWhite, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Text("Esperando clientes potenciales...", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
