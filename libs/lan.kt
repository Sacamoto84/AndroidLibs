package libs

import android.content.Context
import android.net.wifi.WifiManager
import android.os.StrictMode
import android.text.format.Formatter
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.experimental.and

// ! Добавить в манифест
// <uses-permission android:name= "android.permission.INTERNET" />
// <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
// <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

/*

   * ddddddd
   # dffffff
   ! eee3333

 */

/*

*     ░░░░░░░░░
    * ▒▒▒▒▒▒▒▒▒ opopop ▒▒▒▒▒▒▒▒
    *  ╔═════════╗
    *  ║ sendUDP ║====> ┌─────┐
    *  ╚═════════╝      │  !  │
    *                   └─────┘


*/

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
        Log.e("sendUDP", "IOException: " + e.message)
        return e.message.toString()
    }
    return "OK"
}
//endregion
//=====================================================

//=====================================================
// val Context = applicationContext
// Получить IP адресс Wifi -> "192.168.0.100"
// region > readIP(contex : Context): String
fun readIP(contex : Context): String {
    val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
    StrictMode.setThreadPolicy(policy)
    try {
        val wifiManager = contex.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        println("IP: $ipAddress")
        return ipAddress
    } catch (e: IOException) {
        Log.e("readIP1", "IOException: " + e.message)
    }
    return "127.0.0.1"
}
//endregion
//=====================================================

//=====================================================
// IP сделать broadcast "192.168.0.100" -> "192.168.0.255"
// region > ipToBroadCast(value : String): String
fun ipToBroadCast(value : String): String {
    val ip = InetAddress.getByName(value)
    val bytes = ip.address
    val ibytes: IntArray = IntArray(4)
    for (i in bytes.indices) {
        if (bytes[i] >= 0) ibytes[i] = bytes[i].toInt()
        else {
            ibytes[i] = 256 + bytes[i].toInt()
        }
    }
    ibytes[3] = 255
    val broadcast = "${ibytes[0]}.${ibytes[1]}.${ibytes[2]}.255"
    println("ipToBroadCast : $broadcast")
    return broadcast
}
//endregion
//=====================================================