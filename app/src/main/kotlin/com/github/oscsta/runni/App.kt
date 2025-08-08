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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.getValue


enum class TrackingState {
    ACTIVE, INACTIVE, DETAILED_ITEM_VIEW
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    @Suppress("unused")
    private var activeId by mutableLongStateOf(0)
    var status by mutableStateOf(TrackingState.INACTIVE)
    private val db by lazy {
        TrackedActivityDatabase.getDatabase(application)
    }

    val allTrackedActivities = db.trackedActivityDao().getAllItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(), emptyList()
    )

    fun insertNewTrackedActivity(entity: TrackedActivityEntity, postInsert: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = db.trackedActivityDao()
            val id = dao.insert(entity)
            postInsert(id)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> startTracking()
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                ) -> shouldShowInsufficientPermissionsDialog = true

                else -> shouldShowInsufficientPermissionsDialog = true
            }
        }
    private var shouldShowInsufficientPermissionsDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                when (vm.status) {
                    TrackingState.ACTIVE -> ActiveView()
                    TrackingState.INACTIVE -> DefaultView()
                    TrackingState.DETAILED_ITEM_VIEW -> DetailedItemView()
                }

                if (shouldShowInsufficientPermissionsDialog) {
                    AlertDialog(
                        onDismissRequest = { shouldShowInsufficientPermissionsDialog = false },
                        title = { Text("Precise location was denied") },
                        text = { Text("Accurate tracking is not possible without precise location permissions. Allow access to precise location in order to start tracking.") },
                        confirmButton = {
                            Button(onClick = {
                                shouldShowInsufficientPermissionsDialog = false
                            }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }

    private fun startForegroundLocationService(activeId: Long) {
        val serviceIntent = Intent(this, PeriodicLocationService::class.java).apply {
            putExtra(
                "ACTIVE_ID",
                activeId
            )
        }
        startForegroundService(serviceIntent)
    }

    private fun startTracking() {
        vm.status = TrackingState.ACTIVE
        val entity = TrackedActivityEntity(timestamp = System.currentTimeMillis())
        vm.insertNewTrackedActivity(entity) { id ->
            startForegroundLocationService(id)
        }
    }

    private fun onStartButtonClick() {
        val fineLocationPerm =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        @Suppress("unused", "UnusedVariable")
        val coarseLocationPerm =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocationPerm == PM.PERMISSION_GRANTED) {
            startTracking()
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            TODO()
        }
        requestFineLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@Composable
@Preview(showBackground = false)
fun MainTest() {
    AppTheme(darkTheme = true, dynamicColor = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                Modifier
                    .weight(3f)
                    .fillMaxSize()
            ) { Box(modifier = Modifier.fillMaxSize(), Alignment.Center) { Text("Hi") } }
//            Spacer(Modifier.height(16.dp))
//            Card(Modifier.weight(1f).fillMaxSize().clickable(true, onClick = {})) { Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text("Clickable") } }
            Spacer(Modifier.height(16.dp))
            Button(
                {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            ) { }
        }
    }
}

@Composable
fun ActiveView() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier
                .weight(3f)
                .fillMaxSize()) { }
            Spacer(Modifier.height(16.dp))
            Button(
                {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            ) { }
        }
    }
}

@Composable
fun DefaultView(): Unit = TODO()

@Composable
fun DetailedItemView(): Unit = TODO()