package com.github.oscsta.runni

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.oscsta.runni.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import android.content.pm.PackageManager as PM


enum class TrackingState {
    ACTIVE, INACTIVE, DETAILED_ITEM_VIEW
}

class MonoViewModel(application: Application) : AndroidViewModel(application) {
    var activeId by mutableLongStateOf(0)
    var status by mutableStateOf(TrackingState.INACTIVE)
    private val db by lazy {
        TrackedActivityDatabase.getDatabase(application)
    }
    val allTrackedActivities = db.trackedActivityDao().getAllItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(), emptyList()
    )
    val startTime by lazy {
        db.trackedActivityDao().getMostRecentStartTime()
    }

    fun insertNewTrackedActivity(entity: TrackedActivityEntity, postInsert: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = db.trackedActivityDao()
            val id = dao.insert(entity)
            postInsert(id)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MonoViewModel by viewModels()
    private var shouldShowInsufficientPermissionsDialog by mutableStateOf(false)
    private var shouldShowFineLocationRationale by mutableStateOf(false)
    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    false
                ) -> startTracking()

                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                ) -> shouldShowInsufficientPermissionsDialog = true

                else -> shouldShowInsufficientPermissionsDialog = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Scaffold { scaffoldPadding ->
                    Box(modifier = Modifier.padding(scaffoldPadding)) {
                        when (vm.status) {
                            TrackingState.INACTIVE -> DefaultView(::onStartButtonClick)
                            TrackingState.ACTIVE -> ActiveView(::onStopButtonClick)
                            TrackingState.DETAILED_ITEM_VIEW -> DetailedItemView()
                        }
                    }
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
                if (shouldShowFineLocationRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            shouldShowFineLocationRationale = false; launchFinePermissionLauncher()
                        },
                        title = { Text("Precise location is required") },
                        text = { Text("Accurate tracking is not possible without precise location permissions. Allow access to precise location in order to start tracking.") },
                        confirmButton = {
                            Button(onClick = {
                                shouldShowFineLocationRationale = false
                                launchFinePermissionLauncher()
                            }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }

    private fun startForegroundLocationService(activeId: Long) {
        vm.activeId = activeId
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
        val entity =
            TrackedActivityEntity(startTimestamp = System.currentTimeMillis()) // Could instead use timestamp from first location belonging to the entity
        vm.insertNewTrackedActivity(entity) { id ->
            startForegroundLocationService(id)
        }
    }

    private fun stopTracking() {
        val serviceIntent = Intent(this, PeriodicLocationService::class.java)
        stopService(serviceIntent)
        // TODO: Maybe save a timestamp for stop-time to entity. Otherwise just use last timestamp for the locations belonging to the entity.
        vm.activeId = 0
        vm.status = TrackingState.INACTIVE
    }

    private fun launchFinePermissionLauncher() {
        requestFineLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
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
        } else launchFinePermissionLauncher()
    }

    private fun onStopButtonClick() {
        stopTracking()
    }
}


@Composable
fun DefaultView(onStart: () -> Unit, vm: MonoViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .weight(3f)
                .fillMaxSize()
        ) {
            val allTrackedActivities by vm.allTrackedActivities.collectAsState()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(allTrackedActivities) { item ->
                    Card() { Text(item.id.toString()) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(8.dp)
        ) { }
    }
}
@Preview
@Composable
fun ActiveView(onStop: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.weight(3f).fillMaxWidth()) {
            ElapsedTimeText()
        }
        Spacer(Modifier.height(16.dp))
        Button(onStop, modifier = Modifier.weight(1f).fillMaxSize(), shape = RoundedCornerShape(8.dp)) { }
    }
}

@Composable
fun DetailedItemView(): Unit = TODO()

@Composable
fun ElapsedTimeText(vm: MonoViewModel = viewModel()) {
    val startTime by vm.startTime.collectAsState(0)
    if (startTime == 0L) return

    var elapsedTimeMillis by remember { mutableLongStateOf(0) }
    var elapsedTimeFormattedString by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(Unit) {
        while (isActive) {
            val currentTime = System.currentTimeMillis()
            elapsedTimeMillis = currentTime - startTime
            val duration = elapsedTimeMillis.milliseconds
            val hh = duration.inWholeHours
            val mm = duration.inWholeMinutes % 60
            val ss = duration.inWholeSeconds % 60
            elapsedTimeFormattedString = String.format(Locale.ROOT, "%02d:%02d:%02d", hh, mm, ss)
            Log.d("TAQGTAG", startTime.toString())
            Log.d("TAQGTAG", currentTime.toString())
            Log.d("TAQGTAG", elapsedTimeMillis.toString())
            Log.d("TAQGTAG", elapsedTimeFormattedString)
            delay(1000)
        }
    }
    Text(text = elapsedTimeFormattedString)
}