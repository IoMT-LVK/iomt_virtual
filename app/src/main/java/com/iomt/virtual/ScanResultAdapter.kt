package com.iomt.virtual

import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.row_scan_result.view.device_name
import kotlinx.android.synthetic.main.row_scan_result.view.mac_address
import kotlinx.android.synthetic.main.row_scan_result.view.signal_strength
import org.jetbrains.anko.layoutInflater


class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val parentActivity: MainActivity,
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = parent.context.layoutInflater.inflate(
            R.layout.row_scan_result,
            parent,
            false
        )
        return ViewHolder(view, parentActivity)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }


    inner class ViewHolder(
        private val view: View,
        private val parentActivity: MainActivity,
    ) : RecyclerView.ViewHolder(view) {


        fun bind(result: ScanResult) {
            view.device_name.text = result.device.name ?: "Unnamed"
            view.mac_address.text = result.device.address
            view.signal_strength.text = "${result.rssi} dBm"
            view.setOnClickListener {
                val intent = Intent(parentActivity, DeviceActivity::class.java)
                    .putExtra("DEVICE_NAME", view.device_name.text.toString())
                    .putExtra("MAC_ADDRESS", view.mac_address.text.toString())
                    .putExtra("SIGNAL_STRENGTH", view.signal_strength.text.toString())
                startActivity(parentActivity, intent, null)
            }
        }
    }
}