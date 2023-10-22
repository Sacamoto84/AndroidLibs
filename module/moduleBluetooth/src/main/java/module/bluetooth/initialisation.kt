package module.bluetooth

import android.content.Context


fun btInitialization(context: Context) {

    bt.init(context)
    //bt.getPairedDevices()
    bt.autoConnect()

    decoder.run()

//    //Следим за тем чтобы при дисконекте снова прошла инициализация компос
//    GlobalScope.launch(Dispatchers.IO) {
//        bt.btStatus.collect {
//
//            if (it == module.bluetooth.BT.Status.DISCONNECT)
//            {
//                initCompose = false
//            }
//
//        }
//    }

}