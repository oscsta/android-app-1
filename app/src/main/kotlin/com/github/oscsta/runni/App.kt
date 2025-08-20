package com.github.oscsta.runni

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.oscsta.runni.ui.AppTheme
import com.github.oscsta.runni.ui.labelExtraLarge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import android.content.pm.PackageManager as PM


enum class TrackingState {
    ACTIVE, INACTIVE
}

class MonoViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy {
        TrackedActivityDatabase.getDatabase(application)
    }
    var activeId by mutableLongStateOf(0)
    var status by mutableStateOf(TrackingState.INACTIVE)
    val allTrackedActivities = db.trackedActivityDao().getAllItemsByMostRecent().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), emptyList()
    )
    val mostRecentLocation = db.locationEntityDao().getMostRecentLocation().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), null
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
    private var shouldShowInsufficientPermissionsDialog by mutableStateOf(false)    // These two dialogs and flags are painfully similar
    private var shouldShowFineLocationRationale by mutableStateOf(false)            // These two dialogs and flags are painfully similar
    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION, false
                ) -> startTracking()

                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION, false
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
                        AnimatedVisibility(
                            visible = vm.status == TrackingState.INACTIVE,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            DefaultView(onStart = ::onStartButtonClick)
                        }
                        AnimatedVisibility(
                            visible = vm.status == TrackingState.ACTIVE,
                            enter = slideInVertically(initialOffsetY = { -it }),
                            exit = slideOutVertically(targetOffsetY = { -it })
                        ) {
                            ActiveView(onStop = ::onStopButtonClick)
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
                        })
                }
                if (shouldShowFineLocationRationale) {
                    AlertDialog(
                        onDismissRequest = {
                        shouldShowFineLocationRationale =
                            false; launchFineLocationPermissionLauncher()
                    },
                        title = { Text("Precise location is required") },
                        text = { Text("Accurate tracking is not possible without precise location permissions. Allow access to precise location in order to start tracking.") },
                        confirmButton = {
                            Button(onClick = {
                                shouldShowFineLocationRationale = false
                                launchFineLocationPermissionLauncher()
                            }) { Text("OK") }
                        })
                }
            }
        }
    }

    private fun startForegroundLocationService(activeId: Long) {
        vm.activeId = activeId
        val serviceIntent = Intent(this, PeriodicLocationService::class.java).apply {
            putExtra(
                "ACTIVE_ID", vm.activeId
            )
        }
        Log.d("MainActivity", "Created intent $serviceIntent")
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
        vm.activeId = 0
        vm.status = TrackingState.INACTIVE
    }

    private fun launchFineLocationPermissionLauncher() {
        requestFineLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun onStartButtonClick() {
        val fineLocationPerm =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        @Suppress("unused", "UnusedVariable") val coarseLocationPerm =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocationPerm == PM.PERMISSION_GRANTED) {
            startTracking()
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.d("runni.MainActivity", "Permission rationale for fine location was requested")
        } else launchFineLocationPermissionLauncher()
    }

    private fun onStopButtonClick() {
        stopTracking()
    }
}


// Possibly not great to have an entire viewmodel as input to composable
@Composable
fun DefaultView(vm: MonoViewModel = viewModel(), onStart: () -> Unit) {
    val allTrackedActivities by vm.allTrackedActivities.collectAsState()
    var isInDeleteMode by remember { mutableStateOf(false) }
    var deletionSet by remember { mutableStateOf(setOf<Long>()) }

    BackHandler(enabled = isInDeleteMode) {
        isInDeleteMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .weight(4f)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(allTrackedActivities) { item ->
                    TrackedActivityListItem(item,
                        border = if (isInDeleteMode) BorderStroke(1.dp, color = MaterialTheme.colorScheme.outline) else null,
                        onClick = {
                        /* Expand card for more details,graphs,etc (if not in deletion mode) */
                    }, onLongClick = {
                        isInDeleteMode = true
                    })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isInDeleteMode) {
            Button(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    Icons.Default.Delete,
                    modifier = Modifier.fillMaxSize(0.5f),
                    contentDescription = "Delete"
                )
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = stringResource(R.string.StartButtonText), style = MaterialTheme.typography.labelExtraLarge)
            }
        }
    }
}

