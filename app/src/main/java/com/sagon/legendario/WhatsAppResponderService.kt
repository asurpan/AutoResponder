package com.sagon.legendario

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WhatsAppResponderService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return
        val packageName = sbn.packageName
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b" && packageName != "com.sagon.legendario") return

        val prefs = getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE)
        
        // Comprobar Licencia Pro
        val isPro = prefs.getBoolean("is_pro_activated", false)
        val usageCount = prefs.getInt("usage_count", 0)
        
        if (!isPro && usageCount >= 30) {
            addLog("⚠️ PRUEBA AGOTADA ($usageCount/30). Activa la versión PRO.")
            return
        }

        // Resetear la consola cada vez que llega una nueva notificación válida
        prefs.edit().putString("activity_logs", "").apply()

        val isEnabled = prefs.getBoolean("service_enabled", false)
        if (!isEnabled) {
            addLog("Servicio desactivado. Ignorando mensaje.")
            return
        }

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Desconocido"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        addLog("Leído de $packageName: $title")
        prefs.edit().putString("debug_last_notif", "De: $title\nTexto: $text\nPaquete: $packageName").apply()

        if (text.isEmpty()) {
            addLog("⚠️ Notificación sin texto visible.")
            return
        }

        // Leer palabras clave
        val defaultKeywords = "pulir, suelo, brillo, pulidor, terrazo, marmol, escalera, presupuesto, cuanto, precio, cera, repasar, salon"
        val keywordsString = prefs.getString("keywords", defaultKeywords) ?: defaultKeywords
        val keywords = keywordsString.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val shouldRespond = keywords.any { text.lowercase().contains(it) }

        if (shouldRespond) {
            addLog("✅ Palabra clave detectada en: \"$text\"")
            
            // Sistema Anti-Repetición (desactivado para pruebas si es de la propia app)
            if (packageName != "com.sagon.legendario") {
                val lastResponseTime = prefs.getLong("last_resp_$title", 0L)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastResponseTime < 24 * 60 * 60 * 1000L) {
                    addLog("⏳ Ya respondido a $title hoy. Callando.")
                    return
                }
            }

            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val startHour = prefs.getInt("start_hour", 8)
            val endHour = prefs.getInt("end_hour", 22)
            val isWorkingHours = hour in startHour until endHour

            val technicianNumber = prefs.getString("tech_number", "34677319393") ?: "34677319393"
            
            val defaultDayMsg = "¡Buenas! Soy Jose el pulidor. Ahora mismo estoy liado con la máquina y no puedo atenderte bien por aquí. Escríbeme porfa a mi número personal {numero} y así te paso un presupuesto bueno y económico rápido. Dime por allí los metros, la zona donde estás y mándame alguna foto del suelo si puedes. ¡En cuanto pare un segundo te escribo!"
            val defaultNightMsg = "¡Buenas! Soy Jose el pulidor. Ya he terminado por hoy y estoy descansando. Para dejarte el presupuesto listo para mañana a primera hora, escríbeme porfa a mi número personal {numero}. Déjame allí los metros, la zona y mándame alguna foto del suelo. ¡Mañana a primera hora te paso el precio sin falta! ¡Gracias!"

            val replyTemplate = if (isWorkingHours) {
                prefs.getString("msg_day", defaultDayMsg) ?: defaultDayMsg
            } else {
                prefs.getString("msg_night", defaultNightMsg) ?: defaultNightMsg
            }

            val replyMessage = replyTemplate.replace("{numero}", technicianNumber)

            serviceScope.launch {
                addLog("⏰ Preparando respuesta en 20 segundos...")
                delay(20000) // Retraso fijo de 20 segundos
                if (sendReply(notification, replyMessage)) {
                    addLog("🚀 ¡Respuesta enviada con éxito!")
                    addLog("✉️ Mensaje: $replyMessage") 
                    
                    // Actualizar contador y tiempo de respuesta
                    val currentCount = prefs.getInt("usage_count", 0)
                    prefs.edit()
                        .putLong("last_resp_$title", System.currentTimeMillis())
                        .putInt("usage_count", currentCount + 1)
                        .apply()
                    
                    val number = getPhoneNumber(title)
                    saveLead(title, number)
                } else {
                    addLog("❌ ERROR: No se encontró el botón de responder.")
                }
            }
        }
    }

    private fun getPhoneNumber(contactName: String): String {
        // Excepción para la prueba del sistema para que se vea un número realista
        if (contactName == "Prueba del Sistema") {
            return "666 555 444"
        }

        // Si el título ya es un número (contiene solo dígitos, espacios o +)
        if (contactName.replace(" ", "").matches(Regex("(\\+)?\\d+"))) {
            return contactName
        }
        
        try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, 
                android.net.Uri.encode(contactName)
            )
            val cursor = contentResolver.query(
                uri, 
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), 
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e("PuliSevilla", "Error al buscar contacto: ${e.message}")
        }
        
        return "Número no encontrado"
    }

    private fun addLog(message: String) {
        val prefs = getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("activity_logs", "") ?: ""
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "[$timestamp] $message"
        
        val logList = if (logs.isEmpty()) mutableListOf() else logs.split("|").toMutableList()
        logList.add(0, newLog)
        if (logList.size > 15) logList.removeAt(logList.size - 1)
        
        prefs.edit().putString("activity_logs", logList.joinToString("|")).apply()
        Log.d("PuliSevilla", message)
    }

    private fun saveLead(name: String, number: String) {
        val prefs = getSharedPreferences("PuliSevillaPrefs", Context.MODE_PRIVATE)
        val currentHistory = prefs.getString("leads_history", "") ?: ""
        val timestamp = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        
        // Formato: Fecha#Nombre#Número#Estado
        val newEntry = "$timestamp#$name#$number#0"
        
        val historyList = if (currentHistory.isEmpty()) mutableListOf() else currentHistory.split("|").toMutableList()
        historyList.add(0, newEntry)
        if (historyList.size > 20) {
            historyList.removeAt(historyList.size - 1)
        }
        
        prefs.edit().putString("leads_history", historyList.joinToString("|")).apply()
    }

    private fun sendReply(notification: Notification, message: String): Boolean {
        val wearableExtender = Notification.WearableExtender(notification)
        val actions = wearableExtender.actions
        
        // Buscamos la acción que permite respuesta rápida (Direct Reply)
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null) {
                    for (remoteInput in remoteInputs) {
                        val results = Bundle()
                        results.putCharSequence(remoteInput.resultKey, message)
                        
                        val intent = Intent()
                        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
                        
                        try {
                            action.actionIntent.send(this, 0, intent)
                            Log.d("PuliSevilla", "Respuesta enviada con éxito.")
                            return true
                        } catch (e: Exception) {
                            Log.e("PuliSevilla", "Error al enviar la respuesta: ${e.message}")
                        }
                    }
                }
            }
        }
        
        // Si no se encuentra en WearableExtender, intentamos en las acciones normales
        val normalActions = notification.actions
        if (normalActions != null) {
            for (action in normalActions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null) {
                    for (remoteInput in remoteInputs) {
                        val results = Bundle()
                        results.putCharSequence(remoteInput.resultKey, message)
                        
                        val intent = Intent()
                        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
                        
                        try {
                            action.actionIntent.send(this, 0, intent)
                            Log.d("PuliSevilla", "Respuesta enviada con éxito (acción normal).")
                            return true
                        } catch (e: Exception) {
                            Log.e("PuliSevilla", "Error al enviar la respuesta: ${e.message}")
                        }
                    }
                }
            }
        }
        return false
    }
}
