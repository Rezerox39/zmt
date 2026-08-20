package dev.abhi.zmt.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothReceiver(private val onConnected: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            Log.d("BluetoothReceiver", "Bluetooth device connected, auto-resuming playback")
            onConnected()
        }
    }
}
