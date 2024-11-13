package kr.co.kmwdev.bluetooth_sdk_android_v2_example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.BluetoothConnectionActivity
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.util.MyPermission

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            val intent = Intent(this, BluetoothConnectionActivity::class.java)
            startActivity(intent)
        }
    }
}