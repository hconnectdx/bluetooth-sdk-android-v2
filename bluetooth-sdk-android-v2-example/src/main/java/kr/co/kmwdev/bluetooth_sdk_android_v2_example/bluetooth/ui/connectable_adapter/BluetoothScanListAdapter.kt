package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.connectable_adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import androidx.recyclerview.widget.ListAdapter
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.ScanResultModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.databinding.ItemBluetoothDeviceBinding

class BluetoothScanListAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : ListAdapter<ScanResultModel, BluetoothScanListAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(private val binding: ItemBluetoothDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(scanResultModel: ScanResultModel, onClick: (BluetoothDevice) -> Unit) {
            val state = scanResultModel.state
            val device = scanResultModel.device
            binding.name.text = device.name ?: "Unknown Device"

            when (state) {
                BLEState.STATE_CONNECTED -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_connected)
                    binding.status.visibility = View.VISIBLE
                    setStatusTextColor(R.color.primary)

                }

                BLEState.STATE_CONNECTING -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_connecting)
                    binding.status.visibility = View.VISIBLE
                    setStatusTextColor(R.color.gray70)
                }

                BLEState.STATE_DISCONNECTING -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_disconnecting)
                    binding.status.visibility = View.VISIBLE
                    setStatusTextColor(R.color.gray70)
                }

                else -> {
                    binding.status.visibility = View.GONE
                    setStatusTextColor(R.color.gray70)
                }
            }

            binding.root.setOnClickListener {
                onClick(device)
            }
            binding.executePendingBindings()
        }

        private fun setStatusTextColor(res: Int = R.color.gray70) {
            binding.name.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    res
                )
            )
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
                return oldItem.device.address == newItem.device.address
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
