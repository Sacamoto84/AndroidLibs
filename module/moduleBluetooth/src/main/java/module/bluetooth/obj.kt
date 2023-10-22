package module.bluetooth

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Содержит декодированные строки данные из пакета, после декодера
 */
val chDecodedString = Channel<String>(1000) //Готовые команды из пакета

//Канал передачи из STM32
val chBtReceive = Channel<String>(Channel.UNLIMITED)

//Канал передачи в STM32, просто записываем команды
val chBtSend = Channel<String>(Channel.UNLIMITED)


var bt = BT("Tonometr", chBtReceive, chBtSend)

val decoder = NetPacketDecoder(chBtReceive, chDecodedString)

@OptIn(DelicateCoroutinesApi::class)
fun send(value: Char) {
    GlobalScope.launch(Dispatchers.IO) {
        chBtSend.send(value.toString())
    }
}