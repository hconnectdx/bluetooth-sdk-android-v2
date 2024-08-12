package kr.co.hconnect.polihealth_sdk_android_v2_example.characteristic_detail

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import kr.co.hconnect.bluetooth_sdk_android.HCBle
import kr.co.hconnect.polihealth_sdk_android.PoliBLE
import kr.co.hconnect.polihealth_sdk_android_v2_example.R

class CharacteristicDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_characteristic_detail)

        val characteristicUuid: String? = intent.getStringExtra("CHARACTERISTIC_UUID")
        val tvCharacteristicUuid: TextView = findViewById(R.id.tv_characteristic_uuid)
        val btnNotify: Button = findViewById(R.id.btn_notify)
        val btnWriteCall: Button = findViewById(R.id.btn_write_call)
        val btnWriteSms: Button = findViewById(R.id.btn_write_sms)
        val btnWriteCallEmpty: Button = findViewById(R.id.btn_write_call_empty)
        val btnWriteKakao: Button = findViewById(R.id.btn_write_kakao)

        tvCharacteristicUuid.text = "Characteristic UUID: $characteristicUuid"

        btnNotify.setOnClickListener {
            // Notify 기능을 구현합니다.
            Toast.makeText(this, "밴드를 구독합니다.", Toast.LENGTH_SHORT).show()
            PoliBLE.apply {
                setCharacteristicNotification(true)
            }
        }

        btnWriteCall.setOnClickListener {
            HCBle.writeCharacteristic("Call_12:21_02-123-1234_".toByteArray())
        }

        btnWriteSms.setOnClickListener {
            HCBle.writeCharacteristic("Message_12:10_010-1234-1234_".toByteArray())
        }

        btnWriteCallEmpty.setOnClickListener {
            HCBle.writeCharacteristic("MissedCall_12:34_043-123-1234_".toByteArray())
        }

        btnWriteKakao.setOnClickListener {
            HCBle.writeCharacteristic("Talk_12:45_".toByteArray())
        }


    }
}
