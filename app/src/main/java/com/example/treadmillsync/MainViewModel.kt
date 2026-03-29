package com.example.treadmillsync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class SpeedUnit {
    METRIC, IMPERIAL
}

class MainViewModel : ViewModel() {
    var speedKmh by mutableStateOf(10f)
    var unit by mutableStateOf(SpeedUnit.METRIC)
    
    var bleManager: BleManager? = null
    var bleService: BleService? = null

    // Updated defaults as requested: 5, 11, 12, 13, 14, 15, 16, 17, 18
    var presetsMetric by mutableStateOf(listOf(5f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f))
    var presetsImperial by mutableStateOf(listOf(3.1f, 6.8f, 7.5f, 8.1f, 8.7f, 9.3f, 10f, 10.6f, 11.2f))

    fun updateSpeed(newSpeed: Float) {
        speedKmh = newSpeed.coerceIn(0f, 25f)
        if (bleService != null) {
            bleService?.updateSpeed(speedKmh, unit)
        } else {
            bleManager?.updateSpeed(speedKmh)
        }
    }

    fun setSpeedFromDisplay(value: Float) {
        val newSpeedKmh = if (unit == SpeedUnit.METRIC) value else value / 0.621371f
        updateSpeed(newSpeedKmh)
    }
    
    fun getDisplaySpeed(): Float {
        return if (unit == SpeedUnit.METRIC) speedKmh else speedKmh * 0.621371f
    }
    
    fun toggleUnit() {
        unit = if (unit == SpeedUnit.METRIC) SpeedUnit.IMPERIAL else SpeedUnit.METRIC
    }

    fun updatePreset(index: Int, newValue: Float) {
        if (unit == SpeedUnit.METRIC) {
            presetsMetric = presetsMetric.toMutableList().apply { 
                if (index < size) this[index] = newValue 
            }
        } else {
            presetsImperial = presetsImperial.toMutableList().apply { 
                if (index < size) this[index] = newValue 
            }
        }
    }

    fun incrementWhole() {
        val current = getDisplaySpeed()
        setSpeedFromDisplay(current + 1f)
    }

    fun decrementWhole() {
        val current = getDisplaySpeed()
        setSpeedFromDisplay(current - 1f)
    }

    fun incrementDecimal() {
        val current = getDisplaySpeed()
        setSpeedFromDisplay(current + 0.1f)
    }

    fun decrementDecimal() {
        val current = getDisplaySpeed()
        setSpeedFromDisplay(current - 0.1f)
    }
}
