package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.connectable_adapter

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import androidx.recyclerview.widget.ListAdapter
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.ScanResultModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.databinding.ItemBluetoothDeviceBinding

class BluetoothScanListAdapter(
    private val onDeviceClick: (ScanResult) -> Unit
) : ListAdapter<ScanResultModel, BluetoothScanListAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(private val binding: ItemBluetoothDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(scanResultModel: ScanResultModel, onClick: (ScanResult) -> Unit) {
            val state = scanResultModel.state
            val scanResult = scanResultModel.scanResult
            binding.name.text = scanResult.device.name ?: "Unknown Device"

            when (state) {
                BLEState.STATE_CONNECTED -> {
                    binding.status.text = "Connected"
                    binding.status.visibility = View.VISIBLE
                }

                BLEState.STATE_CONNECTING -> {
                    binding.status.text = "Connecting"
                    binding.status.visibility = View.VISIBLE
                }

                BLEState.STATE_DISCONNECTING -> {
                    binding.status.text = "disconnecting"
                    binding.status.visibility = View.VISIBLE
                }

                else -> {
                    binding.status.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                onClick(scanResult)
            }
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBluetoothDeviceBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDeviceClick)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScanResultModel>() {
            override fun areItemsTheSame(
                oldItem: ScanResultModel,
                newItem: ScanResultModel
            ): Boolean {
                return oldItem.scanResult.device.address == newItem.scanResult.device.address
            }

            override fun areContentsTheSame(
                oldItem: ScanResultModel,
                newItem: ScanResultModel
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
