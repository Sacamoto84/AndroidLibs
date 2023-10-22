package module.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import timber.log.Timber

//import android.app.Activity
//import android.bluetooth.BluetoothAdapter
//import android.content.Intent
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import timber.log.Timber
//
//
//@Composable
//fun ButtonBluetooth() {
//
//    val enableBluetoothContract = rememberLauncherForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) {
//
//        bt.btStatus.value = if (it.resultCode == Activity.RESULT_OK) {
//            Timber.w("Включение блютуза пользователем")
//            BT.Status.READY
//        } else {
//            Timber.w("Включение блютуза отклонено пользователем")
//            BT.Status.NOTREADY
//        }
//
//    }
//
//    // This intent will open the enable bluetooth dialog
//    val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//
//    if (bt.btStatus.collectAsState().value == BT.Status.NOTREADY) {
//        Column(
//            modifier = Modifier.fillMaxSize(),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(text = "На телефоне отключен Bluetooth")
//
//            Button(onClick = {
//                if (!bt.bluetoothAdapter.isEnabled) { //Блютуз выключен и идет запрос пользоваталя на влючение блютуза
//                    enableBluetoothContract.launch(enableBluetoothIntent)
//                }
//            }) {
//                Text(text = "Включить Bluetooth")
//            }
//
//        }
//    }
//
//}