// Possibly not great to have an entire viewmodel as input to composable
@Composable
fun ActiveView(
    modifier: Modifier = Modifier, vm: MonoViewModel = viewModel(), onStop: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .weight(4f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val startTime by vm.startTime.collectAsState(0)
                val lastLocation by vm.mostRecentLocation.collectAsState()
                Box(
                    modifier = modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ElapsedTimeText(startTime)
                }
                Box(
                    modifier = modifier
                        .weight(2f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                text = "%.2f".format(lastLocation?.speed ?: 0.00f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(text = "Speed", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "m/s (avg.)",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier.height(16.dp))
        HoldToActivateButton(
            modifier = modifier
                .weight(1f)
                .fillMaxSize(), onComplete = onStop
        ) { Text(text = stringResource(R.string.StopButtonText), style = MaterialTheme.typography.labelExtraLarge) }
    }
}


@Composable
fun ElapsedTimeText(startTime: Long, modifier: Modifier = Modifier) {
    if (startTime == 0L) return
    var elapsedTimeMillis by remember { mutableLongStateOf(0) }
    LaunchedEffect(startTime) {
        while (isActive) {
            val currentTime = System.currentTimeMillis()
            elapsedTimeMillis = currentTime - startTime
            delay(1000)
        }
    }
    val duration = elapsedTimeMillis.milliseconds
    Text(
        text = duration.toHourMinuteSecondColonDelimited(),
        fontSize = 48.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
    )
}

@OptIn(ExperimentalTime::class)
@Composable
fun TrackedActivityListItem(
    item: TrackedActivityEntity,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    isSelectedForDeletion: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val totalDuration = ((item.endTimestamp
        ?: item.startTimestamp) - item.startTimestamp).milliseconds // For now just show 0 if there was no end timestamp
    val startDateTimeInSysTz = Instant.fromEpochMilliseconds(item.startTimestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val formatter =
        LocalDateTime.Format { date(LocalDate.Formats.ISO); chars(" | "); hour(); char(':'); minute(); }

    val offsetWhenSelected by animateDpAsState(targetValue = if (isSelectedForDeletion) (-10).dp else 0.dp)
    val deletionBorder = BorderStroke(4.dp, Color.Red)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .offset(offsetWhenSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(8.dp),
        border = if (isSelectedForDeletion) deletionBorder else border
    ) {
        Column(
            modifier = modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = modifier.fillMaxWidth(),
                text = startDateTimeInSysTz.format(formatter),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = modifier.weight(1f)) // Make the columns right-aligned while still being able to use spacedBy as the arrangement
                Column(
                    modifier = modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = totalDuration.toHourMinuteSecondColonDelimited(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(text = "Duration", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "hh:mm:ss",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(
                    modifier = modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = item.averageSpeedInMetersPerSecond?.let { averageSpeed ->
                        "%.2f".format(
                            averageSpeed
                        )
                    } ?: "N/A", // For now just inform of missing value in UI
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Speed", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "m/s (avg.)",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(
                    modifier = modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = item.totalDistanceInMeters?.let { totalDistance ->
                        "%.2f".format(
                            totalDistance / 1000f
                        )
                    } ?: "N/A", // For now just inform of missing value in UI
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Distance", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "km. (total)",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
fun HoldToActivateButton(
    modifier: Modifier = Modifier, holdDurationMillis: Int = 2000, onComplete: () -> Unit = {
        Log.d(
            "HoldToActivateButton", "Hold press activated"
        )
    }, content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            holdProgress.animateTo(
                1f, animationSpec = tween(holdDurationMillis, easing = LinearEasing)
            )
            onComplete()
        } else {
            holdProgress.animateTo(0f)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.inversePrimary
    val textStyle = MaterialTheme.typography.labelLarge
    val contentColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(primaryColor)
            .drawBehind {
                val progress = holdProgress.value
                val fillHeight = size.height * progress
                val yOffset = size.height - fillHeight
                drawRect(
                    color = secondaryColor,
                    topLeft = Offset(x = 0f, y = yOffset),
                    size = Size(width = size.width, height = fillHeight)
                )
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle, LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}


@Preview
@Composable
fun ListItemPreview() {
    AppTheme(darkTheme = true) {
        val item = TrackedActivityEntity(
            id = 0,
            startTimestamp = 2111111111111,
        )
        TrackedActivityListItem(item)
    }
}