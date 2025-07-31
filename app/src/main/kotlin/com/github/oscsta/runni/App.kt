package com.github.oscsta.runni

import android.content.Intent
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import com.github.oscsta.runni.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                
            }
        }
    }

    fun startLocationActivity(activeId: Int) {
        val serviceIntent = Intent(this, RunniLocationService::class.java).also { it.putExtra("ACTIVE_ID", activeId) }
        startForegroundService(serviceIntent)
    }
}