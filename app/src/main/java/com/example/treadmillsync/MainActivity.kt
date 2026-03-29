package com.example.treadmillsync

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.treadmillsync.ui.theme.TreadmillSyncTheme
import com.example.treadmillsync.ui.theme.NeonCyan
import com.example.treadmillsync.ui.theme.SurfaceGray
import com.example.treadmillsync.ui.theme.GlowCyan
import com.example.treadmillsync.ui.theme.White
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var bleService: BleService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBound = true
            viewModel.bleService = bleService
            viewModel.bleManager = bleService?.bleManager
            checkPermissionsAndStart()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            bleService = null
            viewModel.bleService = null
            viewModel.bleManager = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startBleService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Intent(this, BleService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            TreadmillSyncTheme {
                TreadmillApp(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startBleService()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Initial start of advertising
        viewModel.bleManager?.startAdvertising()
    }
}

@Composable
fun TreadmillApp(viewModel: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowCyan, Color.Transparent),
                            center = center,
                            radius = size.width
                        ),
                        radius = size.width / 1.5f,
                        center = center.copy(y = center.y * 0.5f)
                    )
                }
        )

        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ScreenTransition"
        ) { isSettings ->
            if (isSettings) {
                SettingsScreen(viewModel, onBack = { 
                    showSettings = false 
                    // REMOVED: Re-advertising on back. This kills the connection if already paired.
                })
            } else {
                MainControlScreen(viewModel, onOpenSettings = { showSettings = true })
            }
        }
    }
}

@Composable
fun MainControlScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit) {
    val displaySpeed = viewModel.getDisplaySpeed()
    val unitText = if (viewModel.unit == SpeedUnit.METRIC) "KM/H" else "MPH"

    val speedKmh = viewModel.speedKmh
    val paceString = if (speedKmh > 0.5f) {
        val totalSeconds = (3600 / speedKmh).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "%d:%02d min/km".format(minutes, seconds)
    } else {
        "--:-- min/km"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TREADMILL SYNC",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.background(SurfaceGray, CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = White)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlCircleButton(Icons.Default.KeyboardArrowUp) { viewModel.incrementWhole() }
                Spacer(modifier = Modifier.height(16.dp))
                ControlCircleButton(Icons.Default.KeyboardArrowDown) { viewModel.decrementWhole() }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Text(
                    text = "%.1f".format(displaySpeed),
                    color = White,
                    fontSize = if (displaySpeed >= 10f) 80.sp else 100.sp,
                    fontWeight = FontWeight.Thin,
                    lineHeight = if (displaySpeed >= 10f) 80.sp else 100.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = unitText,
                    color = White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = paceString,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlCircleButton(Icons.Default.KeyboardArrowUp) { viewModel.incrementDecimal() }
                Spacer(modifier = Modifier.height(16.dp))
                ControlCircleButton(Icons.Default.KeyboardArrowDown) { viewModel.decrementDecimal() }
            }
        }

        Spacer(modifier = Modifier.weight(1.2f))

        val presets = if (viewModel.unit == SpeedUnit.METRIC) viewModel.presetsMetric else viewModel.presetsImperial
        
        Text(
            "QUICK ACCESS",
            color = White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            itemsIndexed(presets) { _, preset ->
                ModernPresetButton(
                    value = preset,
                    isSelected = (displaySpeed * 10).roundToInt() == (preset * 10).roundToInt(),
                    onClick = { viewModel.setSpeedFromDisplay(preset) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SETTINGS",
                color = White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(SurfaceGray, CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = White)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        SettingsSectionTitle("SPEED UNIT")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGray)
                .padding(4.dp)
        ) {
            UnitToggleButton(
                text = "METRIC", 
                isSelected = viewModel.unit == SpeedUnit.METRIC,
                onClick = { viewModel.unit = SpeedUnit.METRIC }
            )
            UnitToggleButton(
                text = "IMPERIAL", 
                isSelected = viewModel.unit == SpeedUnit.IMPERIAL,
                onClick = { viewModel.unit = SpeedUnit.IMPERIAL }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingsSectionTitle("PRESET BUTTONS")
        val presets = if (viewModel.unit == SpeedUnit.METRIC) viewModel.presetsMetric else viewModel.presetsImperial
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(presets) { index, preset ->
                PresetEditField(
                    value = preset,
                    onValueChange = { viewModel.updatePreset(index, it) }
                )
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
    )
}

@Composable
fun RowScope.UnitToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NeonCyan else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PresetEditField(value: Float, onValueChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    
    OutlinedTextField(
        value = textValue,
        onValueChange = { 
            textValue = it
            it.toFloatOrNull()?.let { f -> onValueChange(f) }
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = White,
            unfocusedTextColor = White,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
fun ControlCircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp) 
            .clip(CircleShape)
            .background(SurfaceGray)
            .border(1.dp, White.copy(alpha = 0.1f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ModernPresetButton(value: Float, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NeonCyan else SurfaceGray)
            .border(
                1.dp, 
                if (isSelected) NeonCyan else White.copy(alpha = 0.1f), 
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "%.1f".format(value),
            color = if (isSelected) Color.Black else White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
