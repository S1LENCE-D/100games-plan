package com.playbox.app.games.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.ArrayDeque

@SuppressLint("MissingPermission")
class NearbyBleClient(
    context: Context,
    private val onPeers: (List<NearbyPeer>) -> Unit,
    private val onConnected: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onMessage: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter = manager?.adapter
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    private val peers = linkedMapOf<String, NearbyPeer>()
    private val writeQueue = ArrayDeque<String>()
    private var writeInFlight = false

    fun startScan(): Boolean {
        val adapter = adapter ?: run {
            onError("设备不支持蓝牙")
            return false
        }
        if (!adapter.isEnabled) {
            onError("请先开启蓝牙")
            return false
        }
        if (scanning) return true
        val scanner = adapter.bluetoothLeScanner ?: run {
            onError("无法启动蓝牙扫描")
            return false
        }
        peers.clear()
        onPeers(emptyList())
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(NearbyProtocol.SERVICE_UUID))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(1) // SCAN_MATCH_MODE_AGGRESSIVE
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        return runCatching {
            scanner.startScan(filters, settings, scanCallback)
            scanning = true
            true
        }.getOrElse {
            onError("无法开始蓝牙扫描")
            false
        }
    }

    fun connect(peer: NearbyPeer) {
        stopScan()
        val adapter = adapter ?: return
        val device = runCatching { adapter.getRemoteDevice(peer.address) }.getOrNull()
        if (device == null) {
            onError("无法连接该设备")
            return
        }
        clearWriteQueue()
        gatt?.close()
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun send(message: String): Boolean = synchronized(this) {
        if (gatt == null || characteristic == null) return false
        writeQueue.addLast(message)
        pumpWriteLocked()
        true
    }

    private fun pumpWriteLocked() {
        if (writeInFlight) return
        val message = writeQueue.pollFirst() ?: return
        val gatt = gatt ?: run { writeQueue.clear(); return }
        val char = characteristic ?: run { writeQueue.clear(); return }
        writeInFlight = true
        val started = runCatching {
            val data = message.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    char,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }.getOrElse {
            onError("蓝牙发送失败")
            false
        }
        if (!started) {
            writeInFlight = false
            onError("蓝牙发送繁忙，正在重试")
        }
    }

    private fun clearWriteQueue() = synchronized(this) {
        writeQueue.clear()
        writeInFlight = false
    }

    fun stopScan() {
        if (!scanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
    }

    fun close() {
        clearWriteQueue()
        stopScan()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        characteristic = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address
            val advertisedName = result.scanRecord
                ?.getManufacturerSpecificData(NearbyProtocol.ROOM_MANUFACTURER_ID)
                ?.let(NearbyProtocol::decodeRoomName)
                ?.takeIf { it.isNotBlank() }
            val name = advertisedName
                ?: runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "附近玩家 ${address.takeLast(5)}"
            peers[address] = NearbyPeer(
                name = name,
                address = address,
                rssi = result.rssi,
            )
            onPeers(peers.values.sortedByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError("蓝牙扫描失败（$errorCode）")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                clearWriteQueue()
                characteristic = null
                runCatching { gatt.close() }
                onDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError("无法读取蓝牙服务")
                return
            }
            val service: BluetoothGattService = gatt.getService(NearbyProtocol.SERVICE_UUID)
                ?: run {
                    onError("附近设备不是 100Games 联机房间")
                    return
                }
            val char = service.getCharacteristic(NearbyProtocol.CHARACTERISTIC_UUID)
                ?: run {
                    onError("附近设备不是 100Games 联机房间")
                    return
                }
            characteristic = char
            runCatching { gatt.requestMtu(128) }
            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(NearbyProtocol.CCCD_UUID)
            if (descriptor == null) {
                onError("蓝牙通知配置失败")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid == NearbyProtocol.CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                runCatching {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                }
                onConnected(peerName(gatt.device))
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            synchronized(this@NearbyBleClient) {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onError("蓝牙写入失败，正在重试")
                }
                pumpWriteLocked()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onMessage(String(value, Charsets.UTF_8))
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onMessage(String(value, Charsets.UTF_8))
        }
    }

    private fun peerName(device: BluetoothDevice): String {
        val name = runCatching { device.name }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "附近玩家 ${device.address.takeLast(5)}"
    }
}
