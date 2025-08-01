package com.github.oscsta.runni

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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

@Composable
@Preview(showSystemUi = true)
fun MainTest() {
    AppTheme {
        Row {
            Card { Text("Hello There") }
        }
    }
}