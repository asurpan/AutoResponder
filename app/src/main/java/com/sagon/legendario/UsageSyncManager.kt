package com.sagon.legendario

import android.content.Context
import android.provider.Settings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UsageSyncManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance("https://autoresponder-pro-f7cec-default-rtdb.europe-west1.firebasedatabase.app/")
    private val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    private val deviceRef = database.getReference("devices").child(androidId)
    private val prefs = context.getSharedPreferences("AutoResponderPrefs", Context.MODE_PRIVATE)

    fun syncUsage(onComplete: (Int) -> Unit) {
        deviceRef.child("usage_count").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudCount = snapshot.getValue(Int::class.java) ?: 0
                val localCount = prefs.getInt("usage_count", 0)

                // Nos quedamos con el valor más alto (más restrictivo)
                val finalCount = if (cloudCount > localCount) cloudCount else localCount
                
                if (finalCount != localCount) {
                    prefs.edit().putInt("usage_count", finalCount).apply()
                }
                
                // Actualizamos la nube si el local era mayor
                if (localCount > cloudCount) {
                    deviceRef.child("usage_count").setValue(localCount)
                }
                
                onComplete(finalCount)
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(prefs.getInt("usage_count", 0))
            }
        })
    }

    fun updateUsage(newCount: Int) {
        deviceRef.child("usage_count").setValue(newCount)
    }

    fun setProStatus(isPro: Boolean) {
        deviceRef.child("is_pro").setValue(isPro)
        if (isPro) {
            prefs.edit().putBoolean("is_pro_activated", true).apply()
        }
    }

    fun checkProStatus(onComplete: (Boolean) -> Unit) {
        deviceRef.child("is_pro").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isPro = snapshot.getValue(Boolean::class.java) ?: false
                if (isPro) {
                    prefs.edit().putBoolean("is_pro_activated", true).apply()
                }
                onComplete(isPro)
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(prefs.getBoolean("is_pro_activated", false))
            }
        })
    }
}
