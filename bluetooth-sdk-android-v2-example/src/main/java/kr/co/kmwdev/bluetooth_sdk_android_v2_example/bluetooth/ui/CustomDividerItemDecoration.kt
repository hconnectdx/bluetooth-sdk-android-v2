package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView

class CustomDividerItemDecoration(
    context: Context,
    orientation: Int
) : RecyclerView.ItemDecoration() {

    private val divider: Drawable?

    init {
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider))
        divider = attrs.getDrawable(0)
        attrs.recycle()
        setOrientation(orientation)
    }

    private var orientation: Int = orientation

    private fun setOrientation(orientation: Int) {
        require(!(orientation != RecyclerView.VERTICAL && orientation != RecyclerView.HORIZONTAL)) {
            "Invalid orientation"
        }
        this.orientation = orientation
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null || divider == null) {
            return
        }

        if (orientation == RecyclerView.VERTICAL) {
            drawVerticalDividers(canvas, parent)
        } else {
            drawHorizontalDividers(canvas, parent)
        }
    }

    private fun drawVerticalDividers(canvas: Canvas, parent: RecyclerView) {
        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight

        val childCount = parent.childCount
        for (i in 0 until childCount - 1) { // 마지막 아이템 제외
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = child.bottom + params.bottomMargin
            val bottom = top + (divider?.intrinsicHeight ?: 0)
            divider?.setBounds(left, top, right, bottom)
            divider?.draw(canvas)
        }
    }

    private fun drawHorizontalDividers(canvas: Canvas, parent: RecyclerView) {
        val top = parent.paddingTop
        val bottom = parent.height - parent.paddingBottom

        val childCount = parent.childCount
        for (i in 0 until childCount - 1) { // 마지막 아이템 제외
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val left = child.right + params.rightMargin
            val right = left + (divider?.intrinsicWidth ?: 0)
            divider?.setBounds(left, top, right, bottom)
            divider?.draw(canvas)
        }
    }
}
