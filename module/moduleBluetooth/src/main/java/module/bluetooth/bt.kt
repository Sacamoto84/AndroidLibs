package module.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


/**
 * Использование
 *
 * ```kotlin
 *  BT.init(context)
 *  //BT.getPairedDevices()
 *  BT.autoConnect()
 * ```
 */

class BT(val name: String = "Tonometr", private val chIn: Channel<String>, private val chOut: Channel<String> ) {

    private lateinit var bluetoothManager: BluetoothManager
    lateinit var bluetoothAdapter: BluetoothAdapter

    enum class Status {
        NOTREADY, //Определяет то что сам блютус модуль не включен на телефоне
        READY,    //Определяет то что сам блютус модуль включен на телефоне, или что устройство Disconnect
        CONNECTING,
        CONNECTED
    }

    /**
     * Статус работы блютус устройства
     */
    var btStatus = MutableStateFlow(Status.NOTREADY)

    //Текст ошибок в модуле
    var error = MutableStateFlow("")

    private var stm32device: BluetoothDevice? = null

    private var mSocket: BluetoothSocket? = null
    private val uuid: String = "00001101-0000-1000-8000-00805F9B34FB"


    fun init(context: Context) {
        bluetoothManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(BluetoothManager::class.java)
        } else {
            ContextCompat.getSystemService(context, BluetoothManager::class.java)!!
        }
        bluetoothAdapter = bluetoothManager.adapter

        if (bt.bluetoothAdapter.isEnabled)
            btStatus.value = Status.READY

    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices() {

        /**
         *  Получить список связанных устройств
         */
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

        pairedDevices.forEach {
            Timber.i(it.name)
            Timber.i(it.address)
            it.uuids.forEach { u ->
                Timber.i(u.toString())
            }
        }

        try {
            stm32device = pairedDevices.first { it.name == name }
        } catch (e: NoSuchElementException) {
            Timber.e("Блютус устройство '$name' не найдено")
        }

    }

    private fun connect() {

        if (bluetoothAdapter.isEnabled and (stm32device != null)) {
            btStatus.value = Status.CONNECTING
            //bluetoothAdapter.getRemoteDevice(mac)
            connectScope(stm32device!!)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun autoConnect() {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                if (btStatus.value == Status.READY) {
                    if(stm32device == null)
                      getPairedDevices()
                    connect()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(DelicateCoroutinesApi::class)
    fun connectScope(device: BluetoothDevice) {
        GlobalScope.launch(Dispatchers.IO) {

            try {
                mSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
                Timber.i("Подключение...")
                mSocket?.connect()
                Timber.i("Подключились к устройству")
                btStatus.value = Status.CONNECTED
                sendReceiveScope()
            } catch (e: IOException) {
                mSocket?.close()
                Timber.e("Не смогли подключиться к устройсву ${e.message}")
                btStatus.value = Status.READY
            }

        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun sendReceiveScope() {

        val inStream: InputStream?
        val outStream: OutputStream?

        try {
            inStream = mSocket?.inputStream
            outStream = mSocket?.outputStream
        } catch (e: IOException) {
            Timber.e("Ошибка создания inputStream")
            return
        }

        val buf = inStream?.reader(Charsets.UTF_8)?.buffered(1024 * 1024 * 8)

        var isExit = false

        //Пинг
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    if (chOut.isEmpty) {
                        delay(1000)
                        outStream?.write(createMessage("0").toByteArray())
                    }
                } catch (e: IOException) {
                    isExit = true
                    break
                }
            }
        }

        //Отправка команд
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val data = chOut.receive()
                    outStream?.write(createMessage(data).toByteArray())
                } catch (e: IOException) {
                    isExit = true
                    break
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {

            while (true) {
                try {
                    if (isExit) throw IOException("Произошла ошибка ввода-вывода")
                    if (buf != null) {
                        var s = ""
                        val startTime = System.currentTimeMillis()
                        while (buf.ready() && s.length < (1024 * 16) && ((System.currentTimeMillis() - startTime) < 300)) {
                            val ss = buf.read().toChar()
                            s += ss
                            if (ss == '\n'){
                                break
                            }
                        }
                        if (s.isNotEmpty()) {
                            chIn.send(s)
                        }
                    } else {
                        Timber.e("buf == null")
                    }

                } catch (e: IOException) {
                    Timber.e("Ошибка в приемном потоке ${e.message}")
                    mSocket?.close()
                    btStatus.value = Status.READY
                    break  //При отключении подключения
                }
            }

        }

    }

    private fun createMessage(str: String): String {
        val crc = CRC8(str)
        return "!${str};${crc}$"
    }

}

