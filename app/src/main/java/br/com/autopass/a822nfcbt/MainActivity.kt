package br.com.autopass.a822nfcbt

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.bluetooth.BluetoothDevice
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_main.*
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.util.Log


class MainActivity : AppCompatActivity() {

    val LOGGER_TAG = "Example822"
    val REQUEST_ENABLE_BT = 1

    //Gambiarra pra poder usar o simple_list_item no listView
    var m_devices = ArrayList<BluetoothDevice>()
    var m_deviceNames = ArrayList<String>()

    var m_arrayAdapter: ArrayAdapter<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getDevices()

        m_arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, android.R.id.text1, m_deviceNames)
        lvBluetooth.adapter = m_arrayAdapter
        lvBluetooth.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            connectArduino(position)
        }

    }

    //Essa função está horrível: Faz duas coisas e com um péssimo nome. Mas é só pra vcs verem o funcionamento.
    private fun getDevices(){
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            Log.i(LOGGER_TAG, "Found Bluetooth adapter")
            if (!adapter.isEnabled) {
                Log.i(LOGGER_TAG, "Bluetooth disabled, launch enable intent")
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
            }
            if (adapter.isEnabled) {
                Log.i(LOGGER_TAG, "Bluetooth enabled, find paired devices")
                retrieveAndFillDevicesLists(adapter)
            }
        }
    }

    private fun retrieveAndFillDevicesLists(adapter: BluetoothAdapter){
        val devices = adapter.bondedDevices
        if (!devices.isEmpty()) {
            m_devices.clear()
            m_deviceNames.clear()
            for (device in devices) {
                Log.i(LOGGER_TAG, String.format("Found bluetooth device: name %s", device.name))
                m_devices.add(device)
                m_deviceNames.add(device.name)
            }
        }
    }

    //Não me julguem por essa função kkkkk
    private fun connectArduino(position:Int){
        Log.i(LOGGER_TAG, String.format("Connect to device: %d", position))
        val device = m_devices[position]
        val connectIntent = Intent(this, ConnectedActivity::class.java)
        connectIntent.putExtra("device", device)
        startActivity(connectIntent)
    }
}
