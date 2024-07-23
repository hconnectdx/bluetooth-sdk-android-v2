import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCharacteristic

class CustomExpandableListAdapter(
    private val context: Context,
    private val serviceList: List<BluetoothGattService>,
    private val characteristicMap: Map<String, List<BluetoothGattCharacteristic>>
) : BaseExpandableListAdapter() {

    override fun getChild(groupPosition: Int, childPosition: Int): Any {
        val serviceUuid = serviceList[groupPosition].uuid.toString()
        return characteristicMap[serviceUuid]?.get(childPosition) ?: ""
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val serviceUuid = serviceList[groupPosition].uuid.toString()
        return characteristicMap[serviceUuid]?.size ?: 0
    }

    override fun getGroup(groupPosition: Int): Any {
        return serviceList[groupPosition]
    }

    override fun getGroupCount(): Int {
        return serviceList.size
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val service = getGroup(groupPosition) as BluetoothGattService
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_expandable_list_item_1, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = service.uuid.toString()
        return view
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val characteristic = getChild(groupPosition, childPosition) as BluetoothGattCharacteristic
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = characteristic.uuid.toString()
        return view
    }
}
