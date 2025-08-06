package com.github.oscsta.runni

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager as PM
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.oscsta.runni.ui.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlin.getValue


enum class TrackingState {
    ACTIVE, INACTIVE, DETAILED_ITEM_VIEW
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var activeId by mutableStateOf<Int?>(null)
    var status by mutableStateOf<TrackingState>(TrackingState.INACTIVE)
    private val db by lazy {
        TrackedActivityDatabase.getDatabase(application)
    }

    val allTrackedActivities = db.trackedActivityDao().getAllItems().stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(), emptyList())
}

class MainActivity : ComponentActivity() {
    val requestFineLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionIsGranted ->

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm: MainViewModel by viewModels()

        setContent {
            AppTheme {
                when(vm.status) {
                    TrackingState.ACTIVE -> ActiveView()
                    TrackingState.INACTIVE -> DefaultView()
                    TrackingState.DETAILED_ITEM_VIEW -> DetailedItemView()
                }
            }
        }
    }

    private fun startForegroundLocationService(activeId: Int) {
        val serviceIntent = Intent(this, RunniLocationService::class.java).apply {
            putExtra(
                "ACTIVE_ID",
                activeId
            )
        }
        startForegroundService(serviceIntent)
    }

    private fun onStartButtonClick() {
        val fineLocationPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineLocationPerm == PM.PERMISSION_GRANTED) {
            startForegroundLocationService(TODO())
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
        }
        requestFineLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

@Composable
@Preview(showBackground = false)
fun MainTest() {
    AppTheme(darkTheme = true, dynamicColor = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                Modifier
                    .weight(3f)
                    .fillMaxWidth()
            ) { Box(modifier = Modifier.fillMaxSize(), Alignment.Center) { Text("Hi") } }
//            Spacer(Modifier.height(16.dp))
//            Card(Modifier.weight(1f).fillMaxSize().clickable(true, onClick = {})) { Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text("Clickable") } }
            Spacer(Modifier.height(16.dp))
            Button({}, modifier = Modifier.weight(1f).fillMaxSize(), shape = RoundedCornerShape(8.dp)) { }
        }
    }
}