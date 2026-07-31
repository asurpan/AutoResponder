/* Copyright (c) 2026 AutoResponder Pro / Lorensoft. Todos los derechos reservados. */
package com.sagon.legendario

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import android.util.Base64
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import androidx.core.app.RemoteInput
import android.media.AudioManager
import android.media.SoundPool
import android.content.ClipboardManager
import android.content.ClipData
import java.security.MessageDigest
import android.media.ToneGenerator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import com.sagon.legendario.ui.theme.AutoResponderTheme

// --- COLORES PREMIUM "MIDNIGHT OCEAN" ---
val OceanDeep = Color(0xFF001A2E) // Azul medianoche más notable y elegante
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

    private var onContactPickedCallback: ((String) -> Unit)? = null

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { contactUri ->
            val projection = arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    onContactPickedCallback?.invoke(name)
                }
            }
        }
    }

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
                    MainScreen(this)
                }
            }
        }
    }

    fun requestContactsPermission() {
        requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
    }

    fun pickContact(onPicked: (String) -> Unit) {
        onContactPickedCallback = onPicked
        pickContactLauncher.launch(null)
    }

    fun playActivationSound() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            
            // Usamos un hilo para el segundo pitido para no bloquear la UI
            Thread {
                try {
                    Thread.sleep(300)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    Thread.sleep(500)
                    tg.release()
                } catch (e: Exception) {
                    try { tg.release() } catch (ignored: Exception) {}
                }
            }.start()
        } catch (e: Exception) {
            android.util.Log.e("AutoResponder", "Error en sonido: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Test Channel"
            val descriptionText = "Channel for test notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) // Silenciar el canal de prueba para oír solo al asistente
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendTestNotification(context: Context) {
        val prefs = context.getSharedPreferences("AutoResponderPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)
        
        if (!isEnabled) {
            Toast.makeText(context, "⚠️ ¡Activa primero el interruptor del asistente!", Toast.LENGTH_LONG).show()
        }

        // Crear una acción de respuesta (RemoteInput) para que el asistente la detecte
        val remoteInput = RemoteInput.Builder("direct_reply")
            .setLabel("Responder...")
            .build()

        val replyIntent = Intent("com.sagon.legendario.TEST_REPLY").apply {
            setPackage(context.packageName)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context, 0, replyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val action = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher, "Responder", replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Cliente de Prueba")
            .setContentText("Hola soy jose, quiero un presupuesto")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(action) // Añadimos la acción con el RemoteInput

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
        
        Toast.makeText(context, "Notificación enviada. ¡Verifica si el asistente responde!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun WaterFillingSplash(onFinish: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(150) // Pausa breve antes de que suba el agua
        
        val duration = 3000L
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < duration) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            delay(16) 
        }
        
        progress = 1f
        delay(500)
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize().background(OceanDeep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).border(2.dp, TurquoiseNeon, CircleShape)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawWater(progress)
                }
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).align(Alignment.Center),
                    tint = if (progress > 0.5f) OceanDeep else TurquoiseNeon
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
fun MainScreen(activity: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AutoResponderPrefs", Context.MODE_PRIVATE) }
    val syncManager = remember { UsageSyncManager(context) }
    val androidId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "0000" }
    
    var hasNotifPerm by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var hasContactsPerm by remember { mutableStateOf(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var isIgnoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    
    var isPro by remember { mutableStateOf(true) } // Siempre Pro en versión de pago
    var history by remember { mutableStateOf(prefs.getString("leads_history", "") ?: "") }
    var logs by remember { mutableStateOf(prefs.getString("activity_logs", "") ?: "") }
    var isAssistantEnabled by remember { mutableStateOf(prefs.getBoolean("service_enabled", false)) }
    var isSoundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }

    // --- NUEVOS ESTADOS DE CONFIGURACIÓN ---
    val defaultKeywords = "presupuesto, averia, reparacion, urgente, mantenimiento, cuanto, precio, servicio, instalacion, arreglar"
    var keywords by remember { 
        val current = prefs.getString("keywords", defaultKeywords) ?: defaultKeywords
        val cleaned = current.replace(", mi pulidor", "").replace("mi pulidor,", "").replace("mi pulidor", "").trim()
        mutableStateOf(if (cleaned.endsWith(",")) cleaned.dropLast(1) else cleaned)
    }
    var techNumber by remember { mutableStateOf(prefs.getString("tech_number", "600000000") ?: "600000000") }
    var blacklist by remember { mutableStateOf(prefs.getString("blacklist", "") ?: "") }
    var promoLink by remember { mutableStateOf(prefs.getString("promo_link", "") ?: "") }
    var repetitionDelay by remember { mutableIntStateOf(prefs.getInt("repetition_delay", 24)) }
    var vipList by remember { mutableStateOf(prefs.getString("vip_list", "") ?: "") }
    
    val defaultDayMsg = "¡Buenas! Soy el técnico de mantenimiento. Ahora mismo estoy trabajando y no puedo atenderte bien por aquí. Escríbeme porfa a mi número personal {numero} y dime que quieres el DESCUENTO DEL MES. Así te paso un presupuesto rápido. ¡En cuanto pare un segundo te escribo!"
    val defaultNightMsg = "¡Buenas! Soy el técnico de mantenimiento. Ya he terminado por hoy. Para dejarte el presupuesto listo para mañana a primera hora, escríbeme porfa a mi número personal {numero} mencionando el DESCUENTO DEL MES. ¡Mañana a primera hora te paso el precio con la oferta aplicada sin falta! ¡Gracias!"
    
    var msgDay by remember { mutableStateOf(prefs.getString("msg_day", defaultDayMsg) ?: defaultDayMsg) }
    var msgNight by remember { mutableStateOf(prefs.getString("msg_night", defaultNightMsg) ?: defaultNightMsg) }
    
    val defaultVipMsg = "¡Hola! He recibido tu mensaje. Como eres un contacto VIP, te respondo automáticamente aunque no haya usado palabras clave (solo cuando tengo la pantalla apagada). ¡En cuanto pueda te escribo!"
    var msgVip by remember { mutableStateOf(prefs.getString("msg_vip", defaultVipMsg) ?: defaultVipMsg) }

    // --- HORARIOS DINÁMICOS ---
    var startHourMonSat by remember { mutableIntStateOf(prefs.getInt("start_hour_mon_sat", 8)) }
    var endHourMonSat by remember { mutableIntStateOf(prefs.getInt("end_hour_mon_sat", 22)) }
    var startHourSun by remember { mutableIntStateOf(prefs.getInt("start_hour_sun", 9)) }
    var endHourSun by remember { mutableIntStateOf(prefs.getInt("end_hour_sun", 14)) }
    
    var showHourPickerMonSat by remember { mutableStateOf(false) }
    var showHourPickerSun by remember { mutableStateOf(false) }

    // --- ESTADOS DE INTERFAZ INTELIGENTE ---
    var hasVerifiedNotifContent by rememberSaveable { mutableStateOf(prefs.getBoolean("notif_verified", false)) }
    val allPermsOk = hasNotifPerm && hasContactsPerm && isIgnoringBattery && hasVerifiedNotifContent
    var userExpandedSettings by remember { mutableStateOf<Boolean?>(null) }
    val isSettingsExpanded = userExpandedSettings ?: !allPermsOk

    val isAssistantComplete = keywords.isNotBlank() && techNumber.isNotBlank() && msgDay.isNotBlank() && msgNight.isNotBlank()
    var userExpandedAssistant by remember { mutableStateOf<Boolean?>(null) }
    val isAssistantExpanded = userExpandedAssistant ?: !isAssistantComplete

    var showConsole by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var showMasterDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(!prefs.getBoolean("disclosure_accepted", false)) }
    var selectedLeadEntry by remember { mutableStateOf<String?>(null) }
    var leadIndexToDelete by remember { mutableStateOf<Int?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Listener de SharedPreferences para actualizaciones en tiempo real
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = isNotificationServiceEnabled(context)
                hasContactsPerm = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                isIgnoringBattery = isIgnoringBatteryOptimizations(context)
            }
        }
        
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p: SharedPreferences, key: String? ->
            when (key) {
                "activity_logs" -> logs = p.getString("activity_logs", "") ?: ""
                "leads_history" -> history = p.getString("leads_history", "") ?: ""
                "usage_count" -> usageCount = p.getInt("usage_count", usageCount)
                "service_enabled" -> isAssistantEnabled = p.getBoolean("service_enabled", false)
                "is_pro_activated" -> isPro = p.getBoolean("is_pro_activated", false)
            }
        }
        
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
    }

    BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(Unit) {
        syncManager.syncUsage { newCount ->
            usageCount = newCount
        }
        syncManager.checkProStatus { isProStatus ->
            isPro = isProStatus
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
                StatusProfessionalCard(
                    onLongClick = { showMasterDialog = true }
                )
            }
            
            item {
                AutomationCard(
                    isEnabled = isAssistantEnabled,
                    isSoundEnabled = isSoundEnabled,
                    onSoundToggle = { 
                        isSoundEnabled = it
                        prefs.edit().putBoolean("sound_enabled", it).apply()
                        if (it) {
                            activity.playActivationSound()
                        }
                    },
                    hasPerms = hasNotifPerm && isIgnoringBattery,
                    onToggle = { newValue -> 
                        if (!newValue) {
                            showDisableDialog = true
                        } else {
                            if (!prefs.getBoolean("disclosure_accepted", false)) {
                                showDisclosureDialog = true
                            } else {
                                isAssistantEnabled = true
                                prefs.edit().putBoolean("service_enabled", true).apply()
                                if (isSoundEnabled) {
                                    activity.playActivationSound()
                                }
                            }
                        }
                    }
                )
            }

            item {
                SectionTitle(
                    title = "PASO 1: AJUSTES DE PERMISOS",
                    icon = Icons.Default.Settings,
                    isExpanded = isSettingsExpanded,
                    onToggle = { userExpandedSettings = !isSettingsExpanded },
                    isComplete = allPermsOk
                )
                AnimatedVisibility(visible = isSettingsExpanded) {
                    SettingsGrid(
                        hasNotif = hasNotifPerm,
                        hasContacts = hasContactsPerm,
                        hasBattery = isIgnoringBattery,
                        hasVerifiedContent = hasVerifiedNotifContent,
                        onRequestContacts = {
                            activity.requestContactsPermission()
                        },
                        onVerifyChange = { 
                            hasVerifiedNotifContent = it
                            prefs.edit().putBoolean("notif_verified", it).apply()
                        }
                    )
                }
            }

            item {
                SectionTitle(
                    title = "PASO 2: ASISTENTE INTELIGENTE", 
                    icon = Icons.Default.SmartToy,
                    isExpanded = isAssistantExpanded,
                    onToggle = { userExpandedAssistant = !isAssistantExpanded },
                    isComplete = isAssistantComplete
                )
                AnimatedVisibility(visible = isAssistantExpanded) {
                    AssistantConfigPanel(
                        keywords = keywords,
                        onKeywordsChange = { keywords = it; prefs.edit().putString("keywords", it).apply() },
                        techNumber = techNumber,
                        onTechNumberChange = { techNumber = it; prefs.edit().putString("tech_number", it).apply() },
                        blacklist = blacklist,
                        onBlacklistChange = { blacklist = it; prefs.edit().putString("blacklist", it).apply() },
                        promoLink = promoLink,
                        onPromoLinkChange = { promoLink = it; prefs.edit().putString("promo_link", it).apply() },
                        repetitionDelay = repetitionDelay,
                        onRepetitionDelayChange = { repetitionDelay = it; prefs.edit().putInt("repetition_delay", it).apply() },
                        vipList = vipList,
                        onVipListChange = { vipList = it; prefs.edit().putString("vip_list", it).apply() },
                        onPickContact = { isForVip ->
                            if (hasContactsPerm) {
                                activity.pickContact { pickedName ->
                                    val current = if (isForVip) vipList.trim() else blacklist.trim()
                                    val newList = if (current.isEmpty()) {
                                        pickedName
                                    } else if (current.endsWith(",")) {
                                        "$current $pickedName"
                                    } else {
                                        "$current, $pickedName"
                                    }
                                    
                                    if (isForVip) {
                                        vipList = newList
                                        prefs.edit().putString("vip_list", newList).apply()
                                    } else {
                                        blacklist = newList
                                        prefs.edit().putString("blacklist", newList).apply()
                                    }
                                }
                            } else {
                                activity.requestContactsPermission()
                            }
                        },
                        msgDay = msgDay,
                        onMsgDayChange = { msgDay = it; prefs.edit().putString("msg_day", it).apply() },
                        msgNight = msgNight,
                        onMsgNightChange = { msgNight = it; prefs.edit().putString("msg_night", it).apply() },
                        msgVip = msgVip,
                        onMsgVipChange = { msgVip = it; prefs.edit().putString("msg_vip", it).apply() },
                        startMS = startHourMonSat,
                        endMS = endHourMonSat,
                        onOpenMS = { showHourPickerMonSat = true },
                        startS = startHourSun,
                        endS = endHourSun,
                        onOpenS = { showHourPickerSun = true }
                    )
                }
            }

            item {
                SectionTitle(
                    title = "TERMINAL DE ACTIVIDAD",
                    icon = Icons.Default.Terminal,
                    isExpanded = showConsole,
                    onToggle = { showConsole = !showConsole },
                    isComplete = logs.isNotBlank()
                )
                AnimatedVisibility(visible = showConsole) {
                    ActivityTerminal(
                        logs = logs,
                        onTestClick = { activity.sendTestNotification(context) }
                    )
                }
            }

            item {
                Text(
                    text = "CLIENTES DETECTADOS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val leadEntries = history.split("\n").filter { it.isNotBlank() }
                if (leadEntries.isEmpty()) {
                    EmptyLeadsPlaceholder()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        leadEntries.reversed().forEachIndexed { index, entry ->
                            LeadPremiumCard(
                                entry = entry,
                                onClick = { selectedLeadEntry = entry },
                                onDelete = { leadIndexToDelete = leadEntries.size - 1 - index }
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AutoResponder Pro v3.0",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp
                    )
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://asurpan.github.io/AutoResponder/es/politica.html"))
                        context.startActivity(intent)
                    }) {
                        Text(
                            "Política de Privacidad y Términos Legales",
                            color = TurquoiseNeon.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }
                    Text(
                        "© 2026 Lorensoft. Todos los derechos reservados.",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    if (leadIndexToDelete != null) {
        AlertDialog(
            onDismissRequest = { leadIndexToDelete = null },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Eliminar Cliente", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que quieres eliminar este registro?", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        val leadEntries = history.split("\n").filter { it.isNotBlank() }.toMutableList()
                        leadEntries.removeAt(leadIndexToDelete!!)
                        val newHistory = leadEntries.joinToString("\n")
                        history = newHistory
                        prefs.edit().putString("leads_history", newHistory).apply()
                        leadIndexToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ELIMINAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { leadIndexToDelete = null }) {
                    Text("CANCELAR", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    if (selectedLeadEntry != null) {
        LeadDetailDialog(
            entry = selectedLeadEntry!!,
            onDismiss = { selectedLeadEntry = null }
        )
    }

    if (showMasterDialog) {
        var authCode by remember { mutableStateOf("") }
        var isAuthenticated by remember { mutableStateOf(false) }
        
        var customCount by remember { mutableStateOf("") }
        var clientIdToGenerate by remember { mutableStateOf("") }
        var generatedCode by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showMasterDialog = false },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    if (!isAuthenticated) "Acceso de Desarrollador" else "Panel de Control Maestro", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold 
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!isAuthenticated) {
                        Text("Ingrese el código de licencia maestra:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        OutlinedTextField(
                            value = authCode,
                            onValueChange = { authCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TurquoiseNeon, unfocusedBorderColor = GlassWhite)
                        )
                    } else {
                        // SECCIÓN 1: GENERADOR DE CÓDIGO (NUEVO)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("GENERADOR DE CÓDIGOS PRO", color = TurquoiseNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(
                                value = clientIdToGenerate,
                                onValueChange = { clientIdToGenerate = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Pegar ID del Cliente aquí", color = Color.White.copy(alpha = 0.2f)) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TurquoiseNeon, unfocusedBorderColor = GlassWhite)
                            )
                            Button(
                                onClick = {
                                    generatedCode = generateActivationCode(clientIdToGenerate)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                            ) {
                                Text("GENERAR CÓDIGO")
                            }
                            if (generatedCode.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MintElectric.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("CÓDIGO: $generatedCode", color = MintElectric, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("GeneratedCode", generatedCode))
                                        Toast.makeText(context, "Código Copiado", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, tint = MintElectric, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(color = GlassWhite)
                        
                        // SECCIÓN 2: AJUSTES DE SISTEMA
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("AJUSTES DE SISTEMA", color = TurquoiseNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Restaurar contador de uso:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            OutlinedTextField(
                                value = customCount,
                                onValueChange = { customCount = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Ej: 0", color = Color.White.copy(alpha = 0.3f)) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TurquoiseNeon, unfocusedBorderColor = GlassWhite)
                            )
                        }

                        HorizontalDivider(color = GlassWhite)

                        // SECCIÓN 3: GESTIÓN DE PLANTILLAS
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("GESTIÓN DE PLANTILLAS", color = TurquoiseNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            
                            Button(
                                onClick = {
                                    prefs.edit()
                                        .putString("tpl_keywords", keywords)
                                        .putString("tpl_tech_number", techNumber)
                                        .putString("tpl_msg_day", msgDay)
                                        .putString("tpl_msg_night", msgNight)
                                        .putString("tpl_msg_vip", msgVip)
                                        .apply()
                                    Toast.makeText(context, "✅ Configuración actual grabada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MintElectric.copy(alpha = 0.2f), contentColor = MintElectric),
                                border = BorderStroke(1.dp, MintElectric.copy(alpha = 0.4f))
                            ) {
                                Text("GUARDAR COMO MI PLANTILLA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val tplK = prefs.getString("tpl_keywords", null)
                                    if (tplK != null) {
                                        // Cargar plantilla guardada por el usuario
                                        val k = tplK
                                        val n = prefs.getString("tpl_tech_number", "") ?: ""
                                        val d = prefs.getString("tpl_msg_day", "") ?: ""
                                        val g = prefs.getString("tpl_msg_night", "") ?: ""
                                        val v = prefs.getString("tpl_msg_vip", "") ?: ""
                                        
                                        keywords = k; techNumber = n; msgDay = d; msgNight = g; msgVip = v
                                        prefs.edit().putString("keywords", k).putString("tech_number", n).putString("msg_day", d).putString("msg_night", g).putString("msg_vip", v).apply()
                                        Toast.makeText(context, "✅ Tu plantilla ha sido cargada", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Cargar textos originales si no hay plantilla
                                        val b = Base64.DEFAULT
                                        val k = String(Base64.decode("cHVsaXIsIHN1ZWxvLCBicmlsbG8sIHB1bGlkb3IsIHRlcnJhem8sIG1hcm1vbCwgZXNjYWxlcmEsIHByZXN1cHVlc3RvLCBjdWFudG8sIHByZWNpbywgY2VyYSwgcmVwYXNhciwgc2Fsb24=", b))
                                        val n = String(Base64.decode("Njc3MzE5Mzkz", b))
                                        val d = String(Base64.decode("wqFCdWVuYXMhIFNveSBKb3NlIGVsIHB1bGlkb3IuIEFob3JhIG1pc21vIGVzdG95IGxpYWRvIGNvbiBsYSBtw6FxdWluYSB5IG5vIHB1ZWRvIGF0ZW5kZXJ0ZSBiaWVuIHBvciBhcXXDrS4gRXNjcsOtYmVtZSBwb3JmYSBhIG1pIG7Dum1lcm8gcGVyc29uYWwge251bWVyb30geSBkaW1lIHF1ZSBxdWllcmVzIGVsIERFU0NVRU5UTyBERUwgTUVTLiBBc8OtIHRlIHBhc28gdW4gcHJlc3VwdWVzdG8gYnVlbm8geSBlY29uw7NtaWNvIHLDoXBpZG8uIMKhRW4gY3VhbnRvIHBhcmUgdW4gc2VndW5kbyB0ZSBlc2NyaWJvIQ==", b))
                                        val g = String(Base64.decode("wqFCdWVuYXMhIFNveSBKb3NlIGVsIHB1bGlkb3IuIFlhIGhlIHRlcm1pbmFkbyBwb3IgaG95IHkgZXN0b3kgZGVzY2Fuc2FuZG8uIFBhcmEgZGVqYXJ0ZSBlbCBwcmVzdXB1ZXN0byBsaXN0byBwYXJhIG1hw7FhbmEgYSBwcmltZXJhIGhvcmEsIGVzY3LDrWJlbWUgcG9yZmEgYSBtaSBuw7ptZXJvIHBlcnNvbmFsIHtudW1lcm99IG1lbmNpb25hbmRvIGVsIERFU0NVRU5UTyBERUwgTUVTLiDCoU1hw7FhbmEgYSBwcmltZXJhIGhvcmEgdGUgcGFzbyBlbCBwcmVjaW8gY29uIGxhIG9mZXJ0YSBhcGxpY2FkYSBzaW4gZmFsdGEhIMKhR3JhY2lhcyE=", b))
                                        
                                        keywords = k; techNumber = n; msgDay = d; msgNight = g
                                        prefs.edit().putString("keywords", k).putString("tech_number", n).putString("msg_day", d).putString("msg_night", g).apply()
                                        Toast.makeText(context, "Configuración del Pulidor Restaurada", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                                border = BorderStroke(1.dp, GlassWhite)
                            ) {
                                Text("CARGAR MI PLANTILLA", fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isAuthenticated) {
                    Button(
                        onClick = { if (authCode == "121212") isAuthenticated = true else Toast.makeText(context, "Código Incorrecto", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                    ) { Text("ENTRAR") }
                } else {
                    Button(
                        onClick = {
                            customCount.toIntOrNull()?.let {
                                prefs.edit().putInt("usage_count", it).apply()
                                usageCount = it
                            }
                            showMasterDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                    ) { Text("CERRAR PANEL") }
                }
            }
        )
    }

    if (showHourPickerMonSat) {
        AlertDialog(
            onDismissRequest = { showHourPickerMonSat = false },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Horario Lun a Sáb", color = Color.White) },
            text = {
                Column {
                    Text("Hora de Inicio: ${startHourMonSat.toString().padStart(2, '0')}:00", color = TurquoiseNeon)
                    Slider(
                        value = startHourMonSat.toFloat(),
                        onValueChange = { startHourMonSat = it.toInt() },
                        valueRange = 0f..23f,
                        colors = SliderDefaults.colors(thumbColor = TurquoiseNeon, activeTrackColor = TurquoiseNeon)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Hora de Fin: ${endHourMonSat.toString().padStart(2, '0')}:00", color = TurquoiseNeon)
                    Slider(
                        value = endHourMonSat.toFloat(),
                        onValueChange = { endHourMonSat = it.toInt() },
                        valueRange = 0f..23f,
                        colors = SliderDefaults.colors(thumbColor = TurquoiseNeon, activeTrackColor = TurquoiseNeon)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putInt("start_hour_mon_sat", startHourMonSat).putInt("end_hour_mon_sat", endHourMonSat).apply()
                        showHourPickerMonSat = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                ) { Text("GUARDAR") }
            }
        )
    }

    if (showHourPickerSun) {
        AlertDialog(
            onDismissRequest = { showHourPickerSun = false },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Horario Domingo", color = Color.White) },
            text = {
                Column {
                    Text("Hora de Inicio: ${startHourSun.toString().padStart(2, '0')}:00", color = TurquoiseNeon)
                    Slider(
                        value = startHourSun.toFloat(),
                        onValueChange = { startHourSun = it.toInt() },
                        valueRange = 0f..23f,
                        colors = SliderDefaults.colors(thumbColor = TurquoiseNeon, activeTrackColor = TurquoiseNeon)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Hora de Fin: ${endHourSun.toString().padStart(2, '0')}:00", color = TurquoiseNeon)
                    Slider(
                        value = endHourSun.toFloat(),
                        onValueChange = { endHourSun = it.toInt() },
                        valueRange = 0f..23f,
                        colors = SliderDefaults.colors(thumbColor = TurquoiseNeon, activeTrackColor = TurquoiseNeon)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putInt("start_hour_sun", startHourSun).putInt("end_hour_sun", endHourSun).apply()
                        showHourPickerSun = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                ) { Text("GUARDAR") }
            }
        )
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = Color(0xFFFF4B4B))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Detener Asistente", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Si detienes el asistente, dejará de responder a tus clientes automáticamente.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isAssistantEnabled = false
                        prefs.edit().putBoolean("service_enabled", false).apply()
                        showDisableDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("DETENER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("CANCELAR", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    if (showExitDialog) {
        val isReady = allPermsOk && isAssistantComplete && isAssistantEnabled
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = OceanSurface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isReady) MintElectric else Color(0xFFFF4B4B)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("¿Salir de la App?", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        if (isReady) "¡Todo está configurado correctamente! El asistente seguirá trabajando en segundo plano."
                        else "Atención: Aún faltan pasos por completar. El asistente podría no funcionar correctamente si sales ahora.",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (!isReady) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!allPermsOk) Text("• Faltan permisos (Paso 1)", color = Color(0xFFFF4B4B), fontSize = 12.sp)
                        if (!isAssistantComplete) Text("• Configuración incompleta (Paso 2)", color = Color(0xFFFF4B4B), fontSize = 12.sp)
                        if (!isAssistantEnabled) Text("• Asistente desactivado", color = Color(0xFFFF4B4B), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { activity.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isReady) TurquoiseNeon else Color(0xFFFF4B4B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SALIR", fontWeight = FontWeight.Bold, color = if (isReady) OceanDeep else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("CONTINUAR CONFIGURANDO", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    if (showDisclosureDialog) {
        ProminentDisclosureDialog(
            onDismiss = { showDisclosureDialog = false },
            onAccept = {
                prefs.edit().putBoolean("disclosure_accepted", true).apply()
                showDisclosureDialog = false
            }
        )
    }
}

@Composable
fun ProminentDisclosureDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OceanSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, null, tint = TurquoiseNeon)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Aviso de Privacidad", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Para funcionar como asistente comercial, esta aplicación necesita acceder a tus notificaciones de WhatsApp.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    "• Uso de Datos: Solo leemos el texto de los mensajes entrantes para detectar tus palabras clave y enviar la respuesta que hayas configurado.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    "• Privacidad Total: Tus conversaciones nunca salen de este dispositivo. No recopilamos, almacenamos ni compartimos ningún dato personal o mensaje con servidores externos.",
                    color = MintElectric,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
            ) {
                Text("ACEPTAR Y CONTINUAR")
            }
        }
    )
}

@Composable
fun StatusProfessionalCard(onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OceanSurface),
        border = BorderStroke(1.dp, GoldPro.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(20.dp).align(Alignment.CenterStart)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Verified, 
                        null, 
                        tint = GoldPro,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ASISTENTE PROFESIONAL",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Licencia Premium Activa • Privacidad Garantizada",
                    color = MintElectric.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun LeadPremiumCard(entry: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val parts = entry.split(" | ")
    val name = parts.getOrNull(0) ?: "Desconocido"
    val time = parts.getOrNull(2) ?: ""

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OceanSurface),
        border = BorderStroke(1.dp, GlassWhite)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(TurquoiseNeon.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = TurquoiseNeon, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(time, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun LeadDetailDialog(entry: String, onDismiss: () -> Unit) {
    val parts = entry.split(" | ")
    val name = parts.getOrNull(0) ?: ""
    val phone = parts.getOrNull(1) ?: ""
    val time = parts.getOrNull(2) ?: ""
    val message = parts.getOrNull(3) ?: ""
    val response = parts.getOrNull(4) ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OceanDeep,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContactPage, null, tint = TurquoiseNeon)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Detalle del Cliente", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailItem("NOMBRE", name)
                DetailItem("TELÉFONO", phone)
                DetailItem("FECHA/HORA", time)
                DetailItem("MENSAJE RECIBIDO", message)
                DetailItem("RESPUESTA ENVIADA", response)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CERRAR")
            }
        }
    )
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, color = TurquoiseNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun AutomationCard(
    isEnabled: Boolean,
    isSoundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    hasPerms: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val active = isEnabled && hasPerms
    
    Card(
        modifier = Modifier.fillMaxWidth().shadow(if (active) 20.dp else 0.dp, RoundedCornerShape(24.dp), spotColor = TurquoiseNeon),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) OceanSurface else OceanSurface.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, if (active) TurquoiseNeon.copy(alpha = 0.4f) else GlassWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (active) Icons.Default.AutoMode else Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = if (active) TurquoiseNeon else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (active) "ASISTENTE ACTIVO" else "ASISTENTE EN PAUSA",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        color = if (active) TurquoiseNeon else Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val statusText = when {
                        !hasPerms -> "Faltan permisos (Paso 1)"
                        active -> "Respondiendo a clientes..."
                        else -> "Activa el interruptor para empezar"
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TurquoiseNeon,
                        checkedTrackColor = TurquoiseNeon.copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = GlassWhite)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onSoundToggle(!isSoundEnabled) }
            ) {
                Icon(
                    imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = if (isSoundEnabled) MintElectric else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sonido \"Bep Bep\" al detectar cliente",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isSoundEnabled,
                    onCheckedChange = onSoundToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MintElectric,
                        checkedTrackColor = MintElectric.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (isExhausted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPayClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
                ) {
                    Icon(Icons.Default.Stars, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VER OPCIONES DE PAGO", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsGrid(
    hasNotif: Boolean, 
    hasContacts: Boolean, 
    hasBattery: Boolean, 
    hasVerifiedContent: Boolean,
    onRequestContacts: () -> Unit,
    onVerifyChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        
        // Guía de Notificaciones
        NotificationInstructionCard(hasVerifiedContent, onVerifyChange)
        
        Spacer(modifier = Modifier.height(12.dp))

        SettingsItem("1. Permisos de Notificación", hasNotif) { 
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(context, "Busca 'AutoResponder Pro' y actívalo. Si no te deja, lee la nota de arriba.", Toast.LENGTH_LONG).show()
        }
        
        // Guía de Agenda
        Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            Text("Necesario para identificar el número de tus clientes guardados.", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
        SettingsItem("2. Permisos de Agenda", hasContacts) { onRequestContacts() }
        
        // Guía de Batería
        Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)) {
            Text("GUÍA XIAOMI: 1. Uso de batería -> 2. Pulsa en 'En segundo plano' -> 3. Elige 'SIN RESTRICCIONES'.", color = MintElectric.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        SettingsItem("3. Optimización de Batería", hasBattery) { 
            Toast.makeText(context, "⚠️ Pulsa 'Uso de batería', entra en 'Segundo plano' y elige 'SIN RESTRICCIONES'", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.fromParts("package", context.packageName, null) }) 
        }
    }
}

@Composable
fun NotificationInstructionCard(isVerified: Boolean, onVerifyChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TurquoiseNeon.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, TurquoiseNeon.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = TurquoiseNeon, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("IMPORTANTE: Cómo funciona el Asistente", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "El asistente responde leyendo el texto de tus NOTIFICACIONES. Por seguridad, nunca entra en tus chats privados. Para que funcione, sigue estos pasos:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            InstructionStep("1. Pulsa abajo en 'Permisos de Notificación'.")
            InstructionStep("2. Busca 'AutoResponder Pro' y actívalo.")
            
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4B4B).copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "SI EL INTERRUPTOR NO SE DEJA ACTIVAR (XIAOMI):",
                        color = Color(0xFFFF4B4B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        "Ve a Ajustes del móvil > Aplicaciones > Gestionar Aplicaciones > AutoResponder Pro > Pulsa los 3 PUNTOS (arriba derecha) > 'Permitir Ajustes Restringidos'.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TurquoiseNeon.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable { onVerifyChange(!isVerified) }
                    .padding(8.dp)
            ) {
                Checkbox(
                    checked = isVerified,
                    onCheckedChange = onVerifyChange,
                    colors = CheckboxDefaults.colors(checkedColor = TurquoiseNeon, uncheckedColor = Color.White.copy(alpha = 0.4f))
                )
                Text(
                    "He verificado que mis notificaciones muestran el texto del mensaje",
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun InstructionStep(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•", color = TurquoiseNeon, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
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
fun SectionTitle(
    title: String, 
    icon: ImageVector, 
    isExpanded: Boolean, 
    onToggle: (() -> Unit)?,
    isComplete: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sectionPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isComplete) {
                    Modifier
                        .background(Color(0xFFFF4B4B).copy(alpha = 0.05f * pulseAlpha), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.2f * pulseAlpha), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .clickable(enabled = onToggle != null) { onToggle?.invoke() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp).background(if (isComplete) TurquoiseNeon.copy(alpha = 0.1f) else Color(0xFFFF4B4B).copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isComplete) TurquoiseNeon else Color(0xFFFF4B4B), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isComplete) Color.White else Color(0xFFFF4B4B).copy(alpha = 0.8f + (0.2f * pulseAlpha)),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (isComplete) {
            Icon(Icons.Default.CheckCircle, null, tint = MintElectric, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (onToggle != null) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isExpanded) TurquoiseNeon.copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp).padding(4.dp)
            )
        }
    }
}

@Composable
fun ActivityTerminal(logs: String, onTestClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Esta pantalla te permite ver el 'centro de procesamiento' del asistente trabajando en tiempo real. Aquí verás cuando detecta un mensaje, comprueba tus palabras clave y prepara la respuesta. Es la mejor forma de confirmar que todo está funcionando perfectamente.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GlassWhite)
        ) {
            Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = logs.ifBlank { "Esperando actividad..." },
                    color = MintElectric,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
        
        Button(
            onClick = onTestClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TurquoiseNeon, contentColor = OceanDeep)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("PROBAR SI EL ASISTENTE ME LEE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyLeadsPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Inbox, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No hay clientes detectados aún", color = Color.White.copy(alpha = 0.2f), fontSize = 13.sp)
    }
}

@Composable
fun AssistantConfigPanel(
    keywords: String, onKeywordsChange: (String) -> Unit,
    techNumber: String, onTechNumberChange: (String) -> Unit,
    blacklist: String, onBlacklistChange: (String) -> Unit,
    promoLink: String, onPromoLinkChange: (String) -> Unit,
    repetitionDelay: Int, onRepetitionDelayChange: (Int) -> Unit,
    vipList: String, onVipListChange: (String) -> Unit,
    onPickContact: (Boolean) -> Unit,
    msgDay: String, onMsgDayChange: (String) -> Unit,
    msgNight: String, onMsgNightChange: (String) -> Unit,
    msgVip: String, onMsgVipChange: (String) -> Unit,
    startMS: Int, endMS: Int, onOpenMS: () -> Unit,
    startS: Int, endS: Int, onOpenS: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConfigTextField(
            label = "PALABRAS CLAVE (separadas por comas)", 
            value = keywords, 
            onValueChange = onKeywordsChange, 
            isMultiline = true,
            description = "Escribe las palabras que detecta el asistente para capturar al cliente automáticamente."
        )
        ConfigTextField(
            label = "TU NÚMERO DE TELÉFONO", 
            value = techNumber, 
            onValueChange = onTechNumberChange,
            description = "Pon el número donde quieres que contacten (el tuyo, el de un empleado o empresa)."
        )
        
        ConfigTextField(
            label = "LISTA NEGRA (Nombres o números a ignorar)", 
            value = blacklist, 
            onValueChange = onBlacklistChange,
            description = "Contactos que el asistente debe ignorar siempre. Separa por comas.",
            trailingIcon = {
                IconButton(onClick = { onPickContact(false) }) {
                    Icon(Icons.Default.PersonAdd, null, tint = TurquoiseNeon)
                }
            }
        )

        ConfigTextField(
            label = "ENLACE / CATÁLOGO (Opcional)", 
            value = promoLink, 
            onValueChange = onPromoLinkChange,
            description = "Se pegará al final del mensaje. Úsalo para tu catálogo de WhatsApp o web."
        )

        // --- NUEVA SECCIÓN VIP ---
        var isVipExpanded by rememberSaveable { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GoldPro.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .border(1.dp, GoldPro.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .clickable { isVipExpanded = !isVipExpanded }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stars, null, tint = GoldPro, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "CONTACTOS VIP (RESPUESTA TOTAL)", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isVipExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = GoldPro.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(visible = isVipExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "El asistente responderá a estas personas aunque NO usen palabras clave, pero solo cuando tengas la PANTALLA APAGADA. Úsalo para familia o socios.", 
                        color = Color.White.copy(alpha = 0.6f), 
                        fontSize = 11.sp, 
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ConfigTextField(
                        label = "LISTA VIP", 
                        value = vipList, 
                        onValueChange = onVipListChange,
                        trailingIcon = {
                            IconButton(onClick = { onPickContact(true) }) {
                                Icon(Icons.Default.Stars, null, tint = GoldPro)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ConfigTextField("MENSAJE EXCLUSIVO PARA VIP", msgVip, onMsgVipChange, isMultiline = true)
                }
            }
        }

        AnimatedVisibility(visible = !isVipExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("HORARIO COMERCIAL", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    Text("Define tu horario comercial para que el asistente sepa cuándo enviar el mensaje de DÍA o el de NOCHE.", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            onClick = onOpenMS,
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = OceanSurface),
                            border = BorderStroke(1.dp, GlassWhite)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("LUN - SÁB", color = TurquoiseNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${startMS.toString().padStart(2, '0')}:00 - ${endMS.toString().padStart(2, '0')}:00", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Card(
                            onClick = onOpenS,
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = OceanSurface),
                            border = BorderStroke(1.dp, GlassWhite)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("DOMINGOS", color = TurquoiseNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${startS.toString().padStart(2, '0')}:00 - ${endS.toString().padStart(2, '0')}:00", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                ConfigTextField("MENSAJE DE DÍA", msgDay, onMsgDayChange, isMultiline = true)
                ConfigTextField("MENSAJE DE NOCHE", msgNight, onMsgNightChange, isMultiline = true)

                // --- SECCIÓN FRECUENCIA (MINIMIZADA) ---
                var isFreqExpanded by rememberSaveable { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TurquoiseNeon.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .border(1.dp, TurquoiseNeon.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .clickable { isFreqExpanded = !isFreqExpanded }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Timer, null, tint = TurquoiseNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "FRECUENCIA DE RESPUESTA", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isFreqExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TurquoiseNeon.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    AnimatedVisibility(visible = isFreqExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "¿Cuánto tiempo debe esperar el asistente para volver a contestar a la misma persona?", 
                                color = Color.White.copy(alpha = 0.6f), 
                                fontSize = 11.sp, 
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val options = listOf(0 to "Siempre responder", 1 to "Esperar 1 hora", 12 to "Esperar 12 horas", 24 to "Esperar 24 horas")
                            
                            options.forEach { (value, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { onRepetitionDelayChange(value) }.padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = repetitionDelay == value,
                                        onClick = { onRepetitionDelayChange(value) },
                                        colors = RadioButtonDefaults.colors(selectedColor = TurquoiseNeon, unselectedColor = Color.White.copy(alpha = 0.3f))
                                    )
                                    Text(label, color = if (repetitionDelay == value) TurquoiseNeon else Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                            
                            if (repetitionDelay == 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4B4B).copy(alpha = 0.1f)), border = BorderStroke(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.2f))) {
                                    Text("Ojo: Al contestar siempre, podrías ser muy pesado si el cliente escribe mucho.", modifier = Modifier.padding(8.dp), color = Color(0xFFFF4B4B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(
    label: String, 
    value: String, 
    onValueChange: (String) -> Unit, 
    isMultiline: Boolean = false, 
    description: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        if (description != null) {
            Text(description, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TurquoiseNeon,
                unfocusedBorderColor = GlassWhite,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            minLines = if (isMultiline) 3 else 1,
            maxLines = if (isMultiline) 5 else 1,
            trailingIcon = trailingIcon
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    // Método 1: AndroidX (Recomendado)
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    if (enabledListeners.contains(context.packageName)) return true

    // Método 2: Lectura directa y desglose de ComponentName (Fallo común en Xiaomi/MIUI)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == context.packageName) {
                return true
            }
        }
    }
    return false
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
