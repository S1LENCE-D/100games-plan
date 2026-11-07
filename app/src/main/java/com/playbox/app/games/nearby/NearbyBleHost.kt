package com.playbox.app.games.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.ArrayDeque

/**
 * 一个小型 BLE GATT 房主，可同时接受多个客人连接。
 * 房间名放在 scan response 的 manufacturer data 中，避免主广播包超长。
 */
@SuppressLint("MissingPermission")
class NearbyBleHost(
    context: Context,
    private val roomName: String,
    private val onPeerConnected: (String, String) -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onMessage: (String, String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter = manager?.adapter
    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private val devices = linkedMapOf<String, BluetoothDevice>()
    private val notifiedAddresses = mutableSetOf<String>()
    private val outgoing = mutableMapOf<String, ArrayDeque<String>>()
    private val sending = mutableSetOf<String>()

    fun start(): Boolean {
        val adapter = adapter ?: run {
            onError("设备不支持蓝牙")
            return false
        }
        if (!adapter.isEnabled) {
            onError("请先开启蓝牙")
            return false
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            onError("当前设备不支持 BLE 广播")
            return false
        }
        stop()
        server = manager?.openGattServer(appContext, serverCallback) ?: run {
            onError("无法创建蓝牙服务")
            return false
        }
        val service = BluetoothGattService(
            NearbyProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        val char = BluetoothGattCharacteristic(
            NearbyProtocol.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        char.addDescriptor(
            BluetoothGattDescriptor(
                NearbyProtocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(char)
        characteristic = char
        server?.addService(service)

        advertiser = adapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(NearbyProtocol.SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(
                NearbyProtocol.ROOM_MANUFACTURER_ID,
                NearbyProtocol.encodeRoomName(roomName),
            )
            .build()
        return runCatching {
            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
            true
        }.getOrElse {
            onError("无法开始蓝牙广播")
            false
        }
    }

    fun stopAdvertising() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        advertiser = null
    }

    fun send(message: String): Boolean {
        val targets = notifiedAddresses.toList()
        if (targets.isEmpty()) return false
        var delivered = false
        targets.forEach { address ->
            if (sendTo(address, message)) delivered = true
        }
        return delivered
    }

    @Synchronized
    fun sendTo(address: String, message: String): Boolean {
        if (devices[address] == null || characteristic == null) return false
        if (address !in notifiedAddresses) return false
        outgoing.getOrPut(address) { ArrayDeque() }.addLast(message)
        pumpLocked(address)
        return true
    }

    @Synchronized
    private fun pumpLocked(address: String) {
        if (address in sending) return
        val queue = outgoing[address] ?: return
        val message = queue.pollFirst() ?: return
        val server = server ?: return
        val device = devices[address] ?: return
        val char = characteristic ?: return
        sending.add(address)
        val started = runCatching {
            val data = message.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, char, false, data) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, char, false)
            }
        }.getOrElse { false }
        if (!started) {
            sending.remove(address)
            onError("蓝牙发送繁忙，正在重试")
        }
    }

    @Synchronized
    private fun clearQueues() {
        outgoing.clear()
        sending.clear()
    }

    fun disconnect(address: String) {
        val device = devices[address] ?: return
        runCatching { server?.cancelConnection(device) }
        notifiedAddresses.remove(address)
        devices.remove(address)
        outgoing.remove(address)
        sending.remove(address)
    }

    fun stop() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        advertiser = null
        runCatching { server?.clearServices() }
        runCatching { server?.close() }
        server = null
        characteristic = null
        devices.clear()
        notifiedAddresses.clear()
        clearQueues()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            onError("蓝牙广播失败（$errorCode）")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                devices[device.address] = device
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                devices.remove(device.address)
                outgoing.remove(device.address)
                sending.remove(device.address)
                val wasNotified = notifiedAddresses.remove(device.address)
                if (wasNotified) onDisconnected(device.address)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(this@NearbyBleHost) {
                sending.remove(device.address)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onError("蓝牙通知发送失败，正在重试")
                }
                pumpLocked(device.address)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == NearbyProtocol.CCCD_UUID) {
                val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                devices[device.address] = device
                if (enabled) notifiedAddresses.add(device.address) else notifiedAddresses.remove(device.address)
                if (responseNeeded) {
                    server?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        ByteArray(0),
                    )
                }
                if (enabled) onPeerConnected(peerName(device), device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == NearbyProtocol.CHARACTERISTIC_UUID) {
                if (responseNeeded) {
                    server?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value,
                    )
                }
                onMessage(String(value, Charsets.UTF_8), device.address)
            }
        }
    }

    private fun peerName(device: BluetoothDevice): String {
        val name = runCatching { device.name }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "附近玩家 ${device.address.takeLast(5)}"
    }
}
