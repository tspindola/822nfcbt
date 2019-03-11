package br.com.autopass.a822nfcbt

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlinx.android.synthetic.main.activity_connected.*
import java.math.BigDecimal
import java.text.NumberFormat

class ConnectedActivity : AppCompatActivity() {
    val LOGGER_TAG = "Example822"

    val BLUETOOTH_CONNECTED = 1
    val BLUETOOTH_DISCONNECTED = 2
    val BLUETOOTH_RECEIVED = 3

    private inner class DeviceThread internal constructor(
        internal var m_handler: Handler,
        internal var m_device: BluetoothDevice
    ) : Thread() {
        internal var m_socket: BluetoothSocket? = null
        internal lateinit var m_output: OutputStream
        internal lateinit var m_input: InputStream

        init {
            Log.i(LOGGER_TAG, "DeviceThread running")
            Log.i(LOGGER_TAG, String.format("Received device: %s", m_device.name))
        }

        private fun connect() {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
                Log.i(LOGGER_TAG, "Create BluetoothSocket")
                m_socket = m_device.createRfcommSocketToServiceRecord(uuid)
                Log.i(LOGGER_TAG, "Connect BluetoothSocket")
                m_socket!!.connect()
                m_handler.obtainMessage(BLUETOOTH_CONNECTED).sendToTarget()
                m_output = m_socket!!.outputStream
                m_input = m_socket!!.inputStream
            } catch (e: IOException) {
                Log.e(LOGGER_TAG, String.format("Caught IOException e: %s", e.toString()))
                m_socket = null
                m_handler.obtainMessage(BLUETOOTH_DISCONNECTED).sendToTarget()
            }

        }

        override fun run() {
            connect()
            val buffer = ByteArray(1024)
            while (m_socket != null) {
                try {
                    val len = m_input.read(buffer)
                    if (len > 0) {
                        m_handler.obtainMessage(BLUETOOTH_RECEIVED, len, -1, buffer).sendToTarget()
                    }
                } catch (e: IOException) {
                    Log.e(LOGGER_TAG, String.format("Caught IOException e: %s", e.toString()))
                    m_socket = null
                    m_handler.obtainMessage(BLUETOOTH_DISCONNECTED).sendToTarget()
                }

            }
        }

        fun cancel() {
            try {
                m_socket!!.close()
            } catch (e: IOException) {
                Log.e(LOGGER_TAG, String.format("Caught IOException e: %s", e.toString()))
            } finally {
                m_socket = null
                m_handler.obtainMessage(BLUETOOTH_DISCONNECTED).sendToTarget()
            }
        }

        fun sendCommand(command: String) {
            try {
                m_output.write(command.toByteArray())
                m_output.flush()
                Log.i(LOGGER_TAG, String.format("Sent command \"%s\" to device", command))
            } catch (e: IOException) {
                Log.e(LOGGER_TAG, String.format("Caught IOException e: %s", e.toString()))
                m_socket = null
                m_handler.obtainMessage(BLUETOOTH_DISCONNECTED).sendToTarget()
            }

        }
    }

    private lateinit var m_handler: Handler
    private var m_thread: DeviceThread? = null
    private val locale = Locale.getDefault()
    private var credits: String = ""
    private var isWaitingAnswer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)
        Log.i(LOGGER_TAG, "Started DeviceActivity");

        m_handler = object : Handler() {
            override fun handleMessage(msg: Message) {
                onMessage(msg)
            }
        }

        val device = intent.extras!!.get("device") as BluetoothDevice
        tvDeviceName.text = device.name
        tvStatus.text = "Connecting..."

        Log.i(LOGGER_TAG, "Start new DeviceThread")
        m_thread = DeviceThread(m_handler, device)
        m_thread!!.start()
    }

    fun onMessage(msg: Message) {
        when (msg.what) {
            BLUETOOTH_CONNECTED -> onBluetoothConnected()
            BLUETOOTH_DISCONNECTED -> onBluetoothDisconnected()
            BLUETOOTH_RECEIVED -> {
                val buffer = msg.obj as ByteArray
                val len = msg.arg1
                if (len > 0) {
                    onBluetoothRead(buffer, len)
                }
            }
        }
    }

    override fun onDestroy() {
        if (m_thread != null && m_thread!!.isAlive) {
            m_thread!!.cancel()
        }
        super.onDestroy()
    }

    private fun onBluetoothConnected() {
        Log.i(LOGGER_TAG, "Bluetooth connected")
        tvStatus.text = "Connected"
    }

    private fun onBluetoothDisconnected() {
        Log.i(LOGGER_TAG, "Bluetooth disconnected")
        tvStatus.text = "Disconnected"
    }

    private fun removeCreditsIfNeeded(){
        var sendToCard:String = credits
        credits = credits.trimStart('x')
        credits = credits.substring(credits.lastIndexOf("x") + 1)
        if(switchFee.isChecked){
            val fee = etFee.text.toString().toDouble()
            var creditsFloat = credits.toDouble()/100
            creditsFloat -= fee
            sendToCard = creditsFloat.toString().format("%.2f").replace(".", "")
            credits = sendToCard
            sendToCard = sendToCard.padStart(16,'x')
        }
        val parsed = parseToBigDecimal(credits, locale)
        credits = NumberFormat.getCurrencyInstance(locale).format(parsed)
        tvAnswer.text = credits

        if (m_thread != null) {
            m_thread!!.sendCommand(sendToCard)
        }
    }

    private fun interpretAnswer(s:String){
        if(isWaitingAnswer){
            isWaitingAnswer = false
            if(s == "0")
                Toast.makeText(this,"Saldo gravado com sucesso!",Toast.LENGTH_SHORT).show()
        }
        else {
            credits += s
            if (credits.length == 16){
                Log.i(LOGGER_TAG, String.format("String amount (before trim) = %s",credits))
                removeCreditsIfNeeded()
                isWaitingAnswer = true
                credits = ""
            }
        }
    }

    private fun onBluetoothRead(buffer: ByteArray, len: Int) {
        Log.i(LOGGER_TAG, String.format("Received %d bytes", len))
        val output = String(buffer, 0, len)
        Log.i(LOGGER_TAG, String.format("Bytes: %s", output))

        interpretAnswer(output)
    }

    private fun parseToBigDecimal(value: String, locale: Locale): BigDecimal {
        val replaceable = String.format("[%s,.\\s]", NumberFormat.getCurrencyInstance(locale).getCurrency().getSymbol())

        val cleanString = value.replace(replaceable.toRegex(), "")

        return BigDecimal(cleanString).setScale(
            2, BigDecimal.ROUND_FLOOR).divide(BigDecimal(100), BigDecimal.ROUND_FLOOR
        )
    }
}
