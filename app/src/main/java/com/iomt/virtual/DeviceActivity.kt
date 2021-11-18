package com.iomt.virtual

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.connected_device_activity.*

class DeviceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.connected_device_activity)

        send_button.setOnClickListener { sendButtonOnClickListener() }
        disconnect_button.setOnClickListener { finish() }

        deviceName.text = intent.getStringExtra("DEVICE_NAME")
        deviceSignalStrength.text = intent.getStringExtra("SIGNAL_STRENGTH")
        deviceMacAddress.text = intent.getStringExtra("MAC_ADDRESS")
    }

    var isSending: Boolean = false

    @SuppressLint("SetTextI18n")
    private fun sendButtonOnClickListener() {
        isSending = !isSending
        send_button.text = if(isSending) { "Stop sending" } else { "Send" }
        // TODO: create connection and send data to device
    }

}