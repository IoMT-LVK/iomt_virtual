package com.iomt.virtual

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.connected_device_activity.*
import java.util.*
import kotlin.math.sin

class DeviceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.connected_device_activity)

        send_button.setOnClickListener { sendButtonOnClickListener() }
        disconnect_button.setOnClickListener { finish() }

        deviceName.text = intent.getStringExtra("DEVICE_NAME")
        deviceSignalStrength.text = intent.getStringExtra("SIGNAL_STRENGTH")
        deviceMacAddress.text = intent.getStringExtra("MAC_ADDRESS")

        Log.i("DeviceActivity", "Successfully created")

        device = intent.getParcelableExtra("DEVICE")!!
        gatt = device.connectGatt(this, false, gattCallback)
    }

    override fun onStop() {
        super.onStop()
        gatt.disconnect()
    }

    val HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D)
    val HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2a37)
    val HEART_RATE_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2a37)

    private var isSending: Boolean = false
    lateinit var device: BluetoothDevice
    lateinit var gatt: BluetoothGatt

    private val gattCallback = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            val deviceAddress = gatt.device?.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    // TODO: Store a reference to BluetoothGatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.i("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun sendButtonOnClickListener() {
        isSending = !isSending
        send_button.text = if(isSending) { "Stop sending" } else { "Send" }

        // TODO: create connection and send data to device
        if (isSending) {
            val heartRateService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val heartRateCharacteristic = heartRateService?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
            RepeatHandler.repeatDelayed(1000L) { sendData(heartRateCharacteristic!!) }
        }
    }

    private fun sendData(characteristic: BluetoothGattCharacteristic): Boolean {
        val data = byteArrayOf(generateData().toByte(), generateData().toByte(), generateData().toByte())
        if ((characteristic.properties and PROPERTY_WRITE) == 0) {
            Log.e("sendData", "characteristic is not writable")
            return false
        }
        characteristic.value = data
        characteristic.writeType = PROPERTY_WRITE
        if ( gatt.writeCharacteristic(characteristic)) {
            Log.d("sendData", "sent data to $deviceName")
            return true
        } else {
            Log.e("sendData", "error while sending data")
            return false
        }
    }

    private fun generateData() = (sin(System.currentTimeMillis().toDouble() / 1000) * 100).toInt()

    private fun convertFromInteger(i: Int): UUID {
        val MSB = 0x0000000000001000L
        val LSB = -0x7fffff7fa064cb05L
        val value = (i and -0x1).toLong()
        return UUID(MSB or (value shl 32), LSB)
    }
    object RepeatHandler {
        fun repeatDelayed(delay: Long, todo: () -> Unit) {
            val handler = Handler()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    todo()
                    handler.postDelayed(this, delay)
                }
            }, delay)
        }
    }
}