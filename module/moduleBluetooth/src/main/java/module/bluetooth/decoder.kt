package module.bluetooth

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Декодировка входных данных из порта, принимает все что приходит,
 * на выходе выводит готовые данные их пакета в виде строк
 */
class NetPacketDecoder(
    private val channelIn : Channel<String>,               //Входной канал от bt и wifi
    private val channelOut: Channel<String>,               //Выходной канал, содержит данные пакета, далее на парсер отправляем
) {

    /**
     * # Добавить команду
     */
    //fun addCmd(name: String, cb: (List<String>) -> Unit = { }) = cmdList.add(CliCommand(name, cb))

    private val channelRoute = Channel<String>(1000000)

    @OptIn(DelicateCoroutinesApi::class)
    fun run() {
        Timber.i("Запуск декодировщика")
        GlobalScope.launch(Dispatchers.IO) { decodeScope() }
        GlobalScope.launch(Dispatchers.IO) { commandDecoder() }
        //GlobalScope.launch(Dispatchers.IO) { cliDecoder() }
    }


    /**
     * Делим на строки и шлем на декодировку команд
     */
    private suspend fun decodeScope() {


        val bigStr: StringBuilder = StringBuilder()//Большая строка в которую и складируются данные с канала

        while (true) {

            val string = channelIn.receive()//replace('\r', '▒') //Получить строку с канала, может содежать несколько строк

            //Timber.e( "in>>>${string.length} "+string )

            if (string.isEmpty()) continue

            bigStr.append(string) //Захерячиваем в большую строку

            //MARK: Будем сами делить на строки
            while (true) {
                //Индекс \n
                val indexN = bigStr.indexOf('\n')

                if (indexN != -1)
                {
                    //Область полюбому имеет конец строки
                    //MARK: Чета есть, копируем в подстроку
                    val stringDoN = bigStr.substring(0, indexN)
                    bigStr.delete(0, indexN + 1)

                    //lastString += stringDoN
                    //channelRoute.send(lastString)

                    channelRoute.send(stringDoN)

                    //Timber.i( "out>>>${lastString.length} "+lastString )
                    //lastString = ""

                }
                else {
                    //Конец строки не найден
                    //MARK: Тут для дополнения прошлой строки
                    //Получить полную запись посленней строки
                    //lastString += bigStr
                    //bigStr.clear() //Он отжил свое)
                    break
                }

            }


        }


    }

    /**
     * Декодировка команд получаем целые строки без \n и вывод в cliDecoder тела пакета
     */
    private suspend fun commandDecoder() {

        while (true) {

            //val raw = "qqq¹xzassd¡¡¡¡¡²45³qw" //'¹' '²' '³' 179 '¡' 161    ¡ A1   §A7 ¿DF ¬AC
            val raw = channelRoute.receive()

            if (raw.isEmpty()) continue

            val posStart = raw.indexOf("!")
            val posCRC = raw.indexOf(";")
            val posEnd = raw.indexOf("$")

            if ((posStart == -1) || (posEnd == -1) || (posCRC == -1) || (posCRC !in (posStart + 1) until posEnd)) {
                Timber.e("Ошибка позиции пакета S:$posStart C:$posCRC E:$posEnd")
                continue
            }

            if (((posEnd - posCRC) > 4) || ((posEnd - posCRC) == 1)) {
                Timber.e("S:$posStart C:$posCRC E:$posEnd")
                Timber.e("L0 > Error > (PosE - PosCRC) > 4 or == 1")
                continue
            }

            val crcStr = raw.substring(posCRC + 1 until posEnd)
            var crc: Int
            try {
                crc = crcStr.toInt()
            } catch (e: Exception) {
                Timber.e("Ошибка преобразования CRC $crcStr")
                continue
            }

            val s = raw.substring(posStart + 1 until posCRC)
            if (s == "") {
                Timber.e("Нет тела команды $raw")
                continue
            }
            val crc8 = CRC8(s)

            if (crc.toUByte() != crc8) {
                Timber.e("Ошибка CRC $crc != CRC8 $crc8 $raw")
                continue
            }
            //Прошли все проверкu
            channelOut.send(s)
        }




    }



//    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
//    private val channelOutCommand = Channel<String>(1000000) //Готовые команды из пакета
//
//    data class CliCommand(var name: String, var cb: (List<String>) -> Unit)
//
//    //Перевод на сет
//    private val cmdList = mutableListOf<CliCommand>() //Список команд
//
//
//    /**
//     * Получение самой команды и его парсинг
//     */
//    private suspend fun cliDecoder() {
//        while (true) {
//            val s = channelOutCommand.receive()
//            parse(s)
//        }
//    }
//
//    private fun parse(str: String) {
//
//        if (str.isEmpty()) return
//
//        val l = str.split(' ').toMutableList()
//        val name = l.first()
//        l.removeFirst()
//        val arg: List<String> = l.filter { it.isNotEmpty() }
//        try {
//            val command: CliCommand = cmdList.first { it.name == name }
//            command.cb.invoke(arg)
//        } catch (e: Exception) {
//            Timber.e("CLI отсутствует команда $name")
//        }
//
//    }


}

/*
Name  : CRC-8
Poly  : 0x31    x^8 + x^5 + x^4 + 1
Init  : 0xFF
Revert: false
XorOut: 0x00
Check : 0xF7 ("123456789")
MaxLen: 15 байт(127 бит) - обнаружение
одинарных, двойных, тройных и всех нечетных ошибок
*/
fun CRC8(str: String): UByte {

    var crc: UByte = 0xFFu.toUByte()
    var i: Int

    for (j in str.indices) {
        crc = crc xor str[j].code.toUByte()

        for (k in 0 until 8) {
            crc = if (crc and 0x80u.toUByte() != 0u.toUByte()) {
                (crc.toUInt() shl 1 xor 0x31u.toUInt()).toUByte()
            } else {
                crc.toUInt().shl(1).toUByte()
            }
        }
    }
    return crc
}