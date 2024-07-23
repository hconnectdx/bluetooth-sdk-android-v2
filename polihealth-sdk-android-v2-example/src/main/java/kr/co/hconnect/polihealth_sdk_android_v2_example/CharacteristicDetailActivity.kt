package kr.co.hconnect.polihealth_sdk_android_v2_example

import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import kr.co.hconnect.polihealth_sdk_android.PoliBLE

class CharacteristicDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_characteristic_detail)

        val characteristicUuid: String? = intent.getStringExtra("CHARACTERISTIC_UUID")
        val tvCharacteristicUuid: TextView = findViewById(R.id.tv_characteristic_uuid)
        val btnNotify: Button = findViewById(R.id.btn_notify)

        tvCharacteristicUuid.text = "Characteristic UUID: $characteristicUuid"

        btnNotify.setOnClickListener {
            // Notify 기능을 구현합니다.
            Toast.makeText(this, "밴드를 구독합니다.", Toast.LENGTH_SHORT).show()
            PoliBLE.apply {
                setCharacteristicNotification(true)
            }
        }
    }
}
