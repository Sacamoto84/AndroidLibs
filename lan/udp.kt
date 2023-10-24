package lan

import android.os.StrictMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UDP{
    /**
     * Прием строк из UDP порта и помещение в канал
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun receiveScope(port: Int = 8888, channel: Channel<String>) {

        GlobalScope.launch(Dispatchers.IO){
            println("Запуск UDP receiveScope")
            val buffer = ByteArray(1024 * 1024)
            val socket = DatagramSocket(port)
            socket.broadcast = true
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receiveBufferSize = 1024 * 1024
            while (true) {
                socket.receive(packet)
                val string = String( packet.data.copyOfRange( 0, packet.length )) //println("!UDPRoutine! packet RAW=[$string")
                channel.send(string)
            }
        }

    }


    //=====================================================
    // Отправить Udp сообщение * Возвращает OK или ошибку
    // region // sendUDP(messageStr: String, ip :String, port: Int): String

    fun sendUDP(messageStr: String, ip: String, port: Int): String {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            val sendData = messageStr.toByteArray()
            val sendPacket =
                    DatagramPacket(sendData, sendData.size, InetAddress.getByName(ip), port)
            socket.send(sendPacket)
            println("sendUDP: $ip:$port")
        } catch (e: IOException) {
            Timber.e("IOException: " + e.message)
            return e.message.toString()
        }
        return "OK"
    }

}