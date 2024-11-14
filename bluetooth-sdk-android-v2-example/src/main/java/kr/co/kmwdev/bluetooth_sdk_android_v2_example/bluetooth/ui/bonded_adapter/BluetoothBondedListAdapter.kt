package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui.bonded_adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import androidx.recyclerview.widget.ListAdapter
import kr.co.hconnect.bluetooth_sdk_android.gatt.BLEState
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.R
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.model.BondModel
import kr.co.kmwdev.bluetooth_sdk_android_v2_example.databinding.ItemBluetoothDeviceBinding

class BluetoothBondedListAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : ListAdapter<BondModel, BluetoothBondedListAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(private val binding: ItemBluetoothDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(bondModel: BondModel, onClick: (BluetoothDevice) -> Unit) {
            val state = bondModel.state
            val bondState = bondModel.bondState
            val device = bondModel.device
            binding.name.text = device.name ?: "Unknown Device"


            when (state) {
                BLEState.STATE_CONNECTED -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_connected)
                    binding.status.visibility = View.VISIBLE
                }

                BLEState.STATE_CONNECTING -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_connecting)
                    binding.status.visibility = View.VISIBLE
                }

                BLEState.STATE_DISCONNECTING -> {
                    binding.status.text =
                        binding.root.context.getString(R.string.bluetooth_connection_disconnecting)
                    binding.status.visibility = View.VISIBLE
                }

                else -> {
                    binding.status.text = ""
                    binding.status.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                onClick(device)
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
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BondModel>() {
            override fun areItemsTheSame(
                oldItem: BondModel,
                newItem: BondModel
            ): Boolean {
                return oldItem.device.address == newItem.device.address
            }

            override fun areContentsTheSame(
                oldItem: BondModel,
                newItem: BondModel
            ): Boolean {
                return oldItem.state == newItem.state &&
                        oldItem.bondState == newItem.bondState &&
                        oldItem.device.address == newItem.device.address
            }
        }
    }
}
