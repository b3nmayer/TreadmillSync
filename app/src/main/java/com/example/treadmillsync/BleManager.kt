package com.example.treadmillsync

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGattServer: BluetoothGattServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val rscServiceUuid = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
    private val rscMeasurementUuid = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
    private val rscFeatureUuid = UUID.fromString("00002a54-0000-1000-8000-00805f9b34fb")

    private val disServiceUuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val manuNameUuid = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    private val modelNumUuid = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val registeredDevices = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private var lastSpeedKmh = 0f
    private var isAdvertising = false
    private var pendingCustomName: String? = null
    private var isHeartbeatRunning = false

    private val servicesToAdd = mutableListOf<BluetoothGattService>()

    @SuppressLint("MissingPermission")
    fun startAdvertising(customName: String? = null) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.e("BleManager", "Bluetooth is disabled")
            return
        }

        pendingCustomName = customName
        stopAdvertising()

        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val rscService = BluetoothGattService(rscServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val measurementChar = BluetoothGattCharacteristic(rscMeasurementUuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        measurementChar.addDescriptor(BluetoothGattDescriptor(cccdUuid, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        val featureChar = BluetoothGattCharacteristic(rscFeatureUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)

        rscService.addCharacteristic(measurementChar)
        rscService.addCharacteristic(featureChar)

        val disService = BluetoothGattService(disServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        disService.addCharacteristic(BluetoothGattCharacteristic(manuNameUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        disService.addCharacteristic(BluetoothGattCharacteristic(modelNumUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))

        servicesToAdd.clear()
        servicesToAdd.add(rscService)
        servicesToAdd.add(disService)

        addNextService()
    }

    @SuppressLint("MissingPermission")
    private fun addNextService() {
        val server = bluetoothGattServer ?: return
        if (servicesToAdd.isNotEmpty()) {
            val s = servicesToAdd.removeAt(0)
            server.addService(s)
        } else {
            mainHandler.postDelayed({ startAdvInternal() }, 200)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w("BleManager", "Stop advertising error")
        }
        stopHeartbeat()
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        isAdvertising = false
        registeredDevices.clear()
        servicesToAdd.clear()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvInternal() {
        if (isAdvertising) return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val nameToUse = if (!pendingCustomName.isNullOrBlank()) pendingCustomName!! else "FootPod"
        try {
            bluetoothAdapter.name = nameToUse
        } catch (e: Exception) {
            Log.e("BleManager", "Failed to set adapter name")
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(rscServiceUuid))

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val method = dataBuilder.javaClass.getMethod("setAppearance", Int::class.javaPrimitiveType)
                method.invoke(dataBuilder, 1088)
            } catch (e: Exception) {
                Log.w("BleManager", "setAppearance not supported")
            }
        }

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()

        advertiser.startAdvertising(settings, dataBuilder.build(), scanResponse, advertiseCallback)
        isAdvertising = true
    }

    @SuppressLint("MissingPermission")
    fun updateSpeed(speedKmh: Float) {
        lastSpeedKmh = speedKmh
        val speedValue = ((speedKmh / 3.6f) * 256).toInt()
        
        val cadence = if (speedKmh > 0.5f) {
            (150 + (speedKmh - 5) * 2.5f).toInt().coerceIn(150, 180)
        } else 0

        val data = byteArrayOf(
            0x00.toByte(),
            (speedValue and 0xFF).toByte(),
            ((speedValue shr 8) and 0xFF).toByte(),
            cadence.toByte()
        )

        val server = bluetoothGattServer ?: return
        val rscChar = server.getService(rscServiceUuid)?.getCharacteristic(rscMeasurementUuid) ?: return

        for (device in registeredDevices) {
            if (Build.VERSION.SDK_INT >= 33) {
                server.notifyCharacteristicChanged(device, rscChar, false, data)
            } else {
                @Suppress("DEPRECATION")
                rscChar.value = data
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, rscChar, false)
            }
        }
    }

    private fun startHeartbeat() {
        if (isHeartbeatRunning) return
        isHeartbeatRunning = true
        mainHandler.post(heartbeatRunnable)
    }

    private fun stopHeartbeat() {
        isHeartbeatRunning = false
        mainHandler.removeCallbacks(heartbeatRunnable)
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!isHeartbeatRunning) return
            if (registeredDevices.isNotEmpty()) {
                updateSpeed(lastSpeedKmh)
                mainHandler.postDelayed(this, 500)
            } else {
                isHeartbeatRunning = false
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("BleManager", "Advertising Success")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("BleManager", "Advertising Failure: $errorCode")
            isAdvertising = false
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            mainHandler.post { addNextService() }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
                if (registeredDevices.isEmpty()) stopHeartbeat()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (descriptor.uuid == cccdUuid) {
                if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    registeredDevices.add(device)
                    startHeartbeat()
                } else {
                    registeredDevices.remove(device)
                    if (registeredDevices.isEmpty()) stopHeartbeat()
                }
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val responseValue = when (characteristic.uuid) {
                rscFeatureUuid -> byteArrayOf(0x00, 0x00)
                manuNameUuid -> "NeoSync".toByteArray()
                else -> characteristic.value ?: byteArrayOf()
            }
            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue)
        }
    }
}
